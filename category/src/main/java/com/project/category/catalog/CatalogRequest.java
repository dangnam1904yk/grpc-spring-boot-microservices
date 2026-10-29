package com.project.category.catalog;

/**
 * Field chung của request tạo / sửa danh mục. Danh mục có field riêng khai báo record riêng implement interface này.
 * <ul>
 * <li>Tạo: bắt buộc {@code code}, {@code name}; bỏ qua {@code version}.</li>
 * <li>Sửa: bắt buộc {@code name}, {@code version}; bỏ qua {@code code} (không đổi được).</li>
 * <li>{@code active}, {@code sortOrder} để null = giữ nguyên (tạo mới: active=true).</li>
 * </ul>
 * Kiểm tra (bắt buộc, độ dài) nằm ở {@link CatalogService} để áp dụng cho cả REST lẫn gRPC, record không cần
 * annotation Bean Validation.
 */
public interface CatalogRequest {

    String code();

    String name();

    String description();

    Integer sortOrder();

    Boolean active();

    Long version();
}
