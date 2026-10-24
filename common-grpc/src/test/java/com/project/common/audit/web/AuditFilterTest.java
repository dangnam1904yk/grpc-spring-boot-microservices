package com.project.common.audit.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.audit.AuditContext;
import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.AuditType;
import com.project.common.audit.test.CapturingAuditSink;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AuditFilterTest {

    private final CapturingAuditSink sink = new CapturingAuditSink();
    private final AuditFilter filter = new AuditFilter(sink, new AuditProperties(), new ObjectMapper(), "test-service");

    /** Đọc body request rồi trả lại nguyên văn, giống controller. */
    private final HttpServlet echo = new HttpServlet() {
        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            byte[] body = req.getInputStream().readAllBytes();
            resp.setStatus(201);
            resp.setContentType("application/json");
            resp.getOutputStream().write(body);
        }
    };

    @Test
    void recordsIpCurlRequestAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setQueryString("lang=vi&token=abc");
        request.setContentType("application/json");
        request.setContent("{\"email\":\"a@b.c\",\"password\":\"Secret@123\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer eyJ.secret.token");
        // Client tự gửi IP giả ở đầu, gateway nối IP thật vào cuối
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.7");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "shop.example.com");
        request.addHeader("X-Request-Id", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain(echo));

        // Client vẫn nhận đủ body
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).contains("Secret@123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");

        AuditEvent event = sink.find("req-123", AuditType.HTTP).get(0);
        assertThat(event.service()).isEqualTo("test-service");
        assertThat(event.clientIp()).isEqualTo("203.0.113.7");
        assertThat(event.method()).isEqualTo("POST");
        assertThat(event.url()).isEqualTo("https://shop.example.com/api/auth/login?lang=vi&token=***");
        assertThat(event.status()).isEqualTo("201");
        assertThat(event.requestBody()).isEqualTo("{\"email\":\"a@b.c\",\"password\":\"***\"}");
        assertThat(event.responseBody()).doesNotContain("Secret@123");
        assertThat(event.requestHeaders()).contains("Bearer ***").doesNotContain("eyJ");
        assertThat(event.curl())
                .startsWith("curl -X POST 'https://shop.example.com/api/auth/login?lang=vi&token=***'")
                .contains("-H 'Authorization: Bearer ***'")
                .contains("--data-raw '{\"email\":\"a@b.c\",\"password\":\"***\"}'")
                .doesNotContain("X-Forwarded")
                .doesNotContain("secret");
        assertThat(event.durationMs()).isNotNull();
        // MDC được dọn sau request
        assertThat(AuditContext.requestId()).isNull();
    }

    @Test
    void invalidRequestIdIsReplacedAndExcludedPathIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");
        request.addHeader("X-Request-Id", "bad id\nwith newline");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain(echo));

        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
        assertThat(sink.events()).hasSize(1);

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"), new MockHttpServletResponse(),
                new MockFilterChain(echo));
        assertThat(sink.events()).hasSize(1);
    }
}
