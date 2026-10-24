package com.project.auth.service;

import java.util.EnumSet;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.project.auth.config.AuthProperties;
import com.project.auth.entity.Role;
import com.project.auth.repository.UserAccountRepository;
import com.project.common.exception.AppException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Tạo tài khoản ADMIN đầu tiên từ app.admin.email / app.admin.password (nếu chưa có). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AuthProperties.Admin admin;
    private final UserAccountRepository userAccountRepository;
    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(admin.email()) || !StringUtils.hasText(admin.password())) {
            return;
        }
        if (userAccountRepository.existsByEmail(admin.email().trim().toLowerCase(Locale.ROOT))) {
            return;
        }
        try {
            authService.createUser(admin.email(), admin.password(), EnumSet.of(Role.USER, Role.ADMIN));
            log.info("Đã tạo tài khoản ADMIN {}", admin.email());
        } catch (AppException e) {
            // Nhiều instance cùng khởi động: instance khác đã tạo trước
            log.info("Tài khoản ADMIN {} đã tồn tại", admin.email());
        }
    }
}
