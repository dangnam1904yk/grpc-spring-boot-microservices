package com.project.category.model;

import com.google.protobuf.StringValue;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CategoryDbhc {
    String id;
    String code;
    String name;
    String description;
    String parentId;
    String parentCode;
    boolean status;
}
