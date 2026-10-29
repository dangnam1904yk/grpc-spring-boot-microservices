package com.project.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.project.common.i18n.LocalizedMessage;

import lombok.Getter;

/**
 * Lỗi nghiệp vụ. Được chuyển tự động thành gRPC Status (GrpcExceptionAdvice)
 * hoặc HTTP ProblemDetail (RestExceptionHandler) theo {@link ErrorCode}.
 * <p>
 * Message là key trong {@code i18n/messages_xx.properties} của service, dịch theo ngôn ngữ của request lúc trả lỗi:
 * <pre>
 * throw new AppException(ErrorCode.NOT_FOUND, "category.not-found", code);   // category.not-found=Category {0} không tồn tại
 * </pre>
 * Chưa có key thì trả nguyên chuỗi (code cũ truyền thẳng câu tiếng Việt vẫn chạy).
 * {@link #getErrors()}: lỗi theo field, trả cho client trong {@code errors} của ProblemDetail /
 * {@code google.rpc.BadRequest} của gRPC. Tạo bằng {@link FieldErrors}.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    // Không serialize exception qua mạng (gRPC / REST chỉ gửi message đã dịch)
    private final transient LocalizedMessage detail;
    private final transient Map<String, LocalizedMessage> errors;

    public AppException(ErrorCode errorCode, String message, Object... args) {
        this(errorCode, LocalizedMessage.of(message, args), Map.of());
    }

    public AppException(ErrorCode errorCode, LocalizedMessage detail, Map<String, LocalizedMessage> errors) {
        super(detail.toString());
        this.errorCode = errorCode;
        this.detail = detail;
        this.errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
