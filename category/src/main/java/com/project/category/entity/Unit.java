package com.project.category.entity;

import com.project.category.catalog.CatalogEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Đơn vị tính (kg, cái, hộp...). Danh mục mẫu cho {@link CatalogEntity}: chỉ thêm field riêng {@code symbol}.
 */
@Entity
@Table(name = "unit", uniqueConstraints = @UniqueConstraint(name = "uk_unit_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Unit extends CatalogEntity {

    /** Ký hiệu hiển thị, ví dụ "kg". */
    @Column(length = 20)
    String symbol;
}
