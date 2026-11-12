package com.project.common.security;

import org.springframework.security.core.GrantedAuthority;

/**
 * Quyền {@code "menuCode:PERMISSION"} đã được {@link AuthorityResolver} lấy về, lưu trong authorities của
 * Authentication để {@link CurrentUser} đọc lại (không phải gọi resolver lần nữa trong cùng request).
 */
public record PermissionAuthority(String permission) implements GrantedAuthority {

    @Override
    public String getAuthority() {
        return permission;
    }
}
