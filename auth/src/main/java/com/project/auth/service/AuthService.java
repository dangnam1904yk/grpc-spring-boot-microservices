package com.project.auth.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.auth.dto.AuthDtos.CreateUserRequest;
import com.project.auth.dto.AuthDtos.LoginRequest;
import com.project.auth.dto.AuthDtos.RegisterRequest;
import com.project.auth.dto.AuthDtos.TokenResponse;
import com.project.auth.dto.AuthDtos.UpdateUserRequest;
import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.entity.Role;
import com.project.auth.entity.UserAccount;
import com.project.auth.repository.RoleRepository;
import com.project.auth.repository.UserAccountRepository;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.AuthUser;
import com.project.common.security.CurrentUser;
import com.project.common.security.Permissions;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** So mật khẩu với hash giả khi email không tồn tại: thời gian phản hồi như nhau, không dò được email. */
    private final String dummyPasswordHash;

    public AuthService(UserAccountRepository userAccountRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserAccount register(RegisterRequest req) {
        return createUser(req.email(), req.password(), Set.of(Role.USER));
    }

    @Transactional
    public UserAccount createUser(String rawEmail, String rawPassword, Set<String> roles) {
        String email = normalizeEmail(rawEmail);
        if (userAccountRepository.existsByEmail(email)) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Email " + email + " đã được đăng ký");
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRoles(new HashSet<>(roles));
        try {
            // 2 request đăng ký cùng email đồng thời: request sau bị unique constraint chặn
            return userAccountRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "Email " + email + " đã được đăng ký");
        }
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        UserAccount user = userAccountRepository.findByEmail(normalizeEmail(req.email())).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(req.password(),
                user != null ? user.getPasswordHash() : dummyPasswordHash);
        // Cùng một thông báo cho "sai email" và "sai mật khẩu"
        if (user == null || !passwordMatches) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "Email hoặc mật khẩu không đúng");
        }
        if (!user.isEnabled()) {
            throw new AppException(ErrorCode.FORBIDDEN, "Tài khoản đã bị khoá");
        }
        return tokenService.issue(user);
    }

    @Transactional(readOnly = true)
    public UserAccount getUser(UUID id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User id=" + id + " không tồn tại"));
    }

    /** keyword: tìm theo email, họ tên, số điện thoại. */
    public Page<UserAccount> list(String keyword, Pageable pageable) {
        CurrentUser.requirePermission(Permissions.USER_READ);
        if (keyword == null || keyword.isBlank()) {
            return userAccountRepository.findAll(pageable);
        }
        return userAccountRepository.search("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%", pageable);
    }

    /** Tạo tài khoản từ màn hình quản trị (cần quyền user:ADD; gán role thì cần thêm user:EDIT). */
    @Transactional
    public UserAccount createByAdmin(CreateUserRequest req) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.USER_CREATE);
        Set<String> roles = Set.of(Role.USER);
        if (req.roles() != null && !req.roles().equals(roles)) {
            CurrentUser.requirePermission(Permissions.USER_UPDATE);
            roles = resolveRoles(req.roles(), Set.of(), actor);
        }
        UserAccount user = createUser(req.email(), req.password(), roles);
        user.setFullName(AuthzChecks.trimToNull(req.fullName()));
        user.setPhoneNumber(AuthzChecks.trimToNull(req.phoneNumber()));
        user.setEnabled(req.status() != Status.INACTIVE);
        return userAccountRepository.saveAndFlush(user);
    }

    /**
     * Sửa thông tin, khoá / mở khoá, đổi role (roles null = giữ nguyên). Đổi role hoặc khoá tài khoản thì
     * mọi phiên của user bị thu hồi.
     */
    @Transactional
    public UserAccount updateByAdmin(UUID id, UpdateUserRequest req) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.USER_UPDATE);
        UserAccount user = getUser(id);
        AuthzChecks.requireVersion(user.getVersion(), req.version(), "Tài khoản " + user.getEmail());
        boolean revoke = false;
        if (req.roles() != null) {
            Set<String> roles = resolveRoles(req.roles(), user.getRoles(), actor);
            revoke = !roles.equals(user.getRoles());
            user.setRoles(roles);
        }
        if (req.status() != null) {
            boolean enabled = req.status() == Status.ACTIVE;
            if (!enabled && user.getId().toString().equals(actor.id())) {
                throw new AppException(ErrorCode.INVALID_ARGUMENT, "Không tự khoá tài khoản của chính mình");
            }
            revoke |= user.isEnabled() && !enabled;
            user.setEnabled(enabled);
        }
        user.setFullName(AuthzChecks.trimToNull(req.fullName()));
        user.setPhoneNumber(AuthzChecks.trimToNull(req.phoneNumber()));
        UserAccount saved = userAccountRepository.saveAndFlush(user);
        if (revoke) {
            tokenService.revokeAll(id);
        }
        return saved;
    }

    /**
     * Đổi role (cần quyền user:EDIT). Quyền mới có hiệu lực từ access token kế tiếp; các phiên cũ bị thu hồi
     * để user phải lấy token mới ngay khi refresh.
     */
    @Transactional
    public UserAccount updateRoles(UUID id, Set<String> roles) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.USER_UPDATE);
        Set<String> newRoles = resolveRoles(roles, getUser(id).getRoles(), actor);
        // Thu hồi trước: bulk update xoá persistence context, nên phải tải lại user sau bước này
        tokenService.revokeAll(id);
        UserAccount user = getUser(id);
        user.setRoles(newRoles);
        return userAccountRepository.saveAndFlush(user);
    }

    /**
     * Kiểm tra role tồn tại, luôn giữ USER. Chỉ ADMIN được cấp / thu hồi role ADMIN: nếu không, ai có
     * user:EDIT cũng tự nâng mình thành ADMIN được.
     */
    private Set<String> resolveRoles(Set<String> requested, Set<String> current, AuthUser actor) {
        Set<String> known = roleRepository.findByCodeIn(requested).stream().map(Role::getCode)
                .collect(Collectors.toSet());
        AuthzChecks.requireKnown(requested, known, "Vai trò");
        Set<String> roles = new HashSet<>(requested);
        roles.add(Role.USER);
        if (!actor.isAdmin() && roles.contains(Role.ADMIN) != current.contains(Role.ADMIN)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ ADMIN được cấp / thu hồi vai trò ADMIN");
        }
        return roles;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
