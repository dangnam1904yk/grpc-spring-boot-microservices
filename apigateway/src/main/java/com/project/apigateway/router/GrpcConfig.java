package com.project.apigateway.router;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import common.CategoryDbhcServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;

@Configuration
public class GrpcConfig {

    @GrpcClient("category-service")
    CategoryDbhcServiceGrpc.CategoryDbhcServiceBlockingStub categoryDbhcServiceBlockingStub;

    @Bean
    public CategoryDbhcServiceGrpc.CategoryDbhcServiceBlockingStub categoryDbhcServiceBlockingStub() {
        return categoryDbhcServiceBlockingStub;
    }
}
