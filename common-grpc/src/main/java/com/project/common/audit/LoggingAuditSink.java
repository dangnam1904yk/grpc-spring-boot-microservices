package com.project.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dùng khi service không có gRPC client để gửi về audit-service: ghi ra log của chính service
 * (logger "AUDIT"), để không mất log.
 */
public class LoggingAuditSink implements AuditSink {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    @Override
    public void publish(AuditEvent event) {
        AUDIT.info("{}", event);
    }
}
