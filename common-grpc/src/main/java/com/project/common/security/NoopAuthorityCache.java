package com.project.common.security;

import java.util.Optional;

/** Không có Redis / tắt cache ({@code app.security.authority-cache-enabled=false}): mọi request hỏi auth-service. */
public class NoopAuthorityCache implements AuthorityCache {

    @Override
    public Optional<Authorities> get(String userId) {
        return Optional.empty();
    }

    @Override
    public void put(String userId, Authorities authorities) {
    }

    @Override
    public void evict(String userId) {
    }

    @Override
    public void evictAll() {
    }
}
