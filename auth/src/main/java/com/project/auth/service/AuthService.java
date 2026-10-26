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

import com.project.auth.dto.AuthDtos.LoginRequest;
import com.project.auth.dto.AuthDtos.RegisterRequest;
import com.project.auth.dto.AuthDtos.TokenResponse;
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

    public Page<UserAccount> list(Pageable pageable) {
        CurrentUser.requirePermission(Permissions.USER_READ);
        return userAccountRepository.findAll(pageable);
    }

    /**
     * Đổi role (cần quyền user:update). Quyền mới có hiệu lực từ access token kế tiếp; các phiên cũ bị thu hồi
     * để user phải lấy token mới ngay khi refresh.
     * <p>
     * Chỉ ADMIN được cấp / thu hồi role ADMIN: nếu không, ai có user:update cũng tự nâng mình thành ADMIN được.
     */
    @Transactional
    public UserAccount updateRoles(UUID id, Set<String> roles) {
        AuthUser actor = CurrentUser.requirePermission(Permissions.USER_UPDATE);
        Set<String> known = roleRepository.findByCodeIn(roles).stream().map(Role::getCode)
                .collect(Collectors.toSet());
        Set<String> unknown = new HashSet<>(roles);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, "Role không tồn tại: " + unknown);
        }
        Set<String> newRoles = new HashSet<>(roles);
        newRoles.add(Role.USER);
        if (!actor.isAdmin() && newRoles.contains(Role.ADMIN) != getUser(id).getRoles().contains(Role.ADMIN)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Chỉ ADMIN được cấp / thu hồi role ADMIN");
        }
        // Thu hồi trước: bulk update xoá persistence context, nên phải tải lại user sau bước này
        tokenService.revokeAll(id);
        UserAccount user = getUser(id);
        user.setRoles(newRoles);
        return userAccountRepository.saveAndFlush(user);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
