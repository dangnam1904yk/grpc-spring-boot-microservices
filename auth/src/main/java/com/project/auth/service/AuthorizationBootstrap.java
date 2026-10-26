package com.project.auth.service;

import java.util.List;
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

import com.project.auth.entity.Action;
import com.project.auth.entity.Menu;
import com.project.auth.entity.Resource;
import com.project.auth.entity.Role;
import com.project.auth.repository.ActionRepository;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.ResourceRepository;
import com.project.auth.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tạo sẵn action / resource / role mà code đang kiểm tra ({@link com.project.common.security.Permissions})
 * và menu các trang quản trị của frontend, nếu chưa có. Chỉ thêm phần còn thiếu, không ghi đè tên / mô tả đã sửa qua API.
 * <p>
 * Nhiều instance cùng khởi động: instance chậm hơn bị unique constraint / optimistic lock chặn, bỏ qua.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthorizationBootstrap implements ApplicationRunner {

    private static final String READ = "read";
    private static final String CREATE = "create";
    private static final String UPDATE = "update";
    private static final String DELETE = "delete";
    private static final Set<String> CRUD = Set.of(READ, CREATE, UPDATE, DELETE);

    private static final Map<String, String> ACTIONS = Map.of(READ, "Xem", CREATE, "Thêm", UPDATE, "Sửa",
            DELETE, "Xoá");
    private static final List<ResourceSeed> RESOURCES = List.of(
            new ResourceSeed("category", "Danh mục", Set.of(CREATE, UPDATE)),
            new ResourceSeed("customer", "Khách hàng (hồ sơ của người khác)", Set.of(READ, UPDATE)),
            new ResourceSeed("business", "Doanh nghiệp (tạo cho customer của người khác)", Set.of(CREATE)),
            new ResourceSeed("audit", "Audit log", Set.of(READ)),
            new ResourceSeed("user", "Tài khoản (xem, đổi role)", Set.of(READ, UPDATE)),
            new ResourceSeed("role", "Role", CRUD),
            new ResourceSeed("menu", "Menu", CRUD),
            new ResourceSeed("resource", "Resource", CRUD),
            new ResourceSeed("action", "Action", CRUD));

    /** Menu các trang quản trị của frontend (ecommerce); icon là tên icon của Ant Design. */
    private static final String ADMIN_MENU = "system";
    private static final List<MenuSeed> ADMIN_MENUS = List.of(
            new MenuSeed("system.users", "Tài khoản", "/admin/users", "UserOutlined", 1),
            new MenuSeed("system.roles", "Role", "/admin/roles", "TeamOutlined", 2),
            new MenuSeed("system.menus", "Menu", "/admin/menus", "MenuOutlined", 3),
            new MenuSeed("system.resources", "Resource", "/admin/resources", "AppstoreOutlined", 4),
            new MenuSeed("system.actions", "Action", "/admin/actions", "ThunderboltOutlined", 5));

    private final ActionRepository actionRepository;
    private final ResourceRepository resourceRepository;
    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;

    @Override
    public void run(ApplicationArguments args) {
        ACTIONS.forEach(this::ensureAction);
        RESOURCES.forEach(this::ensureResource);
        ensureRole(Role.USER, "Người dùng");
        ensureRole(Role.ADMIN, "Quản trị (toàn quyền)");
        Menu parent = ensureMenu(new MenuSeed(ADMIN_MENU, "Quản trị hệ thống", null, "SettingOutlined", 100), null);
        if (parent != null) {
            ADMIN_MENUS.forEach(seed -> ensureMenu(seed, parent.getId()));
        }
    }

    /** Trả về menu (đã có hoặc vừa tạo); null nếu instance khác đang tạo cùng lúc. */
    private Menu ensureMenu(MenuSeed seed, UUID parentId) {
        Optional<Menu> existing = menuRepository.findByCode(seed.code());
        if (existing.isPresent()) {
            return existing.get();
        }
        Menu menu = new Menu();
        menu.setCode(seed.code());
        menu.setName(seed.name());
        menu.setPath(seed.path());
        menu.setIcon(seed.icon());
        menu.setParentId(parentId);
        menu.setSortOrder(seed.sortOrder());
        saveIgnoringRace(() -> menuRepository.saveAndFlush(menu), "menu " + seed.code());
        return menuRepository.findByCode(seed.code()).orElse(null);
    }

    private void ensureAction(String code, String name) {
        if (actionRepository.existsByCode(code)) {
            return;
        }
        Action action = new Action();
        action.setCode(code);
        action.setName(name);
        saveIgnoringRace(() -> actionRepository.saveAndFlush(action), "action " + code);
    }

    private void ensureResource(ResourceSeed seed) {
        Resource resource = resourceRepository.findByCode(seed.code()).orElseGet(() -> {
            Resource created = new Resource();
            created.setCode(seed.code());
            created.setName(seed.name());
            return created;
        });
        if (resource.getId() != null && resource.getActions().containsAll(seed.actions())) {
            return;
        }
        resource.getActions().addAll(seed.actions());
        saveIgnoringRace(() -> resourceRepository.saveAndFlush(resource), "resource " + seed.code());
    }

    private void ensureRole(String code, String name) {
        if (roleRepository.existsByCode(code)) {
            return;
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setBuiltIn(true);
        saveIgnoringRace(() -> roleRepository.saveAndFlush(role), "role " + code);
    }

    private static void saveIgnoringRace(Runnable save, String what) {
        try {
            save.run();
            log.info("Đã tạo / cập nhật {}", what);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
            log.info("Bỏ qua {}: instance khác đã tạo", what);
        }
    }

    private record ResourceSeed(String code, String name, Set<String> actions) {
    }

    private record MenuSeed(String code, String name, String path, String icon, int sortOrder) {
    }
}
