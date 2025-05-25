package com.project.apigateway.services.categorys;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.protobuf.Any;
import com.project.apigateway.interfaces.CategoryDbhcMapper;
import com.project.apigateway.mapper.CategoryGrpcMapper;
import com.project.apigateway.model.app.AppResponse;
import com.project.apigateway.model.category.request.CategoryDbhcRequest;
import com.project.apigateway.model.category.response.CategoryDbhcDto;
import com.project.apigateway.utils.GrpcMapper;

import common.CategoryDbhc.CategoryGrpc;
import common.CategoryDbhc.GenericResponseGrpc;
import lombok.RequiredArgsConstructor;
import common.CategoryDbhcServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;

@Service
@RequiredArgsConstructor
public class CategoryServiceGrpc {
    @GrpcClient("category-service")
    private CategoryDbhcServiceGrpc.CategoryDbhcServiceBlockingStub categoryDbhcServiceBlockingStub;

    // @Autowired
    private final CategoryDbhcMapper categoryDbhcMapper;

    public AppResponse<CategoryDbhcDto> test(CategoryDbhcRequest request) {
        common.CategoryDbhc.CategoryGrpc.Builder builder = common.CategoryDbhc.CategoryGrpc.newBuilder();
        categoryDbhcMapper.updateProtoFromDto(request, builder);

        Any requestData = Any.pack(
                builder.build());
        common.CategoryDbhc.GenericRequestGrpc requestSend = common.CategoryDbhc.GenericRequestGrpc.newBuilder()
                .setPayload(requestData)
                .build();

        GenericResponseGrpc grpcResponse = categoryDbhcServiceBlockingStub.processRequest(requestSend);

        CategoryDbhcDto response = GrpcMapper.unpackAndMap(grpcResponse.getData(), CategoryGrpc.class,
                new CategoryGrpcMapper());
        AppResponse<CategoryDbhcDto> resultapp = new AppResponse<CategoryDbhcDto>();
        resultapp.setStatus(grpcResponse.getStatus());
        resultapp.setMessege(grpcResponse.getMessage());
        resultapp.setData(response);
        return resultapp;
    }
}
