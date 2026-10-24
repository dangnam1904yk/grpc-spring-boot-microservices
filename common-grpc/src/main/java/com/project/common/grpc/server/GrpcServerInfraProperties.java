package com.project.common.grpc.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Cấu hình trong application.yml của từng service:
 * <pre>
 * app:
 *   grpc:
 *     server:
 *       worker-threads: 32
 *       max-concurrent-calls: 200
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.grpc.server")
public class GrpcServerInfraProperties {

    /** Số thread xử lý gRPC call. */
    private int workerThreads = 32;

    /** Vượt ngưỡng này server trả RESOURCE_EXHAUSTED ngay thay vì xếp hàng vô hạn. */
    private int maxConcurrentCalls = 200;
}
