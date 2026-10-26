package com.project.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.auth.entity.Action;

public interface ActionRepository extends JpaRepository<Action, UUID> {

    List<Action> findAllByOrderByCodeAsc();

    boolean existsByCode(String code);

    long countByCodeIn(Collection<String> codes);
}
