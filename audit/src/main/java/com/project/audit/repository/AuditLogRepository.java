package com.project.audit.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByRequestIdOrderByCreatedAtAscIdAsc(String requestId, Pageable pageable);

    @Modifying
    @Query("delete from AuditLog a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
