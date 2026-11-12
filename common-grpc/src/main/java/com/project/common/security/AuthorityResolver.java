package com.project.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lấy role + quyền của user đang gọi request lúc xác thực (REST và gRPC).
 * <p>
 * Access token chỉ cho biết user là ai ({@code sub}); role và ma trận phân quyền nằm ở auth-service. Mặc định
 * common-grpc dùng {@link com.project.common.security.grpc.AuthServiceAuthorityResolver} (cache Redis, hết cache
 * thì gọi gRPC auth-service), auth-service tự khai báo bean đọc thẳng DB. {@link ClaimAuthorityResolver} đọc
 * claim "roles" / "permissions" của token (test dùng qua {@code TestJwt.Config}).
 */
@FunctionalInterface
public interface AuthorityResolver {

    /** Không được ném lỗi: lỗi thì trả về {@link Authorities#USER_ONLY}. */
    Authorities resolve(Jwt jwt);
}
