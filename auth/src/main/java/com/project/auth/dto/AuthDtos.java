package com.project.auth.dto;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.project.auth.entity.UserAccount;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            // BCrypt chỉ dùng 72 byte đầu -> giới hạn độ dài để không âm thầm cắt mật khẩu
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Mật khẩu phải có cả chữ và số")
            String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Code của role; USER luôn được giữ. */
    public record UpdateRolesRequest(@NotEmpty Set<String> roles) {
    }

    /** expiresIn: số giây access token còn hiệu lực. */
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    }

    public record UserResponse(UUID id, String email, Set<String> roles, boolean enabled) {

        public static UserResponse from(UserAccount user) {
            return new UserResponse(user.getId(), user.getEmail(), new TreeSet<>(user.getRoles()), user.isEnabled());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
