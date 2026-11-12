package com.project.common.security;

import java.time.Duration;
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

    /**
     * Nguồn role + quyền của user: {@code auth-service} (mặc định, cache Redis + gRPC grpc.client.auth-service)
     * hoặc {@code token} (claim "roles" / "permissions" của token).
     */
    private String authoritySource = "auth-service";

    /** Tắt thì mọi request hỏi auth-service (không dùng Redis dù có trên classpath). */
    private boolean authorityCacheEnabled = true;

    /**
     * Thời gian sống của cache role + quyền trên Redis. auth-service xoá cache ngay khi phân quyền thay đổi,
     * TTL chỉ là lưới an toàn khi lệnh xoá bị lỡ (Redis tạm mất kết nối).
     */
    private Duration authorityCacheTtl = Duration.ofMinutes(5);

    /** Deadline của lời gọi GetAuthorities sang auth-service. */
    private Duration authorityDeadline = Duration.ofSeconds(3);
}
