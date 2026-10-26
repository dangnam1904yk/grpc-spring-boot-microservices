package com.project.auth.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.MenuNode;
import com.project.auth.dto.AuthzDtos.MyAuthoritiesResponse;
import com.project.auth.entity.Menu;
import com.project.auth.entity.Role;
import com.project.auth.entity.UserAccount;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.ResourceRepository;
import com.project.auth.repository.RoleRepository;
import com.project.auth.repository.UserAccountRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.id.Ids;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Quyền và menu thực tế của user = hợp các role của user. ADMIN có mọi quyền và thấy mọi menu đang bật.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorityService {

    private final RoleRepository roleRepository;
    private final ResourceRepository resourceRepository;
    private final MenuRepository menuRepository;
    private final UserAccountRepository userAccountRepository;

    /** Mọi quyền đang có: mỗi cặp (resource, action). */
    public Set<String> allPermissions() {
        return resourceRepository.findAll().stream()
                .flatMap(r -> r.getActions().stream().map(a -> Permissions.of(r.getCode(), a)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Đưa vào claim "permissions" của access token. */
    public Set<String> permissionsOf(Collection<String> roleCodes) {
        if (roleCodes.contains(Role.ADMIN)) {
            return allPermissions();
        }
        return roleRepository.findByCodeIn(roleCodes).stream()
                .flatMap(role -> role.getPermissions().stream())
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
     * Menu được gán cho role kèm mọi menu cha của nó (để cây không bị đứt). Menu đang tắt thì bị ẩn
     * cùng toàn bộ menu con.
     */
    private List<MenuNode> menusOf(Collection<String> roleCodes) {
        List<Menu> menus = menuRepository.findAllByOrderBySortOrderAscCodeAsc();
        if (roleCodes.contains(Role.ADMIN)) {
            return MenuTree.build(menus, Menu::isActive);
        }
        Map<UUID, Menu> byId = menus.stream().collect(Collectors.toMap(Menu::getId, Function.identity()));
        Set<UUID> visible = new HashSet<>();
        for (Role role : roleRepository.findByCodeIn(roleCodes)) {
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
