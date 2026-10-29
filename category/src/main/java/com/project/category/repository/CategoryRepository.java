package com.project.category.repository;

import com.project.category.catalog.CatalogRepository;
import com.project.category.entity.Category;

public interface CategoryRepository extends CatalogRepository<Category> {

    boolean existsByParentCode(String parentCode);
}
