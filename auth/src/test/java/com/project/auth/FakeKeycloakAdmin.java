package com.project.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.project.auth.keycloak.KeycloakAdmin;
import com.project.auth.keycloak.KeycloakTokens;
import com.project.auth.keycloak.KeycloakUser;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.security.test.TestJwt;

/** Keycloak giả trong bộ nhớ: chỉ giữ tài khoản (role của user nằm ở DB của auth-service). */
public class FakeKeycloakAdmin implements KeycloakAdmin {

    private final Map<String, KeycloakUser> users = new LinkedHashMap<>();
    private final Map<String, Integer> logouts = new ConcurrentHashMap<>();
    private final Map<String, String> passwords = new ConcurrentHashMap<>();
    /** refresh token -> user id; mỗi refresh token chỉ dùng được 1 lần, như realm bật revoke refresh token. */
    private final Map<String, String> refreshTokens = new ConcurrentHashMap<>();

    public int logoutCount(String userId) {
        return logouts.getOrDefault(userId, 0);
    }

    @Override
    public synchronized List<KeycloakUser> searchUsers(String search, int first, int max) {
        return matching(search).stream().skip(first).limit(max).toList();
    }

    @Override
    public synchronized long countUsers(String search) {
        return matching(search).size();
    }

    @Override
    public synchronized Optional<KeycloakUser> findUser(String id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public synchronized String createUser(KeycloakUser user, String password) {
        if (users.values().stream().anyMatch(u -> u.email().equalsIgnoreCase(user.email()))) {
            throw new AppException(ErrorCode.ALREADY_EXISTS, "user.email-exists", user.email());
        }
        String id = UUID.randomUUID().toString();
        users.put(id, new KeycloakUser(id, user.email(), user.fullName(), user.phoneNumber(), user.enabled(),
                Instant.now()));
        passwords.put(id, password);
        return id;
    }

    @Override
    public synchronized void updateUser(KeycloakUser user) {
        KeycloakUser old = users.get(user.id());
        users.put(user.id(), new KeycloakUser(user.id(), old.email(), user.fullName(), user.phoneNumber(),
                user.enabled(), old.createdAt()));
    }

    @Override
    public void logout(String userId) {
        logouts.merge(userId, 1, Integer::sum);
    }

    @Override
    public synchronized void deleteUser(String id) {
        users.remove(id);
        passwords.remove(id);
    }

    @Override
    public synchronized KeycloakTokens login(String email, String password) {
        KeycloakUser user = users.values().stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst()
                .filter(u -> password.equals(passwords.get(u.id())))
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED, "auth.invalid-credentials"));
        if (!user.enabled()) {
            throw new AppException(ErrorCode.FORBIDDEN, "auth.account-locked");
        }
        return issue(user);
    }

    @Override
    public synchronized KeycloakTokens refresh(String refreshToken) {
        String userId = refreshTokens.remove(refreshToken);
        KeycloakUser user = userId == null ? null : users.get(userId);
        if (user == null || !user.enabled()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED, "auth.session-expired");
        }
        return issue(user);
    }

    @Override
    public void endSession(String refreshToken) {
        refreshTokens.remove(refreshToken);
    }

    private KeycloakTokens issue(KeycloakUser user) {
        String refreshToken = UUID.randomUUID().toString();
        refreshTokens.put(refreshToken, user.id());
        return new KeycloakTokens(TestJwt.token(user.id(), user.email(), List.of(), List.of()), refreshToken, 900,
                604800);
    }

    private List<KeycloakUser> matching(String search) {
        String keyword = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return new ArrayList<>(users.values()).stream()
                .filter(u -> keyword.isEmpty() || u.email().contains(keyword)
                        || (u.fullName() != null && u.fullName().toLowerCase(Locale.ROOT).contains(keyword)))
                .sorted(Comparator.comparing(KeycloakUser::createdAt).reversed())
                .toList();
    }
}
