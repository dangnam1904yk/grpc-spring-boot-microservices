package com.project.common.audit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình trong application.yml của từng service (đều có giá trị mặc định):
 * <pre>
 * app:
 *   audit:
 *     enabled: true
 *     entity-changes: true
 *     grpc: true
 *     http:
 *       exclude-paths: [/actuator/**]
 *       max-body-length: 10000
 *     sender:
 *       transport: kafka        # hoặc grpc (dự phòng)
 *       topic: audit-log
 *       key: ${AUDIT_KEY:dev-audit-key}
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.audit")
public class AuditProperties {

    /** Tắt toàn bộ: không filter, không listener, không gửi log. */
    private boolean enabled = true;

    /** Tên service ghi vào log; để trống thì lấy spring.application.name. */
    private String serviceName;

    /** Ghi log thay đổi dữ liệu (thêm / sửa / xoá entity JPA). */
    private boolean entityChanges = true;

    /** Ghi log gRPC call đi vào service. */
    private boolean grpc = true;

    /** Header bị thay bằng "***" (không phân biệt hoa thường). */
    private List<String> maskedHeaders = new ArrayList<>(
            List.of("authorization", "proxy-authorization", "cookie", "set-cookie", "x-audit-key"));

    /** Field (JSON body, form, query, entity) có tên chứa một trong các từ này bị thay bằng "***". */
    private List<String> maskedFields = new ArrayList<>(List.of("password", "token", "secret"));

    private final Http http = new Http();

    private final Sender sender = new Sender();

    @Getter
    @Setter
    public static class Http {

        /** Không ghi log các path này (Ant pattern). */
        private List<String> excludePaths = new ArrayList<>(List.of("/actuator/**", "/error"));

        /** Vẫn ghi log nhưng bỏ response body (ví dụ API trả dữ liệu lớn). */
        private List<String> excludeResponseBodyPaths = new ArrayList<>();

        /** Body dài hơn bị cắt bớt. */
        private int maxBodyLength = 10_000;
    }

    public enum Transport {
        /** Gửi vào Kafka topic; audit-service consume. Log không mất khi audit-service tắt. */
        KAFKA,
        /** Gọi thẳng gRPC AuditService.Record (dự phòng khi không có Kafka). */
        GRPC
    }

    @Getter
    @Setter
    public static class Sender {

        private Transport transport = Transport.KAFKA;

        /** Kafka topic (transport = kafka). Phải trùng app.audit-server.topic của audit-service. */
        private String topic = "audit-log";

        /** Tên gRPC client (transport = grpc): grpc.client.&lt;name&gt;.address. */
        private String client = "audit-service";

        /** Transport = grpc: phải trùng app.audit-server.ingest-key của audit-service. */
        private String key = "dev-audit-key";

        /** Đầy hàng đợi (audit-service chết lâu) thì bỏ log mới thay vì giữ vô hạn trong RAM. */
        private int queueCapacity = 10_000;

        private int batchSize = 100;

        /** Gom log tối đa bấy lâu rồi gửi 1 lô. */
        private Duration flushInterval = Duration.ofSeconds(1);

        private Duration deadline = Duration.ofSeconds(3);
    }
}
