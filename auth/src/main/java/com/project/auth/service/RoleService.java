package com.project.auth.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthzDtos.CreateRoleRequest;
import com.project.auth.dto.AuthzDtos.UpdateRoleRequest;
import com.project.auth.entity.Menu;
import com.project.auth.entity.Role;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.RoleRepository;
import com.project.auth.repository.UserAccountRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Role, quyền và menu của role. Đổi quyền của role có hiệu lực từ access token kế tiếp của user
 * (đăng nhập lại hoặc refresh, tối đa sau {@code app.jwt.access-token-ttl}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthorityService authorityService;

    public List<Role> list() {
        CurrentUser.requirePermission(Permissions.ROLE_READ);
        return roleRepository.findAllByOrderByCodeAsc();
    }

    public Role get(UUID id) {
        CurrentUser.requirePermission(Permissions.ROLE_READ);
        return getById(id);
    }

    @Transactional
    public Role create(CreateRoleRequest req) {
        CurrentUser.requirePermission(Permissions.ROLE_CREATE);
        if (roleRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Role code=" + req.code() + " đã tồn tại");
        }
        requireKnown(req.permissions(), req.menuIds());
        Role role = new Role();
        role.setCode(req.code());
        role.setName(req.name().trim());
        role.setDescription(AuthzChecks.trimToNull(req.description()));
        role.setPermissions(new HashSet<>(req.permissions()));
        role.setMenuIds(new HashSet<>(req.menuIds()));
        try {
            return roleRepository.saveAndFlush(role);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Role code=" + req.code() + " đã tồn tại");
        }
    }

    @Transactional
    public Role update(UUID id, UpdateRoleRequest req) {
        CurrentUser.requirePermission(Permissions.ROLE_UPDATE);
        Role role = getById(id);
        AuthzChecks.requireVersion(role.getVersion(), req.version(), "Role");
        requireKnown(req.permissions(), req.menuIds());
        role.setName(req.name().trim());
        role.setDescription(AuthzChecks.trimToNull(req.description()));
        role.setPermissions(new HashSet<>(req.permissions()));
        role.setMenuIds(new HashSet<>(req.menuIds()));
        return roleRepository.saveAndFlush(role);
    }

    /** Không xoá được role hệ thống, hay role đang gán cho user (phải gỡ khỏi user trước). */
    @Transactional
    public void delete(UUID id) {
        CurrentUser.requirePermission(Permissions.ROLE_DELETE);
        Role role = getById(id);
        if (role.isBuiltIn()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Không xoá được role hệ thống " + role.getCode());
        }
        long users = userAccountRepository.countByRole(role.getCode());
        if (users > 0) {
            throw new AppException(ErrorCode.IN_USE,
                    "Role " + role.getCode() + " đang được gán cho " + users + " user");
        }
        roleRepository.delete(role);
    }

    private void requireKnown(Set<String> permissions, Set<UUID> menuIds) {
        AuthzChecks.requireKnown(permissions, authorityService.allPermissions(), "Quyền");
        Set<UUID> knownMenus = menuRepository.findAllById(menuIds).stream().map(Menu::getId)
                .collect(Collectors.toSet());
        AuthzChecks.requireKnown(menuIds, knownMenus, "Menu");
    }

    private Role getById(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> AuthzChecks.notFound("Role", id));
    }
}
