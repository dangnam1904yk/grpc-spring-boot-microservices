package com.project.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class MessagesTest {

    private static final Locale VI = Locale.forLanguageTag("vi");

    private final Messages messages = Messages.commonOnly();

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void translatesByCurrentLocaleAndFallsBackToDefaultLanguage() {
        assertThat(messages.get("error.required")).isEqualTo("Trường thông tin là bắt buộc");

        LocaleContextHolder.setLocale(Locale.US);
        assertThat(messages.locale()).isEqualTo(Locale.ENGLISH);
        assertThat(messages.get("error.required")).isEqualTo("This field is required");
        assertThat(messages.get(Map.of("id", LocalizedMessage.of("error.not-uuid"))))
                .containsEntry("id", "Not a valid UUID");

        // Ngôn ngữ không hỗ trợ -> mặc định (không theo locale máy chủ)
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(messages.get("error.required")).isEqualTo("Trường thông tin là bắt buộc");
    }

    @Test
    void argumentsAndUnknownKeys() {
        assertThat(messages.get("error.not-uuid.detail", "id", "abc")).isEqualTo("id không phải UUID hợp lệ: abc");
        // Tham số là message khác cũng được dịch
        assertThat(messages.get(LocalizedMessage.of("error.not-uuid.detail", LocalizedMessage.of("error.invalid"), "x"),
                Locale.ENGLISH)).isEqualTo("Invalid data is not a valid UUID: x");
        // Chưa có key: trả nguyên chuỗi (câu tiếng Việt cũ vẫn chạy)
        assertThat(messages.get("Category code=X không tồn tại")).isEqualTo("Category code=X không tồn tại");
    }

    @Test
    void resolvesAcceptLanguageHeader() {
        assertThat(messages.resolve((String) null)).isEqualTo(VI);
        assertThat(messages.resolve("en-US,en;q=0.9")).isEqualTo(Locale.ENGLISH);
        assertThat(messages.resolve("vi-VN")).isEqualTo(VI);
        assertThat(messages.resolve("fr, de;q=0.5")).isEqualTo(VI);
        assertThat(messages.resolve("fr, en;q=0.5")).isEqualTo(Locale.ENGLISH);
        assertThat(messages.resolve("@@not a header")).isEqualTo(VI);
    }

    @Test
    void commonBundlesHaveTheSameKeys() throws IOException {
        assertThat(load("i18n/common_en.properties").keySet())
                .isEqualTo(load("i18n/common_vi.properties").keySet());
    }

    static Properties load(String resource) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = MessagesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
