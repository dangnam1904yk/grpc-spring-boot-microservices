package com.project.common.audit;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Copy MDC (request id, user...) của thread gửi task sang thread của applicationTaskExecutor,
 * để log / thay đổi dữ liệu ghi trong {@code thenApplyAsync(..., executor)} vẫn gắn đúng request.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> AuditContext.runWith(context, runnable);
    }
}
