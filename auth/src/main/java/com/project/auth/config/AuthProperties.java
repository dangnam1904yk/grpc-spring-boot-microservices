package com.project.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

public final class AuthProperties {

    private AuthProperties() {
    }

    /** @param email tài khoản (trên Keycloak) được tự gán ADMIN khi hệ thống chưa có ADMIN nào */
    @ConfigurationProperties(prefix = "app.admin")
    public record Admin(String email) {
    }

    /**
     * Keycloak Admin REST API (chỉ quản lý tài khoản: tạo, sửa, khoá, kết thúc phiên), gọi bằng service account
     * của client {@code clientId} (client credentials). Service account cần role manage-users, view-users,
     * query-users của client realm-management.
     *
     * @param serverUrl gốc URL Keycloak mà auth-service gọi tới, vd. http://keycloak:8080 (không gồm /realms/...)
     * @param timeout   connect / read timeout mỗi lời gọi
     */
    @ConfigurationProperties(prefix = "app.keycloak")
    public record Keycloak(String serverUrl, String realm, String clientId, String clientSecret, Duration timeout) {

        public Keycloak {
            timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        }
    }
}
