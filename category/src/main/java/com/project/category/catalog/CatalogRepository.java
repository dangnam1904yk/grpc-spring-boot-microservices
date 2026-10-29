package com.project.category.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Repository chung cho danh mục. Repository con chỉ cần {@code interface UnitRepository extends CatalogRepository<Unit> {}}.
 */
@NoRepositoryBean
public interface CatalogRepository<E extends CatalogEntity>
        extends JpaRepository<E, UUID>, JpaSpecificationExecutor<E> {

    Optional<E> findByCode(String code);

    List<E> findByCodeIn(Collection<String> codes);

    boolean existsByCode(String code);
}
