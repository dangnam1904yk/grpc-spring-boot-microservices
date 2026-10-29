package com.project.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.project.auth.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("select count(u) from UserAccount u join u.roles r where r = :role")
    long countByRole(String role);

    @Query("select u from UserAccount u join u.roles r where r = :role")
    List<UserAccount> findByRole(String role);

    /** keyword đã ở dạng "%...%" chữ thường. */
    @Query("select u from UserAccount u where lower(u.email) like :keyword or lower(u.fullName) like :keyword"
            + " or u.phoneNumber like :keyword")
    Page<UserAccount> search(String keyword, Pageable pageable);
}
