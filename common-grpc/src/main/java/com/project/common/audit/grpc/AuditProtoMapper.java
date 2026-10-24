package com.project.common.audit.grpc;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditType;

/** {@link AuditEvent} <-> proto {@code audit.v1.AuditEvent}. Proto không có null: chuỗi rỗng = không có. */
public final class AuditProtoMapper {

    private static final String TYPE_PREFIX = "AUDIT_TYPE_";

    private AuditProtoMapper() {
    }

    public static com.project.grpc.audit.v1.AuditEvent toProto(AuditEvent e) {
        com.project.grpc.audit.v1.AuditEvent.Builder b = com.project.grpc.audit.v1.AuditEvent.newBuilder();
        if (e.id() != null) {
            b.setId(e.id().toString());
        }
        if (e.type() != null) {
            b.setType(com.project.grpc.audit.v1.AuditType.valueOf(TYPE_PREFIX + e.type().name()));
        }
        if (e.timestamp() != null) {
            b.setTimestampMillis(e.timestamp().toEpochMilli());
        }
        if (e.durationMs() != null) {
            b.setDurationMs(e.durationMs());
        }
        set(b::setRequestId, e.requestId());
        set(b::setService, e.service());
        set(b::setClientIp, e.clientIp());
        set(b::setUserId, e.userId());
        set(b::setUserEmail, e.userEmail());
        set(b::setMethod, e.method());
        set(b::setUrl, e.url());
        set(b::setCurl, e.curl());
        set(b::setRequestHeaders, e.requestHeaders());
        set(b::setRequestBody, e.requestBody());
        set(b::setStatus, e.status());
        set(b::setResponseBody, e.responseBody());
        set(b::setError, e.error());
        set(b::setEntityName, e.entityName());
        set(b::setEntityId, e.entityId());
        set(b::setAction, e.action());
        set(b::setChanges, e.changes());
        return b.build();
    }

    public static AuditEvent fromProto(com.project.grpc.audit.v1.AuditEvent p) {
        AuditType type = p.getType() == com.project.grpc.audit.v1.AuditType.AUDIT_TYPE_UNSPECIFIED
                || p.getType() == com.project.grpc.audit.v1.AuditType.UNRECOGNIZED
                        ? null
                        : AuditType.valueOf(p.getType().name().substring(TYPE_PREFIX.length()));
        return AuditEvent.builder()
                .id(p.getId().isEmpty() ? null : UUID.fromString(p.getId()))
                .type(type)
                .requestId(blankToNull(p.getRequestId()))
                .service(blankToNull(p.getService()))
                .timestamp(p.getTimestampMillis() == 0 ? null : Instant.ofEpochMilli(p.getTimestampMillis()))
                .clientIp(blankToNull(p.getClientIp()))
                .userId(blankToNull(p.getUserId()))
                .userEmail(blankToNull(p.getUserEmail()))
                .method(blankToNull(p.getMethod()))
                .url(blankToNull(p.getUrl()))
                .curl(blankToNull(p.getCurl()))
                .requestHeaders(blankToNull(p.getRequestHeaders()))
                .requestBody(blankToNull(p.getRequestBody()))
                .status(blankToNull(p.getStatus()))
                .responseBody(blankToNull(p.getResponseBody()))
                // Thay đổi dữ liệu không có thời gian xử lý; proto không phân biệt được 0 với "không có"
                .durationMs(type == AuditType.ENTITY_CHANGE ? null : p.getDurationMs())
                .error(blankToNull(p.getError()))
                .entityName(blankToNull(p.getEntityName()))
                .entityId(blankToNull(p.getEntityId()))
                .action(blankToNull(p.getAction()))
                .changes(blankToNull(p.getChanges()))
                .build();
    }

    private static void set(Consumer<String> setter, String value) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private static String blankToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
