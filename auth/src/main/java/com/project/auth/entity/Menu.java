package com.project.auth.entity;

import java.util.UUID;

import com.project.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Menu của frontend, dạng cây (parentId). User thấy menu theo role ({@link Role#getMenuIds()}). */
@Entity
@Table(name = "authz_menu",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_code", columnNames = "code"),
        indexes = @Index(name = "idx_menu_parent_id", columnList = "parent_id"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Menu extends BaseEntity {

    @Column(nullable = false, length = 100, updatable = false)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    /** Đường dẫn route ở frontend; menu nhóm (chỉ chứa menu con) thì để trống. */
    @Column(length = 500)
    String path;

    @Column(length = 100)
    String icon;

    @Column(name = "parent_id")
    UUID parentId;

    @Column(name = "sort_order", nullable = false)
    int sortOrder;

    @Column(nullable = false)
    boolean active = true;

    @Version
    Long version;
}
