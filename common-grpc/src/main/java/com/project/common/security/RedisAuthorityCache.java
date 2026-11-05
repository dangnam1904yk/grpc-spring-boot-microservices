package com.project.common.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Key {@value #PREFIX}{@code <userId>} -> JSON {@code {"roles":[...],"permissions":[...]}}, hết hạn sau
 * {@code app.security.authority-cache-ttl} (lưới an toàn khi lệnh xoá cache bị lỡ).
 */
@Slf4j
public class RedisAuthorityCache implements AuthorityCache {

    static final String PREFIX = "authz:user:";
    private static final int SCAN_BATCH = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisAuthorityCache(StringRedisTemplate redis, ObjectMapper objectMapper, Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<Authorities> get(String userId) {
        try {
            String json = redis.opsForValue().get(PREFIX + userId);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, Authorities.class));
        } catch (Exception e) {
            log.warn("Không đọc được cache quyền của user {} từ Redis: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String userId, Authorities authorities) {
        try {
            redis.opsForValue().set(PREFIX + userId, objectMapper.writeValueAsString(authorities), ttl);
        } catch (Exception e) {
            log.warn("Không ghi được cache quyền của user {} vào Redis: {}", userId, e.getMessage());
        }
    }

    @Override
    public void evict(String userId) {
        try {
            redis.delete(PREFIX + userId);
        } catch (Exception e) {
            log.warn("Không xoá được cache quyền của user {} (hết hạn sau {}): {}", userId, ttl, e.getMessage());
        }
    }

    /** SCAN thay cho KEYS để không chặn Redis khi có nhiều key. */
    @Override
    public void evictAll() {
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(SCAN_BATCH)
                .build())) {
            List<String> batch = new ArrayList<>();
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() == SCAN_BATCH) {
                    redis.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redis.delete(batch);
            }
        } catch (Exception e) {
            log.warn("Không xoá được cache quyền trên Redis (hết hạn sau {}): {}", ttl, e.getMessage());
        }
    }
}
