package com.project.common.audit.web;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import com.project.common.audit.AuditContext;
import com.project.common.security.CurrentUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Chạy ngay sau Spring Security: lưu user đã xác thực lại cho {@link AuditFilter}.
 * Cần filter riêng vì AuditFilter chạy trước Spring Security (để ghi được cả request bị 401/403),
 * mà lúc đó SecurityContext chưa có, còn khi chain chạy xong thì SecurityContext đã bị xoá.
 */
public class AuditUserFilter extends OncePerRequestFilter {

    public static final String USER_ATTRIBUTE = AuditUserFilter.class.getName() + ".user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CurrentUser.current().ifPresent(user -> {
            AuditContext.User auditUser = new AuditContext.User(user.id(), user.email());
            request.setAttribute(USER_ATTRIBUTE, auditUser);
            AuditContext.bindUser(auditUser);
        });
        chain.doFilter(request, response);
    }
}
