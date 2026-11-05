package com.project.common.security;

import java.util.Optional;

/**
 * Cache role + quyền theo user, dùng chung giữa các service (Redis). Service đọc / ghi khi xác thực request;
 * auth-service xoá khi đổi role của user ({@link #evict}) hoặc đổi ma trận / role / menu / thao tác
 * ({@link #evictAll}), nên thay đổi có hiệu lực ngay.
 * <p>
 * Lỗi kết nối cache không được làm hỏng request: implementation tự nuốt lỗi (get trả về rỗng).
 */
public interface AuthorityCache {

    Optional<Authorities> get(String userId);

    void put(String userId, Authorities authorities);

    void evict(String userId);

    void evictAll();
}
