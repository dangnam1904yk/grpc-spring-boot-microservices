package com.project.common.audit.grpc;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.MDC;
import org.springframework.core.Ordered;

import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import com.project.common.audit.AuditContext;
import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditSink;
import com.project.common.audit.AuditType;
import com.project.common.audit.Sanitizer;
import com.project.common.id.UuidV7;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.common.util.InterceptorOrder;

/**
 * Ghi log mỗi gRPC call đi vào service (method, request, response, status) và đặt MDC (request id, IP)
 * trong lúc xử lý, để thay đổi dữ liệu do call gây ra được nối với request gốc ở service gọi.
 * <p>
 * Đứng ngoài cùng (trước cả bộ xử lý exception và xác thực của net.devh) để thấy mọi kết quả: thành công,
 * lỗi nghiệp vụ, thiếu token, quá tải. User do {@link GrpcAuditUserInterceptor} (chạy sau bước xác thực)
 * đặt vào MDC.
 */
@Slf4j
public class GrpcAuditServerInterceptor implements ServerInterceptor, Ordered {

    /** Không ghi log: health check, reflection, và chính API nhận log (tránh vòng lặp). */
    private static final List<String> SKIPPED_SERVICE_PREFIXES = List.of("grpc.health.", "grpc.reflection.",
            "audit.v1.");

    private final AuditSink sink;
    private final Sanitizer sanitizer;
    private final String serviceName;

    public GrpcAuditServerInterceptor(AuditSink sink, Sanitizer sanitizer, String serviceName) {
        this.sink = sink;
        this.sanitizer = sanitizer;
        this.serviceName = serviceName;
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.ORDER_GLOBAL_EXCEPTION_HANDLING - 100;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        if (SKIPPED_SERVICE_PREFIXES.stream().anyMatch(method::startsWith)) {
            return next.startCall(call, headers);
        }

        String requestId = AuditContext.requestIdOrNew(headers.get(AuditMetadata.REQUEST_ID));
        String forwardedIp = headers.get(AuditMetadata.CLIENT_IP);
        String clientIp = forwardedIp != null ? forwardedIp : peerIp(call);
        CallState state = new CallState(System.nanoTime());

        ServerCall<ReqT, RespT> auditedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void sendMessage(RespT message) {
                state.response = text(message);
                super.sendMessage(message);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                record(method, requestId, clientIp, state, status);
                super.close(status, trailers);
            }
        };

        Map<String, String> mdc = Map.of(AuditContext.MDC_REQUEST_ID, requestId,
                AuditContext.MDC_CLIENT_IP, clientIp);
        ServerCall.Listener<ReqT> delegate = withMdc(mdc, () -> next.startCall(auditedCall, headers));

        // Mỗi callback có thể chạy trên thread khác nhau của pool gRPC: đặt MDC lại mỗi lần
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                state.request = text(message);
                withMdc(mdc, () -> {
                    super.onMessage(message);
                    return null;
                });
            }

            @Override
            public void onHalfClose() {
                withMdc(mdc, () -> {
                    super.onHalfClose();
                    return null;
                });
            }

            @Override
            public void onCancel() {
                withMdc(mdc, () -> {
                    super.onCancel();
                    return null;
                });
            }

            @Override
            public void onComplete() {
                withMdc(mdc, () -> {
                    super.onComplete();
                    return null;
                });
            }

            @Override
            public void onReady() {
                withMdc(mdc, () -> {
                    super.onReady();
                    return null;
                });
            }
        };
    }

    private static <T> T withMdc(Map<String, String> mdc, Supplier<T> action) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        mdc.forEach(MDC::put);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    private void record(String method, String requestId, String clientIp, CallState state, Status status) {
        try {
            // close() được gọi bên trong callback (service trả kết quả / exception handler trả lỗi):
            // MDC lúc này đã có user do GrpcAuditUserInterceptor đặt
            AuditContext.User user = AuditContext.currentUser();
            sink.publish(AuditEvent.builder()
                    .id(UuidV7.next())
                    .type(AuditType.GRPC)
                    .requestId(requestId)
                    .service(serviceName)
                    .timestamp(Instant.now())
                    .clientIp(clientIp)
                    .userId(user == null ? null : user.id())
                    .userEmail(user == null ? null : user.email())
                    .method(method)
                    .requestBody(state.request)
                    .status(status.getCode().name())
                    .responseBody(state.response)
                    .durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startNanos))
                    .error(status.isOk() ? null : status.getDescription())
                    .build());
        } catch (Exception e) {
            log.warn("Không tạo được audit log cho gRPC {}: {}", method, e.toString());
        }
    }

    private String text(Object message) {
        String text = message instanceof MessageOrBuilder proto
                ? TextFormat.printer().emittingSingleLine(true).printToString(proto)
                : String.valueOf(message);
        return sanitizer.truncate(text);
    }

    private static String peerIp(ServerCall<?, ?> call) {
        SocketAddress address = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }
        return String.valueOf(address);
    }

    private static final class CallState {
        final long startNanos;
        volatile String request;
        volatile String response;

        CallState(long startNanos) {
            this.startNanos = startNanos;
        }
    }
}
