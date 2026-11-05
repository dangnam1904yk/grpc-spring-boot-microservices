package com.project.auth.entity;

import com.project.common.jpa.BaseEntity;

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
 * Bản sao tài khoản Keycloak trong DB của auth-service (không có mật khẩu: mật khẩu chỉ nằm ở Keycloak).
 * Ghi khi đăng ký, khi quản trị tạo / sửa user, và bù khi đăng nhập nếu chưa có (user tạo sẵn trong realm, tạo
 * từ console Keycloak...). Keycloak vẫn là nguồn chuẩn.
 * <p>
 * Tên bảng khác bảng user_account cũ (thời auth-service tự giữ mật khẩu) để không đụng dữ liệu cũ.
 */
@Entity
@Table(name = "auth_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_user_user_id", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_auth_user_email", columnNames = "email") })
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAccount extends BaseEntity {

    /** Id user bên Keycloak (claim "sub"), cũng là authz_user_role.user_id. */
    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    String userId;

    @Column(nullable = false, length = 255)
    String email;

    @Column(name = "full_name", length = 255)
    String fullName;

    @Column(name = "phone_number", length = 20)
    String phoneNumber;

    @Column(nullable = false)
    boolean enabled;

    public UserAccount(String userId) {
        this.userId = userId;
    }
}
