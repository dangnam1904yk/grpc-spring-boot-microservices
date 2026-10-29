package com.project.category.dto;

import java.util.UUID;

import com.project.category.catalog.CatalogRequest;
import com.project.category.entity.Category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO dùng chung cho cả REST controller và gRPC adapter.
 */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    /** Dùng cho cả tạo và sửa, xem quy ước ở {@link CatalogRequest}. */
    public record CategoryRequest(
            @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            @Size(max = 50) String parentCode,
            Integer sortOrder,
            Boolean active,
            Long version) implements CatalogRequest {
    }

    public record CategoryResponse(
            UUID id,
            String code,
            String name,
            String description,
            String parentCode,
            boolean active,
            Integer sortOrder,
            Long version) {

        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.getParentCode(), c.isActive(), c.getSortOrder(), c.getVersion());
        }
    }
}
