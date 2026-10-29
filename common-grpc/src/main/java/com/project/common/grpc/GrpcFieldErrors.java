package com.project.common.grpc;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.rpc.BadRequest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

/**
 * Lỗi theo field qua gRPC theo mô hình chuẩn của Google: {@code google.rpc.Status} kèm {@code google.rpc.BadRequest}
 * (field_violations), nằm trong trailer {@code grpc-status-details-bin}. Client không đọc chi tiết vẫn có code và
 * description như thường.
 */
public final class GrpcFieldErrors {

    private GrpcFieldErrors() {
    }

    public static StatusRuntimeException toException(Status.Code code, String description, Map<String, String> errors) {
        BadRequest.Builder badRequest = BadRequest.newBuilder();
        errors.forEach((field, message) -> badRequest.addFieldViolations(
                BadRequest.FieldViolation.newBuilder().setField(field).setDescription(message)));
        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                .setCode(code.value())
                .setMessage(description)
                .addDetails(Any.pack(badRequest.build()))
                .build();
        return StatusProto.toStatusRuntimeException(status);
    }

    /** Field -&gt; message từ lỗi gRPC; rỗng nếu lỗi không kèm {@code BadRequest}. */
    public static Map<String, String> from(Throwable error) {
        Map<String, String> errors = new LinkedHashMap<>();
        com.google.rpc.Status status = StatusProto.fromThrowable(error);
        if (status == null) {
            return errors;
        }
        for (Any detail : status.getDetailsList()) {
            if (detail.is(BadRequest.class)) {
                try {
                    detail.unpack(BadRequest.class).getFieldViolationsList()
                            .forEach(v -> errors.putIfAbsent(v.getField(), v.getDescription()));
                } catch (InvalidProtocolBufferException e) {
                    // Chi tiết hỏng thì bỏ qua, vẫn còn code + description
                }
            }
        }
        return errors;
    }
}
