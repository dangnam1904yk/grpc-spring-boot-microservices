package com.project.category.catalog;

import com.project.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Field chung của mọi bảng danh mục (category, đơn vị tính...). Entity con chỉ khai báo thêm field riêng.
 * <p>
 * Unique constraint của {@code code} khai báo ở {@code @Table} của từng entity con, tên constraint phải khác nhau
 * giữa các bảng (SQL Server yêu cầu duy nhất trong schema):
 * <pre>
 * &#64;Entity
 * &#64;Table(name = "unit", uniqueConstraints = &#64;UniqueConstraint(name = "uk_unit_code", columnNames = "code"))
 * public class Unit extends CatalogEntity { ... }
 * </pre>
 */
@MappedSuperclass
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class CatalogEntity extends BaseEntity {

    // Unique constraint ở DB là chốt chặn cuối khi 2 request tạo cùng code đồng thời. Code không đổi sau khi tạo
    // vì service khác tham chiếu danh mục bằng code.
    @Column(nullable = false, length = 50, updatable = false)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 1000)
    String description;

    @Column(nullable = false)
    boolean active = true;

    @Column(name = "sort_order")
    Integer sortOrder;

    // Optimistic locking: 2 request update cùng lúc -> request sau bị từ chối thay vì ghi đè
    @Version
    Long version;
}
