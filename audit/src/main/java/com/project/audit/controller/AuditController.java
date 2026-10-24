package com.project.audit.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.audit.dto.AuditDtos.AuditDetail;
import com.project.audit.dto.AuditDtos.AuditSearch;
import com.project.audit.dto.AuditDtos.AuditSummary;
import com.project.audit.dto.AuditDtos.PageResponse;
import com.project.audit.service.AuditLogService;
import com.project.common.audit.AuditType;

import lombok.RequiredArgsConstructor;

/**
 * Tra cứu log (qua gateway: /api/audit/**). Chỉ ADMIN (kiểm tra trong {@link AuditLogService}).
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogService auditLogService;

    /**
     * Ví dụ: {@code /api/audit?type=ENTITY_CHANGE&entityName=Category&entityId=1} (lịch sử thay đổi của 1 bản ghi),
     * {@code /api/audit?userId=u-1&from=2026-10-01T00:00:00Z} (user làm gì từ ngày 1/10).
     */
    @GetMapping
    public PageResponse<AuditSummary> search(
            @RequestParam(required = false) AuditType type,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String clientIp,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuditSearch filter = new AuditSearch(type, service, requestId, userId, clientIp, entityName, entityId,
                action, status, from, to);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(auditLogService.search(filter, pageable).map(AuditSummary::from));
    }

    @GetMapping("/{id}")
    public AuditDetail get(@PathVariable UUID id) {
        return AuditDetail.from(auditLogService.get(id));
    }

    /** Toàn bộ log của 1 request (giá trị header X-Request-Id trả về cho client). */
    @GetMapping("/requests/{requestId}")
    public List<AuditDetail> timeline(@PathVariable String requestId) {
        return auditLogService.timeline(requestId).stream().map(AuditDetail::from).toList();
    }
}
