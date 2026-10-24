package com.project.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

public final class AuthProperties {

    private AuthProperties() {
    }

    /**
     * @param privateKeyLocation PEM PKCS#8 ("-----BEGIN PRIVATE KEY-----"), vd. file:/run/secrets/jwt.key
     * @param publicKeyLocation  PEM X.509 ("-----BEGIN PUBLIC KEY-----")
     */
    @ConfigurationProperties(prefix = "app.jwt")
    public record Jwt(String issuer, Duration accessTokenTtl, Duration refreshTokenTtl, String privateKeyLocation,
            String publicKeyLocation) {
    }

    @ConfigurationProperties(prefix = "app.admin")
    public record Admin(String email, String password) {
    }
}
