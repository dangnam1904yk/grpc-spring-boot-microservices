package com.project.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.project.common.exception.AppException;
import com.project.common.exception.ConstraintViolations;
import com.project.common.exception.ErrorCode;
import com.project.common.exception.FieldErrors;
import com.project.common.grpc.GrpcFieldErrors;
import com.project.common.i18n.Messages;

import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Lỗi REST trả về theo chuẩn RFC 7807 (ProblemDetail), message dịch theo header {@code Accept-Language}
 * ({@link Messages}). Lỗi từ CompletableFuture đã được Spring bóc khỏi CompletionException trước khi vào đây.
 * <p>
 * Lỗi dữ liệu đầu vào luôn có thuộc tính {@code errors} dạng field -&gt; message, dù đến từ {@link FieldErrors} /
 * {@link AppException} do service tự kiểm tra, {@code @Valid} / {@code @Validated} (message lấy từ annotation của
 * service, {@code message = "{key}"} tra trong file message của service), JSON sai kiểu, thiếu / sai kiểu query
 * param hay lỗi gRPC của service khác:
 * <pre>
 * { "status": 400, "detail": "Dữ liệu không hợp lệ", "code": "INVALID_ARGUMENT",
 *   "errors": { "code": "Trường thông tin là bắt buộc", "name": "Quá 255 ký tự" } }
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private final Messages messages;

    public RestExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(AppException.class)
    public ProblemDetail handleApp(AppException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.getErrorCode().getHttpStatus(),
                messages.get(e.getDetail()));
        problem.setProperty("code", e.getErrorCode().name());
        setErrors(problem, messages.get(e.getErrors()));
        return problem;
    }

    /** Method validation ({@code @Validated}) trên bean service. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        return invalid(HttpStatus.BAD_REQUEST, ConstraintViolations.toMap(e.getConstraintViolations()));
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ProblemDetail handleConcurrencyFailure(ConcurrencyFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, messages.get("error.concurrent-update"));
    }

    /** Lỗi khi gọi service khác qua gRPC: message đã được service đó dịch theo ngôn ngữ chuyển tiếp sang. */
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
                e.getStatus().getDescription() != null ? e.getStatus().getDescription()
                        : messages.get("error.dependency"));
        problem.setProperty("grpcStatus", e.getStatus().getCode().name());
        setErrors(problem, GrpcFieldErrors.from(e));
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError f : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(f.getField(), f.isBindingFailure() ? messages.get("error.type-mismatch") : message(f));
        }
        for (ObjectError g : ex.getBindingResult().getGlobalErrors()) {
            errors.putIfAbsent(g.getObjectName(), message(g));
        }
        return badRequest(ex, errors, headers, status, request);
    }

    /** Ràng buộc trên tham số controller: {@code @RequestParam @Min(1) int size}, {@code @Valid List<...>}... */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError f ? f.getField() : param;
                errors.putIfAbsent(field, message(error));
            }
        });
        return badRequest(ex, errors, headers, status, request);
    }

    /** Body không đọc được: JSON hỏng, hoặc sai kiểu ({@code "sortOrder": "abc"}) -&gt; chỉ ra field. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            errors.put(jsonPath(mismatch), messages.get("error.type-mismatch"));
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                messages.get(errors.isEmpty() ? "error.invalid-json" : FieldErrors.INVALID));
        problem.setProperty("code", ErrorCode.INVALID_ARGUMENT.name());
        setErrors(problem, errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Query param / path variable sai kiểu ({@code ?page=abc}, id không phải UUID). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "value";
        return badRequest(ex, Map.of(field, messages.get("error.type-mismatch")), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return badRequest(ex, Map.of(ex.getParameterName(), messages.get("error.required")), headers, status,
                request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, messages.get("error.system"));
    }

    private ResponseEntity<Object> badRequest(Exception ex, Map<String, String> errors, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, invalid(status, errors), headers, status, request);
    }

    private ProblemDetail invalid(HttpStatusCode status, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, messages.get(FieldErrors.INVALID));
        problem.setProperty("code", ErrorCode.INVALID_ARGUMENT.name());
        setErrors(problem, errors);
        return problem;
    }

    private static void setErrors(ProblemDetail problem, Map<String, String> errors) {
        if (!errors.isEmpty()) {
            problem.setProperty("errors", errors);
        }
    }

    /** Message của annotation đã được Validator dịch (kể cả {@code message = "{key}"}) theo ngôn ngữ của request. */
    private String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : messages.get(FieldErrors.INVALID);
    }

    /** {@code items[0].name} */
    private static String jsonPath(MismatchedInputException e) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
