package com.project.auth.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.project.auth.entity.Menu;
import com.project.auth.entity.Permission;
import com.project.auth.entity.Role;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.PermissionRepository;
import com.project.auth.repository.RoleRepository;
import com.project.auth.service.AuthzSeeds.MenuSeed;
import com.project.auth.service.AuthzSeeds.PermissionSeed;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tạo sẵn thao tác / menu / role mà code đang kiểm tra ({@link AuthzSeeds}) nếu chưa có. Chỉ thêm phần còn thiếu,
 * không ghi đè tên / thứ tự / trạng thái đã sửa qua API.
 * <p>
 * Đồng thời chuyển dữ liệu của bản phân quyền trước (resource / action): quyền "category:create" thành
 * "category:ADD", menu "system.users" thành "user"...
 * <p>
 * Nhiều instance cùng khởi động: instance chậm hơn bị unique constraint / optimistic lock chặn, bỏ qua.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthorizationBootstrap implements ApplicationRunner {

    /** Thao tác của bản trước -> thao tác hiện tại. */
    private static final Map<String, String> LEGACY_ACTIONS = Map.of(
            "read", Permissions.VIEW, "create", Permissions.ADD, "update", Permissions.EDIT,
            "delete", Permissions.DELETE);
    /** Resource / menu của bản trước -> menu hiện tại. */
    private static final Map<String, String> LEGACY_RESOURCES = Map.of("resource", "permission", "action", "permission");
    private static final Map<String, String> LEGACY_MENUS = Map.of(
            "system.users", "user", "system.roles", "role", "system.menus", "menu",
            "system.resources", "permission", "system.actions", "permission");

    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;

    @Override
    public void run(ApplicationArguments args) {
        AuthzSeeds.PERMISSIONS.forEach(this::ensurePermission);
        AuthzSeeds.MENUS.forEach(this::ensureMenu);
        ensureRole(Role.USER, "Người dùng");
        ensureRole(Role.ADMIN, "Quản trị (toàn quyền)");
        migrateLegacy();
    }

    private void ensurePermission(PermissionSeed seed) {
        if (permissionRepository.existsByCode(seed.code())) {
            return;
        }
        Permission permission = new Permission();
        permission.setCode(seed.code());
        permission.setName(seed.name());
        permission.setBuiltIn(true);
        saveIgnoringRace(() -> permissionRepository.saveAndFlush(permission), "quyền " + seed.code());
    }

    private void ensureMenu(MenuSeed seed) {
        if (menuRepository.existsByCode(seed.code())) {
            return;
        }
        Menu menu = new Menu();
        menu.setCode(seed.code());
        menu.setName(seed.name());
        menu.setUrl(seed.url());
        menu.setIcon(seed.icon());
        menu.setOrderIndex(seed.orderIndex());
        menu.setActive(seed.active());
        if (seed.parentCode() != null) {
            menu.setParentId(menuRepository.findByCode(seed.parentCode()).map(Menu::getId).orElse(null));
        }
        saveIgnoringRace(() -> menuRepository.saveAndFlush(menu), "menu " + seed.code());
    }

    private void ensureRole(String code, String name) {
        if (roleRepository.existsByCode(code)) {
            return;
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setBuiltIn(true);
        saveIgnoringRace(() -> roleRepository.saveAndFlush(role), "vai trò " + code);
    }

    private void migrateLegacy() {
        Map<UUID, UUID> menuIds = new HashMap<>();
        LEGACY_MENUS.forEach((oldCode, newCode) -> menuRepository.findByCode(oldCode).ifPresent(old ->
                menuRepository.findByCode(newCode).ifPresent(current -> menuIds.put(old.getId(), current.getId()))));

        for (Role role : roleRepository.findAll()) {
            Set<String> permissions = new HashSet<>();
            role.getPermissions().forEach(p -> permissions.add(migratePermission(p)));
            Set<UUID> menus = new HashSet<>();
            role.getMenuIds().forEach(id -> menus.add(menuIds.getOrDefault(id, id)));
            if (!permissions.equals(role.getPermissions()) || !menus.equals(role.getMenuIds())) {
                role.setPermissions(permissions);
                role.setMenuIds(menus);
                saveIgnoringRace(() -> roleRepository.saveAndFlush(role), "chuyển quyền của vai trò " + role.getCode());
            }
        }
        for (String oldCode : LEGACY_MENUS.keySet()) {
            Optional<Menu> old = menuRepository.findByCode(oldCode);
            if (old.isPresent()) {
                try {
                    menuRepository.delete(old.get());
                    log.info("Đã xoá menu cũ {}", oldCode);
                } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                    log.info("Bỏ qua xoá menu cũ {}: instance khác đã xử lý", oldCode);
                }
            }
        }
    }

    /** "category:create" -> "category:ADD"; quyền đã đúng dạng mới thì giữ nguyên. */
    private static String migratePermission(String permission) {
        int sep = permission.indexOf(Permissions.SEPARATOR);
        if (sep < 0) {
            return permission;
        }
        String action = LEGACY_ACTIONS.get(permission.substring(sep + 1));
        if (action == null) {
            return permission;
        }
        String resource = permission.substring(0, sep);
        return Permissions.of(LEGACY_RESOURCES.getOrDefault(resource, resource), action);
    }

    private static void saveIgnoringRace(Runnable save, String what) {
        try {
            save.run();
            log.info("Đã tạo / cập nhật {}", what);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            log.info("Bỏ qua {}: instance khác đã tạo", what);
        }
    }
}
