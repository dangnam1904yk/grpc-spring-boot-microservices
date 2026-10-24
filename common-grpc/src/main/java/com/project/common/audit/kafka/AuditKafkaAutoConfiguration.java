package com.project.common.audit.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.audit.AuditAutoConfiguration;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.AuditSink;
import com.project.common.audit.grpc.AuditGrpcAutoConfiguration;

/**
 * Sink mặc định: gửi log vào Kafka ({@code app.audit.sender.transport=kafka}, địa chỉ broker lấy từ
 * {@code spring.kafka.bootstrap-servers}). Đặt {@code transport=grpc} để dùng đường gRPC dự phòng.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class,
        before = { AuditGrpcAutoConfiguration.class, AuditAutoConfiguration.class })
@ConditionalOnClass({ KafkaProducer.class, KafkaProperties.class, ObjectMapper.class })
@ConditionalOnBean(KafkaProperties.class)
// Boot 3.4 chưa cho lặp @ConditionalOnProperty: gộp 2 điều kiện (audit bật + transport = kafka)
@ConditionalOnExpression("${app.audit.enabled:true} and '${app.audit.sender.transport:kafka}'.equalsIgnoreCase('kafka')")
@EnableConfigurationProperties(AuditProperties.class)
public class AuditKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditSink.class)
    KafkaAuditSink kafkaAuditSink(KafkaProperties kafkaProperties, ObjectMapper objectMapper,
            AuditProperties properties) {
        return new KafkaAuditSink(
                KafkaAuditSink.createProducer(kafkaProperties.getBootstrapServers(), kafkaProperties.getProperties()),
                objectMapper, properties);
    }
}
