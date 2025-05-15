package com.project.apigateway.controller.category;

import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.protobuf.Any;
import com.project.apigateway.model.category.request.CategoryDbhcRequest;
import com.project.apigateway.utils.Constains;

import common.CategoryDbhcServiceGrpc;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;

@RestController
@RequestMapping(Constains.PATH_SERVICE.CATEGORY_SERVICE)
public class CategoryDbhcController {

    @GrpcClient("category-service")
    private CategoryDbhcServiceGrpc.CategoryDbhcServiceBlockingStub categoryDbhcServiceBlockingStub;

    @PostMapping("/test")
    public ResponseEntity<?> getMethodName(@RequestBody CategoryDbhcRequest req) {

        common.CategoryDbhc.Category.Builder builder = common.CategoryDbhc.Category.newBuilder();

        Optional.ofNullable(req.getId()).ifPresent(builder::setId);
        Optional.ofNullable(req.getCode()).ifPresent(builder::setCode);
        Optional.ofNullable(req.getName()).ifPresent(builder::setName);
        Optional.ofNullable(req.getDescription()).ifPresent(builder::setDescription);
        Optional.ofNullable(req.getParentId()).ifPresent(builder::setParentId);
        Optional.ofNullable(req.getParentCode()).ifPresent(builder::setParentCode);
        builder.setStatus(req.isStatus());

        Any requestData = Any.pack(
                builder.build());
        common.CategoryDbhc.GenericRequest request = common.CategoryDbhc.GenericRequest.newBuilder()
                .setPayload(requestData)
                .build();
        var resut = categoryDbhcServiceBlockingStub.processRequest(request);
        return ResponseEntity.ok(resut);

    }
}
