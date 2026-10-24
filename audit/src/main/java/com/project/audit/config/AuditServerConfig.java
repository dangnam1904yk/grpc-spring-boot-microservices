package com.project.audit.config;

import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import com.project.audit.grpc.AuditKeyInterceptor;
import com.project.audit.service.AuditLogWriter;
import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.AuditSink;
import com.project.common.audit.BatchingAuditSink;
import com.project.grpc.audit.v1.AuditServiceGrpc;

import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import net.devh.boot.grpc.server.security.check.AccessPredicate;
import net.devh.boot.grpc.server.security.check.GrpcSecurityMetadataSource;
import net.devh.boot.grpc.server.security.check.ManualGrpcSecurityMetadataSource;

@Configuration
public class AuditServerConfig {

    /** Tạo topic lúc khởi động nếu chưa có (KafkaAdmin của Spring Boot). */
    @Bean
    NewTopic auditLogTopic(AuditServerProperties properties) {
        return TopicBuilder.name(properties.getTopic())
                .partitions(properties.getTopicPartitions())
                .replicas(properties.getTopicReplicas())
                .build();
    }

    /**
     * Ghi DB lỗi (DB chết, mất kết nối): không bỏ lô log mà đọc lại và thử tiếp, chờ tăng dần 1s -> 30s,
     * không giới hạn số lần. Log nằm yên trong Kafka cho tới khi ghi được.
     */
    @Bean
    CommonErrorHandler auditLogErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2);
        backOff.setMaxInterval(30_000);
        return new DefaultErrorHandler(backOff);
    }

    /** Thay quy tắc mặc định của common-grpc: AuditService không cần JWT (đã có {@link AuditKeyInterceptor}). */
    @Bean
    GrpcSecurityMetadataSource grpcSecurityMetadataSource() {
        return new ManualGrpcSecurityMetadataSource()
                .setDefault(AccessPredicate.authenticated())
                .set(HealthGrpc.getServiceDescriptor(), AccessPredicate.permitAll())
                .set(ServerReflectionGrpc.getServiceDescriptor(), AccessPredicate.permitAll())
                .set(AuditServiceGrpc.getServiceDescriptor(), AccessPredicate.permitAll());
    }

    @Bean
    @GrpcGlobalServerInterceptor
    AuditKeyInterceptor auditKeyInterceptor(AuditServerProperties properties) {
        return new AuditKeyInterceptor(properties);
    }

    /**
     * Log của chính audit-service (ai tra cứu log) ghi thẳng DB, không đi vòng qua Kafka rồi lại về chính nó.
     * Thay cho KafkaAuditSink mặc định của common-grpc.
     */
    @Bean
    AuditSink dbAuditSink(AuditLogWriter writer, AuditProperties properties) {
        AuditProperties.Sender sender = properties.getSender();
        return new BatchingAuditSink(sender.getQueueCapacity(), sender.getBatchSize(), sender.getFlushInterval()) {
            @Override
            protected void send(List<AuditEvent> batch) {
                writer.write(batch);
            }
        };
    }
}
