package com.project.auth;

import java.util.List;
import java.util.Map;

import com.project.auth.entity.UserRole;
import com.project.auth.keycloak.KeycloakUser;
import com.project.auth.repository.UserRoleRepository;
import com.project.common.security.test.TestJwt;

/**
 * Tạo user trên {@link FakeKeycloakAdmin} (như tự đăng ký) kèm role gán sẵn trong DB, và token như Keycloak cấp
 * (chỉ có sub, email; role / quyền do auth-service tự đọc từ DB).
 */
public class TestUsers {

    private final FakeKeycloakAdmin keycloak;
    private final UserRoleRepository userRoleRepository;

    public TestUsers(FakeKeycloakAdmin keycloak, UserRoleRepository userRoleRepository) {
        this.keycloak = keycloak;
        this.userRoleRepository = userRoleRepository;
    }

    public String register(String email, String... roles) {
        String id = keycloak.createUser(new KeycloakUser(null, email, null, null, true, null), "secret123");
        for (String role : roles) {
            userRoleRepository.saveAndFlush(new UserRole(id, role));
        }
        return id;
    }

    public String admin() {
        return token(register("admin-" + System.nanoTime() + "@example.com", "ADMIN"));
    }

    public String token(String userId) {
        return TestJwt.token(userId, keycloak.findUser(userId).orElseThrow().email(), List.of(), List.of());
    }

    /** Token có claim email_verified (như tài khoản tạo sẵn trong realm). */
    public String verifiedToken(String userId) {
        return TestJwt.token(userId, keycloak.findUser(userId).orElseThrow().email(), List.of(), List.of(),
                Map.of("email_verified", true));
    }
}
