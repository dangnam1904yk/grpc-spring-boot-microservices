package com.project.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.project.auth.service.AuthorityService;
import com.project.common.security.AuthorityResolver;

/**
 * auth-service dùng SecurityFilterChain / JwtDecoder (JWKS của Keycloak) mặc định của common-grpc. Riêng role +
 * quyền thì đọc thẳng DB thay vì gọi gRPC vào chính mình (không qua cache Redis).
 */
@Configuration
public class SecurityConfig {

    @Bean
    AuthorityResolver authorityResolver(AuthorityService authorityService) {
        return authorityService::authoritiesOf;
    }
}
