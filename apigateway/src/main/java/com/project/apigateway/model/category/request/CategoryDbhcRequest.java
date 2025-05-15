package com.project.apigateway.model.category.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryDbhcRequest {
    String id;
    String code;
    String name;
    String description;
    String parentId;
    String parentCode;
    boolean status;
}
