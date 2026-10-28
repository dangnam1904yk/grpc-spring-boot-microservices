package com.project.category.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request cho danh mục chỉ có field chung; dùng kèm {@link CatalogResponse}, không cần viết DTO riêng.
 */
public record SimpleCatalogRequest(
        @Size(max = 50) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        Integer sortOrder,
        Boolean active,
        Long version) implements CatalogRequest {
}
