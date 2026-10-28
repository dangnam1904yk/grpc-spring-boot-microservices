package com.project.category.catalog;

import java.util.UUID;

/**
 * Response mặc định cho danh mục chỉ có field chung.
 */
public record CatalogResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        Integer sortOrder,
        Long version) {

    public static CatalogResponse from(CatalogEntity e) {
        return new CatalogResponse(e.getId(), e.getCode(), e.getName(), e.getDescription(), e.isActive(),
                e.getSortOrder(), e.getVersion());
    }
}
