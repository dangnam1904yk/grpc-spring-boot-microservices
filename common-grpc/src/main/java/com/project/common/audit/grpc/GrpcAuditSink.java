package com.project.common.audit.grpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.BatchingAuditSink;
import com.project.grpc.audit.v1.AuditServiceGrpc;
import com.project.grpc.audit.v1.RecordRequest;

import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;

/**
 * Gửi log về audit-service qua gRPC theo lô (mặc định 100 log / 1s). Địa chỉ lấy từ
 * {@code grpc.client.audit-service.address} (ví dụ {@code discovery:///audit-service}).
 */
public class GrpcAuditSink extends BatchingAuditSink {

    private final GrpcChannelFactory channelFactory;
    private final AuditProperties.Sender config;
    private volatile AuditServiceGrpc.AuditServiceBlockingStub stub;

    public GrpcAuditSink(GrpcChannelFactory channelFactory, AuditProperties properties) {
        super(properties.getSender().getQueueCapacity(), properties.getSender().getBatchSize(),
                properties.getSender().getFlushInterval());
        this.channelFactory = channelFactory;
        this.config = properties.getSender();
    }

    @Override
    protected void send(List<AuditEvent> batch) {
        RecordRequest request = RecordRequest.newBuilder()
                .addAllEvents(batch.stream().map(AuditProtoMapper::toProto).toList())
                .build();
        stub().withDeadlineAfter(config.getDeadline().toMillis(), TimeUnit.MILLISECONDS).record(request);
    }

    /** Tạo channel lúc gửi lô đầu tiên, không phải lúc khởi động (audit-service có thể chưa chạy). */
    private AuditServiceGrpc.AuditServiceBlockingStub stub() {
        if (stub == null) {
            Metadata headers = new Metadata();
            headers.put(AuditMetadata.AUDIT_KEY, config.getKey());
            stub = AuditServiceGrpc.newBlockingStub(channelFactory.createChannel(config.getClient()))
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        }
        return stub;
    }
}
