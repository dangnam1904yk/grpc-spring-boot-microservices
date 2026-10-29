package com.project.auth.entity;

import java.util.HashSet;
import java.util.Set;

import com.project.common.jpa.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/** Id (trong {@link BaseEntity}) cũng là claim "sub" trong JWT (dạng chuỗi). */
@Entity
@Table(name = "user_account", uniqueConstraints = @UniqueConstraint(name = "uk_user_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAccount extends BaseEntity {

    // Luôn lưu chữ thường; unique constraint chặn 2 request đăng ký cùng email đồng thời
    @Column(nullable = false, length = 255)
    String email;

    // BCrypt hash, không bao giờ lưu mật khẩu gốc
    @Column(name = "password_hash", nullable = false, length = 100)
    String passwordHash;

    @Column(name = "full_name", length = 255)
    String fullName;

    @Column(name = "phone_number", length = 20)
    String phoneNumber;

    /** Code của {@link Role}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 20)
    Set<String> roles = new HashSet<>();

    @Column(nullable = false)
    boolean enabled = true;

    @Version
    Long version;
}
