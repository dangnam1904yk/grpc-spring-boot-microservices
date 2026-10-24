package com.project.audit.grpc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.project.audit.config.AuditServerProperties;
import com.project.common.audit.grpc.AuditMetadata;
import com.project.grpc.audit.v1.AuditServiceGrpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * API nhận log không dùng JWT (log được gửi từ thread nền, có cả request chưa đăng nhập),
 * thay vào đó service gửi phải kèm "x-audit-key" đúng. Sai / thiếu -> UNAUTHENTICATED.
 */
public class AuditKeyInterceptor implements ServerInterceptor {

    private final byte[] expectedKey;

    public AuditKeyInterceptor(AuditServerProperties properties) {
        this.expectedKey = properties.getIngestKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (!AuditServiceGrpc.SERVICE_NAME.equals(call.getMethodDescriptor().getServiceName())) {
            return next.startCall(call, headers);
        }
        String key = headers.get(AuditMetadata.AUDIT_KEY);
        // So sánh thời gian cố định: không dò được key qua thời gian phản hồi
        if (key == null || !MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Sai hoặc thiếu x-audit-key"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        return next.startCall(call, headers);
    }
}
