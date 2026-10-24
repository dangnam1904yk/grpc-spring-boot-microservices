package com.project.audit.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * <pre>
 * app:
 *   audit-server:
 *     topic: audit-log
 *     topic-partitions: 3
 *     ingest-key: ${AUDIT_KEY:dev-audit-key}
 *     retention: 90d
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.audit-server")
public class AuditServerProperties {

    /** Kafka topic chứa log (trùng app.audit.sender.topic ở các service gửi). */
    private String topic = "audit-log";

    /** Số partition khi tạo topic = số consumer tối đa đọc song song (chạy nhiều instance audit-service). */
    private int topicPartitions = 3;

    private short topicReplicas = 1;

    /** Đường gRPC dự phòng: service gửi phải kèm metadata "x-audit-key" bằng giá trị này (app.audit.sender.key). */
    private String ingestKey = "dev-audit-key";

    /** Log cũ hơn bị xoá mỗi ngày. */
    private Duration retention = Duration.ofDays(90);
}
