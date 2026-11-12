package com.project.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link AuthorityCache}: Redis nếu service có spring-boot-starter-data-redis (cấu hình spring.data.redis.*),
 * không thì {@link NoopAuthorityCache}. Chạy trước AuthorityResolverAutoConfiguration (dùng cache này).
 */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        beforeName = "com.project.common.security.grpc.AuthorityResolverAutoConfiguration")
@EnableConfigurationProperties(AppSecurityProperties.class)
public class AuthorityCacheAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "app.security", name = "authority-cache-enabled", matchIfMissing = true)
    static class Redis {

        @Bean
        @ConditionalOnMissingBean
        AuthorityCache authorityCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                AppSecurityProperties properties) {
            return new RedisAuthorityCache(redis, objectMapper, properties.getAuthorityCacheTtl());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    AuthorityCache noopAuthorityCache() {
        return new NoopAuthorityCache();
    }
}
