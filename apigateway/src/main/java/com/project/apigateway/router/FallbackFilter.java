package com.project.apigateway.router;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FallbackFilter implements GatewayFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Đây là nơi bạn xử lý fallback nếu service không có sẵn
        return chain.filter(exchange).onErrorResume(e -> {
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return exchange.getResponse().setComplete();
        });
    }
}
