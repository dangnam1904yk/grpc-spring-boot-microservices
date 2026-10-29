package com.project.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.i18n.Messages;

/**
 * Mỗi service tự kiểm tra JWT (không tin mạng nội bộ): REST bắt buộc có token, trừ actuator health
 * và {@code app.security.public-paths}. Phân quyền chi tiết (ADMIN, chủ sở hữu) làm trong service bằng
 * {@link CurrentUser}.
 * <p>
 * Service muốn quy tắc riêng thì tự khai báo {@link SecurityFilterChain} / {@link JwtDecoder}.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration" })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ SecurityFilterChain.class, JwtDecoder.class })
@EnableConfigurationProperties(AppSecurityProperties.class)
@EnableMethodSecurity
public class ServletSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JwtDecoder jwtDecoder(AppSecurityProperties properties) {
        Assert.hasText(properties.getJwksUri(), "Thiếu cấu hình app.security.jwks-uri (URL JWKS của auth-service)");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwksUri()).build();
        OAuth2TokenValidator<Jwt> validator = StringUtils.hasText(properties.getIssuer())
                ? JwtValidators.createDefaultWithIssuer(properties.getIssuer())
                : JwtValidators.createDefault();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validator));
        return decoder;
    }

    /** claim "roles": ["ADMIN"] -> authority "ROLE_ADMIN" (dùng được với hasRole('ADMIN')). */
    @Bean
    @ConditionalOnMissingBean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(AuthUser.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemDetailSecurityHandler problemDetailSecurityHandler(ObjectMapper objectMapper,
            ObjectProvider<Messages> messages) {
        return new ProblemDetailSecurityHandler(objectMapper, messages.getIfAvailable(Messages::commonOnly));
    }

    @Bean
    @ConditionalOnMissingBean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppSecurityProperties properties,
            JwtAuthenticationConverter jwtAuthenticationConverter, ProblemDetailSecurityHandler problemHandler)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll();
                    for (String publicPath : properties.getPublicPaths()) {
                        String[] parts = publicPath.trim().split("\\s+", 2);
                        if (parts.length == 2) {
                            auth.requestMatchers(HttpMethod.valueOf(parts[0].toUpperCase()), parts[1]).permitAll();
                        } else {
                            auth.requestMatchers(parts[0]).permitAll();
                        }
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler));
        return http.build();
    }
}
