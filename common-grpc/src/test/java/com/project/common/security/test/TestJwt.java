package com.project.common.security.test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
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

import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;

/**
 * Hỗ trợ test cho các service (qua test-jar của common-grpc): tạo JWT hợp lệ mà không cần auth-service.
 * <pre>
 * &#64;SpringBootTest
 * &#64;Import(TestJwt.Config.class)   // thay JwtDecoder (JWKS) bằng decoder dùng khoá test
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

    public static String token(String userId, String email, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("test")
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .id(UUID.randomUUID().toString())
                .claim("email", email)
                .claim("roles", List.of(roles))
                .build();
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
