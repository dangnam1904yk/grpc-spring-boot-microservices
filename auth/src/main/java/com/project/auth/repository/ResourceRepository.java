package com.project.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.project.auth.entity.Resource;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    List<Resource> findAllByOrderByCodeAsc();

    Optional<Resource> findByCode(String code);

    boolean existsByCode(String code);

    @Query("select r.code from Resource r join r.actions a where a = :action order by r.code")
    List<String> findCodesUsingAction(String action);
}
