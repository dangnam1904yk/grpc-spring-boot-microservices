package com.project.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.entity.UserAccount;
import com.project.auth.keycloak.KeycloakUser;
import com.project.auth.repository.UserAccountRepository;

import lombok.RequiredArgsConstructor;

/** Ghi bảng auth_user cho khớp tài khoản trên Keycloak (Keycloak là nguồn chuẩn). */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserAccountRepository repository;

    @Transactional(readOnly = true)
    public boolean exists(String userId) {
        return repository.existsByUserId(userId);
    }

    /**
     * Thêm hoặc cập nhật bản sao của {@code user}. Dòng cũ khác user_id mà trùng email (user đã bị xoá trên
     * Keycloak rồi tạo lại) bị xoá trước, vì email trên Keycloak là duy nhất.
     */
    @Transactional
    public UserAccount sync(KeycloakUser user) {
        repository.findByEmail(user.email())
                .filter(other -> !other.getUserId().equals(user.id()))
                .ifPresent(other -> {
                    repository.delete(other);
                    repository.flush();
                });
        UserAccount account = repository.findByUserId(user.id()).orElseGet(() -> new UserAccount(user.id()));
        account.setEmail(user.email());
        account.setFullName(user.fullName());
        account.setPhoneNumber(user.phoneNumber());
        account.setEnabled(user.enabled());
        return repository.save(account);
    }
}
