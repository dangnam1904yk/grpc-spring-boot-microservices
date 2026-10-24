package com.project.common.audit;

/**
 * Nơi nhận log. Mặc định là {@link com.project.common.audit.grpc.GrpcAuditSink} (gửi về audit-service).
 * Service tự khai báo bean AuditSink thì bean mặc định sẽ lùi lại (audit-service ghi thẳng DB, test thu log lại).
 * <p>
 * Không được block hay ném exception: ghi log không bao giờ được làm hỏng request nghiệp vụ.
 */
public interface AuditSink {

    void publish(AuditEvent event);
}
