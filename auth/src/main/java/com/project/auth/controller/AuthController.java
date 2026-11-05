package com.project.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.project.auth.dto.AuthDtos.CreateUserRequest;
import com.project.auth.dto.AuthDtos.LoginRequest;
import com.project.auth.dto.AuthDtos.PageResponse;
import com.project.auth.dto.AuthDtos.RefreshRequest;
import com.project.auth.dto.AuthDtos.RegisterRequest;
import com.project.auth.dto.AuthDtos.TokenResponse;
import com.project.auth.dto.AuthDtos.UpdateRolesRequest;
import com.project.auth.dto.AuthDtos.UpdateUserRequest;
import com.project.auth.dto.AuthDtos.UserResponse;
import com.project.auth.dto.AuthzDtos.MyAuthoritiesResponse;
import com.project.auth.service.AuthService;
import com.project.auth.service.AuthorityService;
import com.project.auth.service.LoginService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Đăng ký / đăng nhập / refresh / đăng xuất bằng form của frontend (auth-service gọi sang Keycloak; 4 API này
 * không cần token, xem app.security.public-paths), thông tin của user đang đăng nhập và quản trị user.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuthService authService;
    private final AuthorityService authorityService;
    private final LoginService loginService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@RequestBody @Valid RegisterRequest req) {
        return TokenResponse.from(loginService.register(req));
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody @Valid LoginRequest req) {
        return TokenResponse.from(loginService.login(req.email(), req.password()));
    }

    /** Refresh token chỉ dùng được 1 lần (realm bật revoke refresh token). */
    @PostMapping("/refresh")
    public TokenResponse refresh(@RequestBody @Valid RefreshRequest req) {
        return TokenResponse.from(loginService.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid RefreshRequest req) {
        loginService.logout(req.refreshToken());
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.me();
    }

    /** Role, quyền và cây menu của user đang đăng nhập (frontend dùng để hiện menu / nút). */
    @GetMapping("/me/authorities")
    public MyAuthoritiesResponse myAuthorities() {
        return authorityService.myAuthorities();
    }

    /** keyword: tìm theo email, họ tên. */
    @GetMapping("/users")
    public PageResponse<UserResponse> listUsers(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return authService.list(keyword, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@RequestBody @Valid CreateUserRequest req) {
        return authService.createByAdmin(req);
    }

    @PutMapping("/users/{id}")
    public UserResponse updateUser(@PathVariable String id, @RequestBody @Valid UpdateUserRequest req) {
        return authService.updateByAdmin(id, req);
    }

    @PutMapping("/users/{id}/roles")
    public UserResponse updateRoles(@PathVariable String id, @RequestBody @Valid UpdateRolesRequest req) {
        return authService.updateRoles(id, req.roles());
    }
}
