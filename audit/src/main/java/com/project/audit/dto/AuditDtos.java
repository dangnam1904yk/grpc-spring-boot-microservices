package com.project.audit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.audit.entity.AuditLog;
import com.project.common.audit.AuditType;

public final class AuditDtos {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AuditDtos() {
    }

    /** Bộ lọc tra cứu; field null = không lọc. Khoảng thời gian [from, to). */
    public record AuditSearch(
            AuditType type,
            String service,
            String requestId,
            String userId,
            String clientIp,
            String entityName,
            String entityId,
            String action,
            String status,
            Instant from,
            Instant to) {
    }

    /** Dòng trong danh sách: không kèm body / curl (xem chi tiết bằng GET /api/audit/{id}). */
    public record AuditSummary(
            UUID id,
            AuditType type,
            String requestId,
            String service,
            Instant createdAt,
            String clientIp,
            String userId,
            String userEmail,
            String method,
            String url,
            String status,
            Long durationMs,
            String entityName,
            String entityId,
            String action) {

        public static AuditSummary from(AuditLog a) {
            return new AuditSummary(a.getId(), a.getType(), a.getRequestId(), a.getService(), a.getCreatedAt(),
                    a.getClientIp(), a.getUserId(), a.getUserEmail(), a.getMethod(), a.getUrl(), a.getStatus(),
                    a.getDurationMs(), a.getEntityName(), a.getEntityId(), a.getAction());
        }
    }

    /** requestHeaders / changes trả dạng JSON (không phải chuỗi JSON lồng trong JSON). */
    public record AuditDetail(
            UUID id,
            AuditType type,
            String requestId,
            String service,
            Instant createdAt,
            String clientIp,
            String userId,
            String userEmail,
            String method,
            String url,
            String curl,
            JsonNode requestHeaders,
            String requestBody,
            String status,
            String responseBody,
            Long durationMs,
            String error,
            String entityName,
            String entityId,
            String action,
            JsonNode changes) {

        public static AuditDetail from(AuditLog a) {
            return new AuditDetail(a.getId(), a.getType(), a.getRequestId(), a.getService(), a.getCreatedAt(),
                    a.getClientIp(), a.getUserId(), a.getUserEmail(), a.getMethod(), a.getUrl(), a.getCurl(),
                    parse(a.getRequestHeaders()), a.getRequestBody(), a.getStatus(), a.getResponseBody(),
                    a.getDurationMs(), a.getError(), a.getEntityName(), a.getEntityId(), a.getAction(),
                    parse(a.getChanges()));
        }

        private static JsonNode parse(String json) {
            if (json == null) {
                return null;
            }
            try {
                return JSON.readTree(json);
            } catch (Exception e) {
                return JSON.getNodeFactory().textNode(json);
            }
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
