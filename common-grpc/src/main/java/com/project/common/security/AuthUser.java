package com.project.common.security;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * User đang gọi request, lấy từ JWT do auth-service cấp.
 *
 * @param id          claim {@code sub} (id user bên auth-service)
 * @param permissions claim {@code permissions}: {@code "resource:action"}, vd. {@code "category:create"}
 * @param token       access token gốc, dùng để chuyển tiếp sang service khác qua gRPC
 */
public record AuthUser(String id, String email, Set<String> roles, Set<String> permissions, String token) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLES_CLAIM = "roles";
    public static final String PERMISSIONS_CLAIM = "permissions";
    public static final String EMAIL_CLAIM = "email";

    public static AuthUser from(Jwt jwt) {
        return new AuthUser(jwt.getSubject(), jwt.getClaimAsString(EMAIL_CLAIM), claimAsSet(jwt, ROLES_CLAIM),
                claimAsSet(jwt, PERMISSIONS_CLAIM), jwt.getTokenValue());
    }

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }

    /** ADMIN là super-admin: luôn có mọi quyền, kể cả quyền mới tạo sau khi token được cấp. */
    public boolean hasPermission(String permission) {
        return isAdmin() || permissions.contains(permission);
    }

    private static Set<String> claimAsSet(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
