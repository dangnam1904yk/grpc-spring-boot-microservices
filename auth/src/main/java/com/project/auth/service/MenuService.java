package com.project.auth.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.CreateMenuRequest;
import com.project.auth.dto.AuthzDtos.MenuNode;
import com.project.auth.dto.AuthzDtos.MenuOrderRequest;
import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.dto.AuthzDtos.UpdateMenuRequest;
import com.project.auth.entity.Menu;
import com.project.auth.entity.Role;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.RoleRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Menu dạng cây. Xoá menu thì xoá cả menu con / cháu, gỡ chúng khỏi mọi role (menu hiển thị và quyền cấp trên
 * menu). Menu hệ thống (auth-service tạo sẵn, code đang kiểm tra quyền trên nó) không xoá được.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;
    private final AuthorityService authorityService;

    /** Toàn bộ cây menu, kể cả menu đang tắt (màn hình quản trị, ma trận phân quyền). */
    public List<MenuNode> tree() {
        CurrentUser.requirePermission(Permissions.MENU_READ);
        return MenuTree.build(menuRepository.findAllByOrderByOrderIndexAscCodeAsc(), menu -> true);
    }

    public Menu get(UUID id) {
        CurrentUser.requirePermission(Permissions.MENU_READ);
        return getById(id);
    }

    /** Một menu (không kèm menu con), level tính theo số cấp menu cha. */
    public MenuNode node(Menu menu) {
        int level = 1;
        Set<UUID> seen = new HashSet<>();
        for (UUID parent = menu.getParentId(); parent != null && seen.add(parent); level++) {
            parent = menuRepository.findById(parent).map(Menu::getParentId).orElse(null);
        }
        return new MenuNode(menu.getId(), menu.getCode(), menu.getName(), menu.getUrl(), menu.getIcon(),
                menu.getParentId(), menu.getOrderIndex(), level, Status.of(menu.isActive()),
                AuthzSeeds.SYSTEM_MENU_CODES.contains(menu.getCode()), menu.getVersion(), List.of());
    }

    @Transactional
    public Menu create(CreateMenuRequest req) {
        CurrentUser.requirePermission(Permissions.MENU_CREATE);
        if (menuRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Mã menu " + req.code() + " đã tồn tại");
        }
        if (req.parentId() != null) {
            getById(req.parentId());
        }
        Menu menu = new Menu();
        menu.setCode(req.code());
        menu.setParentId(req.parentId());
        // Không chỉ định thứ tự thì xếp cuối trong các menu cùng cha
        menu.setOrderIndex(req.orderIndex() != null ? req.orderIndex() : nextOrderIndex(req.parentId()));
        apply(menu, req.name(), req.url(), req.icon(), req.status());
        try {
            return menuRepository.saveAndFlush(menu);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Mã menu " + req.code() + " đã tồn tại");
        }
    }

    @Transactional
    public Menu update(UUID id, UpdateMenuRequest req) {
        CurrentUser.requirePermission(Permissions.MENU_UPDATE);
        Menu menu = getById(id);
        AuthzChecks.requireVersion(menu.getVersion(), req.version(), "Menu " + menu.getCode());
        requireNoCycle(id, req.parentId());
        menu.setParentId(req.parentId());
        if (req.orderIndex() != null) {
            menu.setOrderIndex(req.orderIndex());
        }
        apply(menu, req.name(), req.url(), req.icon(), req.status());
        return menuRepository.saveAndFlush(menu);
    }

    /** Đổi thứ tự / chuyển menu cha cho nhiều menu một lần (nút lên / xuống, kéo thả). */
    @Transactional
    public void reorder(List<MenuOrderRequest> items) {
        CurrentUser.requirePermission(Permissions.MENU_UPDATE);
        for (MenuOrderRequest item : items) {
            Menu menu = getById(item.id());
            requireNoCycle(item.id(), item.parentId());
            menu.setParentId(item.parentId());
            menu.setOrderIndex(item.orderIndex());
        }
        menuRepository.flush();
    }

    @Transactional
    public void delete(UUID id) {
        CurrentUser.requirePermission(Permissions.MENU_DELETE);
        getById(id);
        List<Menu> subtree = MenuTree.subtree(menuRepository.findAll(), id);
        List<String> system = subtree.stream().map(Menu::getCode)
                .filter(AuthzSeeds.SYSTEM_MENU_CODES::contains).sorted().toList();
        if (!system.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Không xoá được menu hệ thống " + system);
        }
        Set<UUID> ids = subtree.stream().map(Menu::getId).collect(Collectors.toSet());
        for (Role role : roleRepository.findHavingAnyMenu(ids)) {
            role.getMenuIds().removeAll(ids);
        }
        for (Menu menu : subtree) {
            String prefix = menu.getCode() + Permissions.SEPARATOR;
            authorityService.revokeGrants(prefix + "%", grant -> grant.startsWith(prefix));
        }
        menuRepository.deleteAll(subtree);
    }

    private static void apply(Menu menu, String name, String url, String icon, Status status) {
        menu.setName(name.trim());
        menu.setUrl(AuthzChecks.trimToNull(url));
        menu.setIcon(AuthzChecks.trimToNull(icon));
        if (status != null) {
            menu.setActive(status == Status.ACTIVE);
        }
    }

    private int nextOrderIndex(UUID parentId) {
        return menuRepository.findAll().stream()
                .filter(m -> parentId == null ? m.getParentId() == null : parentId.equals(m.getParentId()))
                .mapToInt(Menu::getOrderIndex).max().orElse(0) + 1;
    }

    /** Menu cha mới phải tồn tại, không được là chính nó hay một menu con / cháu của nó. */
    private void requireNoCycle(UUID id, UUID parentId) {
        Set<UUID> seen = new HashSet<>();
        for (UUID current = parentId; current != null; current = getById(current).getParentId()) {
            if (current.equals(id)) {
                throw new AppException(ErrorCode.INVALID_ARGUMENT, "Menu cha không được là chính nó hoặc menu con");
            }
            if (!seen.add(current)) {
                break;
            }
        }
    }

    private Menu getById(UUID id) {
        return menuRepository.findById(id).orElseThrow(() -> AuthzChecks.notFound("Menu", id));
    }
}
