package com.project.common.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.project.common.i18n.Messages;

/**
 * Tự bật cho service Spring MVC. Service muốn xử lý lỗi theo cách riêng thì khai báo
 * {@link ResponseEntityExceptionHandler} của mình, bean này sẽ tự lùi lại.
 */
@AutoConfiguration(before = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebInfraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)
    RestExceptionHandler restExceptionHandler(ObjectProvider<Messages> messages) {
        return new RestExceptionHandler(messages.getIfAvailable(Messages::commonOnly));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.access.AccessDeniedException")
    static class SecurityErrors {

        @Bean
        @ConditionalOnMissingBean
        SecurityExceptionHandler securityExceptionHandler(ObjectProvider<Messages> messages) {
            return new SecurityExceptionHandler(messages.getIfAvailable(Messages::commonOnly));
        }
    }
}
