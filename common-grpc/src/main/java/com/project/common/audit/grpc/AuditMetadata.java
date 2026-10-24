package com.project.common.audit.grpc;

import io.grpc.Metadata;

/** Metadata gRPC để nối log giữa các service. */
public final class AuditMetadata {

    public static final Metadata.Key<String> REQUEST_ID =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    /** IP của client gốc (người dùng), không phải IP của service gọi. */
    public static final Metadata.Key<String> CLIENT_IP =
            Metadata.Key.of("x-client-ip", Metadata.ASCII_STRING_MARSHALLER);

    /** Khoá để audit-service nhận log (thay cho JWT). */
    public static final Metadata.Key<String> AUDIT_KEY =
            Metadata.Key.of("x-audit-key", Metadata.ASCII_STRING_MARSHALLER);

    private AuditMetadata() {
    }
}
