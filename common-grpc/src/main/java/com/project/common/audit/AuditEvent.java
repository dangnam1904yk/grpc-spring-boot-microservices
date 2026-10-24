package com.project.common.audit;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

/**
 * Một dòng log. Field không áp dụng cho loại log thì để null
 * (ví dụ {@code curl} chỉ có ở HTTP, {@code changes} chỉ có ở ENTITY_CHANGE).
 *
 * @param id        UUID v7 sinh lúc tạo log; audit-service dùng làm khoá chính nên Kafka gửi lại cũng không bị trùng
 * @param requestId giống nhau cho mọi log sinh ra từ cùng 1 request (qua gateway, REST, gRPC, ghi DB)
 * @param status    HTTP status (200, 404...) hoặc gRPC status (OK, NOT_FOUND...)
 * @param changes   JSON {@code [{"field":"name","old":"A","new":"B"}]}
 */
@Builder(toBuilder = true)
public record AuditEvent(
        UUID id,
        AuditType type,
        String requestId,
        String service,
        Instant timestamp,
        String clientIp,
        String userId,
        String userEmail,
        String method,
        String url,
        String curl,
        String requestHeaders,
        String requestBody,
        String status,
        String responseBody,
        Long durationMs,
        String error,
        String entityName,
        String entityId,
        String action,
        String changes) {
}
