package com.project.common.audit;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.util.ClassUtils;

import com.project.common.security.CurrentUser;

/**
 * Thông tin của request đang xử lý, giữ trong MDC (nên cũng hiện được trong log thường qua %X{requestId}).
 * <p>
 * MDC gắn với thread. Để không mất khi chuyển thread: {@code MdcTaskDecorator} copy sang
 * applicationTaskExecutor, {@code GrpcFutures} copy sang callback gRPC, gRPC client gửi kèm sang service khác.
 */
public final class AuditContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_CLIENT_IP = "clientIp";
    public static final String MDC_USER_ID = "userId";
    public static final String MDC_USER_EMAIL = "userEmail";

    /** Không nhận request id tuỳ ý từ client: tránh chèn ký tự lạ vào log. */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private static final boolean SECURITY_PRESENT = ClassUtils.isPresent(
            "org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken",
            AuditContext.class.getClassLoader());

    private AuditContext() {
    }

    public record User(String id, String email) {
    }

    /** Dùng lại request id nhận được (từ gateway / service gọi) nếu hợp lệ, không thì tạo mới. */
    public static String requestIdOrNew(String candidate) {
        return candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }

    public static String requestId() {
        return MDC.get(MDC_REQUEST_ID);
    }

    public static String clientIp() {
        return MDC.get(MDC_CLIENT_IP);
    }

    /** User trong SecurityContext (cùng thread với request) hoặc trong MDC (thread khác). */
    public static User currentUser() {
        if (SECURITY_PRESENT) {
            User user = CurrentUser.current().map(u -> new User(u.id(), u.email())).orElse(null);
            if (user != null) {
                return user;
            }
        }
        String id = MDC.get(MDC_USER_ID);
        return id == null ? null : new User(id, MDC.get(MDC_USER_EMAIL));
    }

    public static void bind(String requestId, String clientIp, User user) {
        putIfNotNull(MDC_REQUEST_ID, requestId);
        putIfNotNull(MDC_CLIENT_IP, clientIp);
        bindUser(user);
    }

    public static void bindUser(User user) {
        if (user != null) {
            putIfNotNull(MDC_USER_ID, user.id());
            putIfNotNull(MDC_USER_EMAIL, user.email());
        }
    }

    public static void clear() {
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_CLIENT_IP);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_USER_EMAIL);
    }

    /** Chạy {@code task} với MDC = {@code context}, xong trả lại MDC cũ của thread. */
    public static void runWith(Map<String, String> context, Runnable task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        setContextMap(context);
        try {
            task.run();
        } finally {
            setContextMap(previous);
        }
    }

    private static void setContextMap(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    private static void putIfNotNull(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
