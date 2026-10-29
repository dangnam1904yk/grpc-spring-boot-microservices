package com.project.auth.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.project.auth.entity.Permission;
import com.project.auth.entity.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response cho API phân quyền (theo mô hình của managerstudio): permission (thao tác), menu, role,
 * ma trận role × menu × thao tác.
 * Code không đổi được sau khi tạo (mã quyền "menuCode:PERMISSION" ghép từ code), nên request cập nhật không có code.
 */
public final class AuthzDtos {

    /** Không có ":" vì ":" ngăn cách menu và thao tác trong mã quyền. */
    static final String PERMISSION_CODE = "^[A-Z][A-Z0-9_]{0,49}$";
    static final String MENU_CODE = "^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$";
    static final String ROLE_CODE = "^[A-Z][A-Z0-9_]{0,19}$";

    private AuthzDtos() {
    }

    public enum Status {
        ACTIVE, INACTIVE;

        public static Status of(boolean active) {
            return active ? ACTIVE : INACTIVE;
        }
    }

    // ---- Permission (thao tác) ----

    public record CreatePermissionRequest(
            @NotBlank @Pattern(regexp = PERMISSION_CODE, message = "Chỉ gồm chữ in hoa, số, \"_\" (vd. EXPORT)")
            String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description) {
    }

    public record UpdatePermissionRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            Status status,
            @NotNull Long version) {
    }

    /** type: SYSTEM (code đang dùng, không xoá được) hoặc CUSTOM. */
    public record PermissionResponse(UUID id, String code, String name, String description, String type,
            Status status, Long version) {

        public static PermissionResponse from(Permission p) {
            return new PermissionResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.isBuiltIn() ? "SYSTEM" : "CUSTOM", Status.of(!p.isDisabled()), p.getVersion());
        }
    }

    // ---- Menu ----

    public record CreateMenuRequest(
            @NotBlank @Pattern(regexp = MENU_CODE, message = "Chỉ gồm chữ, số, \".\", \"_\", \"-\", tối đa 50 ký tự")
            String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String url,
            @Size(max = 100) String icon,
            UUID parentId,
            Integer orderIndex,
            Status status) {
    }

    public record UpdateMenuRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String url,
            @Size(max = 100) String icon,
            UUID parentId,
            Integer orderIndex,
            Status status,
            @NotNull Long version) {
    }

    /** Một dòng của thao tác kéo / đổi thứ tự: menu đặt ở vị trí orderIndex dưới parentId (null = gốc). */
    public record MenuOrderRequest(@NotNull UUID id, @NotNull Integer orderIndex, UUID parentId) {
    }

    /**
     * Một nút trong cây menu. level: 1 = gốc. system: menu auth-service tạo sẵn (code đang kiểm tra quyền trên nó),
     * không xoá được.
     */
    public record MenuNode(UUID id, String code, String name, String url, String icon, UUID parentId,
            int orderIndex, int level, Status status, boolean system, Long version, List<MenuNode> children) {
    }

    // ---- Role ----

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = ROLE_CODE, message = "Chỉ gồm A-Z, 0-9, \"_\", bắt đầu bằng chữ, tối đa 20 ký tự")
            String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            Status status,
            Set<UUID> menuIds,
            /** "menuCode:PERMISSION", vd. "category:ADD". */
            Set<String> permissions,
            Set<UUID> userIds) {
    }

    /** Field null = giữ nguyên (vd. chỉ đổi tên thì không gửi menuIds / permissions / userIds). */
    public record UpdateRoleRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            Status status,
            Set<UUID> menuIds,
            Set<String> permissions,
            Set<UUID> userIds,
            @NotNull Long version) {
    }

    /** Lưu một hàng của ma trận phân quyền (mọi ô của role). */
    public record UpdateRolePermissionsRequest(@NotNull Set<String> permissions, @NotNull Long version) {
    }

    public record UpdateRoleMenusRequest(@NotNull Set<UUID> menuIds, @NotNull Long version) {
    }

    public record RoleResponse(UUID id, String code, String name, String description, boolean system,
            Status status, long accountCount, int permissionCount, int menuCount, Long version) {

        public static RoleResponse from(Role r, long accountCount) {
            return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isBuiltIn(),
                    Status.of(!r.isDisabled()), accountCount, r.getPermissions().size(), r.getMenuIds().size(),
                    r.getVersion());
        }
    }

    public record RoleDetailResponse(UUID id, String code, String name, String description, boolean system,
            Status status, List<String> permissions, List<UUID> menuIds, List<UUID> userIds, Long version) {

        public static RoleDetailResponse from(Role r, List<UUID> userIds) {
            return new RoleDetailResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isBuiltIn(),
                    Status.of(!r.isDisabled()), r.getPermissions().stream().sorted().toList(),
                    r.getMenuIds().stream().sorted().toList(), userIds, r.getVersion());
        }
    }

    // ---- User đang đăng nhập ----

    /** Role, quyền và cây menu của user đang đăng nhập (frontend dùng để hiện menu / nút). */
    public record MyAuthoritiesResponse(List<String> roles, List<String> permissions, List<MenuNode> menus) {
    }
}
