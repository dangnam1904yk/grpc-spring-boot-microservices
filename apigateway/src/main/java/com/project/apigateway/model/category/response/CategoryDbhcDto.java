package com.project.apigateway.model.category.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class CategoryDbhcDto {
    String id;
    String code;
    String name;
    String description;
    String parentId;
    String parentCode;
    Boolean status;
}
