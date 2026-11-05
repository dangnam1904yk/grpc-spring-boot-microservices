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
import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.dto.AuthzDtos.UpdateRoleRequest;
import com.project.auth.entity.Menu;
import com.project.auth.entity.Role;
import com.project.auth.entity.UserRole;
import com.project.auth.keycloak.KeycloakAdmin;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.RoleRepository;
import com.project.auth.repository.UserRoleRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Role: thông tin, trạng thái, menu hiển thị, quyền (ô của ma trận role × menu × thao tác) và user thuộc role.
 * <p>
 * Thành viên của role nằm ở bảng authz_user_role (USER: mọi tài khoản, không lưu).
 * <p>
 * Mọi thay đổi (quyền, trạng thái, thành viên) có hiệu lực ngay ở mọi service: cache role + quyền trên Redis bị
 * xoá sau khi commit ({@link AuthorityChanges}), không cần đăng xuất user.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final AuthorityService authorityService;
    private final UserRoleRepository userRoleRepository;
    private final AuthorityChanges authorityChanges;
    private final KeycloakAdmin keycloak;

    public List<Role> list() {
        CurrentUser.requirePermission(Permissions.ROLE_READ);
        return roleRepository.findAllByOrderByCodeAsc();
    }

    public Role get(UUID id) {
        CurrentUser.requirePermission(Permissions.ROLE_READ);
        return getById(id);
    }

    /** USER: số tài khoản trên Keycloak (mọi tài khoản đều có USER). */
    public long countUsers(Role role) {
        return Role.USER.equals(role.getCode()) ? keycloak.countUsers(null) : userRoleRepository.countByRole(role.getCode());
    }

    /** User được gán role (USER: rỗng, vì mọi tài khoản đều có). */
    public List<String> userIdsOf(Role role) {
        return memberIds(role).stream().sorted().toList();
    }

    @Transactional
    public Role create(CreateRoleRequest req) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.ROLE_CREATE);
        if (roleRepository.existsByCode(req.code())) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Mã vai trò " + req.code() + " đã tồn tại");
        }
        Role role = new Role();
        role.setCode(req.code());
        role.setName(req.name().trim());
        role.setDescription(AuthzChecks.trimToNull(req.description()));
        role.setDisabled(req.status() == Status.INACTIVE);
        setPermissions(role, orEmpty(req.permissions()));
        setMenus(role, orEmpty(req.menuIds()));
        Role saved;
        try {
            saved = roleRepository.saveAndFlush(role);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Mã vai trò " + req.code() + " đã tồn tại");
        }
        if (req.userIds() != null && !req.userIds().isEmpty()) {
            syncMembers(saved, req.userIds(), actor);
        }
        return saved;
    }

    /** Field null trong request = giữ nguyên. */
    @Transactional
    public Role update(UUID id, UpdateRoleRequest req) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.ROLE_UPDATE);
        Role role = getById(id);
        AuthzChecks.requireVersion(role.getVersion(), req.version(), "Vai trò " + role.getCode());
        role.setName(req.name().trim());
        role.setDescription(AuthzChecks.trimToNull(req.description()));
        if (req.status() != null) {
            setStatus(role, req.status());
        }
        if (req.permissions() != null) {
            setPermissions(role, req.permissions());
        }
        if (req.menuIds() != null) {
            setMenus(role, req.menuIds());
        }
        Role saved = roleRepository.saveAndFlush(role);
        authorityChanges.allChanged();
        if (req.userIds() != null) {
            syncMembers(saved, req.userIds(), actor);
        }
        return saved;
    }

    /** Lưu một hàng của ma trận phân quyền: mọi ô (menu, thao tác) được tích cho role. */
    @Transactional
    public Role updatePermissions(UUID id, Set<String> permissions, Long version) {
        CurrentUser.requirePermission(Permissions.ROLE_UPDATE);
        Role role = getById(id);
        AuthzChecks.requireVersion(role.getVersion(), version, "Vai trò " + role.getCode());
        setPermissions(role, permissions);
        authorityChanges.allChanged();
        return roleRepository.saveAndFlush(role);
    }

    @Transactional
    public Role updateMenus(UUID id, Set<UUID> menuIds, Long version) {
        CurrentUser.requirePermission(Permissions.ROLE_UPDATE);
        Role role = getById(id);
        AuthzChecks.requireVersion(role.getVersion(), version, "Vai trò " + role.getCode());
        setMenus(role, menuIds);
        return roleRepository.saveAndFlush(role);
    }

    /** Role hệ thống không xoá được. Role đang gán cho user thì gỡ khỏi các user đó. */
    @Transactional
    public void delete(UUID id) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.ROLE_DELETE);
        Role role = getById(id);
        if (role.isBuiltIn()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Không xoá được vai trò hệ thống " + role.getCode());
        }
        syncMembers(role, Set.of(), actor);
        roleRepository.delete(role);
    }

    private void setStatus(Role role, Status status) {
        if (status == Status.INACTIVE && role.isBuiltIn()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Không tắt được vai trò hệ thống " + role.getCode());
        }
        role.setDisabled(status == Status.INACTIVE);
    }

    private void setPermissions(Role role, Set<String> permissions) {
        AuthzChecks.requireKnown(permissions, authorityService.allPermissions(), "Quyền");
        role.setPermissions(new HashSet<>(permissions));
    }

    private void setMenus(Role role, Set<UUID> menuIds) {
        Set<UUID> known = menuRepository.findAllById(menuIds).stream().map(Menu::getId).collect(Collectors.toSet());
        AuthzChecks.requireKnown(menuIds, known, "Menu");
        role.setMenuIds(new HashSet<>(menuIds));
    }

    /**
     * Đưa danh sách user của role về đúng {@code userIds}. Cần thêm quyền user:EDIT (đây là đổi role của user);
     * chỉ ADMIN được đổi thành viên role ADMIN. Mọi user luôn có role USER nên không đổi được thành viên của USER.
     */
    private void syncMembers(Role role, Set<String> userIds, AuthUser actor) {
        Set<String> currentIds = memberIds(role);
        if (currentIds.equals(userIds)) {
            return;
        }
        if (Role.USER.equals(role.getCode())) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Mọi tài khoản luôn có vai trò USER");
        }
        CurrentUser.requirePermission(Permissions.USER_UPDATE);
        if (Role.ADMIN.equals(role.getCode()) && !actor.isAdmin()) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ ADMIN được cấp / thu hồi vai trò ADMIN");
        }
        Set<String> toAdd = new HashSet<>(userIds);
        toAdd.removeAll(currentIds);
        Set<String> found = toAdd.stream().filter(id -> keycloak.findUser(id).isPresent()).collect(Collectors.toSet());
        AuthzChecks.requireKnown(toAdd, found, "User");
        Set<String> toRemove = new HashSet<>(currentIds);
        toRemove.removeAll(userIds);
        Set<String> roleCode = Set.of(role.getCode());
        toRemove.forEach(id -> userRoleRepository.deleteRoles(id, roleCode));
        toAdd.forEach(id -> userRoleRepository.save(new UserRole(id, role.getCode())));
        userRoleRepository.flush();
        Set<String> changed = new HashSet<>(toAdd);
        changed.addAll(toRemove);
        authorityChanges.usersChanged(changed);
    }

    private Set<String> memberIds(Role role) {
        return userRoleRepository.findByRole(role.getCode()).stream().map(UserRole::getUserId)
                .collect(Collectors.toSet());
    }

    private static <T> Set<T> orEmpty(Set<T> values) {
        return values == null ? Set.of() : values;
    }

    private Role getById(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> AuthzChecks.notFound("Vai trò", id));
    }
}
