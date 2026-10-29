package com.project.category.dto;

import java.util.UUID;

import com.project.category.catalog.CatalogRequest;
import com.project.category.entity.Category;

/**
 * DTO dùng chung cho cả REST controller và gRPC adapter.
 */
public final class CategoryDtos {

    private CategoryDtos() {
    }

    /** Dùng cho cả tạo và sửa, xem quy ước ở {@link CatalogRequest}. */
    public record CategoryRequest(
            String code,
            String name,
            String description,
            String parentCode,
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
