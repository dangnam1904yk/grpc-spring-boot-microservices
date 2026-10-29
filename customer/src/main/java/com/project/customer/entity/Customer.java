package com.project.customer.entity;

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

@Entity
@Table(name = "customer", uniqueConstraints = {
        @UniqueConstraint(name = "uk_customer_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_customer_user_id", columnNames = "user_id") })
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Customer extends BaseEntity {

    // id user bên auth-service (claim "sub"); mỗi user tối đa 1 hồ sơ customer.
    // nullable để không vỡ dữ liệu cũ tạo trước khi có đăng nhập
    @Column(name = "user_id", length = 64)
    String userId;

    @Column(name = "full_name", nullable = false, length = 255)
    String fullName;

    // Lấy từ token (đã xác thực ở auth-service), luôn chữ thường
    @Column(nullable = false, length = 255)
    String email;

    @Column(length = 20)
    String phone;

    @Column(nullable = false)
    boolean active = true;

    // Optimistic locking: 2 request update cùng lúc -> request sau bị từ chối thay vì ghi đè
    @Version
    Long version;
}
