package com.project.common.security;

/**
 * Mã quyền {@code "menuCode:PERMISSION"} mà code đang kiểm tra ({@link CurrentUser#requirePermission}).
 * Menu là đối tượng được phân quyền, permission là thao tác (VIEW, ADD, EDIT, DELETE...); role được cấp từng cặp
 * (menu, thao tác) qua ma trận phân quyền của auth-service. auth-service tự tạo sẵn các menu / thao tác dưới đây
 * lúc khởi động nếu chưa có.
 * <p>
 * Quyền trên dữ liệu "của người khác": không có quyền thì user vẫn thao tác được trên dữ liệu của chính mình
 * (hồ sơ customer của mình, business cho hồ sơ của mình).
 */
public final class Permissions {

    /** Thao tác mặc định. */
    public static final String VIEW = "VIEW";
    public static final String ADD = "ADD";
    public static final String EDIT = "EDIT";
    public static final String DELETE = "DELETE";
    public static final String EXPORT = "EXPORT";

    public static final String CATEGORY_CREATE = "category:ADD";
    public static final String CATEGORY_UPDATE = "category:EDIT";
    public static final String CATEGORY_DELETE = "category:DELETE";

    /** Danh mục đơn vị tính (category-service). */
    public static final String UNIT_CREATE = "unit:ADD";
    public static final String UNIT_UPDATE = "unit:EDIT";
    public static final String UNIT_DELETE = "unit:DELETE";

    /** Xem danh sách / hồ sơ customer của người khác. */
    public static final String CUSTOMER_READ = "customer:VIEW";
    /** Sửa hồ sơ customer của người khác, khoá / mở khoá customer. */
    public static final String CUSTOMER_UPDATE = "customer:EDIT";

    /** Tạo business cho hồ sơ customer của người khác. */
    public static final String BUSINESS_CREATE = "business:ADD";

    public static final String AUDIT_READ = "audit:VIEW";

    public static final String USER_READ = "user:VIEW";
    public static final String USER_CREATE = "user:ADD";
    /** Sửa user: thông tin, khoá / mở khoá, đổi role. */
    public static final String USER_UPDATE = "user:EDIT";

    public static final String ROLE_READ = "role:VIEW";
    public static final String ROLE_CREATE = "role:ADD";
    public static final String ROLE_UPDATE = "role:EDIT";
    public static final String ROLE_DELETE = "role:DELETE";

    public static final String MENU_READ = "menu:VIEW";
    public static final String MENU_CREATE = "menu:ADD";
    public static final String MENU_UPDATE = "menu:EDIT";
    public static final String MENU_DELETE = "menu:DELETE";

    public static final String PERMISSION_READ = "permission:VIEW";
    public static final String PERMISSION_CREATE = "permission:ADD";
    public static final String PERMISSION_UPDATE = "permission:EDIT";
    public static final String PERMISSION_DELETE = "permission:DELETE";

    public static final String SEPARATOR = ":";

    private Permissions() {
    }

    public static String of(String menuCode, String permissionCode) {
        return menuCode + SEPARATOR + permissionCode;
    }
}
