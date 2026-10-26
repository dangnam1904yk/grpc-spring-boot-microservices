package com.project.auth.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.CreateMenuRequest;
import com.project.auth.dto.AuthzDtos.MenuNode;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;

    /** Toàn bộ cây menu, kể cả menu đang tắt (màn hình quản trị). */
    public List<MenuNode> tree() {
        CurrentUser.requirePermission(Permissions.MENU_READ);
        return MenuTree.build(menuRepository.findAllByOrderBySortOrderAscCodeAsc(), menu -> true);
    }

    public Menu get(UUID id) {
        CurrentUser.requirePermission(Permissions.MENU_READ);
        return getById(id);
    }

    @Transactional
    public Menu create(CreateMenuRequest req) {
        CurrentUser.requirePermission(Permissions.MENU_CREATE);
        if (menuRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Menu code=" + req.code() + " đã tồn tại");
        }
        if (req.parentId() != null) {
            getById(req.parentId());
        }
        Menu menu = new Menu();
        menu.setCode(req.code());
        menu.setName(req.name().trim());
        menu.setPath(AuthzChecks.trimToNull(req.path()));
        menu.setIcon(AuthzChecks.trimToNull(req.icon()));
        menu.setParentId(req.parentId());
        menu.setSortOrder(req.sortOrder());
        menu.setActive(req.active());
        try {
            return menuRepository.saveAndFlush(menu);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Menu code=" + req.code() + " đã tồn tại");
        }
    }

    @Transactional
    public Menu update(UUID id, UpdateMenuRequest req) {
        CurrentUser.requirePermission(Permissions.MENU_UPDATE);
        Menu menu = getById(id);
        AuthzChecks.requireVersion(menu.getVersion(), req.version(), "Menu");
        requireNoCycle(id, req.parentId());

        menu.setName(req.name().trim());
        menu.setPath(AuthzChecks.trimToNull(req.path()));
        menu.setIcon(AuthzChecks.trimToNull(req.icon()));
        menu.setParentId(req.parentId());
        menu.setSortOrder(req.sortOrder());
        menu.setActive(req.active());
        return menuRepository.saveAndFlush(menu);
    }

    /** Phải xoá / chuyển menu con trước. Menu bị xoá cũng được gỡ khỏi mọi role. */
    @Transactional
    public void delete(UUID id) {
        CurrentUser.requirePermission(Permissions.MENU_DELETE);
        Menu menu = getById(id);
        if (menuRepository.existsByParentId(id)) {
            throw new AppException(ErrorCode.IN_USE, "Menu " + menu.getCode() + " còn menu con");
        }
        for (Role role : roleRepository.findHavingMenu(id)) {
            role.getMenuIds().remove(id);
        }
        menuRepository.delete(menu);
    }

    /** Menu cha mới không được là chính nó hay một menu con / cháu của nó. */
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
