package com.project.common.grpc.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import com.project.common.i18n.Messages;

import io.grpc.ServerBuilder;
import net.devh.boot.grpc.server.autoconfigure.GrpcAdviceAutoConfiguration;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;

/**
 * Tự bật cho mọi service có gRPC server (net.devh) trên classpath.
 * <p>
 * Phải chạy trước {@link GrpcAdviceAutoConfiguration}: auto-config đó chỉ bật khi đã có bean {@code @GrpcAdvice}.
 */
@AutoConfiguration(before = { GrpcAdviceAutoConfiguration.class, GrpcServerFactoryAutoConfiguration.class })
@ConditionalOnClass(GrpcServerConfigurer.class)
@EnableConfigurationProperties(GrpcServerInfraProperties.class)
public class GrpcServerInfraAutoConfiguration {

    /**
     * Mặc định grpc-java dùng cached thread pool không giới hạn: tải cao sẽ tạo hàng nghìn thread.
     * Ở đây dùng pool cố định, kết hợp {@link ConcurrencyLimitInterceptor} để chặn quá tải.
     */
    @Bean
    @ConditionalOnMissingBean
    GrpcWorkerExecutor grpcWorkerExecutor(GrpcServerInfraProperties properties) {
        return new GrpcWorkerExecutor(properties.getWorkerThreads());
    }

    @Bean
    @GrpcGlobalServerInterceptor
    @ConditionalOnMissingBean
    ConcurrencyLimitInterceptor concurrencyLimitInterceptor(GrpcServerInfraProperties properties) {
        return new ConcurrencyLimitInterceptor(properties.getMaxConcurrentCalls());
    }

    @Bean
    @ConditionalOnMissingBean
    GrpcExceptionAdvice grpcExceptionAdvice(ObjectProvider<Messages> messages) {
        return new GrpcExceptionAdvice(messages.getIfAvailable(Messages::commonOnly));
    }

    public static class GrpcWorkerExecutor implements GrpcServerConfigurer, DisposableBean {

        private final ExecutorService executor;

        public GrpcWorkerExecutor(int threads) {
            this.executor = Executors.newFixedThreadPool(threads, new CustomizableThreadFactory("grpc-worker-"));
        }

        @Override
        public void accept(ServerBuilder<?> serverBuilder) {
            serverBuilder.executor(executor);
        }

        @Override
        public void destroy() throws InterruptedException {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}
