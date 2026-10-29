package com.project.common.i18n;

import java.util.Locale;
import java.util.function.Supplier;

import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.common.util.InterceptorOrder;

/**
 * Đọc ngôn ngữ từ metadata {@code accept-language} và đặt vào {@link LocaleContextHolder} trong lúc xử lý call,
 * để lỗi (GrpcExceptionAdvice) và message của service dùng đúng ngôn ngữ của người dùng gốc.
 * <p>
 * Đứng ngoài bộ xử lý exception của net.devh để advice chạy trong locale này. Mỗi callback có thể chạy trên thread
 * khác nhau của pool gRPC nên đặt lại mỗi lần (giống MDC của audit).
 */
public class GrpcLocaleServerInterceptor implements ServerInterceptor, Ordered {

    static final Metadata.Key<String> ACCEPT_LANGUAGE = Metadata.Key.of("accept-language",
            Metadata.ASCII_STRING_MARSHALLER);

    private final Messages messages;

    public GrpcLocaleServerInterceptor(Messages messages) {
        this.messages = messages;
    }

    @Override
    public int getOrder() {
        return InterceptorOrder.ORDER_GLOBAL_EXCEPTION_HANDLING - 50;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        Locale locale = messages.resolve(headers.get(ACCEPT_LANGUAGE));
        ServerCall.Listener<ReqT> delegate = withLocale(locale, () -> next.startCall(call, headers));
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                withLocale(locale, () -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                withLocale(locale, super::onHalfClose);
            }

            @Override
            public void onCancel() {
                withLocale(locale, super::onCancel);
            }

            @Override
            public void onComplete() {
                withLocale(locale, super::onComplete);
            }

            @Override
            public void onReady() {
                withLocale(locale, super::onReady);
            }
        };
    }

    private static void withLocale(Locale locale, Runnable action) {
        withLocale(locale, () -> {
            action.run();
            return null;
        });
    }

    private static <T> T withLocale(Locale locale, Supplier<T> action) {
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        LocaleContextHolder.setLocale(locale);
        try {
            return action.get();
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }
}
