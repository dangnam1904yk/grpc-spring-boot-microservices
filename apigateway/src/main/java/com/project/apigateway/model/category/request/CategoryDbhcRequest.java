package com.project.apigateway.model.category.request;

import jakarta.validation.constraints.NotNull;
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
    @NotNull
    String name;
    @NotNull
    String description;
    @NotNull
    String parentId;
    @NotNull
    String parentCode;
    boolean status;
}
