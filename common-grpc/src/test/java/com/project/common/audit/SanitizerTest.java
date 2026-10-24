package com.project.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.project.common.audit.web.CurlBuilder;

class SanitizerTest {

    private final Sanitizer sanitizer = new Sanitizer(new AuditProperties());

    @Test
    void masksSensitiveJsonFieldsButKeepsOthers() {
        String body = "{\"email\":\"a@b.c\",\"password\":\"Secret@123\",\"refreshToken\":\"abc\\\"def\",\"age\":1}";

        assertThat(sanitizer.json(body))
                .isEqualTo("{\"email\":\"a@b.c\",\"password\":\"***\",\"refreshToken\":\"***\",\"age\":1}");
    }

    @Test
    void masksFormAndQueryValues() {
        assertThat(sanitizer.form("user=bob&password=123&access_token=x"))
                .isEqualTo("user=bob&password=***&access_token=***");
    }

    @Test
    void masksAuthorizationHeaderButKeepsScheme() {
        assertThat(sanitizer.header("Authorization", "Bearer eyJhbGciOi")).isEqualTo("Bearer ***");
        assertThat(sanitizer.header("Cookie", "SESSION=1")).isEqualTo("***");
        assertThat(sanitizer.header("Content-Type", "application/json")).isEqualTo("application/json");
    }

    @Test
    void truncatesLongBody() {
        AuditProperties properties = new AuditProperties();
        properties.getHttp().setMaxBodyLength(5);

        assertThat(new Sanitizer(properties).truncate("1234567890")).startsWith("12345...").contains("10");
    }

    @Test
    void curlQuotesSingleQuotes() {
        String curl = CurlBuilder.build("POST", "http://localhost/api/x",
                List.of(Map.entry("Content-Type", "application/json")), "{\"name\":\"O'Neil\"}");

        assertThat(curl).isEqualTo("curl -X POST 'http://localhost/api/x' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  --data-raw '{\"name\":\"O'\\''Neil\"}'");
    }
}
