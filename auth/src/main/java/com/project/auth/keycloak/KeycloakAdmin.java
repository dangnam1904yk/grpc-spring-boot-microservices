package com.project.auth.keycloak;

import java.util.List;
import java.util.Optional;

/**
 * Các thao tác auth-service cần từ Keycloak: Admin REST API (quản lý tài khoản) và token endpoint của realm (đăng
 * nhập bằng form của frontend). Keycloak chỉ giữ tài khoản (email, mật khẩu, họ tên, trạng thái, phiên); role
 * của user, ma trận phân quyền và menu nằm ở auth-service.
 * <p>
 * Lỗi kết nối / lỗi không mong đợi từ Keycloak -> {@code AppException(UNAVAILABLE)} (503).
 */
public interface KeycloakAdmin {

    /** search: khớp một phần email / username / họ tên; null = tất cả. Mới nhất lên đầu không được hỗ trợ. */
    List<KeycloakUser> searchUsers(String search, int first, int max);

    long countUsers(String search);

    Optional<KeycloakUser> findUser(String id);

    /** Trả về id mới. Email đã tồn tại -> ALREADY_EXISTS. */
    String createUser(KeycloakUser user, String password);

    /** Cập nhật email không được hỗ trợ (email là tên đăng nhập). */
    void updateUser(KeycloakUser user);

    /** Kết thúc mọi phiên (refresh token) của user; access token đã cấp vẫn dùng được tới khi hết hạn. */
    void logout(String userId);

    /** Xoá tài khoản (bù khi đăng ký lỗi giữa chừng). Không tồn tại thì bỏ qua. */
    void deleteUser(String id);

    /**
     * Đăng nhập bằng email + mật khẩu (password grant của client auth-service). Sai thông tin -> UNAUTHENTICATED,
     * tài khoản bị khoá / chưa thiết lập xong -> FORBIDDEN.
     */
    KeycloakTokens login(String email, String password);

    /** Đổi refresh token lấy cặp token mới (refresh token cũ hết dùng được). Hết hạn / bị thu hồi -> UNAUTHENTICATED. */
    KeycloakTokens refresh(String refreshToken);

    /** Kết thúc phiên của refresh token này. Token sai / đã hết hạn thì bỏ qua. */
    void endSession(String refreshToken);
}
