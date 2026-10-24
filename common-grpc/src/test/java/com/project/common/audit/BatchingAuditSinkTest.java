package com.project.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class BatchingAuditSinkTest {

    @Test
    void sendsInBatchesAndFlushesOnShutdown() throws Exception {
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        BatchingAuditSink sink = new BatchingAuditSink(100, 10, Duration.ofMillis(50)) {
            @Override
            protected void send(List<AuditEvent> batch) {
                batchSizes.add(batch.size());
            }
        };
        for (int i = 0; i < 25; i++) {
            sink.publish(event());
        }
        sink.afterPropertiesSet();
        sink.destroy();

        assertThat(batchSizes).allMatch(size -> size <= 10);
        assertThat(batchSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(25);
    }

    @Test
    void failingSendDoesNotStopWorkerAndFullQueueDropsInsteadOfBlocking() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Integer> sent = new CopyOnWriteArrayList<>();
        BatchingAuditSink sink = new BatchingAuditSink(2, 1, Duration.ofMillis(20)) {
            private boolean first = true;

            @Override
            protected void send(List<AuditEvent> batch) throws Exception {
                if (first) {
                    first = false;
                    blocked.countDown();
                    release.await();
                    throw new IllegalStateException("audit-service down");
                }
                sent.add(batch.size());
            }
        };
        sink.afterPropertiesSet();
        sink.publish(event());
        assertThat(blocked.await(2, TimeUnit.SECONDS)).isTrue();

        // Worker đang kẹt: hàng đợi 2 chỗ, log thứ 3 bị bỏ ngay chứ không block thread gọi
        long start = System.nanoTime();
        sink.publish(event());
        sink.publish(event());
        sink.publish(event());
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(500);

        release.countDown();
        sink.destroy();
        assertThat(sent).hasSize(2);
    }

    private static AuditEvent event() {
        return AuditEvent.builder().type(AuditType.HTTP).build();
    }
}
