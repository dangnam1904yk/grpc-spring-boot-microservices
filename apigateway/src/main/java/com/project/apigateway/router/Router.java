
package com.project.apigateway.router;

import java.time.Duration;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.project.apigateway.utils.Constains;

/**
 * Gateway chỉ định tuyến. Mỗi service tự expose REST, còn giao tiếp giữa các service đi qua gRPC.
 */
@Configuration
public class Router {

        @Bean
        public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
                RouteLocatorBuilder.Builder routes = builder.routes();
                route(routes, Constains.AUTH_SERVICE, Constains.PATH_SERVICE.AUTH_SERVICE);
                route(routes, Constains.CUSTOMER_SERVICE, Constains.PATH_SERVICE.CUSTOMER_SERVICE);
                route(routes, Constains.BUSINESS_SERVICE, Constains.PATH_SERVICE.BUSINESS_SERVICE);
                route(routes, Constains.CATEGORY_SERVICE, Constains.PATH_SERVICE.CATEGORY_SERVICE);
                return routes.build();
        }

        private static void route(RouteLocatorBuilder.Builder routes, String serviceId, String pathPrefix) {
                routes.route(serviceId, r -> r.path(pathPrefix + "/**")
                                .filters(f -> f
                                                // Service lỗi liên tục -> mở mạch, trả fallback ngay thay vì dồn request
                                                .circuitBreaker(c -> c.setName(serviceId)
                                                                .setFallbackUri("forward:/fallback/" + serviceId))
                                                // Chỉ retry GET (idempotent); POST/PUT retry có thể tạo bản ghi trùng
                                                .retry(retry -> retry.setRetries(2)
                                                                .setMethods(HttpMethod.GET)
                                                                .setStatuses(HttpStatus.BAD_GATEWAY,
                                                                                HttpStatus.SERVICE_UNAVAILABLE)
                                                                .setBackoff(Duration.ofMillis(100),
                                                                                Duration.ofSeconds(1), 2, false)))
                                // lb:// lấy danh sách instance từ Eureka và chia tải
                                .uri("lb://" + serviceId));
        }

}
