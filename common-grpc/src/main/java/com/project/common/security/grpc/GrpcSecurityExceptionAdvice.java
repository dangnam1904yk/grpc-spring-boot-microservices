package com.project.common.security.grpc;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

/**
 * Lỗi bảo mật ném ra bên trong gRPC service (vd. {@code @PreAuthorize}) -> status chuẩn,
 * thay vì rơi vào handler Exception chung thành INTERNAL.
 */
@GrpcAdvice
public class GrpcSecurityExceptionAdvice {

    @GrpcExceptionHandler(AuthenticationException.class)
    public Status handleAuthentication(AuthenticationException e) {
        return Status.UNAUTHENTICATED.withDescription("Chưa đăng nhập hoặc token không hợp lệ");
    }

    @GrpcExceptionHandler(AccessDeniedException.class)
    public Status handleAccessDenied(AccessDeniedException e) {
        return Status.PERMISSION_DENIED.withDescription("Không có quyền thực hiện thao tác này");
    }
}
