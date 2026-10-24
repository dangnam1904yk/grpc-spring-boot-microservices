package com.project.auth.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import lombok.extern.slf4j.Slf4j;

/**
 * Khoá RSA dùng để ký JWT (RS256). Private key chỉ nằm ở auth-service; các service khác lấy public key
 * qua /api/auth/jwks nên không phải chia sẻ secret.
 */
@Slf4j
@Configuration
public class JwtKeyConfig {

    @Bean
    RSAKey jwtSigningKey(AuthProperties.Jwt properties, ResourceLoader resourceLoader)
            throws IOException, JOSEException {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (StringUtils.hasText(properties.privateKeyLocation()) && StringUtils.hasText(properties.publicKeyLocation())) {
            try (InputStream priv = resourceLoader.getResource(properties.privateKeyLocation()).getInputStream();
                    InputStream pub = resourceLoader.getResource(properties.publicKeyLocation()).getInputStream()) {
                privateKey = RsaKeyConverters.pkcs8().convert(priv);
                publicKey = RsaKeyConverters.x509().convert(pub);
            }
            log.info("Dùng khoá ký JWT từ {}", properties.privateKeyLocation());
        } else {
            KeyPair pair = generate();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
            log.warn("Chưa cấu hình app.jwt.private-key-location: đang dùng khoá TẠM sinh lúc khởi động. "
                    + "Restart sẽ làm mọi token cũ hết hiệu lực. Không dùng cho production.");
        }
        // kid = thumbprint của public key: ổn định khi dùng cùng một khoá, đổi khoá thì kid đổi theo
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyIDFromThumbprint().build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    /** auth-service tự kiểm tra token của mình (cho /me, API admin) bằng public key, không cần gọi JWKS. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, AuthProperties.Jwt properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
