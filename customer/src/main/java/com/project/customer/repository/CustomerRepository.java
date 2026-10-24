package com.project.customer.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.customer.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByUserId(String userId);

    boolean existsByEmail(String email);

    boolean existsByUserId(String userId);
}
