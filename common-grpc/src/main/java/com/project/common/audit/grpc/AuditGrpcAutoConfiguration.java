package com.project.common.audit.grpc;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.project.common.audit.AuditAutoConfiguration;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.AuditSink;
import com.project.common.audit.Sanitizer;
import com.project.grpc.audit.v1.AuditServiceGrpc;

import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import net.devh.boot.grpc.client.nameresolver.NameResolverRegistration;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

/**
 * Phần audit dùng gRPC (chỉ bật khi service có net.devh tương ứng trên classpath):
 * ghi log gRPC call đi vào, truyền request id sang service khác, và (dự phòng) gửi log về audit-service qua gRPC.
 * Chạy trước {@link AuditAutoConfiguration} để sink gRPC được ưu tiên hơn sink ghi ra log.
 */
@AutoConfiguration(before = AuditAutoConfiguration.class,
        afterName = "net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration")
@ConditionalOnProperty(prefix = "app.audit", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditGrpcAutoConfiguration {

    /** Đường dự phòng: chỉ bật khi app.audit.sender.transport=grpc (mặc định là Kafka). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ GrpcChannelFactory.class, AuditServiceGrpc.class })
    @ConditionalOnBean(GrpcChannelFactory.class)
    @ConditionalOnProperty(prefix = "app.audit.sender", name = "transport", havingValue = "grpc")
    static class Sender {

        /**
         * Phải nhận {@link NameResolverRegistration}: bean đó của net.devh là @Lazy và chỉ được tạo khi service
         * có field @GrpcClient. Không ép tạo thì service không có @GrpcClient (category, auth...) không biết
         * địa chỉ discovery:/// ("Could not find a NameResolverProvider for discovery:///audit-service").
         */
        @Bean
        @ConditionalOnMissingBean(AuditSink.class)
        GrpcAuditSink grpcAuditSink(GrpcChannelFactory channelFactory, AuditProperties properties,
                NameResolverRegistration nameResolverRegistration) {
            return new GrpcAuditSink(channelFactory, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(GrpcGlobalClientInterceptor.class)
    static class Client {

        @Bean
        @GrpcGlobalClientInterceptor
        @ConditionalOnMissingBean
        GrpcAuditClientInterceptor grpcAuditClientInterceptor() {
            return new GrpcAuditClientInterceptor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ GrpcGlobalServerInterceptor.class, com.google.protobuf.Message.class })
    @ConditionalOnProperty(prefix = "app.audit", name = "grpc", matchIfMissing = true)
    static class Server {

        @Bean
        @GrpcGlobalServerInterceptor
        @ConditionalOnMissingBean
        GrpcAuditServerInterceptor grpcAuditServerInterceptor(AuditSink sink, AuditProperties properties,
                Environment environment) {
            return new GrpcAuditServerInterceptor(sink, new Sanitizer(properties),
                    AuditAutoConfiguration.serviceName(properties, environment));
        }

        @Bean
        @GrpcGlobalServerInterceptor
        @ConditionalOnMissingBean
        GrpcAuditUserInterceptor grpcAuditUserInterceptor() {
            return new GrpcAuditUserInterceptor();
        }
    }
}
