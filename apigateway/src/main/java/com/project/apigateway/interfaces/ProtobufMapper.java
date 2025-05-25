package com.project.apigateway.interfaces;

import com.google.protobuf.Message;

public interface ProtobufMapper<P extends Message, D> {
    D toDto(P proto);

    P toProto(D dto);
}
