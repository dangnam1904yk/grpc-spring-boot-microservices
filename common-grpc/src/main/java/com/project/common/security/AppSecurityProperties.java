package com.project.common.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình xác thực trong application.yml của từng service:
 * <pre>
 * app:
 *   security:
 *     jwks-uri: http://localhost:9000/api/auth/jwks   # public key của auth-service
 *     issuer: http://auth-service
 *     public-paths:                                   # không cần token (mặc định: mọi thứ đều cần)
 *       - GET /api/category/**
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /** URL JWKS của auth-service; service tự tải public key để kiểm tra chữ ký JWT. */
    private String jwksUri;

    /** Giá trị claim {@code iss} bắt buộc phải khớp. Để trống thì không kiểm tra. */
    private String issuer;

    /** Danh sách "METHOD /path/**" hoặc "/path/**" được truy cập không cần token. */
    private List<String> publicPaths = new ArrayList<>();
}
