package com.project.common.exception;

import org.springframework.http.HttpStatus;

import io.grpc.Status;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Một mã lỗi nghiệp vụ, map sang cả HTTP status (REST) và gRPC status (service-to-service).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, Status.Code.NOT_FOUND),
    ALREADY_EXISTS(HttpStatus.CONFLICT, Status.Code.ALREADY_EXISTS),
    VERSION_CONFLICT(HttpStatus.CONFLICT, Status.Code.ABORTED),
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, Status.Code.INVALID_ARGUMENT),
    /** Không xoá được vì dữ liệu khác đang tham chiếu tới (role đang gán cho user...). */
    IN_USE(HttpStatus.CONFLICT, Status.Code.FAILED_PRECONDITION),
    /** Chưa đăng nhập / token hoặc thông tin đăng nhập sai. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, Status.Code.UNAUTHENTICATED),
    /** Đã đăng nhập nhưng không có quyền (không phải chủ sở hữu, không phải ADMIN...). */
    FORBIDDEN(HttpStatus.FORBIDDEN, Status.Code.PERMISSION_DENIED);

    private final HttpStatus httpStatus;
    private final Status.Code grpcCode;
}
