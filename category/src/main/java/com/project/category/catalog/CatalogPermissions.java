package com.project.category.catalog;

/**
 * Mã quyền thêm / sửa / xoá của một danh mục (hằng trong {@link com.project.common.security.Permissions}).
 * Đọc thì ai cũng được.
 */
public record CatalogPermissions(String create, String update, String delete) {
}
