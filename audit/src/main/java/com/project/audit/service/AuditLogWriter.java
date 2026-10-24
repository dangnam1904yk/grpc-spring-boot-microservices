package com.project.audit.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.project.common.audit.AuditEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ghi 1 lô log, chịu được log trùng (Kafka giao "ít nhất 1 lần": consumer chết trước khi commit offset thì lô đó
 * được đọc lại). Bình thường cả lô 1 transaction; có log trùng / lỗi dữ liệu thì lưu lại từng log, bỏ log lỗi.
 * Lỗi khác (DB chết) ném ra ngoài để Kafka consumer retry, không mất log.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogService auditLogService;

    public int write(List<AuditEvent> events) {
        List<AuditEvent> unique = distinctById(events);
        if (unique.isEmpty()) {
            return 0;
        }
        try {
            return auditLogService.saveAll(unique);
        } catch (DataIntegrityViolationException batchError) {
            int saved = 0;
            for (AuditEvent event : unique) {
                try {
                    saved += auditLogService.saveAll(List.of(event));
                } catch (DataIntegrityViolationException e) {
                    log.debug("Bỏ qua audit log {} (đã có hoặc dữ liệu lỗi): {}", event.id(), e.getMessage());
                }
            }
            return saved;
        }
    }

    /**
     * Bản gửi lại có thể nằm ngay trong cùng 1 lô: persist 2 lần cùng id trong 1 transaction bị Hibernate từ chối
     * trước cả khi xuống DB (không phải lỗi trùng khoá), nên phải lọc trước.
     */
    private static List<AuditEvent> distinctById(List<AuditEvent> events) {
        Map<Object, AuditEvent> byId = new LinkedHashMap<>();
        for (AuditEvent event : events) {
            byId.putIfAbsent(event.id() != null ? event.id() : new Object(), event);
        }
        return new ArrayList<>(byId.values());
    }
}
