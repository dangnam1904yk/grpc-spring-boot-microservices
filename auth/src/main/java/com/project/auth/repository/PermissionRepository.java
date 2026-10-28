package com.project.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.auth.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByOrderByCodeAsc();

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);
}
