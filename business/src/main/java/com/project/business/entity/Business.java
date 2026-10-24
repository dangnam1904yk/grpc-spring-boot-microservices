package com.project.business.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, length = 50)
    String code;

    @Column(nullable = false, length = 255)
    String name;

    // Tham chiếu sang category-service bằng code (không có FK vì khác database)
    @Column(name = "category_code", nullable = false, length = 50)
    String categoryCode;

    // Chủ sở hữu: id bên customer-service
    @Column(name = "owner_id", nullable = false)
    Long ownerId;

    @Column(nullable = false)
    boolean active = true;

    @Version
    Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
