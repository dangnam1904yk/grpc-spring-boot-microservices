package com.project.common.audit;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.audit.jpa.EntityChangeListener;
import com.project.common.audit.jpa.EntityChangeListenerRegistrar;
import com.project.common.audit.web.AuditFilter;
import com.project.common.audit.web.AuditUserFilter;

import jakarta.persistence.EntityManagerFactory;

/**
 * Tự bật hệ thống log cho mọi service (tắt bằng {@code app.audit.enabled=false}):
 * <ul>
 * <li>REST: {@link AuditFilter} ghi IP, curl, request, response.</li>
 * <li>JPA: {@link EntityChangeListener} ghi nội dung thay đổi của dữ liệu.</li>
 * <li>Gửi log: Kafka (AuditKafkaAutoConfiguration), hoặc gRPC nếu {@code app.audit.sender.transport=grpc}
 * (AuditGrpcAutoConfiguration); không có cả hai thì ghi ra log của service.</li>
 * </ul>
 * Service muốn xử lý log theo cách riêng thì khai báo bean {@link AuditSink}.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class, before = TaskExecutionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "app.audit", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

    public static String serviceName(AuditProperties properties, Environment environment) {
        return StringUtils.hasText(properties.getServiceName())
                ? properties.getServiceName()
                : environment.getProperty("spring.application.name", "unknown");
    }

    @Bean
    @ConditionalOnMissingBean
    AuditSink loggingAuditSink() {
        return new LoggingAuditSink();
    }

    /** applicationTaskExecutor (Spring Boot) tự dùng TaskDecorator này -> MDC đi theo task. */
    @Bean
    @ConditionalOnMissingBean
    TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(ObjectMapper.class)
    static class Web {

        /** Chạy ngay trước Spring Security (order -100) để ghi được cả request bị 401/403. */
        @Bean
        FilterRegistrationBean<AuditFilter> auditFilter(AuditSink sink, AuditProperties properties,
                ObjectMapper objectMapper, Environment environment) {
            FilterRegistrationBean<AuditFilter> registration = new FilterRegistrationBean<>(
                    new AuditFilter(sink, properties, objectMapper, serviceName(properties, environment)));
            registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER - 1);
            return registration;
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnClass(name = "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken")
        static class Security {

            /** Chạy ngay sau Spring Security: lúc này đã biết user. */
            @Bean
            FilterRegistrationBean<AuditUserFilter> auditUserFilter() {
                FilterRegistrationBean<AuditUserFilter> registration = new FilterRegistrationBean<>(
                        new AuditUserFilter());
                registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
                return registration;
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ SessionFactoryImplementor.class, EntityManagerFactory.class, ObjectMapper.class })
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnProperty(prefix = "app.audit", name = "entity-changes", matchIfMissing = true)
    static class Jpa {

        @Bean
        EntityChangeListener entityChangeListener(AuditSink sink, AuditProperties properties,
                ObjectMapper objectMapper, Environment environment) {
            return new EntityChangeListener(sink, new Sanitizer(properties), objectMapper,
                    serviceName(properties, environment));
        }

        @Bean
        EntityChangeListenerRegistrar entityChangeListenerRegistrar(EntityManagerFactory entityManagerFactory,
                EntityChangeListener listener) {
            return new EntityChangeListenerRegistrar(entityManagerFactory, listener);
        }
    }
}
