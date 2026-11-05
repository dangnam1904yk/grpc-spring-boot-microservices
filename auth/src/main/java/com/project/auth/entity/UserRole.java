package com.project.auth.entity;

import com.project.common.jpa.BaseEntity;

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
 * User có role nào. User nằm ở Keycloak ({@link #userId} = claim "sub"), role là {@link Role#getCode()}.
 * {@value Role#USER} không được lưu: mọi user luôn có.
 * <p>
 * Tên bảng khác bảng user_role cũ (thời auth-service tự quản lý tài khoản, user_id kiểu UUID của bảng
 * user_account) để không đụng dữ liệu cũ.
 */
@Entity
@Table(name = "authz_user_role",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_role", columnNames = { "user_id", "role" }),
        indexes = @Index(name = "ix_user_role_role", columnList = "role"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserRole extends BaseEntity {

    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    String userId;

    @Column(nullable = false, length = 20, updatable = false)
    String role;

    public UserRole(String userId, String role) {
        this.userId = userId;
        this.role = role;
    }
}
