package com.project.common.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.project.common.exception.AppException;

class UuidV7Test {

    @Test
    void hasVersion7VariantAndCurrentTimestamp() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7.next();
        long after = System.currentTimeMillis();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(UuidV7.timestampMillis(id)).isBetween(before, after + 1);
    }

    @Test
    void strictlyIncreasingEvenWithinSameMillisecond() {
        List<UUID> ids = new ArrayList<>();
        // Nhiều hơn 4096 (bộ đếm 12 bit) để đi qua cả nhánh tràn bộ đếm
        for (int i = 0; i < 20_000; i++) {
            ids.add(UuidV7.next());
        }
        for (int i = 1; i < ids.size(); i++) {
            assertThat(ids.get(i)).isGreaterThan(ids.get(i - 1));
            // So sánh dạng chuỗi cũng tăng dần: DB lưu dạng chuỗi / binary vẫn đúng thứ tự
            assertThat(ids.get(i).toString()).isGreaterThan(ids.get(i - 1).toString());
        }
    }

    @Test
    void uniqueAcrossThreads() throws Exception {
        Set<UUID> all = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futures.add(pool.submit(() -> {
                    Set<UUID> local = new HashSet<>();
                    for (int i = 0; i < 10_000; i++) {
                        local.add(UuidV7.next());
                    }
                    all.addAll(local);
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(all).hasSize(80_000);
    }

    @Test
    void parseRejectsInvalidIdAsInvalidArgument() {
        UUID id = UuidV7.next();
        assertThat(Ids.parse(id.toString(), "id")).isEqualTo(id);
        assertThatThrownBy(() -> Ids.parse("123", "id")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> Ids.parse(null, "id")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> UuidV7.timestampMillis(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
