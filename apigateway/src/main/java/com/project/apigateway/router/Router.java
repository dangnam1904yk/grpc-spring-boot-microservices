
package com.project.apigateway.router;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Router {

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                return builder.routes()
                                .route("customer-service", r -> r.path("/api/customer/**")
                                                .uri("lb://customer-service"))
                                .route("business-service", r -> r.path("/api/business/**")
                                                .uri("lb://business-service"))
                                .route("category-service", r -> r.path("/api/category/**")
                                                .uri("lb://category-service"))
                                .build();

                // return builder.routes()
                // .route("customer-service", r -> r.path("/api/customer/**")
                // .filters(f -> f.filter(new JwtAuthenticationFilter()) // Áp dụng JWT
                // // filter
                // .filter(new FallbackFilter())) // Áp dụng Fallback
                // // filter
                // .uri("lb://customer-service"))
                // .route("business-service", r -> r.path("/api/business/**")
                // .filters(f -> f.filter(new JwtAuthenticationFilter()) // Áp dụng JWT
                // // filter
                // .filter(new FallbackFilter())) // Áp dụng Fallback
                // // filter
                // .uri("lb://business-service"))
                // .route("category-service", r -> r.path("/api/category/**")
                // .filters(f -> f.filter(new JwtAuthenticationFilter()) // Áp dụng JWT
                // // filter
                // .filter(new FallbackFilter())) // Áp dụng Fallback
                // // filter
                // .uri("lb://category-service"))
                // .build();
        }

        @Bean
        public GlobalFilter corsFilter() {
                return (exchange, chain) -> {
                        exchange.getResponse().getHeaders().add("Access-Control-Allow-Origin", "*"); // "http://localhost:4200"
                        exchange.getResponse().getHeaders().add("Access-Control-Allow-Methods",
                                        "GET, POST, PUT, DELETE, OPTIONS");
                        exchange.getResponse().getHeaders().add("Access-Control-Allow-Headers",
                                        "Content-Type, Authorization");
                        exchange.getResponse().getHeaders().add("Access-Control-Allow-Credentials", "true");
                        // Cho phép gửi cookies hoặc thông tin xác thực
                        return chain.filter(exchange);
                };
        }

}
