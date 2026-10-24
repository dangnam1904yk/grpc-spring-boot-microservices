package com.project.business.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.business.entity.Business;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    boolean existsByCode(String code);
}
