package com.project.category.service;

import com.google.protobuf.Any;

import common.CategoryDbhc;
import common.CategoryDbhcServiceGrpc.CategoryDbhcServiceImplBase;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class CategoryDBHCService extends CategoryDbhcServiceImplBase {

    @Override
    public void processRequest(CategoryDbhc.GenericRequestGrpc request,
            StreamObserver<CategoryDbhc.GenericResponseGrpc> responseObserver) {
        Any requestData = request.getPayload();
        try {
            common.CategoryDbhc.CategoryGrpc categoryDbhc = requestData.unpack(common.CategoryDbhc.CategoryGrpc.class);

            Any responseData = Any.pack(categoryDbhc); // pack lại category đã unpack và xử lý

            CategoryDbhc.GenericResponseGrpc response = CategoryDbhc.GenericResponseGrpc.newBuilder()
                    .setStatus(200)
                    .setMessage("Category processed successfully")
                    .setData(responseData) // dữ liệu hợp lệ
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace();
            responseObserver.onError(e);
        }
    }
}
