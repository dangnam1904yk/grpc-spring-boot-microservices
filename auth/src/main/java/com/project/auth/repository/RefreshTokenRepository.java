package com.project.auth.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.auth.entity.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Thu hồi có điều kiện: chỉ 1 request đồng thời "thắng" (trả về 1), các request còn lại nhận 0.
     * Nhờ vậy 2 request refresh cùng một token không thể cùng nhận token mới.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
