package com.project.apigateway.controller.category;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.protobuf.Any;
import com.project.apigateway.interfaces.CategoryDbhcMapper;
import com.project.apigateway.mapper.CategoryGrpcMapper;
import com.project.apigateway.model.app.AppResponse;
import com.project.apigateway.model.category.request.CategoryDbhcRequest;
import com.project.apigateway.model.category.response.CategoryDbhcDto;
import com.project.apigateway.services.categorys.CategoryServiceGrpc;
import com.project.apigateway.utils.Constains;
import com.project.apigateway.utils.GrpcMapper;

import common.CategoryDbhcServiceGrpc;
import common.CategoryDbhc.CategoryGrpc;
import common.CategoryDbhc.GenericResponseGrpc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;

@RestController
@RequestMapping(Constains.PATH_SERVICE.CATEGORY_SERVICE)
@RequiredArgsConstructor
public class CategoryDbhcController {

    private final CategoryServiceGrpc categoryServiceGrpc;

    @PostMapping("/test")
    public ResponseEntity<?> getMethodName(@RequestBody @Valid CategoryDbhcRequest req) {
        return ResponseEntity.ok(categoryServiceGrpc.test(req));
    }
}
