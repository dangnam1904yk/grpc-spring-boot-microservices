package com.project.auth.keycloak;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.project.auth.config.AuthProperties;
import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link KeycloakAdmin} qua Admin REST API (không dùng keycloak-admin-client để tránh kéo RESTEasy vào
 * classpath). Access token của service account được cache tới gần lúc hết hạn. Không động tới realm role: role
 * của user do auth-service quản lý (bảng authz_user_role).
 */
@Slf4j
@Component
public class RestKeycloakAdmin implements KeycloakAdmin {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST =
            new ParameterizedTypeReference<>() {
            };
    static final String FULL_NAME = "fullName";
    static final String PHONE_NUMBER = "phoneNumber";

    private final AuthProperties.Keycloak properties;
    private final RestClient http;
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.MIN;

    public RestKeycloakAdmin(AuthProperties.Keycloak properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        this.http = builder.baseUrl(properties.serverUrl() == null ? "" : properties.serverUrl())
                .requestFactory(requestFactory).build();
    }

    @Override
    public List<KeycloakUser> searchUsers(String search, int first, int max) {
        return call(() -> get(b -> withSearch(b.path(usersPath()), search)
                .queryParam("first", first).queryParam("max", max).queryParam("briefRepresentation", false)
                .build(properties.realm()), LIST).stream().map(RestKeycloakAdmin::toUser).toList());
    }

    @Override
    public long countUsers(String search) {
        return call(() -> {
            Long count = http.get().uri(b -> withSearch(b.path(usersPath() + "/count"), search)
                    .build(properties.realm())).header(HttpHeaders.AUTHORIZATION, bearer())
                    .retrieve().body(Long.class);
            return count == null ? 0 : count;
        });
    }

