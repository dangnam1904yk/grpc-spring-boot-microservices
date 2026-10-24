package com.project.audit.kafka;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.audit.service.AuditLogWriter;
import com.project.common.audit.AuditEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đọc log từ topic theo lô (tối đa spring.kafka.consumer.max-poll-records) và ghi DB trong 1 transaction.
 * Offset chỉ được commit sau khi ghi DB xong: audit-service chết giữa chừng thì lô đó được đọc lại
 * (log trùng bị bỏ qua nhờ khoá chính = id của log).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogConsumer {

    private final AuditLogWriter writer;
    private final ObjectMapper objectMapper;

    // groupId ghi rõ: không có thì Spring lấy "id" làm group id, bỏ qua spring.kafka.consumer.group-id
    @KafkaListener(id = "audit-log-consumer", groupId = "${spring.kafka.consumer.group-id:audit-service}",
            topics = "${app.audit-server.topic:audit-log}", batch = "true")
    public void consume(List<ConsumerRecord<String, String>> records) {
        List<AuditEvent> events = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            try {
                events.add(objectMapper.readValue(record.value(), AuditEvent.class));
            } catch (Exception e) {
                // Message hỏng thì retry cũng không đọc được: bỏ qua, không chặn cả partition
                log.warn("Bỏ qua audit log không đọc được (partition {}, offset {}): {}", record.partition(),
                        record.offset(), e.getMessage());
            }
        }
        writer.write(events);
    }
}
