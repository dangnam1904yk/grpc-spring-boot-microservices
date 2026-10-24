package com.project.business.config;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;

@Configuration
public class GrpcClientConfig {

    /** Tên client (grpc.client.*) -> gRPC service và các method chỉ đọc (an toàn để retry). */
    private static final Map<String, ReadMethods> READ_METHODS = Map.of(
            "category-service", new ReadMethods("category.v1.CategoryService",
                    List.of("GetCategory", "BatchGetCategories", "ListCategories")),
            "customer-service", new ReadMethods("customer.v1.CustomerService",
                    List.of("GetCustomer", "BatchGetCustomers")));

    /**
     * Tự retry khi service đích tạm thời không truy cập được (UNAVAILABLE: instance đang restart,
     * mất kết nối...). Chỉ áp dụng cho method đọc vì retry method ghi có thể tạo bản ghi trùng.
     * Lưu ý: gRPC yêu cầu số trong service config phải là Double.
     */
    @Bean
    GrpcChannelConfigurer retryChannelConfigurer() {
        return (channelBuilder, name) -> {
            ReadMethods readMethods = READ_METHODS.get(name);
            if (readMethods != null) {
                channelBuilder.defaultServiceConfig(readMethods.retryServiceConfig()).enableRetry();
            }
        };
    }

    private record ReadMethods(String service, List<String> methods) {

        Map<String, Object> retryServiceConfig() {
            Map<String, Object> retryPolicy = Map.of(
                    "maxAttempts", 3.0,
                    "initialBackoff", "0.1s",
                    "maxBackoff", "1s",
                    "backoffMultiplier", 2.0,
                    "retryableStatusCodes", List.of("UNAVAILABLE"));
            List<Map<String, String>> names = methods.stream()
                    .map(method -> Map.of("service", service, "method", method))
                    .toList();
            return Map.of("methodConfig", List.of(Map.of("name", names, "retryPolicy", retryPolicy)));
        }
    }
}
