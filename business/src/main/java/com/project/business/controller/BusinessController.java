package com.project.business.controller;

import java.util.concurrent.CompletableFuture;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.business.dto.BusinessDtos.BusinessResponse;
import com.project.business.dto.BusinessDtos.CreateBusinessRequest;
import com.project.business.dto.BusinessDtos.PageResponse;
import com.project.business.service.BusinessService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API cho client bên ngoài (đi qua API Gateway: /api/business/**).
 * Trả về CompletableFuture -> Spring MVC xử lý async (servlet async), không giữ Tomcat thread.
 */
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class BusinessController {

    private static final int MAX_PAGE_SIZE = 100;

    private final BusinessService businessService;

    @GetMapping("/{id}")
    public CompletableFuture<BusinessResponse> get(@PathVariable Long id) {
        return businessService.get(id);
    }

    @GetMapping
    public CompletableFuture<PageResponse<BusinessResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return businessService.list(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id")));
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<BusinessResponse>> create(@RequestBody @Valid CreateBusinessRequest req) {
        return businessService.create(req).thenApply(body -> ResponseEntity.status(HttpStatus.CREATED).body(body));
    }
}
