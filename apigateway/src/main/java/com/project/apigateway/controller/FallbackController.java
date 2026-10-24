package com.project.apigateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.apigateway.model.app.AppResponse;

import reactor.core.publisher.Mono;

/**
 * Trả về khi circuit breaker mở hoặc service downstream timeout / không kết nối được.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{serviceId}")
    public Mono<ResponseEntity<AppResponse<Void>>> fallback(@PathVariable String serviceId) {
        AppResponse<Void> body = AppResponse.<Void>builder()
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .messege("Service " + serviceId + " tạm thời không khả dụng, vui lòng thử lại sau")
                .build();
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
