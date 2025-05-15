package com.project.apigateway.router;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GatewayFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (token == null || !token.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String jwtToken = token.substring(7); // Lấy token sau "Bearer "
        if (isValidJwt(jwtToken)) {
            return chain.filter(exchange); // Nếu token hợp lệ, tiếp tục
        } else {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    // Hàm kiểm tra tính hợp lệ của JWT token (Ví dụ đơn giản)
    private boolean isValidJwt(String jwtToken) {
        // Implement validation logic here (có thể sử dụng thư viện JWT như jjwt hoặc
        // jose)
        return true; // Giả sử token hợp lệ
    }
}
