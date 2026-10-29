package com.project.common.i18n;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * Đa ngôn ngữ cho mọi service có common-grpc. Service chỉ cần 2 file
 * {@code src/main/resources/i18n/messages_vi.properties} và {@code messages_en.properties}.
 * <ul>
 * <li>{@code messageSource}: message của service + message chung của common-grpc, UTF-8. Dùng luôn cho
 * {@code message = "{key}"} của annotation Bean Validation.</li>
 * <li>REST: ngôn ngữ lấy từ header {@code Accept-Language}; gRPC: metadata {@code accept-language}, tự chuyển tiếp
 * khi gọi service khác.</li>
 * </ul>
 */
@AutoConfiguration(before = { MessageSourceAutoConfiguration.class, WebMvcAutoConfiguration.class })
@EnableConfigurationProperties(I18nProperties.class)
public class I18nAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "messageSource")
    MessageSource messageSource(I18nProperties properties) {
        return Messages.messageSource(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    Messages messages(MessageSource messageSource, I18nProperties properties) {
        // Code không có request (job nền, khởi động) dùng ngôn ngữ mặc định thay vì locale của JVM, để Validator
        // (đọc LocaleContextHolder) và Messages cùng một ngôn ngữ
        LocaleContextHolder.setDefaultLocale(properties.getDefaultLocale());
        return new Messages(messageSource, properties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(LocaleResolver.class)
    static class Servlet {

        /** Chỉ nhận ngôn ngữ được hỗ trợ; không gửi / không hỗ trợ -&gt; ngôn ngữ mặc định (không theo locale máy chủ). */
        @Bean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
        @ConditionalOnMissingBean(name = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME)
        LocaleResolver localeResolver(I18nProperties properties) {
            AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
            resolver.setSupportedLocales(properties.getSupportedLocales());
            resolver.setDefaultLocale(properties.getDefaultLocale());
            return resolver;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GrpcGlobalServerInterceptor.class)
    static class GrpcServer {

        @Bean
        @GrpcGlobalServerInterceptor
        @ConditionalOnMissingBean
        GrpcLocaleServerInterceptor grpcLocaleServerInterceptor(Messages messages) {
            return new GrpcLocaleServerInterceptor(messages);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GrpcGlobalClientInterceptor.class)
    static class GrpcClient {

        @Bean
        @GrpcGlobalClientInterceptor
        @ConditionalOnMissingBean
        GrpcLocaleClientInterceptor grpcLocaleClientInterceptor(Messages messages) {
            return new GrpcLocaleClientInterceptor(messages);
        }
    }
}
