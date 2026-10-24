package com.project.common.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lỗi bảo mật ném ra trong controller (vd. {@code @PreAuthorize}). Phải đứng trước
 * {@link RestExceptionHandler}: nếu không, handler {@code Exception} chung sẽ bắt mất và trả 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập hoặc token không hợp lệ");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Không có quyền thực hiện thao tác này");
    }
}
