package com.project.common.security.grpc;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.vote.UnanimousBased;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import com.project.common.i18n.Messages;
import com.project.common.security.ServletSecurityAutoConfiguration;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.server.security.authentication.BearerAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.check.AccessPredicate;
import net.devh.boot.grpc.server.security.check.AccessPredicateVoter;
import net.devh.boot.grpc.server.security.check.GrpcSecurityMetadataSource;
import net.devh.boot.grpc.server.security.check.ManualGrpcSecurityMetadataSource;

/**
 * Bảo mật cho gRPC, dùng chung JwtDecoder với REST:
 * <ul>
 * <li>Server: đọc "authorization: Bearer ..." -> kiểm tra JWT -> mọi RPC phải đăng nhập
 * (trừ health check / reflection). Không có token -> UNAUTHENTICATED.</li>
 * <li>Client: tự chuyển tiếp token của user hiện tại ({@link BearerTokenPropagationInterceptor}).</li>
 * </ul>
 * Phải chạy trước GrpcServerSecurityAutoConfiguration của net.devh: auto-config đó chỉ bật khi đã có
 * bean AuthenticationManager / AccessDecisionManager.
 */
@AutoConfiguration(after = ServletSecurityAutoConfiguration.class,
        beforeName = "net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration")
@ConditionalOnClass({ JwtDecoder.class, JwtAuthenticationProvider.class })
public class GrpcSecurityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ GrpcAuthenticationReader.class, HealthGrpc.class })
    @ConditionalOnBean(JwtDecoder.class)
    static class ServerSecurity {

        @Bean
        @ConditionalOnMissingBean
        GrpcAuthenticationReader grpcAuthenticationReader() {
            return new BearerAuthenticationReader(BearerTokenAuthenticationToken::new);
        }

        @Bean
        @ConditionalOnMissingBean
        AuthenticationManager grpcAuthenticationManager(JwtDecoder jwtDecoder,
                JwtAuthenticationConverter jwtAuthenticationConverter) {
            JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
            provider.setJwtAuthenticationConverter(jwtAuthenticationConverter);
            return new ProviderManager(provider);
        }

        @Bean
        @ConditionalOnMissingBean
        GrpcSecurityMetadataSource grpcSecurityMetadataSource() {
            return new ManualGrpcSecurityMetadataSource()
                    .setDefault(AccessPredicate.authenticated())
                    .set(HealthGrpc.getServiceDescriptor(), AccessPredicate.permitAll())
                    .set(ServerReflectionGrpc.getServiceDescriptor(), AccessPredicate.permitAll());
        }

        // net.devh 3.x vẫn dùng AccessDecisionManager (deprecated từ Spring Security 6) cho phân quyền gRPC
        @Bean
        @ConditionalOnMissingBean
        @SuppressWarnings("deprecation")
        AccessDecisionManager grpcAccessDecisionManager() {
            return new UnanimousBased(List.of(new AccessPredicateVoter()));
        }

        @Bean
        @ConditionalOnMissingBean
        GrpcSecurityExceptionAdvice grpcSecurityExceptionAdvice(ObjectProvider<Messages> messages) {
            return new GrpcSecurityExceptionAdvice(messages.getIfAvailable(Messages::commonOnly));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GrpcGlobalClientInterceptor.class)
    static class ClientSecurity {

        @Bean
        @GrpcGlobalClientInterceptor
        @ConditionalOnMissingBean
        BearerTokenPropagationInterceptor bearerTokenPropagationInterceptor() {
            return new BearerTokenPropagationInterceptor();
        }
    }
}
