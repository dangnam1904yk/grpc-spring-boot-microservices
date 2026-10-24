package com.project.customer.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.customer.dto.CustomerDtos.CreateCustomerRequest;
import com.project.customer.dto.CustomerDtos.CustomerResponse;
import com.project.customer.dto.CustomerDtos.PageResponse;
import com.project.customer.dto.CustomerDtos.UpdateCustomerRequest;
import com.project.customer.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API cho client bên ngoài (đi qua API Gateway: /api/customer/**). Mọi endpoint cần đăng nhập.
 */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CustomerService customerService;

    /** Hồ sơ của chính user đang đăng nhập. */
    @GetMapping("/me")
    public CustomerResponse me() {
        return CustomerResponse.from(customerService.getMine());
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return CustomerResponse.from(customerService.getForCurrentUser(id));
    }

    /** Chỉ ADMIN. */
    @GetMapping
    public PageResponse<CustomerResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id"));
        return PageResponse.from(customerService.list(pageable).map(CustomerResponse::from));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@RequestBody @Valid CreateCustomerRequest req) {
        return CustomerResponse.from(customerService.create(req));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @RequestBody @Valid UpdateCustomerRequest req) {
        return CustomerResponse.from(customerService.update(id, req));
    }
}
