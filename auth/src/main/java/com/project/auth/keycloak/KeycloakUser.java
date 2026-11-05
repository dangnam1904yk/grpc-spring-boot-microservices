package com.project.auth.keycloak;

import java.time.Instant;

/**
 * User bên Keycloak (realm ecommerce). Họ tên và số điện thoại là attribute {@code fullName} / {@code phoneNumber}
 * khai báo trong user profile của realm (keycloak/realm-ecommerce.json).
 *
 * @param id id của Keycloak (UUID), cũng là claim "sub" của token
 */
public record KeycloakUser(String id, String email, String fullName, String phoneNumber, boolean enabled,
        Instant createdAt) {
}
