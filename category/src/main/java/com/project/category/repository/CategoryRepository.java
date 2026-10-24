package com.project.category.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.category.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByCode(String code);

    List<Category> findByCodeIn(Collection<String> codes);

    boolean existsByCode(String code);

    Page<Category> findByParentCode(String parentCode, Pageable pageable);
}
