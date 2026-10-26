package com.project.auth.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.project.auth.entity.Action;
import com.project.auth.entity.Menu;
import com.project.auth.entity.Resource;
import com.project.auth.entity.Role;
import com.project.common.security.Permissions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request/response cho API phân quyền: action, resource, role, menu.
 * Code không đổi được sau khi tạo (role / quyền đã gán tham chiếu bằng code), nên request cập nhật không có code.
 */
public final class AuthzDtos {

    /** Chữ thường, số, "-"; không có ":" vì ":" ngăn cách resource và action trong mã quyền. */
    static final String RESOURCE_CODE = "^[a-z][a-z0-9-]{0,49}$";
    static final String ROLE_CODE = "^[A-Z][A-Z0-9_]{0,19}$";
    static final String MENU_CODE = "^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$";

    private AuthzDtos() {
    }

    // ---- Action ----

    public record CreateActionRequest(
            @NotBlank @Pattern(regexp = RESOURCE_CODE, message = "Chỉ gồm a-z, 0-9, \"-\", bắt đầu bằng chữ") String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description) {
    }

    public record UpdateActionRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            @NotNull Long version) {
    }

    public record ActionResponse(UUID id, String code, String name, String description, Long version) {

        public static ActionResponse from(Action a) {
            return new ActionResponse(a.getId(), a.getCode(), a.getName(), a.getDescription(), a.getVersion());
        }
    }

    // ---- Resource ----

    public record CreateResourceRequest(
            @NotBlank @Pattern(regexp = RESOURCE_CODE, message = "Chỉ gồm a-z, 0-9, \"-\", bắt đầu bằng chữ") String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            /** Code các action áp dụng được cho resource này. */
            @NotNull Set<String> actions) {
    }

    /** Bỏ một action khỏi resource thì quyền tương ứng cũng bị gỡ khỏi mọi role. */
    public record UpdateResourceRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            @NotNull Set<String> actions,
            @NotNull Long version) {
    }

    /** permissions: các mã quyền "resource:action" sinh từ resource này (dùng để gán cho role). */
    public record ResourceResponse(UUID id, String code, String name, String description, List<String> actions,
            List<String> permissions, Long version) {

        public static ResourceResponse from(Resource r) {
            List<String> actions = r.getActions().stream().sorted().toList();
            return new ResourceResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), actions,
                    actions.stream().map(a -> Permissions.of(r.getCode(), a)).toList(), r.getVersion());
        }
    }

    // ---- Role ----

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = ROLE_CODE, message = "Chỉ gồm A-Z, 0-9, \"_\", bắt đầu bằng chữ, tối đa 20 ký tự")
            String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            /** "resource:action", vd. "category:create". */
            @NotNull Set<String> permissions,
            @NotNull Set<UUID> menuIds) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String description,
            @NotNull Set<String> permissions,
            @NotNull Set<UUID> menuIds,
            @NotNull Long version) {
    }

    public record RoleResponse(UUID id, String code, String name, String description, boolean builtIn,
            List<String> permissions, List<UUID> menuIds, Long version) {

        public static RoleResponse from(Role r) {
            return new RoleResponse(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isBuiltIn(),
                    r.getPermissions().stream().sorted().toList(), r.getMenuIds().stream().sorted().toList(),
                    r.getVersion());
        }
    }

    // ---- Menu ----

    public record CreateMenuRequest(
            @NotBlank @Pattern(regexp = MENU_CODE, message = "Chỉ gồm chữ, số, \".\", \"_\", \"-\"") String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String path,
            @Size(max = 100) String icon,
            UUID parentId,
            int sortOrder,
            boolean active) {
    }

    public record UpdateMenuRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String path,
            @Size(max = 100) String icon,
            UUID parentId,
            int sortOrder,
            boolean active,
            @NotNull Long version) {
    }

    public record MenuResponse(UUID id, String code, String name, String path, String icon, UUID parentId,
            int sortOrder, boolean active, Long version) {

        public static MenuResponse from(Menu m) {
            return new MenuResponse(m.getId(), m.getCode(), m.getName(), m.getPath(), m.getIcon(), m.getParentId(),
                    m.getSortOrder(), m.isActive(), m.getVersion());
        }
    }

    /** Một nút trong cây menu. */
    public record MenuNode(UUID id, String code, String name, String path, String icon, int sortOrder,
            boolean active, List<MenuNode> children) {
    }

    // ---- User ----

    /** Role, quyền và cây menu của user đang đăng nhập (frontend dùng để hiện menu / nút). */
    public record MyAuthoritiesResponse(List<String> roles, List<String> permissions, List<MenuNode> menus) {
    }
}
