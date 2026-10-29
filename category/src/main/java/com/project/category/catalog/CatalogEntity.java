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

    public static final int CODE_LENGTH = 50;
    public static final int NAME_LENGTH = 255;
    public static final int DESCRIPTION_LENGTH = 1000;

    // Unique constraint ở DB là chốt chặn cuối khi 2 request tạo cùng code đồng thời. Code không đổi sau khi tạo
    // vì service khác tham chiếu danh mục bằng code.
    @Column(nullable = false, length = CODE_LENGTH, updatable = false)
    String code;

    @Column(nullable = false, length = NAME_LENGTH)
    String name;

    @Column(length = DESCRIPTION_LENGTH)
    String description;

    @Column(nullable = false)
    boolean active = true;

    @Column(name = "sort_order")
    Integer sortOrder;

    // Optimistic locking: 2 request update cùng lúc -> request sau bị từ chối thay vì ghi đè
    @Version
    Long version;
}
