package com.project.common.exception;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ. Được chuyển tự động thành gRPC Status (GrpcExceptionAdvice)
 * hoặc HTTP ProblemDetail (RestExceptionHandler) theo {@link ErrorCode}.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
