package com.project.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.project.common.grpc.server.ConcurrencyLimitInterceptor;
import com.project.common.grpc.server.GrpcExceptionAdvice;
import com.project.common.grpc.server.GrpcServerInfraAutoConfiguration;
import com.project.common.grpc.server.GrpcServerInfraAutoConfiguration.GrpcWorkerExecutor;
import com.project.common.grpc.server.GrpcServerInfraProperties;
import com.project.common.web.RestExceptionHandler;
import com.project.common.web.WebInfraAutoConfiguration;

import net.devh.boot.grpc.server.advice.GrpcAdviceExceptionHandler;
import net.devh.boot.grpc.server.autoconfigure.GrpcAdviceAutoConfiguration;

class InfraAutoConfigurationTest {

    private final ApplicationContextRunner grpcRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GrpcServerInfraAutoConfiguration.class));

    @Test
    void grpcServerInfraIsRegisteredWithProperties() {
        grpcRunner.withPropertyValues("app.grpc.server.worker-threads=4", "app.grpc.server.max-concurrent-calls=7")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(GrpcWorkerExecutor.class)
                            .hasSingleBean(ConcurrencyLimitInterceptor.class)
                            .hasSingleBean(GrpcExceptionAdvice.class);
                    GrpcServerInfraProperties props = ctx.getBean(GrpcServerInfraProperties.class);
                    assertThat(props.getWorkerThreads()).isEqualTo(4);
                    assertThat(props.getMaxConcurrentCalls()).isEqualTo(7);
                });
    }

    @Test
    void netDevhAdviceHandlerIsActivated() {
        // GrpcAdviceAutoConfiguration chỉ bật khi đã có bean @GrpcAdvice -> kiểm tra thứ tự auto-config đúng
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(GrpcAdviceAutoConfiguration.class,
                        GrpcServerInfraAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).hasSingleBean(GrpcAdviceExceptionHandler.class));
    }

    @Test
    void serviceCanOverrideInterceptor() {
        grpcRunner.withUserConfiguration(CustomInterceptorConfig.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(ConcurrencyLimitInterceptor.class)
                        .hasBean("customInterceptor"));
    }

    @Test
    void restExceptionHandlerOnlyForServletAppsAndBacksOffWhenServiceHasItsOwn() {
        WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebInfraAutoConfiguration.class));

        webRunner.run(ctx -> assertThat(ctx).hasSingleBean(RestExceptionHandler.class));
        webRunner.withUserConfiguration(CustomRestHandlerConfig.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RestExceptionHandler.class)
                        .hasSingleBean(ResponseEntityExceptionHandler.class));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebInfraAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RestExceptionHandler.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomInterceptorConfig {
        @Bean
        ConcurrencyLimitInterceptor customInterceptor() {
            return new ConcurrencyLimitInterceptor(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomRestHandlerConfig {
        @Bean
        ResponseEntityExceptionHandler customHandler() {
            return new ResponseEntityExceptionHandler() {
            };
        }
    }
}
