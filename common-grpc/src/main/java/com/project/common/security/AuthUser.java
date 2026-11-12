package com.project.common.security;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * User đang gọi request: danh tính lấy từ JWT do Keycloak cấp, role + quyền do {@link AuthorityResolver}
 * lấy từ auth-service.
 *
 * @param id          claim {@code sub} (id user bên Keycloak)
 * @param roles       code role trong auth-service, luôn có {@code USER}
 * @param permissions {@code "menuCode:PERMISSION"}, vd. {@code "category:ADD"}
 * @param token       access token gốc, dùng để chuyển tiếp sang service khác qua gRPC
 */
public record AuthUser(String id, String email, Set<String> roles, Set<String> permissions, String token) {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLES_CLAIM = "roles";
    public static final String PERMISSIONS_CLAIM = "permissions";
    public static final String EMAIL_CLAIM = "email";

    /** Authority "ROLE_x" ứng với role x (dùng được với hasRole('x')). */
    public static final String ROLE_PREFIX = "ROLE_";

    /**
     * {@code authorities}: đầu ra của converter trong ServletSecurityAutoConfiguration ("ROLE_x" và
     * {@link PermissionAuthority}).
     */
    public static AuthUser from(Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        Set<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());
        Set<String> permissions = authorities.stream()
                .filter(PermissionAuthority.class::isInstance)
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        return new AuthUser(jwt.getSubject(), jwt.getClaimAsString(EMAIL_CLAIM), roles, permissions,
                jwt.getTokenValue());
    }

    public boolean isAdmin() {
        return roles.contains(ROLE_ADMIN);
    }

    /** ADMIN là super-admin: luôn có mọi quyền, kể cả quyền mới tạo sau khi token được cấp. */
    public boolean hasPermission(String permission) {
        return isAdmin() || permissions.contains(permission);
    }
}
