package com.project.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.project.auth.entity.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findAllByOrderByCodeAsc();

    Optional<Role> findByCode(String code);

    List<Role> findByCodeIn(Collection<String> codes);

    boolean existsByCode(String code);

    @Query("select distinct r from Role r join r.permissions p where p in :permissions")
    List<Role> findHavingAnyPermission(Collection<String> permissions);

    @Query("select distinct r from Role r join r.menuIds m where m = :menuId")
    List<Role> findHavingMenu(UUID menuId);
}
