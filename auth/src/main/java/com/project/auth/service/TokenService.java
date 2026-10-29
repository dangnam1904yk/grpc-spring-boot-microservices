package com.project.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nimbusds.jose.jwk.RSAKey;
import com.project.auth.config.AuthProperties;
import com.project.auth.dto.AuthDtos.TokenResponse;
import com.project.auth.entity.RefreshToken;
import com.project.auth.entity.UserAccount;
import com.project.auth.repository.RefreshTokenRepository;
import com.project.auth.repository.UserAccountRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.AuthUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cấp access token (JWT ngắn hạn) + refresh token (chuỗi ngẫu nhiên dài hạn, xoay vòng mỗi lần dùng).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtEncoder jwtEncoder;
    private final RSAKey jwtSigningKey;
    private final AuthProperties.Jwt jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthorityService authorityService;

    @Transactional
    public TokenResponse issue(UserAccount user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim(AuthUser.EMAIL_CLAIM, user.getEmail())
                .claim(AuthUser.ROLES_CLAIM, user.getRoles().stream().sorted().toList())
                .claim(AuthUser.PERMISSIONS_CLAIM, List.copyOf(authorityService.permissionsOf(user.getRoles())))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtSigningKey.getKeyID()).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        String rawRefreshToken = newRandomToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(sha256(rawRefreshToken));
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(now.plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(accessToken, rawRefreshToken, "Bearer", jwtProperties.accessTokenTtl().toSeconds());
    }

    /**
     * Đổi refresh token lấy cặp token mới; token cũ bị thu hồi.
     * Dùng lại token đã thu hồi = dấu hiệu token bị đánh cắp -> thu hồi toàn bộ phiên của user.
     * noRollbackFor: lệnh thu hồi phải được commit dù sau đó ném lỗi.
     */
    @Transactional(noRollbackFor = AppException.class)
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Refresh token không hợp lệ"));
        Instant now = Instant.now();

        if (token.getRevokedAt() != null) {
            int revoked = refreshTokenRepository.revokeAllForUser(token.getUserId(), now);
            log.warn("Refresh token đã thu hồi bị dùng lại (user {}), thu hồi {} phiên còn lại",
                    token.getUserId(), revoked);
            throw new AppException(ErrorCode.UNAUTHENTICATED,
                    "Refresh token đã được sử dụng, vui lòng đăng nhập lại");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Refresh token đã hết hạn, vui lòng đăng nhập lại");
        }
        // 2 request cùng refresh 1 token: chỉ request cập nhật được bản ghi mới được cấp token mới
        if (refreshTokenRepository.revokeIfActive(token.getId(), now) == 0) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Refresh token đã được sử dụng");
        }
        UserAccount user = userAccountRepository.findById(token.getUserId())
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "Tài khoản không còn hoạt động"));
        return issue(user);
    }

    /** Idempotent: token sai / đã thu hồi cũng trả về bình thường, không lộ thông tin. */
    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeIfActive(token.getId(), Instant.now()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    private static String newRandomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
