package com.project.category.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.category.catalog.CatalogController;
import com.project.category.dto.CategoryDtos.CategoryRequest;
import com.project.category.dto.CategoryDtos.CategoryResponse;
import com.project.category.entity.Category;
import com.project.category.service.CategoryService;
import com.project.category.ultis.Constants;

/**
 * REST API cho client bên ngoài (đi qua API Gateway: /api/category/**). CRUD + search kế thừa từ
 * {@link CatalogController}; lọc theo cha: {@code GET /api/category?parentCode=FOOD}.
 */
@RestController
@RequestMapping(Constants.ENDPOINT.PREFIX_CATEGORY)
public class CategoryController extends CatalogController<Category, CategoryRequest, CategoryResponse> {

    public CategoryController(CategoryService categoryService) {
        super(categoryService);
    }

    @Override
    protected CategoryResponse toResponse(Category category) {
        return CategoryResponse.from(category);
    }
}
