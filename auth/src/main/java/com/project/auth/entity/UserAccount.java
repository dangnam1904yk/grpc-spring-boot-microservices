package com.project.auth.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.project.common.jpa.BaseEntity;
import com.project.common.jpa.UUIDV7;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "user_account", uniqueConstraints = @UniqueConstraint(name = "uk_user_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserAccount extends BaseEntity {

    /** Cũng là claim "sub" trong JWT (dạng chuỗi). */
    @Id
    @UUIDV7
    UUID id;

    // Luôn lưu chữ thường; unique constraint chặn 2 request đăng ký cùng email đồng thời
    @Column(nullable = false, length = 255)
    String email;

    // BCrypt hash, không bao giờ lưu mật khẩu gốc
    @Column(name = "password_hash", nullable = false, length = 100)
    String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    Set<Role> roles = new HashSet<>();

    @Column(nullable = false)
    boolean enabled = true;

    @Version
    Long version;
}
