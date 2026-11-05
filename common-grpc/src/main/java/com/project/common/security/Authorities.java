package com.project.common.security;

import java.util.Set;

/**
 * Role và quyền {@code "menuCode:PERMISSION"} của một user, do auth-service quản lý (không nằm trong token).
 */
public record Authorities(Set<String> roles, Set<String> permissions) {

    /** Khi không hỏi được auth-service: mọi user luôn có USER, không có quyền nào. */
    public static final Authorities USER_ONLY = new Authorities(Set.of("USER"), Set.of());

    public Authorities {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }
}
