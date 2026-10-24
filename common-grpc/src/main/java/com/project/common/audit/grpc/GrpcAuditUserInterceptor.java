package com.project.common.audit.grpc;

import org.springframework.core.Ordered;

import com.project.common.audit.AuditContext;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.common.util.InterceptorOrder;

/**
 * Chạy ngay sau bước xác thực của net.devh: đưa user (từ SecurityContext) vào MDC.
 * SecurityContext bị xoá khi callback ra khỏi lớp bảo mật, còn MDC thì giữ tới khi
 * {@link GrpcAuditServerInterceptor} (lớp ngoài cùng) ghi log và dọn MDC.
 */
public class GrpcAuditUserInterceptor implements ServerInterceptor, Ordered {

    @Override
    public int getOrder() {
        return InterceptorOrder.ORDER_SECURITY_AUTHORISATION + 100;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {
            @Override
            public void onMessage(ReqT message) {
                AuditContext.bindUser(AuditContext.currentUser());
                super.onMessage(message);
            }

            @Override
            public void onHalfClose() {
                AuditContext.bindUser(AuditContext.currentUser());
                super.onHalfClose();
            }
        };
    }
}
