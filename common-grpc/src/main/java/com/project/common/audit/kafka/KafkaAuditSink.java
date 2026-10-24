package com.project.common.audit.kafka;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.BatchingAuditSink;

import lombok.extern.slf4j.Slf4j;

/**
 * Gửi log vào Kafka topic (mặc định "audit-log") dạng JSON; audit-service consume rồi ghi DB.
 * <ul>
 * <li>Key = request id: mọi log của 1 request vào cùng partition, giữ đúng thứ tự.</li>
 * <li>acks=all + idempotence: broker xác nhận đã ghi đủ bản sao, producer retry không sinh bản ghi trùng.</li>
 * <li>Kafka tạm chết: producer tự giữ trong bộ đệm và retry (tối đa delivery.timeout.ms), audit-service tắt thì
 * log nằm chờ trong topic, bật lại sẽ đọc tiếp, không mất.</li>
 * </ul>
 * Vẫn đi qua hàng đợi của {@link BatchingAuditSink}: {@code producer.send()} có thể block (chờ metadata khi
 * broker chết), việc đó chỉ được xảy ra ở thread nền, không ở thread của request.
 */
@Slf4j
public class KafkaAuditSink extends BatchingAuditSink {

    private final Producer<String, String> producer;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final AtomicLong failed = new AtomicLong();

    public KafkaAuditSink(Producer<String, String> producer, ObjectMapper objectMapper,
            AuditProperties properties) {
        super(properties.getSender().getQueueCapacity(), properties.getSender().getBatchSize(),
                properties.getSender().getFlushInterval());
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topic = properties.getSender().getTopic();
    }

    /**
     * @param bootstrapServers   spring.kafka.bootstrap-servers
     * @param commonProperties   spring.kafka.properties.* (security, ...)
     */
    public static Producer<String, String> createProducer(List<String> bootstrapServers,
            Map<String, String> commonProperties) {
        Map<String, Object> config = new HashMap<>(commonProperties);
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", bootstrapServers));
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "audit-producer");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Gom log thành batch lớn hơn, nén để giảm băng thông (body JSON nén rất tốt)
        config.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        // Broker chết: send() chỉ chờ metadata tối đa 5s (trên thread nền), sau đó báo lỗi
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
        return new KafkaProducer<>(config);
    }

    @Override
    protected void send(List<AuditEvent> batch) throws Exception {
        for (AuditEvent event : batch) {
            String key = event.requestId() != null ? event.requestId() : String.valueOf(event.id());
            producer.send(new ProducerRecord<>(topic, key, objectMapper.writeValueAsString(event)),
                    (metadata, exception) -> {
                        if (exception != null) {
                            long count = failed.incrementAndGet();
                            if (count == 1 || count % 100 == 0) {
                                log.warn("Kafka không nhận audit log (tổng cộng đã mất {}): {}", count,
                                        exception.toString());
                            }
                        }
                    });
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        super.destroy();
        // Đẩy nốt log còn trong bộ đệm của producer trước khi tắt
        producer.close(Duration.ofSeconds(10));
    }
}
