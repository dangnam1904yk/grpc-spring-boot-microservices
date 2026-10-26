package com.project.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.auth.entity.Menu;

public interface MenuRepository extends JpaRepository<Menu, UUID> {

    List<Menu> findAllByOrderBySortOrderAscCodeAsc();

    Optional<Menu> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByParentId(UUID parentId);
}
