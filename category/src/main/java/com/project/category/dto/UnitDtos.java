package com.project.category.dto;

import java.util.UUID;

import com.project.category.catalog.CatalogRequest;
import com.project.category.entity.Unit;

public final class UnitDtos {

    private UnitDtos() {
    }

    /** Dùng cho cả tạo và sửa, xem quy ước ở {@link CatalogRequest}. */
    public record UnitRequest(
            String code,
            String name,
            String description,
            String symbol,
            Integer sortOrder,
            Boolean active,
            Long version) implements CatalogRequest {
    }

    public record UnitResponse(
            UUID id,
            String code,
            String name,
            String description,
            String symbol,
            boolean active,
            Integer sortOrder,
            Long version) {

        public static UnitResponse from(Unit u) {
            return new UnitResponse(u.getId(), u.getCode(), u.getName(), u.getDescription(), u.getSymbol(),
                    u.isActive(), u.getSortOrder(), u.getVersion());
        }
    }
}
