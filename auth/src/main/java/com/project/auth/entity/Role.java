package com.project.auth.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.project.common.jpa.BaseEntity;
import com.project.common.security.AuthUser;

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

/**
 * Nhóm quyền gán cho user. User tham chiếu role bằng {@link #code} (bảng user_role, claim "roles" của JWT),
 * nên code không đổi sau khi tạo.
 * <p>
 * {@value #ADMIN} là super-admin: luôn có mọi quyền và thấy mọi menu, không phụ thuộc {@link #permissions} /
 * {@link #menuIds}.
 */
@Entity
@Table(name = "authz_role", uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Role extends BaseEntity {

    /** Role mặc định của mọi user. */
    public static final String USER = "USER";
    public static final String ADMIN = AuthUser.ROLE_ADMIN;

    // Độ dài 20: trùng cột user_role.role đã có từ trước
    @Column(nullable = false, length = 20, updatable = false)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 1000)
    String description;

    /** Role hệ thống (USER, ADMIN): không xoá được. Tên cột tránh "system" (từ khoá của MySQL 8). */
    @Column(name = "built_in", nullable = false, updatable = false)
    boolean builtIn;

    /** "resource:action", vd. "category:create". */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "authz_role_permission", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 101)
    Set<String> permissions = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "authz_role_menu", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "menu_id", nullable = false)
    Set<UUID> menuIds = new HashSet<>();

    @Version
    Long version;
}
