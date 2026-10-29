package com.project.common.i18n;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Gắn ngôn ngữ của request hiện tại vào metadata {@code accept-language} của mọi lời gọi gRPC đi ra, để lỗi trả về
 * từ service khác cùng ngôn ngữ với người dùng. Đọc lúc tạo call (trên thread của request), như token.
 */
public class GrpcLocaleClientInterceptor implements ClientInterceptor {

    private final Messages messages;

    public GrpcLocaleClientInterceptor(Messages messages) {
        this.messages = messages;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions, Channel next) {
        String language = messages.locale().toLanguageTag();
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (!headers.containsKey(GrpcLocaleServerInterceptor.ACCEPT_LANGUAGE)) {
                    headers.put(GrpcLocaleServerInterceptor.ACCEPT_LANGUAGE, language);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
