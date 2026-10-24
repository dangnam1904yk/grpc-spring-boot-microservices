package com.project.common.security;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * User đang gọi request, lấy từ JWT do auth-service cấp.
 *
 * @param id    claim {@code sub} (id user bên auth-service)
 * @param token access token gốc, dùng để chuyển tiếp sang service khác qua gRPC
 */
public record AuthUser(String id, String email, Set<String> roles, String token) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLES_CLAIM = "roles";
    public static final String EMAIL_CLAIM = "email";

    public static AuthUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        return new AuthUser(jwt.getSubject(), jwt.getClaimAsString(EMAIL_CLAIM),
                roles == null ? Set.of() : Set.copyOf(roles), jwt.getTokenValue());
    }

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }
}
