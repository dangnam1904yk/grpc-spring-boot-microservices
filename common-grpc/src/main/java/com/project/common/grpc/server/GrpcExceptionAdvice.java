package com.project.common.grpc.server;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.ConcurrencyFailureException;

import com.project.common.exception.AppException;
import com.project.common.exception.ConstraintViolations;
import com.project.common.exception.ErrorCode;
import com.project.common.exception.FieldErrors;
import com.project.common.grpc.GrpcFieldErrors;
import com.project.common.i18n.Messages;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

/**
 * Chuyển exception thành gRPC Status chuẩn để client phân biệt được loại lỗi (NOT_FOUND, ABORTED...).
 * Message dịch theo metadata {@code accept-language} của call ({@code GrpcLocaleServerInterceptor}).
 * Lỗi theo field ({@link AppException#getErrors()}) gửi kèm dạng {@code google.rpc.BadRequest}
 * ({@link GrpcFieldErrors}) và liệt kê luôn trong description.
 */
@Slf4j
@GrpcAdvice
public class GrpcExceptionAdvice {

    private final Messages messages;

    public GrpcExceptionAdvice(Messages messages) {
        this.messages = messages;
    }

    @GrpcExceptionHandler(AppException.class)
    public StatusRuntimeException handleApp(AppException e) {
        return toStatus(e.getErrorCode().getGrpcCode(), messages.get(e.getDetail()), messages.get(e.getErrors()));
    }

    /** Method validation ({@code @Validated}) của Spring. */
    @GrpcExceptionHandler(ConstraintViolationException.class)
    public StatusRuntimeException handleConstraintViolation(ConstraintViolationException e) {
        return toStatus(ErrorCode.INVALID_ARGUMENT.getGrpcCode(), messages.get(FieldErrors.INVALID),
                ConstraintViolations.toMap(e.getConstraintViolations()));
    }

    @GrpcExceptionHandler(ConcurrencyFailureException.class)
    public Status handleConcurrencyFailure(ConcurrencyFailureException e) {
        return Status.ABORTED.withDescription(messages.get("error.concurrent-update"));
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleUnknown(Exception e) {
        log.error("Unhandled gRPC exception", e);
        return Status.INTERNAL.withDescription(messages.get("error.system"));
    }

    private static StatusRuntimeException toStatus(Status.Code code, String message, Map<String, String> errors) {
        if (errors.isEmpty()) {
            return Status.fromCode(code).withDescription(message).asRuntimeException();
        }
        String description = message + " (" + errors.entrySet().stream()
                .map(err -> err.getKey() + ": " + err.getValue())
                .collect(Collectors.joining("; ")) + ")";
        return GrpcFieldErrors.toException(code, description, errors);
    }
}
