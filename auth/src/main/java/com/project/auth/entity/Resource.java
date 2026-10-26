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

/**
 * Đối tượng được phân quyền (category, customer...) và các action áp dụng được cho nó.
 * Mỗi cặp (resource, action) là một quyền "resource:action" có thể gán cho role.
 */
@Entity
@Table(name = "authz_resource",
        uniqueConstraints = @UniqueConstraint(name = "uk_resource_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Resource extends BaseEntity {

    /** Không đổi sau khi tạo: là một phần của mã quyền đã gán cho role. */
    @Column(nullable = false, length = 50, updatable = false)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 1000)
    String description;

    /** Code của {@link Action}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "authz_resource_action", joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "action", nullable = false, length = 50)
    Set<String> actions = new HashSet<>();

    @Version
    Long version;
}
