package com.project.common.security.grpc;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.project.common.security.AppSecurityProperties;
import com.project.common.security.AuthorityCache;
import com.project.common.security.AuthorityCacheAutoConfiguration;
import com.project.common.security.AuthorityResolver;
import com.project.common.security.ServletSecurityAutoConfiguration;
import com.project.grpc.auth.v1.AuthorizationServiceGrpc;

import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import net.devh.boot.grpc.client.nameresolver.NameResolverRegistration;

/**
 * Role + quyền lấy từ auth-service qua gRPC ({@code grpc.client.auth-service.address}), cache bằng
 * {@link AuthorityCache}. Chạy trước {@link ServletSecurityAutoConfiguration} để thay {@code ClaimAuthorityResolver}
 * mặc định ở đó. auth-service tự khai báo {@link AuthorityResolver} đọc DB nên bean ở đây không được tạo.
 */
@AutoConfiguration(before = ServletSecurityAutoConfiguration.class, after = AuthorityCacheAutoConfiguration.class,
        afterName = "net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration")
@ConditionalOnClass({ GrpcChannelFactory.class, AuthorizationServiceGrpc.class })
@ConditionalOnBean(GrpcChannelFactory.class)
@ConditionalOnProperty(prefix = "app.security", name = "authority-source", havingValue = "auth-service",
        matchIfMissing = true)
@EnableConfigurationProperties(AppSecurityProperties.class)
public class AuthorityResolverAutoConfiguration {

    /** Nhận {@link NameResolverRegistration} để địa chỉ discovery:/// dùng được (xem AuditGrpcAutoConfiguration). */
    @Bean
    @ConditionalOnMissingBean
    AuthorityResolver authorityResolver(GrpcChannelFactory channelFactory, AuthorityCache authorityCache,
            AppSecurityProperties properties, NameResolverRegistration nameResolverRegistration) {
        return new AuthServiceAuthorityResolver(
                AuthorizationServiceGrpc.newBlockingStub(channelFactory.createChannel("auth-service")),
                authorityCache, properties.getAuthorityDeadline());
    }
}
