package com.project.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;

/**
 * Lấy user hiện tại trong cả REST lẫn gRPC call (cả hai đều đặt vào SecurityContext).
 * <p>
 * SecurityContext gắn với thread: muốn dùng trong {@code thenApplyAsync}/thread khác thì phải
 * lấy {@link AuthUser} ra trước trên thread của request rồi truyền vào.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(AuthUser.from(jwtAuth.getToken()));
        }
        return Optional.empty();
    }

    public static AuthUser require() {
        return current().orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Chưa đăng nhập"));
    }

    public static AuthUser requireAdmin() {
        AuthUser user = require();
        if (!user.isAdmin()) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ ADMIN được thực hiện thao tác này");
        }
        return user;
    }

    /** 401 nếu chưa đăng nhập, 403 nếu không có quyền (xem {@link Permissions}). */
    public static AuthUser requirePermission(String permission) {
        AuthUser user = require();
        if (!user.hasPermission(permission)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền " + permission);
        }
        return user;
    }
}
