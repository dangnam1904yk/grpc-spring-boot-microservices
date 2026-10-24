package com.project.category.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.project.category.entity.Category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO dùng chung cho cả REST controller và gRPC adapter.
 */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            @Size(max = 50) String parentCode) {
    }

    public record UpdateCategoryRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            @Size(max = 50) String parentCode,
            boolean active,
            @NotNull Long version) {
    }

    public record CategoryResponse(
            Long id,
            String code,
            String name,
            String description,
            String parentCode,
            boolean active,
            Long version) {

        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.getParentCode(), c.isActive(), c.getVersion());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
