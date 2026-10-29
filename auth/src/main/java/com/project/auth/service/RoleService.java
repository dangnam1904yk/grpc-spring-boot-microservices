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
import com.project.auth.entity.UserAccount;
import com.project.auth.repository.MenuRepository;
import com.project.auth.repository.RoleRepository;
import com.project.auth.repository.UserAccountRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Role: thông tin, trạng thái, menu hiển thị, quyền (ô của ma trận role × menu × thao tác) và user thuộc role.
 * <p>
 * Đổi quyền / menu / trạng thái của role có hiệu lực từ access token kế tiếp của user (refresh hoặc đăng nhập lại,
 * tối đa sau {@code app.jwt.access-token-ttl}). Đổi danh sách user của role thì phiên của các user bị ảnh hưởng
 * bị thu hồi ngay, giống đổi role từ màn hình user.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthorityService authorityService;
    private final TokenService tokenService;

    public List<Role> list() {
        CurrentUser.requirePermission(Permissions.ROLE_READ);
        return roleRepository.findAllByOrderByCodeAsc();
    }

    public Role get(UUID id) {
        CurrentUser.requirePermission(Permissions.ROLE_READ);
        return getById(id);
    }

    public long countUsers(Role role) {
        return userAccountRepository.countByRole(role.getCode());
    }

    public List<UUID> userIdsOf(Role role) {
        return userAccountRepository.findByRole(role.getCode()).stream().map(UserAccount::getId).sorted().toList();
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

    /** Role hệ thống không xoá được. Role đang gán cho user thì gỡ khỏi các user đó (thu hồi phiên của họ). */
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
    private void syncMembers(Role role, Set<UUID> userIds, AuthUser actor) {
        List<UserAccount> current = userAccountRepository.findByRole(role.getCode());
        Set<UUID> currentIds = current.stream().map(UserAccount::getId).collect(Collectors.toSet());
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
        Set<UserAccount> changed = new HashSet<>();
        for (UserAccount user : current) {
            if (!userIds.contains(user.getId())) {
                user.getRoles().remove(role.getCode());
                changed.add(user);
            }
        }
        Set<UUID> toAdd = new HashSet<>(userIds);
        toAdd.removeAll(currentIds);
        List<UserAccount> added = userAccountRepository.findAllById(toAdd);
        AuthzChecks.requireKnown(toAdd, added.stream().map(UserAccount::getId).collect(Collectors.toSet()), "User");
        for (UserAccount user : added) {
            user.getRoles().add(role.getCode());
            changed.add(user);
        }
        // Lưu trước rồi mới thu hồi phiên: lệnh thu hồi (bulk update) xoá persistence context sau khi flush
        userAccountRepository.saveAllAndFlush(changed);
        changed.forEach(user -> tokenService.revokeAll(user.getId()));
    }

    private static <T> Set<T> orEmpty(Set<T> values) {
        return values == null ? Set.of() : values;
    }

    private Role getById(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> AuthzChecks.notFound("Vai trò", id));
    }
}
