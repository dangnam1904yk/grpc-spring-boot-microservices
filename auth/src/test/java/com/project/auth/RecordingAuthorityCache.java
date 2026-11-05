package com.project.auth;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.project.common.security.Authorities;
import com.project.common.security.AuthorityCache;

/** Ghi lại các lần xoá cache để test kiểm tra auth-service xoá đúng lúc (không có Redis trong test). */
public class RecordingAuthorityCache implements AuthorityCache {

    final List<String> evictedUsers = new CopyOnWriteArrayList<>();
    final AtomicInteger evictAllCount = new AtomicInteger();

    @Override
    public Optional<Authorities> get(String userId) {
        return Optional.empty();
    }

    @Override
    public void put(String userId, Authorities authorities) {
    }

    @Override
    public void evict(String userId) {
        evictedUsers.add(userId);
    }

    @Override
    public void evictAll() {
        evictAllCount.incrementAndGet();
    }
}
