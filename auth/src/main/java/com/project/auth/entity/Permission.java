package com.project.auth.entity;

import com.project.common.jpa.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Thao tác (VIEW, ADD, EDIT, DELETE, EXPORT...). Quyền cấp cho role = menu + thao tác, vd. "category:ADD".
 */
@Entity
@Table(name = "authz_permission",
        uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission extends BaseEntity {

    /** Không đổi sau khi tạo: là một phần của mã quyền đã cấp cho role. */
    @Column(nullable = false, length = 50, updatable = false)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 1000)
    String description;

    /** Thao tác hệ thống (code đang kiểm tra): không xoá / không tắt được. */
    @Column(name = "built_in", nullable = false, updatable = false)
    boolean builtIn;

    /** Thao tác bị tắt thì quyền tương ứng không được cấp vào token. */
    @Column(nullable = false)
    boolean disabled;

    @Version
    Long version;
}
