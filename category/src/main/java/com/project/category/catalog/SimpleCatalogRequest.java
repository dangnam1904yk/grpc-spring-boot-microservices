package com.project.category.catalog;

/**
 * Request cho danh mục chỉ có field chung; dùng kèm {@link CatalogResponse}, không cần viết DTO riêng.
 */
public record SimpleCatalogRequest(
        String code,
        String name,
        String description,
        Integer sortOrder,
        Boolean active,
        Long version) implements CatalogRequest {
}
