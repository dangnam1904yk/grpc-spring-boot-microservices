package com.project.audit.entity;

import java.time.Instant;
import java.util.UUID;

import com.project.common.audit.AuditType;
import com.project.common.jpa.UUIDV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Một dòng log. Chỉ thêm, không bao giờ sửa (không có @Version / updatedAt).
 * Field không áp dụng cho loại log thì null (curl chỉ có ở HTTP, changes chỉ có ở ENTITY_CHANGE).
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_request_id", columnList = "request_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at"),
        @Index(name = "idx_audit_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_audit_entity", columnList = "entity_name, entity_id") })
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLog {

    /**
     * Độ dài cho cột @Lob. Bắt buộc ghi rõ: @Column mặc định length=255 và Hibernate chọn kiểu theo length,
     * nên trên MySQL @Lob + @Column(name=...) sẽ thành TINYTEXT (tối đa 255 byte) và insert bị lỗi.
     */
    public static final int LONG_TEXT = 100_000_000;

    /**
     * Id do service gửi sinh sẵn (UUID v7): Kafka gửi lại cùng 1 log thì trùng khoá chính và bị bỏ qua,
     * không thành 2 dòng. Không có id (log cũ) thì {@link UUIDV7} tự sinh.
     */
    @Id
    @UUIDV7
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    AuditType type;

    @Column(name = "request_id", length = 64)
    String requestId;

    @Column(length = 100)
    String service;

    /** Thời điểm sự kiện xảy ra ở service gửi (không phải lúc audit-service nhận). */
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "client_ip", length = 64)
    String clientIp;

    @Column(name = "user_id", length = 100)
    String userId;

    @Column(name = "user_email", length = 255)
    String userEmail;

    /** HTTP method hoặc tên đầy đủ của RPC. */
    @Column(length = 255)
    String method;

    @Column(length = 2000)
    String url;

    @Lob
    @Column(length = LONG_TEXT)
    String curl;

    @Lob
    @Column(name = "request_headers", length = LONG_TEXT)
    String requestHeaders;

    @Lob
    @Column(name = "request_body", length = LONG_TEXT)
    String requestBody;

    @Column(length = 50)
    String status;

    @Lob
    @Column(name = "response_body", length = LONG_TEXT)
    String responseBody;

    @Column(name = "duration_ms")
    Long durationMs;

    @Column(length = 2000)
    String error;

    @Column(name = "entity_name", length = 100)
    String entityName;

    @Column(name = "entity_id", length = 100)
    String entityId;

    /** CREATE / UPDATE / DELETE */
    @Column(length = 20)
    String action;

    /** JSON [{"field":"name","old":"A","new":"B"}] */
    @Lob
    @Column(length = LONG_TEXT)
    String changes;
}
