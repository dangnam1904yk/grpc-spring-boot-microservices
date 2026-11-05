package com.project.auth.keycloak;

/**
 * Token Keycloak cấp cho user (token endpoint của realm).
 *
 * @param expiresIn        số giây access token còn hiệu lực
 * @param refreshExpiresIn số giây refresh token còn hiệu lực
 */
public record KeycloakTokens(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn) {
}
