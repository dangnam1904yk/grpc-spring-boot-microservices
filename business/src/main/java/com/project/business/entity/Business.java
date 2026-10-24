package com.project.business.entity;

import java.util.UUID;

import com.project.common.jpa.BaseEntity;
import com.project.common.jpa.UUIDV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "business", uniqueConstraints = @UniqueConstraint(name = "uk_business_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Business extends BaseEntity {

    @Id
    @UUIDV7
    UUID id;

    @Column(nullable = false, length = 50)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    // Tham chiếu sang category-service bằng code (không có FK vì khác database)
    @Column(name = "category_code", nullable = false, length = 50)
    String categoryCode;

    // Chủ sở hữu: id bên customer-service
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Column(nullable = false)
    boolean active = true;

    @Version
    Long version;
}
