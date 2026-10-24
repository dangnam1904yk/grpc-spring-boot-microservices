package com.project.auth.entity;

import java.time.Instant;
import java.util.UUID;

import com.project.common.jpa.BaseEntity;
import com.project.common.jpa.UUIDV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Refresh token dạng chuỗi ngẫu nhiên (không phải JWT), chỉ lưu SHA-256 hash: lộ DB cũng không dùng được token.
 * Mỗi lần refresh, token cũ bị thu hồi và cấp token mới (rotation).
 */
@Entity
@Table(name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_refresh_token_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshToken extends BaseEntity {

    @Id
    @UUIDV7
    UUID id;

    @Column(name = "token_hash", nullable = false, length = 64)
    String tokenHash;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "revoked_at")
    Instant revokedAt;
}
