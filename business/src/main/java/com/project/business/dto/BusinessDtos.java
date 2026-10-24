package com.project.business.dto;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.project.business.entity.Business;
import com.project.grpc.category.v1.Category;
import com.project.grpc.customer.v1.Customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record CreateBusinessRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 50) String categoryCode,
            @NotNull UUID ownerId) {
    }

    /** Thông tin category lấy từ category-service qua gRPC. */
    public record CategoryInfo(String code, String name, boolean active) {

        public static CategoryInfo from(Category c) {
            return c == null ? null : new CategoryInfo(c.getCode(), c.getName(), c.getActive());
        }
    }

    /** Thông tin chủ sở hữu lấy từ customer-service qua gRPC. */
    public record CustomerInfo(UUID id, String fullName, String email) {

        public static CustomerInfo from(Customer c) {
            return c == null ? null : new CustomerInfo(UUID.fromString(c.getId()), c.getFullName(), c.getEmail());
        }
    }

    /**
     * category / owner = null khi service tương ứng không phản hồi (degrade) hoặc bản ghi không còn tồn tại.
     */
    public record BusinessResponse(
            UUID id,
            String code,
            String name,
            String categoryCode,
            UUID ownerId,
            boolean active,
            Long version,
            CategoryInfo category,
            CustomerInfo owner) {

        public static BusinessResponse of(Business b, CategoryInfo category, CustomerInfo owner) {
            return new BusinessResponse(b.getId(), b.getCode(), b.getName(), b.getCategoryCode(), b.getOwnerId(),
                    b.isActive(), b.getVersion(), category, owner);
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> from(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
