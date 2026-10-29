package com.project.common.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.project.common.i18n.LocalizedMessage;

/**
 * Danh sách lỗi theo field sau khi service tự kiểm tra dữ liệu. Chỉ gom và ném, không chứa rule nào: rule và message
 * là nghiệp vụ của từng service. Message là key trong {@code i18n/messages_xx.properties} của service (hoặc key chung
 * của common-grpc như {@code error.required}), dịch lúc trả lỗi.
 * <pre>
 * FieldErrors errors = FieldErrors.create();
 * if (!StringUtils.hasText(req.code())) {
 *     errors.add("code", "error.required");
 * }
 * if (req.name() != null &amp;&amp; req.name().length() &gt; 255) {
 *     errors.add("name", "catalog.too-long", 255);      // catalog.too-long=Quá {0} ký tự
 * }
 * errors.throwIfAny();   // -&gt; 400 / INVALID_ARGUMENT
 * </pre>
 * Client nhận (RestExceptionHandler / GrpcExceptionAdvice), theo {@code Accept-Language}:
 * <pre>
 * REST: { "status": 400, "detail": "Dữ liệu không hợp lệ", "code": "INVALID_ARGUMENT",
 *         "errors": { "code": "Trường thông tin là bắt buộc", "name": "Quá 255 ký tự" } }
 * gRPC: INVALID_ARGUMENT + google.rpc.BadRequest (field_violations) trong trailer
 * </pre>
 * Mỗi field chỉ giữ lỗi đầu tiên. Lỗi theo field với mã khác 400 (vd. trùng code -&gt; 409) thì truyền map vào
 * {@link AppException#AppException(ErrorCode, LocalizedMessage, Map)}.
 */
public final class FieldErrors {

    /** Key chung (common-grpc): "Dữ liệu không hợp lệ". */
    public static final String INVALID = "error.invalid";

    private final Map<String, LocalizedMessage> errors = new LinkedHashMap<>();

    private FieldErrors() {
    }

    public static FieldErrors create() {
        return new FieldErrors();
    }

    /** Lỗi của đúng một field: {@code throw FieldErrors.of("sort", "catalog.sort.invalid", sort)}. */
    public static AppException of(String field, String key, Object... args) {
        return create().add(field, key, args).toException();
    }

    public FieldErrors add(String field, String key, Object... args) {
        errors.putIfAbsent(field, LocalizedMessage.of(key, args));
        return this;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public Map<String, LocalizedMessage> asMap() {
        return Collections.unmodifiableMap(errors);
    }

    public AppException toException() {
        return new AppException(ErrorCode.INVALID_ARGUMENT, LocalizedMessage.of(INVALID), errors);
    }

    public void throwIfAny() {
        if (hasErrors()) {
            throw toException();
        }
    }
}
