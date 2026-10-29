package com.project.customer.dto;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.project.common.validation.CustomMaxLength;
import com.project.common.validation.CustomRegex;
import com.project.common.validation.CustomRequired;
import com.project.customer.entity.Customer;

public final class CustomerDtos {

    private static final String PHONE_REGEX = "^\\+?[0-9]{8,15}$";
    private static final String FULL_NAME = "customer.label.full-name";
    private static final String PHONE_EXAMPLE = "0912345678";

    private CustomerDtos() {
    }

    /** Tạo hồ sơ cho chính user đang đăng nhập; email lấy từ token, không nhận từ client. */
    public record CreateCustomerRequest(
            @CustomRequired(label = FULL_NAME) @CustomMaxLength(max = 255, label = FULL_NAME) String fullName,
            @CustomRegex(regexp = PHONE_REGEX, message = "{customer.phone.invalid}", args = PHONE_EXAMPLE) String phone) {
    }

    /** active: chỉ ADMIN được đổi (khoá / mở khoá). */
    public record UpdateCustomerRequest(
            @CustomRequired(label = FULL_NAME) @CustomMaxLength(max = 255, label = FULL_NAME) String fullName,
            @CustomRegex(regexp = PHONE_REGEX, message = "{customer.phone.invalid}", args = PHONE_EXAMPLE) String phone,
            boolean active,
            @CustomRequired Long version) {
    }

    public record CustomerResponse(
            UUID id,
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
