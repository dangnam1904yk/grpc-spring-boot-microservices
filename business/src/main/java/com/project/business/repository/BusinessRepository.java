package com.project.business.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.business.entity.Business;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    boolean existsByCode(String code);
}
