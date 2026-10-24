package com.project.common.security.grpc;

import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Gắn access token của user đang gọi request vào header {@code authorization} của mọi lời gọi gRPC đi ra,
 * để service phía sau biết ai đang thao tác (và tự kiểm tra lại token).
 * <p>
 * Token được đọc lúc tạo call (trên thread của request), nên vẫn đúng khi kết quả được xử lý bất đồng bộ.
 */
public class BearerTokenPropagationInterceptor implements ClientInterceptor {

    static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization",
            Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {
        String token = CurrentUser.current().map(AuthUser::token).orElse(null);
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (token != null && !headers.containsKey(AUTHORIZATION)) {
                    headers.put(AUTHORIZATION, "Bearer " + token);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
