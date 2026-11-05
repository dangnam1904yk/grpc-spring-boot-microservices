package com.project.auth.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.project.common.security.AuthorityCache;

import lombok.RequiredArgsConstructor;

/**
 * Xoá cache role + quyền (Redis, dùng chung với mọi service) khi phân quyền thay đổi. Xoá sau khi transaction
 * commit: xoá trước thì request đồng thời có thể đọc DB cũ và ghi lại cache cũ.
 */
@Component
@RequiredArgsConstructor
public class AuthorityChanges {

    private final AuthorityCache authorityCache;

    /** Role của các user này vừa đổi. */
    public void usersChanged(Collection<String> userIds) {
        List<String> ids = List.copyOf(userIds);
        afterCommit(() -> ids.forEach(authorityCache::evict));
    }

    /** Role / ma trận / menu / thao tác vừa đổi: có thể ảnh hưởng mọi user. */
    public void allChanged() {
        afterCommit(authorityCache::evictAll);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
