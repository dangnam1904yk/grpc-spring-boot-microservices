package com.project.audit.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.project.audit.config.AuditServerProperties;
import com.project.audit.dto.AuditDtos.AuditSearch;
import com.project.audit.entity.AuditLog;
import com.project.audit.repository.AuditLogRepository;
import com.project.common.audit.AuditEvent;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lưu và tra cứu log. Ghi: mọi service gửi về qua gRPC. Đọc: chỉ ADMIN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    /** Số log tối đa của 1 request trả về trong timeline. */
    public static final int MAX_TIMELINE_SIZE = 500;

    private final AuditLogRepository auditLogRepository;
    private final AuditServerProperties properties;
    private final EntityManager entityManager;


    /**
     * Thêm mới (persist, không merge): id do service gửi đặt sẵn, nên merge sẽ tốn 1 câu SELECT cho mỗi log.
     * Log đã có (Kafka gửi lại) -> lỗi trùng khoá chính, cả lô rollback; {@link AuditLogWriter} lưu lại từng log
     * để bỏ qua log trùng.
     */
    @Transactional
    public int saveAll(Collection<AuditEvent> events) {
        int saved = 0;
        for (AuditEvent event : events) {
            if (event.type() != null) {
                entityManager.persist(toEntity(event));
                saved++;
            }
        }
        // flush qua repository (không phải entityManager): lỗi trùng khoá được Spring đổi thành
        // DataIntegrityViolationException để AuditLogWriter nhận ra
        auditLogRepository.flush();
        return saved;
    }


    public Page<AuditLog> search(AuditSearch filter, Pageable pageable) {
        CurrentUser.requireAdmin();
        return auditLogRepository.findAll(toSpecification(filter), pageable);
    }

    public AuditLog get(UUID id) {
        CurrentUser.requireAdmin();
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Audit log id=" + id + " không tồn tại"));
    }

    /** Mọi log của 1 request theo thứ tự thời gian: HTTP ở gateway -> gRPC sang service khác -> thay đổi dữ liệu. */
    public List<AuditLog> timeline(String requestId) {
        CurrentUser.requireAdmin();
        return auditLogRepository.findByRequestIdOrderByCreatedAtAscIdAsc(requestId,
                PageRequest.of(0, MAX_TIMELINE_SIZE));
    }

    @Scheduled(cron = "${app.audit-server.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void deleteExpired() {
        int deleted = auditLogRepository.deleteOlderThan(Instant.now().minus(properties.getRetention()));
        if (deleted > 0) {
            log.info("Đã xoá {} audit log cũ hơn {}", deleted, properties.getRetention());
        }
    }

    private static Specification<AuditLog> toSpecification(AuditSearch f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.type() != null) {
                predicates.add(cb.equal(root.get("type"), f.type()));
            }
            addEqual(predicates, cb, root.get("service"), f.service());
            addEqual(predicates, cb, root.get("requestId"), f.requestId());
            addEqual(predicates, cb, root.get("userId"), f.userId());
            addEqual(predicates, cb, root.get("clientIp"), f.clientIp());
            addEqual(predicates, cb, root.get("entityName"), f.entityName());
            addEqual(predicates, cb, root.get("entityId"), f.entityId());
            addEqual(predicates, cb, root.get("action"), f.action());
            addEqual(predicates, cb, root.get("status"), f.status());
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), f.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addEqual(List<Predicate> predicates, CriteriaBuilder cb, Path<Object> path, String value) {
        if (StringUtils.hasText(value)) {
            predicates.add(cb.equal(path, value));
        }
    }

    private static AuditLog toEntity(AuditEvent e) {
        AuditLog entity = new AuditLog();
        entity.setId(e.id());
        entity.setType(e.type());
        entity.setRequestId(cut(e.requestId(), 64));
        entity.setService(cut(e.service(), 100));
        entity.setCreatedAt(e.timestamp() != null ? e.timestamp() : Instant.now());
        entity.setClientIp(cut(e.clientIp(), 64));
        entity.setUserId(cut(e.userId(), 100));
        entity.setUserEmail(cut(e.userEmail(), 255));
        entity.setMethod(cut(e.method(), 255));
        entity.setUrl(cut(e.url(), 2000));
        entity.setCurl(e.curl());
        entity.setRequestHeaders(e.requestHeaders());
        entity.setRequestBody(e.requestBody());
        entity.setStatus(cut(e.status(), 50));
        entity.setResponseBody(e.responseBody());
        entity.setDurationMs(e.durationMs());
        entity.setError(cut(e.error(), 2000));
        entity.setEntityName(cut(e.entityName(), 100));
        entity.setEntityId(cut(e.entityId(), 100));
        entity.setAction(cut(e.action(), 20));
        entity.setChanges(e.changes());
        return entity;
    }

    /** Cắt theo độ dài cột: 1 giá trị quá dài không được làm hỏng cả lô log. */
    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
