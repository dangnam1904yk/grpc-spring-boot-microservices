package com.project.common.web;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.project.common.exception.AppException;

import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;

/**
 * Lỗi REST trả về theo chuẩn RFC 7807 (ProblemDetail).
 * Lỗi từ CompletableFuture đã được Spring bóc khỏi CompletionException trước khi vào đây.
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleApp(AppException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getErrorCode().getHttpStatus(), e.getMessage());
        problem.setProperty("code", e.getErrorCode().name());
        return problem;
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail handleConcurrencyFailure(ConcurrencyFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Dữ liệu đã bị thay đổi bởi request khác, vui lòng tải lại và thử lại");
    }

    /** Lỗi khi gọi service khác qua gRPC. */
    @ExceptionHandler(StatusRuntimeException.class)
    public ProblemDetail handleGrpc(StatusRuntimeException e) {
        HttpStatus status = switch (e.getStatus().getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case ALREADY_EXISTS, ABORTED -> HttpStatus.CONFLICT;
            case UNAVAILABLE, RESOURCE_EXHAUSTED -> HttpStatus.SERVICE_UNAVAILABLE;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        log.warn("gRPC call failed: {}", e.getStatus());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                e.getStatus().getDescription() != null ? e.getStatus().getDescription() : "Service phụ thuộc lỗi");
        problem.setProperty("grpcStatus", e.getStatus().getCode().name());
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()),
                        (first, second) -> first));
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Dữ liệu không hợp lệ");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
    }
}
