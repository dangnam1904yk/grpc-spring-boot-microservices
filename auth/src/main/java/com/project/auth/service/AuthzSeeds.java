package com.project.auth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.project.common.security.Permissions;

/**
 * Thao tác và menu auth-service tạo sẵn ({@link AuthorizationBootstrap}): code đang kiểm tra quyền trên chúng
 * ({@link Permissions}), nên không xoá được qua API.
 */
final class AuthzSeeds {

    record PermissionSeed(String code, String name) {
    }

    /** parentCode null = menu gốc. active=false: menu chưa có trang ở frontend, chỉ dùng để phân quyền. */
    record MenuSeed(String code, String name, String url, String icon, String parentCode, int orderIndex,
            boolean active) {
    }

    static final List<PermissionSeed> PERMISSIONS = List.of(
            new PermissionSeed(Permissions.VIEW, "Xem"),
            new PermissionSeed(Permissions.ADD, "Thêm mới"),
            new PermissionSeed(Permissions.EDIT, "Chỉnh sửa"),
            new PermissionSeed(Permissions.DELETE, "Xóa"),
            new PermissionSeed(Permissions.EXPORT, "Xuất file"));

    static final String SYSTEM_GROUP = "system";

    static final List<MenuSeed> MENUS = List.of(
            new MenuSeed("category", "Danh mục", "/categories", "AppstoreOutlined", null, 10, false),
            new MenuSeed("customer", "Khách hàng", "/customers", "TeamOutlined", null, 11, false),
            new MenuSeed("business", "Doanh nghiệp", "/businesses", "ShopOutlined", null, 12, false),
            new MenuSeed("audit", "Audit log", "/audit", "FileTextOutlined", null, 13, false),
            new MenuSeed("unit", "Đơn vị tính", "/units", "TagsOutlined", null, 14, false),
            new MenuSeed(SYSTEM_GROUP, "Quản trị hệ thống", null, "SettingOutlined", null, 100, true),
            new MenuSeed("user", "Quản lý người dùng", "/admin/users", "UserOutlined", SYSTEM_GROUP, 1, true),
            new MenuSeed("role", "Vai trò", "/admin/roles", "SafetyCertificateOutlined", SYSTEM_GROUP, 2, true),
            new MenuSeed("permission", "Quyền hạn", "/admin/permissions", "KeyOutlined", SYSTEM_GROUP, 3, true),
            new MenuSeed("menu", "Quản lý menu", "/admin/menus", "MenuOutlined", SYSTEM_GROUP, 4, true));

    static final Set<String> SYSTEM_MENU_CODES = MENUS.stream().map(MenuSeed::code).collect(Collectors.toSet());

    private AuthzSeeds() {
    }
}
