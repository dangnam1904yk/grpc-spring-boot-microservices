package com.project.category.entity;

import java.util.UUID;

import com.project.common.jpa.BaseEntity;
import com.project.common.jpa.UUIDV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "category",
        uniqueConstraints = @UniqueConstraint(name = "uk_category_code", columnNames = "code"),
        indexes = @Index(name = "idx_category_parent_code", columnList = "parent_code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Category extends BaseEntity {

    @Id
    @UUIDV7
    UUID id;

    // Unique constraint ở DB là chốt chặn cuối khi 2 request tạo cùng code đồng thời
    @Column(nullable = false, length = 50)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 1000)
    String description;

    @Column(name = "parent_code", length = 50)
    String parentCode;

    @Column(nullable = false)
    boolean active = true;

    // Optimistic locking: 2 request update cùng lúc -> request sau bị từ chối thay vì ghi đè
    @Version
    Long version;
}
