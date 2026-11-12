package com.project.common.security.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.project.common.security.AuthorityResolver;
import com.project.common.security.ClaimAuthorityResolver;

import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

/**
 * Hỗ trợ test cho các service (qua test-jar của common-grpc): tạo JWT hợp lệ mà không cần auth-service.
 * <pre>
 * &#64;SpringBootTest
 * &#64;Import(TestJwt.Config.class)   // decoder dùng khoá test + role / quyền đọc từ claim
 * class XxxTest {
 *     String token = TestJwt.user("u-1");
 *     mvc.perform(get(...).header("Authorization", TestJwt.bearer(token)));
 *     TestJwt.withToken(stub, token).someRpc(...);
 * }
 * </pre>
 */
public final class TestJwt {

    private static final RSAKey KEY = generateKey();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization",
            Metadata.ASCII_STRING_MARSHALLER);

    private TestJwt() {
    }

    public static String user(String userId) {
        return token(userId, userId + "@example.com", "USER");
    }

    public static String admin(String userId) {
        return token(userId, userId + "@example.com", "USER", "ADMIN");
    }

    /** Role USER kèm các quyền {@code "resource:action"} (không phải ADMIN). */
    public static String withPermissions(String userId, String... permissions) {
        return token(userId, userId + "@example.com", List.of("USER"), List.of(permissions));
    }

    public static String token(String userId, String email, String... roles) {
        return token(userId, email, List.of(roles), List.of());
    }

    public static String token(String userId, String email, List<String> roles, List<String> permissions) {
        return token(userId, email, roles, permissions, Map.of());
    }

    /** extraClaims: claim thêm, vd. {@code "email_verified": true}. */
    public static String token(String userId, String email, List<String> roles, List<String> permissions,
            Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer("test")
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("roles", roles)
                .claim("permissions", permissions);
        extraClaims.forEach(builder::claim);
        JwtClaimsSet claims = builder.build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static <S extends AbstractStub<S>> S withToken(S stub, String token) {
        Metadata headers = new Metadata();
        headers.put(AUTHORIZATION, bearer(token));
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    public static JwtDecoder decoder() {
        try {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        JwtDecoder jwtDecoder() {
            return decoder();
        }

        /** Role / quyền lấy thẳng từ claim "roles" / "permissions" của token test (không gọi auth-service). */
        @Bean
        AuthorityResolver authorityResolver() {
            return new ClaimAuthorityResolver();
        }
    }

    private static RSAKey generateKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID("test-key")
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
