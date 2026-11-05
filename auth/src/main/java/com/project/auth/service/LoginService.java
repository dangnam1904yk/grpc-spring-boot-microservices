package com.project.auth.service;

import java.util.Locale;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import com.project.auth.dto.AuthDtos.RegisterRequest;
import com.project.auth.keycloak.KeycloakAdmin;
import com.project.auth.keycloak.KeycloakTokens;
import com.project.auth.keycloak.KeycloakUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đăng ký / đăng nhập / refresh / đăng xuất bằng form của frontend. auth-service gọi Keycloak thay cho trình duyệt
 * (password grant của client auth-service), nên frontend không cần biết Keycloak; token vẫn do Keycloak ký, mọi
 * service kiểm tra như cũ.
 * <p>
 * Không mở transaction quanh lời gọi Keycloak: mỗi lần ghi bảng auth_user tự commit ({@link UserAccountService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginService {

    private final KeycloakAdmin keycloak;
    private final UserAccountService accounts;
    private final JwtDecoder jwtDecoder;

    /**
     * Tạo tài khoản trên Keycloak, lưu bản sao vào auth_user rồi đăng nhập luôn. Lưu DB lỗi thì xoá tài khoản vừa
     * tạo trên Keycloak để đăng ký lại được.
     */
    public KeycloakTokens register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        KeycloakUser user = new KeycloakUser(null, email, AuthzChecks.trimToNull(req.fullName()),
                AuthzChecks.trimToNull(req.phoneNumber()), true, null);
        String id = keycloak.createUser(user, req.password());
        try {
            accounts.sync(new KeycloakUser(id, user.email(), user.fullName(), user.phoneNumber(), true, null));
        } catch (RuntimeException e) {
            log.error("Lưu user {} vào auth_user lỗi, xoá tài khoản vừa tạo trên Keycloak", id, e);
            try {
                keycloak.deleteUser(id);
            } catch (RuntimeException deleteError) {
                log.error("Không xoá được tài khoản {} trên Keycloak, cần xoá tay", id, deleteError);
            }
            throw e;
        }
        log.info("Đăng ký tài khoản mới userId={}", id);
        return keycloak.login(email, req.password());
    }

    /** Đăng nhập lần đầu của user chưa có trong auth_user (tạo sẵn trong realm, tạo từ console...) thì bù vào. */
    public KeycloakTokens login(String email, String password) {
        KeycloakTokens tokens = keycloak.login(normalizeEmail(email), password);
        backfill(tokens.accessToken());
        return tokens;
    }

    public KeycloakTokens refresh(String refreshToken) {
        return keycloak.refresh(refreshToken);
    }

    public void logout(String refreshToken) {
        keycloak.endSession(refreshToken);
    }

    /** Lỗi ở đây không làm hỏng đăng nhập: auth_user chỉ là bản sao, lần đăng nhập sau sẽ thử lại. */
    private void backfill(String accessToken) {
        try {
            String userId = jwtDecoder.decode(accessToken).getSubject();
            if (!accounts.exists(userId)) {
                keycloak.findUser(userId).ifPresent(accounts::sync);
            }
        } catch (RuntimeException e) {
            log.warn("Không đồng bộ được user vào auth_user khi đăng nhập: {}", e.getMessage());
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
