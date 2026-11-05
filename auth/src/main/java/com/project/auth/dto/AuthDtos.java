package com.project.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.data.domain.Page;

import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.keycloak.KeycloakTokens;
import com.project.auth.keycloak.KeycloakUser;
import com.project.common.validation.CustomMaxLength;
import com.project.common.validation.CustomMinLength;
import com.project.common.validation.CustomRegex;
import com.project.common.validation.CustomRequired;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Đăng ký / đăng nhập bằng form của frontend (auth-service gọi sang Keycloak) và API quản trị user.
 * Độ dài mật khẩu kiểm tra ở đây; các luật còn lại (có chữ số...) do password policy của realm kiểm tra.
 */
public final class AuthDtos {

    private static final String EMAIL = "user.label.email";
    private static final String PASSWORD = "user.label.password";
    private static final String FULL_NAME = "user.label.full-name";
    private static final String PHONE = "user.label.phone-number";
    private static final String REFRESH_TOKEN = "auth.label.refresh-token";
    /** Đủ chặt để chặn dữ liệu rác (khoảng trắng đầu / cuối được bỏ khi lưu); Keycloak kiểm tra lại email. */
    private static final String EMAIL_REGEX = "^\\s*[^\\s@]+@[^\\s@]+\\.[^\\s@]+\\s*$";
    private static final String PHONE_REGEX = "^\\+?[0-9]{8,15}$";

    private AuthDtos() {
    }

    public record LoginRequest(
            @CustomRequired(label = EMAIL) @CustomMaxLength(max = 255, label = EMAIL) String email,
            @CustomRequired(label = PASSWORD) @CustomMaxLength(max = 72, label = PASSWORD) String password) {
    }

    /** Tự đăng ký: tài khoản tạo trên Keycloak, bản sao lưu vào bảng auth_user, chỉ có role USER. */
    public record RegisterRequest(
            @CustomRequired(label = EMAIL) @CustomMaxLength(max = 255, label = EMAIL)
            @CustomRegex(regexp = EMAIL_REGEX, label = EMAIL) String email,
            @CustomRequired(label = PASSWORD) @CustomMinLength(min = 8, label = PASSWORD)
            @CustomMaxLength(max = 72, label = PASSWORD) String password,
            @CustomMaxLength(max = 255, label = FULL_NAME) String fullName,
            @CustomRegex(regexp = PHONE_REGEX, label = PHONE) String phoneNumber) {
    }

    public record RefreshRequest(@CustomRequired(label = REFRESH_TOKEN) String refreshToken) {
    }

    /** expiresIn / refreshExpiresIn: số giây còn hiệu lực. */
    public record TokenResponse(String tokenType, String accessToken, String refreshToken, long expiresIn,
            long refreshExpiresIn) {

        public static TokenResponse from(KeycloakTokens tokens) {
            return new TokenResponse("Bearer", tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
                    tokens.refreshExpiresIn());
        }
    }

    /** Code của role; USER luôn được giữ. */
    public record UpdateRolesRequest(@NotEmpty Set<String> roles) {
    }

    /** Tạo tài khoản (cần quyền user:ADD). roles trống thì chỉ có USER. */
    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 255) String password,
            @Size(max = 255) String fullName,
            @Size(max = 20) String phoneNumber,
            Set<String> roles,
            Status status) {
    }

    /** roles null = giữ nguyên. Đổi role hoặc khoá tài khoản thì mọi phiên của user bị thu hồi. */
    public record UpdateUserRequest(
            @Size(max = 255) String fullName,
            @Size(max = 20) String phoneNumber,
            Status status,
            Set<String> roles) {
    }

    /** id: id user bên Keycloak (claim "sub"). roles: các role của auth-service mà user đang có. */
    public record UserResponse(String id, String email, String fullName, String phoneNumber, Set<String> roles,
            boolean enabled, Status status, Instant createDate) {

        public static UserResponse from(KeycloakUser user, Set<String> roles) {
            return new UserResponse(user.id(), user.email(), user.fullName(), user.phoneNumber(),
                    new TreeSet<>(roles), user.enabled(), Status.of(user.enabled()), user.createdAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
