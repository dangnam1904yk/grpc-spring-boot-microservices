package com.project.apigateway.utils;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.project.apigateway.interfaces.ProtobufMapper;

public class GrpcMapper {

    // Gửi: DTO → Protobuf → Any
    public static <P extends Message, D> Any wrapToAny(D dto, ProtobufMapper<P, D> mapper) {
        P protoMessage = mapper.toProto(dto);
        return Any.pack(protoMessage);
    }

    // Nhận: Any → Protobuf → DTO
    public static <P extends Message, D> D unpackAndMap(Any data, Class<P> protoClass, ProtobufMapper<P, D> mapper) {
        try {
            P proto = data.unpack(protoClass);
            return mapper.toDto(proto);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException("Failed to unpack Any to " + protoClass.getSimpleName(), e);
        }
    }
}
