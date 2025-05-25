package com.project.apigateway.mapper;

import com.project.apigateway.interfaces.ProtobufMapper;
import com.project.apigateway.model.category.response.CategoryDbhcDto;

public class CategoryGrpcMapper implements ProtobufMapper<common.CategoryDbhc.CategoryGrpc, CategoryDbhcDto> {

    @Override
    public CategoryDbhcDto toDto(common.CategoryDbhc.CategoryGrpc proto) {
        return CategoryDbhcDto.builder()
                .id(proto.getId())
                .name(proto.getName())
                .code(proto.getCode())
                .description(proto.getDescription())
                .parentCode(proto.getParentCode())
                .parentId(proto.getParentId())
                .status(proto.getStatus())
                .build();
    }

    @Override
    public common.CategoryDbhc.CategoryGrpc toProto(CategoryDbhcDto dto) {
        return common.CategoryDbhc.CategoryGrpc.newBuilder()
                .setId(dto.getId())
                .setName(dto.getName())
                .setCode(dto.getCode())
                .setDescription(dto.getDescription())
                .setParentCode(dto.getParentCode())
                .setParentId(dto.getParentId())
                .setStatus(dto.getStatus())
                .build();
    }
}
