package com.project.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Thay cache Redis bằng {@link RecordingAuthorityCache} để kiểm tra auth-service xoá cache đúng lúc. Mọi test class
 * của auth import cùng config này để dùng chung 1 Spring context (và 1 database H2).
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthTestConfig {

    @Bean
    RecordingAuthorityCache recordingAuthorityCache() {
        return new RecordingAuthorityCache();
    }
}
