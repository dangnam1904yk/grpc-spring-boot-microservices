package com.project.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.project.auth.dto.AuthDtos.LoginRequest;
import com.project.auth.dto.AuthDtos.RefreshRequest;
import com.project.auth.dto.AuthDtos.RegisterRequest;
import com.project.auth.dto.AuthDtos.TokenResponse;
import com.project.auth.dto.AuthDtos.UpdateRolesRequest;
import com.project.auth.dto.AuthDtos.UserResponse;
import com.project.auth.service.AuthService;
import com.project.auth.service.TokenService;
import com.project.common.security.CurrentUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;
    private final RSAKey jwtSigningKey;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@RequestBody @Valid RegisterRequest req) {
        return UserResponse.from(authService.register(req));
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshRequest req) {
        return tokenService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid RefreshRequest req) {
        tokenService.revoke(req.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(authService.getUser(Long.valueOf(CurrentUser.require().id())));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRoles(@PathVariable Long id, @RequestBody @Valid UpdateRolesRequest req) {
        return UserResponse.from(authService.updateRoles(id, req.roles()));
    }

    /** Public key để các service khác kiểm tra chữ ký JWT (chỉ public key, không bao giờ lộ private key). */
    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }
}
