package com.project.common.jpa;

import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.project.common.audit.AuditContext;

import jakarta.persistence.EntityManagerFactory;

/**
 * Bật JPA Auditing cho {@link BaseEntity} ở mọi service có JPA. Service không cần {@code @EnableJpaAuditing}
 * (khai báo thêm sẽ bị lỗi trùng bean).
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({ EntityManagerFactory.class, EnableJpaAuditing.class })
@ConditionalOnBean(EntityManagerFactory.class)
@EnableJpaAuditing(auditorAwareRef = "baseEntityAuditor", dateTimeProviderRef = "baseEntityDateTimeProvider")
public class JpaAuditingAutoConfiguration {

    public static final String SYSTEM_USER = "system";

    /**
     * User trong SecurityContext (thread của request / gRPC call) hoặc trong MDC (thread async như
     * applicationTaskExecutor, đã được copy sang bằng MdcTaskDecorator).
     */
    @Bean
    @ConditionalOnMissingBean(name = "baseEntityAuditor")
    AuditorAware<String> baseEntityAuditor() {
        return () -> {
            AuditContext.User user = AuditContext.currentUser();
            return Optional.of(user != null && user.id() != null ? user.id() : SYSTEM_USER);
        };
    }

    /** Lưu Instant (UTC), không phụ thuộc múi giờ của server. */
    @Bean
    @ConditionalOnMissingBean(name = "baseEntityDateTimeProvider")
    DateTimeProvider baseEntityDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
