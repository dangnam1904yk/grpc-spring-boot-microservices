package com.project.category.entity;

import com.project.category.catalog.CatalogEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Field chung (code, name, description, active, sortOrder, version) nằm ở {@link CatalogEntity}.
 */
@Entity
@Table(name = "category",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_code", columnNames = "code"),
        indexes = @Index(name = "idx_category_parent_code", columnList = "parent_code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category extends CatalogEntity {

    @Column(name = "parent_code", length = 50)
    String parentCode;
}
