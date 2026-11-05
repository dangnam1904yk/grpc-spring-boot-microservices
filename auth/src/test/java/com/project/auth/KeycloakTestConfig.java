package com.project.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import com.project.auth.repository.UserRoleRepository;
import com.project.common.security.test.TestJwt;

/**
 * Thay Keycloak bằng {@link FakeKeycloakAdmin} và token test của {@link TestJwt}. Không import
 * {@code TestJwt.Config}: auth-service tự đọc role + quyền từ DB (SecurityConfig), không đọc claim của token.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KeycloakTestConfig {

    @Bean
    @Primary
    FakeKeycloakAdmin fakeKeycloakAdmin() {
        return new FakeKeycloakAdmin();
    }

    @Bean
    RecordingAuthorityCache recordingAuthorityCache() {
        return new RecordingAuthorityCache();
    }

    @Bean
    TestUsers testUsers(FakeKeycloakAdmin keycloak, UserRoleRepository userRoleRepository) {
        return new TestUsers(keycloak, userRoleRepository);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return TestJwt.decoder();
    }
}
