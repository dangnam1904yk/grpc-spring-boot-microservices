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

    /** Lọc thô bằng LIKE ("_" trong code cũng là ký tự đại diện): nơi gọi phải so khớp chính xác lại. */
    @Query("select distinct r from Role r join r.permissions p where p like :pattern")
    List<Role> findHavingPermissionLike(String pattern);

    @Query("select distinct r from Role r join r.menuIds m where m in :menuIds")
    List<Role> findHavingAnyMenu(Collection<UUID> menuIds);
}
