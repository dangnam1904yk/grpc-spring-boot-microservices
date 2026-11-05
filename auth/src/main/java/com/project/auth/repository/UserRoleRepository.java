package com.project.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.project.auth.entity.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(String userId);

    List<UserRole> findByUserIdIn(Collection<String> userIds);

    List<UserRole> findByRole(String role);

    long countByRole(String role);

    boolean existsByRole(String role);

    @Modifying
    @Query("delete from UserRole r where r.userId = :userId and r.role in :roles")
    int deleteRoles(String userId, Collection<String> roles);

    @Modifying
    @Query("delete from UserRole r where r.role = :role")
    int deleteByRole(String role);
}
