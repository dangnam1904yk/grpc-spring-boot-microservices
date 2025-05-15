package com.project.category.service;

import com.google.protobuf.Any;

import common.CategoryDbhc;
import common.CategoryDbhcServiceGrpc.CategoryDbhcServiceImplBase;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class CategoryDBHCService extends CategoryDbhcServiceImplBase {

    @Override
    public void processRequest(CategoryDbhc.GenericRequest request,
            StreamObserver<CategoryDbhc.GenericResponse> responseObserver) {
        Any requestData = request.getPayload();
        try {
            common.CategoryDbhc.Category categoryDbhc = requestData.unpack(common.CategoryDbhc.Category.class);

            CategoryDbhc.GenericResponse response = CategoryDbhc.GenericResponse.newBuilder()
                    .setStatus("OK")
                    .setMessage("Category processed successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            e.printStackTrace(); // tốt hơn System.out.println
            responseObserver.onError(e); // báo lỗi ngược lại client
        }
    }
}
