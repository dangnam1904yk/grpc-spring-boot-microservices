package com.project.audit.grpc;

import com.project.audit.service.AuditLogWriter;
import com.project.common.audit.grpc.AuditProtoMapper;
import com.project.grpc.audit.v1.AuditServiceGrpc;
import com.project.grpc.audit.v1.RecordRequest;
import com.project.grpc.audit.v1.RecordResponse;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Đường DỰ PHÒNG (service đặt app.audit.sender.transport=grpc): nhận lô log qua gRPC thay vì Kafka.
 * Xác thực bằng {@link AuditKeyInterceptor}.
 */
@GrpcService
@RequiredArgsConstructor
public class AuditGrpcService extends AuditServiceGrpc.AuditServiceImplBase {

    private final AuditLogWriter writer;

    @Override
    public void record(RecordRequest request, StreamObserver<RecordResponse> responseObserver) {
        int accepted = writer.write(request.getEventsList().stream()
                .map(AuditProtoMapper::fromProto)
                .toList());
        responseObserver.onNext(RecordResponse.newBuilder().setAccepted(accepted).build());
        responseObserver.onCompleted();
    }
}
