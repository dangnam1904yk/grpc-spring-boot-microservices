package com.project.category.grpc;

import org.springframework.data.domain.Page;

import com.project.category.entity.Category;
import com.project.grpc.common.v1.PageInfo;

final class CategoryProtoMapper {

    private CategoryProtoMapper() {
    }

    static com.project.grpc.category.v1.Category toProto(Category c) {
        com.project.grpc.category.v1.Category.Builder builder = com.project.grpc.category.v1.Category.newBuilder()
                .setId(c.getId())
                .setCode(c.getCode())
                .setName(c.getName())
                .setActive(c.isActive())
                .setVersion(c.getVersion());
        if (c.getDescription() != null) {
            builder.setDescription(c.getDescription());
        }
        if (c.getParentCode() != null) {
            builder.setParentCode(c.getParentCode());
        }
        return builder.build();
    }

    static PageInfo toPageInfo(Page<?> page) {
        return PageInfo.newBuilder()
                .setPage(page.getNumber())
                .setSize(page.getSize())
                .setTotalElements(page.getTotalElements())
                .setTotalPages(page.getTotalPages())
                .build();
    }
}
