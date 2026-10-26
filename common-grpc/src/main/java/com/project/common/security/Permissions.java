package com.project.common.security;

/**
 * Mã quyền {@code "resource:action"} mà code đang kiểm tra ({@link CurrentUser#requirePermission}).
 * Danh mục resource / action / role nằm trong DB của auth-service (quản lý qua API {@code /api/auth/...});
 * auth-service tự tạo sẵn các quyền dưới đây lúc khởi động nếu chưa có.
 * <p>
 * Quyền trên dữ liệu "của người khác": không có quyền thì user vẫn thao tác được trên dữ liệu của chính mình
 * (hồ sơ customer của mình, business cho hồ sơ của mình).
 */
public final class Permissions {

    public static final String CATEGORY_CREATE = "category:create";
    public static final String CATEGORY_UPDATE = "category:update";

    /** Xem danh sách / hồ sơ customer của người khác. */
    public static final String CUSTOMER_READ = "customer:read";
    /** Sửa hồ sơ customer của người khác, khoá / mở khoá customer. */
    public static final String CUSTOMER_UPDATE = "customer:update";

    /** Tạo business cho hồ sơ customer của người khác. */
    public static final String BUSINESS_CREATE = "business:create";

    public static final String AUDIT_READ = "audit:read";

    public static final String USER_READ = "user:read";
    /** Đổi role của user. */
    public static final String USER_UPDATE = "user:update";

    public static final String ROLE_READ = "role:read";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_UPDATE = "role:update";
    public static final String ROLE_DELETE = "role:delete";

    public static final String MENU_READ = "menu:read";
    public static final String MENU_CREATE = "menu:create";
    public static final String MENU_UPDATE = "menu:update";
    public static final String MENU_DELETE = "menu:delete";

    public static final String RESOURCE_READ = "resource:read";
    public static final String RESOURCE_CREATE = "resource:create";
    public static final String RESOURCE_UPDATE = "resource:update";
    public static final String RESOURCE_DELETE = "resource:delete";

    public static final String ACTION_READ = "action:read";
    public static final String ACTION_CREATE = "action:create";
    public static final String ACTION_UPDATE = "action:update";
    public static final String ACTION_DELETE = "action:delete";

    public static final String SEPARATOR = ":";

    private Permissions() {
    }

    public static String of(String resource, String action) {
        return resource + SEPARATOR + action;
    }
}
