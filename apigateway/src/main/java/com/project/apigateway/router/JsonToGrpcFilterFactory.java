// package com.project.apigateway.router;

// import org.springframework.cloud.gateway.filter.GatewayFilter;
// import
// org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;

// public class JsonToGrpcFilterFactory extends
// AbstractGatewayFilterFactory<JsonToGrpcFilterFactory.Config> {
// public JsonToGrpcFilterFactory() {
// // no args constructor
// }

// @Override
// public GatewayFilter apply(Config config) {
// return (exchange, chain) -> {
// // chuyển đổi JSON → gRPC call ở đây
// return chain.filter(exchange);
// };
// }

// public static class Config {
// // cấu hình filter nếu cần
// }
// }
