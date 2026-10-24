package com.project.common.audit.grpc;

import com.project.common.audit.AuditContext;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Gửi kèm request id và IP client gốc trong mỗi gRPC call, để log ở service đích nối được với request ban đầu.
 * Lấy từ MDC nên phải gọi trên thread của request (giống chuyển tiếp token).
 */
public class GrpcAuditClientInterceptor implements ClientInterceptor {

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {
        String requestId = AuditContext.requestId();
        String clientIp = AuditContext.clientIp();
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (requestId != null) {
                    headers.put(AuditMetadata.REQUEST_ID, requestId);
                }
                if (clientIp != null) {
                    headers.put(AuditMetadata.CLIENT_IP, clientIp);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
