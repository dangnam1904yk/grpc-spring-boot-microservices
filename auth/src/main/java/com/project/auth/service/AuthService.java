package com.project.auth.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthDtos.CreateUserRequest;
import com.project.auth.dto.AuthDtos.PageResponse;
import com.project.auth.dto.AuthDtos.UpdateUserRequest;
import com.project.auth.dto.AuthDtos.UserResponse;
import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.entity.Role;
import com.project.auth.entity.UserRole;
import com.project.auth.keycloak.KeycloakAdmin;
import com.project.auth.keycloak.KeycloakUser;
import com.project.auth.repository.RoleRepository;
import com.project.auth.repository.UserRoleRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

import lombok.RequiredArgsConstructor;

/**
 * Quản trị user. Tài khoản (email, mật khẩu, họ tên, trạng thái, phiên) nằm ở Keycloak, bản sao (không có mật
 * khẩu) ở bảng auth_user; role của user nằm ở bảng authz_user_role của auth-service (USER không lưu: mọi tài
 * khoản luôn có).
 * <p>
 * Đổi role có hiệu lực ngay ở mọi service (cache role + quyền trên Redis bị xoá sau khi commit), không cần đăng
 * xuất user.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KeycloakAdmin keycloak;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuthorityService authorityService;
    private final AuthorityChanges authorityChanges;
    private final UserAccountService accounts;

    @Transactional(readOnly = true)
    public UserResponse me() {
        AuthUser user = CurrentUser.require();
        return UserResponse.from(getUser(user.id()), user.roles());
    }

    public KeycloakUser getUser(String id) {
        return keycloak.findUser(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "user.not-found", id));
    }

    /** keyword: tìm theo email, họ tên (Keycloak không tìm theo số điện thoại). */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String keyword, int page, int size) {
        CurrentUser.requirePermission(Permissions.USER_READ);
        long total = keycloak.countUsers(keyword);
        List<KeycloakUser> users = keycloak.searchUsers(keyword, page * size, size);
        Map<String, Set<String>> roles = rolesOf(users.stream().map(KeycloakUser::id).toList());
        List<UserResponse> content = users.stream().map(u -> UserResponse.from(u, roles.get(u.id()))).toList();
        return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
    }

    /**
     * Tạo tài khoản từ màn hình quản trị (cần quyền user:ADD; gán role thì cần thêm user:EDIT). Tài khoản tạo
     * trên Keycloak trước; gán role lỗi thì tài khoản vẫn còn (chỉ có USER), gán lại từ màn hình user.
     */
    @Transactional
    public UserResponse createByAdmin(CreateUserRequest req) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.USER_CREATE);
        Set<String> roles = Set.of(Role.USER);
        if (req.roles() != null && !req.roles().equals(roles)) {
            CurrentUser.requirePermission(Permissions.USER_UPDATE);
            roles = resolveRoles(req.roles(), Set.of(Role.USER), actor);
        }
        String id = keycloak.createUser(new KeycloakUser(null, LoginService.normalizeEmail(req.email()),
                AuthzChecks.trimToNull(req.fullName()), AuthzChecks.trimToNull(req.phoneNumber()),
                req.status() != Status.INACTIVE, null), req.password());
        KeycloakUser created = getUser(id);
        accounts.sync(created);
        applyRoles(id, Set.of(Role.USER), roles);
        return UserResponse.from(created, roles);
    }

    /**
     * Sửa thông tin, khoá / mở khoá, đổi role (roles null = giữ nguyên). Khoá tài khoản thì mọi phiên Keycloak
     * của user bị kết thúc.
     */
    @Transactional
    public UserResponse updateByAdmin(String id, UpdateUserRequest req) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.USER_UPDATE);
        KeycloakUser user = getUser(id);
        Set<String> roles = authorityService.rolesOf(id);
        if (req.roles() != null) {
            Set<String> requested = resolveRoles(req.roles(), roles, actor);
            applyRoles(id, roles, requested);
            roles = requested;
        }
        boolean enabled = user.enabled();
        if (req.status() != null) {
            enabled = req.status() == Status.ACTIVE;
            if (!enabled && id.equals(actor.id())) {
                throw new AppException(ErrorCode.INVALID_ARGUMENT, "Không tự khoá tài khoản của chính mình");
            }
        }
        keycloak.updateUser(new KeycloakUser(id, user.email(), AuthzChecks.trimToNull(req.fullName()),
                AuthzChecks.trimToNull(req.phoneNumber()), enabled, user.createdAt()));
        if (user.enabled() && !enabled) {
            keycloak.logout(id);
        }
        KeycloakUser updated = getUser(id);
        accounts.sync(updated);
        return UserResponse.from(updated, roles);
    }

    /** Đổi role (cần quyền user:EDIT). Có hiệu lực ngay ở request kế tiếp của user. */
    @Transactional
    public UserResponse updateRoles(String id, Set<String> roles) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.USER_UPDATE);
        KeycloakUser user = getUser(id);
        Set<String> current = authorityService.rolesOf(id);
        Set<String> requested = resolveRoles(roles, current, actor);
        applyRoles(id, current, requested);
        return UserResponse.from(user, requested);
    }

    /** Role (gồm USER) của nhiều user một lần, cho danh sách user. */
    private Map<String, Set<String>> rolesOf(List<String> userIds) {
        Map<String, Set<String>> roles = new HashMap<>();
        userIds.forEach(id -> roles.put(id, new TreeSet<>(Set.of(Role.USER))));
        if (!userIds.isEmpty()) {
            for (UserRole r : userRoleRepository.findByUserIdIn(userIds)) {
                roles.get(r.getUserId()).add(r.getRole());
            }
        }
        return roles;
    }

    /** Ghi bảng authz_user_role cho khớp {@code roles} (USER không lưu). */
    private void applyRoles(String userId, Set<String> current, Set<String> roles) {
        Set<String> toAdd = new HashSet<>(roles);
        toAdd.removeAll(current);
        toAdd.remove(Role.USER);
        Set<String> toRemove = new HashSet<>(current);
        toRemove.removeAll(roles);
        toRemove.remove(Role.USER);
        if (!toRemove.isEmpty()) {
            userRoleRepository.deleteRoles(userId, toRemove);
        }
        toAdd.forEach(role -> userRoleRepository.save(new UserRole(userId, role)));
        if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
            userRoleRepository.flush();
            authorityChanges.usersChanged(List.of(userId));
        }
    }

    /**
     * Kiểm tra role tồn tại, luôn giữ USER. Chỉ ADMIN được cấp / thu hồi role ADMIN: nếu không, ai có
     * user:EDIT cũng tự nâng mình thành ADMIN được.
     */
    private Set<String> resolveRoles(Set<String> requested, Set<String> current, AuthUser actor) {
        Set<String> known = roleRepository.findByCodeIn(requested).stream().map(Role::getCode)
                .collect(Collectors.toSet());
        AuthzChecks.requireKnown(requested, known, "Vai trò");
        Set<String> roles = new TreeSet<>(requested);
        roles.add(Role.USER);
        if (!actor.isAdmin() && roles.contains(Role.ADMIN) != current.contains(Role.ADMIN)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ ADMIN được cấp / thu hồi vai trò ADMIN");
        }
        return roles;
    }

}
