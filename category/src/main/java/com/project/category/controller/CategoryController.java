package com.project.category.controller;

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

import com.project.category.dto.CategoryDtos.CategoryResponse;
import com.project.category.dto.CategoryDtos.CreateCategoryRequest;
import com.project.category.dto.CategoryDtos.PageResponse;
import com.project.category.dto.CategoryDtos.UpdateCategoryRequest;
import com.project.category.service.CategoryService;
import com.project.category.ultis.Constants;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST API cho client bên ngoài (đi qua API Gateway: /api/category/**).
 */
@RestController
@RequestMapping(Constants.ENDPOINT.PREFIX_CATEGORY)
@RequiredArgsConstructor
public class CategoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryService categoryService;

    @GetMapping("/{code}")
    public CategoryResponse getByCode(@PathVariable String code) {
        return CategoryResponse.from(categoryService.getByCode(code));
    }

    @GetMapping
    public PageResponse<CategoryResponse> list(
            @RequestParam(required = false) String parentCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id"));
        return PageResponse.from(categoryService.list(parentCode, pageable).map(CategoryResponse::from));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@RequestBody @Valid CreateCategoryRequest req) {
        return CategoryResponse.from(categoryService.create(req));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @RequestBody @Valid UpdateCategoryRequest req) {
        return CategoryResponse.from(categoryService.update(id, req));
    }
}
