package com.project.customer.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.project.customer.entity.Customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {

    private static final String PHONE_REGEX = "^\\+?[0-9]{8,15}$";

    private CustomerDtos() {
    }

    /** Tạo hồ sơ cho chính user đang đăng nhập; email lấy từ token, không nhận từ client. */
    public record CreateCustomerRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Pattern(regexp = PHONE_REGEX, message = "Số điện thoại không hợp lệ") String phone) {
    }

    /** active: chỉ ADMIN được đổi (khoá / mở khoá). */
    public record UpdateCustomerRequest(
            @NotBlank @Size(max = 255) String fullName,
            @Pattern(regexp = PHONE_REGEX, message = "Số điện thoại không hợp lệ") String phone,
            boolean active,
            @NotNull Long version) {
    }

    public record CustomerResponse(
            Long id,
            String userId,
            String fullName,
            String email,
            String phone,
            boolean active,
            Long version) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getUserId(), c.getFullName(), c.getEmail(), c.getPhone(),
                    c.isActive(), c.getVersion());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
