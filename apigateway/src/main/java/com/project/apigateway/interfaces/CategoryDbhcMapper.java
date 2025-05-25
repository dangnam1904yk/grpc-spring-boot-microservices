package com.project.apigateway.interfaces;

import org.mapstruct.*;

import com.project.apigateway.model.category.request.CategoryDbhcRequest;
import com.project.apigateway.model.category.response.CategoryDbhcDto;
import common.CategoryDbhc.CategoryGrpc;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CategoryDbhcMapper {

    void updateProtoFromDto(CategoryDbhcDto dto, @MappingTarget CategoryGrpc.Builder builder);

    void updateProtoFromDto(CategoryDbhcRequest dto, @MappingTarget CategoryGrpc.Builder builder);

    CategoryDbhcDto toDto(CategoryGrpc proto);
}