    @Override
    public Optional<KeycloakUser> findUser(String id) {
        return call(() -> {
            try {
                return Optional.of(toUser(get(b -> b.path(usersPath() + "/{id}").build(properties.realm(), id), MAP)));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public String createUser(KeycloakUser user, String password) {
        Map<String, Object> rep = new HashMap<>();
        rep.put("username", user.email());
        rep.put("email", user.email());
        rep.put("emailVerified", true);
        rep.put("enabled", user.enabled());
        rep.put("attributes", attributes(new HashMap<>(), user));
        rep.put("credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        return call(() -> {
            try {
                URI location = http.post().uri(b -> b.path(usersPath()).build(properties.realm()))
                        .header(HttpHeaders.AUTHORIZATION, bearer()).contentType(MediaType.APPLICATION_JSON)
                        .body(rep).retrieve().toBodilessEntity().getHeaders().getLocation();
                String path = location == null ? "" : location.getPath();
                return path.substring(path.lastIndexOf('/') + 1);
            } catch (HttpClientErrorException.Conflict e) {
                throw new AppException(ErrorCode.ALREADY_EXISTS, "user.email-exists", user.email());
            } catch (HttpClientErrorException.BadRequest e) {
                // Vd. mật khẩu không đạt password policy của realm
                throw new AppException(ErrorCode.INVALID_ARGUMENT, errorMessage(e));
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void updateUser(KeycloakUser user) {
        call(() -> {
            Map<String, Object> rep = get(b -> b.path(usersPath() + "/{id}").build(properties.realm(), user.id()), MAP);
            Map<String, Object> attributes = rep.get("attributes") instanceof Map<?, ?> m
                    ? new HashMap<>((Map<String, Object>) m) : new HashMap<>();
            rep.put("attributes", attributes(attributes, user));
            rep.put("enabled", user.enabled());
            try {
                send(HttpMethod.PUT, b -> b.path(usersPath() + "/{id}").build(properties.realm(), user.id()), rep);
            } catch (HttpClientErrorException.BadRequest e) {
                throw new AppException(ErrorCode.INVALID_ARGUMENT, errorMessage(e));
            }
            return null;
        });
    }

    @Override
    public void logout(String userId) {
        call(() -> {
            send(HttpMethod.POST, b -> b.path(usersPath() + "/{id}/logout").build(properties.realm(), userId), null);
            return null;
        });
    }

    @Override
    public void deleteUser(String id) {
        call(() -> {
            try {
                send(HttpMethod.DELETE, b -> b.path(usersPath() + "/{id}").build(properties.realm(), id), null);
            } catch (HttpClientErrorException.NotFound e) {
                // Đã không còn
            }
            return null;
        });
    }

    @Override
    public KeycloakTokens login(String email, String password) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "password");
        form.add("username", email);
        form.add("password", password);
        try {
            return toTokens(tokenRequest(form));
        } catch (HttpClientErrorException e) {
            String description = String.valueOf(oauthError(e).get("error_description")).toLowerCase(Locale.ROOT);
            if (isInvalidGrant(e) && description.contains("disabled")) {
                throw new AppException(ErrorCode.FORBIDDEN, "auth.account-locked");
            }
            if (isInvalidGrant(e) && description.contains("not fully set up")) {
                throw new AppException(ErrorCode.FORBIDDEN, "auth.account-not-ready");
            }
            if (isInvalidGrant(e)) {
                // Gồm cả trường hợp bị khoá tạm do nhập sai nhiều lần (brute force protection): Keycloak không
                // phân biệt để không lộ thông tin
                throw new AppException(ErrorCode.UNAUTHENTICATED, "auth.invalid-credentials");
            }
            throw unexpected(e);
        }
    }

    @Override
    public KeycloakTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            return toTokens(tokenRequest(form));
        } catch (HttpClientErrorException e) {
            if (isInvalidGrant(e)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED, "auth.session-expired");
            }
            throw unexpected(e);
        }
    }

    @Override
    public void endSession(String refreshToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("refresh_token", refreshToken);
        try {
            http.post().uri("/realms/{realm}/protocol/openid-connect/logout", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // Token sai / phiên đã kết thúc: coi như đã đăng xuất
            log.debug("Keycloak từ chối đăng xuất: {}", e.getMessage());
        } catch (RestClientException e) {
            log.warn("Lỗi gọi Keycloak logout: {}", e.getMessage());
            throw new AppException(ErrorCode.UNAVAILABLE, "keycloak.unavailable");
        }
    }

    private MultiValueMap<String, String> clientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    /** Lỗi 4xx ném nguyên {@link HttpClientErrorException} cho nơi gọi xử lý; lỗi kết nối / 5xx -> 503. */
    private Map<String, Object> tokenRequest(MultiValueMap<String, String> form) {
        try {
            Map<String, Object> token = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(MAP);
            if (token == null) {
                throw new RestClientException("Keycloak trả về body rỗng ở token endpoint");
            }
            return token;
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("Lỗi gọi Keycloak token endpoint: {}", e.getMessage());
            throw new AppException(ErrorCode.UNAVAILABLE, "keycloak.unavailable");
        }
    }

    private static KeycloakTokens toTokens(Map<String, Object> token) {
        if (!(token.get("access_token") instanceof String access) || !(token.get("refresh_token") instanceof String refresh)) {
            log.warn("Keycloak không trả về access_token / refresh_token");
            throw new AppException(ErrorCode.UNAVAILABLE, "keycloak.unavailable");
        }
        long expiresIn = token.get("expires_in") instanceof Number n ? n.longValue() : 0;
        long refreshExpiresIn = token.get("refresh_expires_in") instanceof Number n ? n.longValue() : 0;
        return new KeycloakTokens(access, refresh, expiresIn, refreshExpiresIn);
    }

    /** Keycloak trả 401 (sai mật khẩu) hoặc 400 (tài khoản khoá, token hết hạn) kèm error=invalid_grant. */
    private static boolean isInvalidGrant(HttpClientErrorException e) {
        return "invalid_grant".equals(oauthError(e).get("error"));
    }

    private static Map<String, Object> oauthError(HttpClientErrorException e) {
        try {
            Map<String, Object> body = e.getResponseBodyAs(MAP);
            return body == null ? Map.of() : body;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    /** Vd. client chưa bật "Direct access grants" hoặc sai client secret: lỗi cấu hình, không phải lỗi của user. */
    private AppException unexpected(HttpClientErrorException e) {
        log.error("Keycloak từ chối yêu cầu token ({}): {}. Kiểm tra client {} (Direct access grants, secret)",
                e.getStatusCode(), e.getResponseBodyAsString(), properties.clientId());
        return new AppException(ErrorCode.UNAVAILABLE, "keycloak.unavailable");
    }

    private <T> T get(Function<UriBuilder, URI> uri, ParameterizedTypeReference<T> type) {
        return http.get().uri(uri).header(HttpHeaders.AUTHORIZATION, bearer()).retrieve().body(type);
    }

    private void send(HttpMethod method, Function<UriBuilder, URI> uri, Object body) {
        RestClient.RequestBodySpec spec = http.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, bearer());
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        spec.retrieve().toBodilessEntity();
    }

    /**
     * Lỗi Keycloak còn sót lại (không được nơi gọi xử lý) -> 503. Gặp 401 thì có thể token service account vừa
     * bị thu hồi: lấy token mới và thử lại một lần.
     */
    private <T> T call(Supplier<T> action) {
        try {
            try {
                return action.get();
            } catch (HttpClientErrorException.Unauthorized e) {
                clearToken();
                return action.get();
            }
        } catch (RestClientException e) {
            log.warn("Lỗi gọi Keycloak Admin API: {}", e.getMessage());
            throw new AppException(ErrorCode.UNAVAILABLE, "keycloak.unavailable");
        }
    }

    private synchronized String bearer() {
        Instant now = Instant.now();
        if (accessToken == null || now.isAfter(accessTokenExpiresAt)) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            Map<String, Object> token = http.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(MAP);
            if (token == null || !(token.get("access_token") instanceof String value)) {
                throw new RestClientException("Keycloak không trả về access_token cho client " + properties.clientId());
            }
            accessToken = value;
            long expiresIn = token.get("expires_in") instanceof Number n ? n.longValue() : 60;
            accessTokenExpiresAt = now.plusSeconds(Math.max(expiresIn - 30, 1));
        }
        return "Bearer " + accessToken;
    }

    private synchronized void clearToken() {
        accessToken = null;
    }

    private static String usersPath() {
        return "/admin/realms/{realm}/users";
    }

    private static UriBuilder withSearch(UriBuilder builder, String search) {
        return StringUtils.hasText(search) ? builder.queryParam("search", search.trim()) : builder;
    }

    private static Map<String, Object> attributes(Map<String, Object> attributes, KeycloakUser user) {
        putOrRemove(attributes, FULL_NAME, user.fullName());
        putOrRemove(attributes, PHONE_NUMBER, user.phoneNumber());
        return attributes;
    }

    private static void putOrRemove(Map<String, Object> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.put(name, List.of(value));
        } else {
            attributes.remove(name);
        }
    }

    @SuppressWarnings("unchecked")
    private static KeycloakUser toUser(Map<String, Object> rep) {
        Map<String, List<String>> attributes = rep.get("attributes") instanceof Map<?, ?> m
                ? (Map<String, List<String>>) m : Map.of();
        Instant createdAt = rep.get("createdTimestamp") instanceof Number n ? Instant.ofEpochMilli(n.longValue()) : null;
        return new KeycloakUser((String) rep.get("id"), (String) rep.get("email"), first(attributes, FULL_NAME),
                first(attributes, PHONE_NUMBER), Boolean.TRUE.equals(rep.get("enabled")), createdAt);
    }

    private static String first(Map<String, List<String>> attributes, String name) {
        List<String> values = attributes.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /** Keycloak trả lỗi dạng {"errorMessage": ...} hoặc {"error": ..., "error_description": ...}. */
    private static String errorMessage(HttpClientErrorException e) {
        try {
            Map<String, Object> body = e.getResponseBodyAs(MAP);
            if (body != null) {
                for (String key : List.of("errorMessage", "error_description", "error")) {
                    if (body.get(key) instanceof String message && !message.isBlank()) {
                        return message;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Body không phải JSON
        }
        return "error.invalid";
    }
}
