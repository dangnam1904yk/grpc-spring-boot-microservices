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

/** Thao tác (read, create, update, delete...). Quyền = resource + action, vd. "category:create". */
@Entity
@Table(name = "authz_action", uniqueConstraints = @UniqueConstraint(name = "uk_action_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Action extends BaseEntity {

    /** Không đổi sau khi tạo: là một phần của mã quyền đã gán cho role. */
    @Column(nullable = false, length = 50, updatable = false)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    @Column(length = 1000)
    String description;

    @Version
    Long version;
}
