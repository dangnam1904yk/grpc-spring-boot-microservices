package com.project.common.security.grpc;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import com.project.common.i18n.Messages;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

/**
 * Lỗi bảo mật ném ra bên trong gRPC service (vd. {@code @PreAuthorize}) -> status chuẩn,
 * thay vì rơi vào handler Exception chung thành INTERNAL.
 */
@GrpcAdvice
public class GrpcSecurityExceptionAdvice {

    private final Messages messages;

    public GrpcSecurityExceptionAdvice(Messages messages) {
        this.messages = messages;
    }

    @GrpcExceptionHandler(AuthenticationException.class)
    public Status handleAuthentication(AuthenticationException e) {
        return Status.UNAUTHENTICATED.withDescription(messages.get("error.unauthenticated"));
    }

    @GrpcExceptionHandler(AccessDeniedException.class)
    public Status handleAccessDenied(AccessDeniedException e) {
        return Status.PERMISSION_DENIED.withDescription(messages.get("error.forbidden"));
    }
}
