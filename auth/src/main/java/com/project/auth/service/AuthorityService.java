package com.project.auth.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.MenuNode;
import com.project.auth.dto.AuthzDtos.MyAuthoritiesResponse;
import com.project.auth.entity.Menu;
import com.project.auth.entity.Permission;
import com.project.auth.entity.Role;
import com.project.auth.entity.UserAccount;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.PermissionRepository;
import com.project.auth.repository.RoleRepository;
import com.project.auth.repository.UserAccountRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.id.Ids;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Quyền và menu thực tế của user = hợp các role đang bật của user. Quyền chỉ tính thao tác đang bật.
 * ADMIN có mọi quyền và thấy mọi menu đang bật.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorityService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final UserAccountRepository userAccountRepository;

    /** Mọi ô của ma trận: mỗi cặp (menu, thao tác đang bật). Đây cũng là tập quyền hợp lệ để cấp cho role. */
    public Set<String> allPermissions() {
        List<String> actions = permissionRepository.findAll().stream()
                .filter(p -> !p.isDisabled()).map(Permission::getCode).toList();
        Set<String> all = new TreeSet<>();
        for (Menu menu : menuRepository.findAll()) {
            actions.forEach(action -> all.add(Permissions.of(menu.getCode(), action)));
        }
        return all;
    }

    /** Đưa vào claim "permissions" của access token. */
    public Set<String> permissionsOf(Collection<String> roleCodes) {
        if (roleCodes.contains(Role.ADMIN)) {
            return allPermissions();
        }
        Set<String> valid = allPermissions();
        return activeRoles(roleCodes).stream()
                .flatMap(role -> role.getPermissions().stream())
                .filter(valid::contains)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Lấy từ DB (không phải từ token) nên thấy ngay quyền / menu vừa được đổi. */
    public MyAuthoritiesResponse myAuthorities() {
        UUID userId = Ids.parse(CurrentUser.require().id(), "sub");
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User id=" + userId + " không tồn tại"));
        Set<String> roles = user.getRoles();
        return new MyAuthoritiesResponse(roles.stream().sorted().toList(), List.copyOf(permissionsOf(roles)),
                menusOf(roles));
    }

    /**
     * Gỡ các quyền khớp {@code exact} khỏi mọi role (khi xoá menu / thao tác). {@code likePattern} chỉ để lọc
     * thô trong DB, có thể khớp rộng hơn.
     */
    @Transactional
    public void revokeGrants(String likePattern, Predicate<String> exact) {
        for (Role role : roleRepository.findHavingPermissionLike(likePattern)) {
            role.getPermissions().removeIf(exact);
        }
    }

    private List<Role> activeRoles(Collection<String> roleCodes) {
        return roleRepository.findByCodeIn(roleCodes).stream().filter(r -> !r.isDisabled()).toList();
    }

    /**
     * Menu được gán cho role kèm mọi menu cha của nó (để cây không bị đứt). Menu đang tắt thì bị ẩn
     * cùng toàn bộ menu con.
     */
    private List<MenuNode> menusOf(Collection<String> roleCodes) {
        List<Menu> menus = menuRepository.findAllByOrderByOrderIndexAscCodeAsc();
        if (roleCodes.contains(Role.ADMIN)) {
            return MenuTree.build(menus, Menu::isActive);
        }
        Map<UUID, Menu> byId = menus.stream().collect(Collectors.toMap(Menu::getId, Function.identity()));
        Set<UUID> visible = new HashSet<>();
        for (Role role : activeRoles(roleCodes)) {
            for (UUID menuId : role.getMenuIds()) {
                // Thêm menu và các menu cha; gặp menu đã thêm thì các cấp trên cũng đã có
                Menu m = byId.get(menuId);
                while (m != null && visible.add(m.getId())) {
                    m = m.getParentId() == null ? null : byId.get(m.getParentId());
                }
            }
        }
        return MenuTree.build(menus, m -> m.isActive() && visible.contains(m.getId()));
    }
}
