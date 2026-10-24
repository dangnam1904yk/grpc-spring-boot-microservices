package com.project.common.audit.test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditSink;
import com.project.common.audit.AuditType;

/**
 * Thu log lại trong bộ nhớ thay vì gửi về audit-service (qua test-jar của common-grpc):
 * <pre>
 * &#64;SpringBootTest
 * &#64;Import(CapturingAuditSink.Config.class)
 * class XxxTest {
 *     &#64;Autowired CapturingAuditSink audit;
 *     audit.find("req-1", AuditType.HTTP);
 * }
 * </pre>
 */
public class CapturingAuditSink implements AuditSink {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(AuditEvent event) {
        events.add(event);
    }

    public List<AuditEvent> events() {
        return List.copyOf(events);
    }

    public List<AuditEvent> find(String requestId, AuditType type) {
        return events.stream()
                .filter(e -> requestId.equals(e.requestId()) && e.type() == type)
                .toList();
    }

    public void clear() {
        events.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        CapturingAuditSink capturingAuditSink() {
            return new CapturingAuditSink();
        }
    }
}
