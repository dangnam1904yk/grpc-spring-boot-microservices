package com.project.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.entity.UserAccount;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    // BCrypt chỉ dùng 72 byte đầu -> giới hạn độ dài để không âm thầm cắt mật khẩu
    static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    static final String PASSWORD_MESSAGE = "Mật khẩu phải có cả chữ và số";

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
            String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Code của role; USER luôn được giữ. */
    public record UpdateRolesRequest(@NotEmpty Set<String> roles) {
    }

    /** Tạo tài khoản (cần quyền user:ADD). roles trống thì chỉ có USER. */
    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
            String password,
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
            Set<String> roles,
            @NotNull Long version) {
    }

    /** expiresIn: số giây access token còn hiệu lực. */
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    }

    public record UserResponse(UUID id, String email, String fullName, String phoneNumber, Set<String> roles,
            boolean enabled, Status status, Instant createDate, Long version) {

        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhoneNumber(),
                    new TreeSet<>(user.getRoles()), user.isEnabled(), Status.of(user.isEnabled()),
                    user.getCreateDate(), user.getVersion());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
