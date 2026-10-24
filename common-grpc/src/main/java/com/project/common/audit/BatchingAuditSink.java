package com.project.common.audit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread của request chỉ bỏ log vào hàng đợi (không chờ I/O); 1 thread nền gom thành lô rồi gửi.
 * <ul>
 * <li>Hàng đợi có giới hạn: đích ghi chết lâu thì bỏ log mới, không làm hết RAM.</li>
 * <li>Gửi lỗi thì bỏ lô đó (không retry): log không được làm chậm hay treo service.</li>
 * <li>Lúc tắt service: gửi nốt phần còn trong hàng đợi.</li>
 * </ul>
 */
@Slf4j
public abstract class BatchingAuditSink implements AuditSink, InitializingBean, DisposableBean {

    private final BlockingQueue<AuditEvent> queue;
    private final int batchSize;
    private final Duration flushInterval;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final Thread worker;
    private volatile boolean running = true;

    protected BatchingAuditSink(int queueCapacity, int batchSize, Duration flushInterval) {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.batchSize = batchSize;
        this.flushInterval = flushInterval;
        this.worker = new Thread(this::run, "audit-sender");
        this.worker.setDaemon(true);
    }

    /** Gửi 1 lô; ném exception = cả lô bị bỏ. */
    protected abstract void send(List<AuditEvent> batch) throws Exception;

    @Override
    public void publish(AuditEvent event) {
        if (!queue.offer(event)) {
            long count = dropped.incrementAndGet();
            if (count == 1 || count % 1000 == 0) {
                log.warn("Hàng đợi audit log đầy, đã bỏ tổng cộng {} log", count);
            }
        }
    }

    @Override
    public void afterPropertiesSet() {
        worker.start();
    }

    @Override
    public void destroy() throws InterruptedException {
        running = false;
        worker.join(10_000);
    }

    private void run() {
        List<AuditEvent> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                AuditEvent first = queue.poll(flushInterval.toMillis(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
                flush(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                batch.clear();
            }
        }
    }

    private void flush(List<AuditEvent> batch) {
        try {
            send(batch);
        } catch (Exception e) {
            long count = failed.addAndGet(batch.size());
            log.warn("Không ghi được {} audit log (tổng cộng đã mất {}): {}", batch.size(), count, e.getMessage());
        }
    }
}
