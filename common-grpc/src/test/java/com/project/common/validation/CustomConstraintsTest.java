package com.project.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.i18n.LocaleContextHolder;

import com.project.common.exception.ConstraintViolations;
import com.project.common.i18n.I18nAutoConfiguration;

import jakarta.validation.Validator;

/**
 * Chạy với Validator của Spring Boot như trong service: message tra trong file i18n, {@code Messages} được inject
 * vào validator để dịch {@code label}.
 */
class CustomConstraintsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class, ValidationAutoConfiguration.class))
            // label là key: dùng key có sẵn trong file chung cho test
            .withPropertyValues("app.i18n.basenames=");

    record Request(
            @CustomRequired @CustomMaxLength(max = 5) String code,
            @CustomRequired(label = "Họ tên") @CustomMinLength(min = 3, trim = true, label = "Họ tên") String name,
            @CustomRegex(regexp = "^[0-9]{8,15}$", label = "error.type-mismatch") String phone,
            @CustomRegex(regexp = "^[a-z]+$", flags = jakarta.validation.constraints.Pattern.Flag.CASE_INSENSITIVE,
                    message = "{error.invalid}") String slug,
            @CustomRequired List<@CustomMaxLength(max = 2) String> tags,
            @CustomRequired Long version) {
    }

    /** args: chữ thường giữ nguyên, key được dịch; dùng cùng {fieldLabel} và tham số của annotation. */
    record WithArgs(
            @CustomRegex(regexp = "^[0-9]+$", label = "Mã", message = "{fieldLabel} sai, ví dụ: {arg0} ({arg1}, {regexp})",
                    args = { "0912", "error.type-mismatch" }) String code) {
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void validRequestPasses() {
        runner.run(ctx -> assertThat(validate(ctx.getBean(Validator.class),
                new Request("A1", "Nam", "0912345678", "AbC", List.of("x"), 1L))).isEmpty());
    }

    @Test
    void messagesUseLabelsAndAnnotationParameters() {
        Request invalid = new Request("TOO_LONG", "  ab ", "12ab", "a-b", List.of("ok", "long"), null);

        runner.run(ctx -> {
            Validator validator = ctx.getBean(Validator.class);
            assertThat(validate(validator, invalid)).containsOnly(
                    entry("code", "Trường thông tin không được vượt quá 5 ký tự"),
                    entry("name", "Họ tên phải có ít nhất 3 ký tự"),
                    entry("phone", "Sai kiểu dữ liệu không đúng định dạng"),
                    entry("slug", "Dữ liệu không hợp lệ"),
                    entry("tags[1]", "Trường thông tin không được vượt quá 2 ký tự"),
                    entry("version", "Trường thông tin là bắt buộc"));

            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertThat(validate(validator, invalid)).contains(
                    entry("code", "This field must be at most 5 characters"),
                    entry("phone", "Invalid data type has an invalid format"),
                    entry("version", "This field is required"));
        });
    }

    @Test
    void requiredRejectsBlankAndEmpty() {
        runner.run(ctx -> assertThat(validate(ctx.getBean(Validator.class),
                new Request(" ", "", null, null, List.of(), 1L))).containsOnly(
                        entry("code", "Trường thông tin là bắt buộc"),
                        entry("name", "Họ tên là bắt buộc"),
                        entry("tags", "Trường thông tin là bắt buộc")));
    }

    @Test
    void argsAreTranslatedAndInserted() {
        runner.run(ctx -> {
            Validator validator = ctx.getBean(Validator.class);
            assertThat(validate(validator, new WithArgs("ab"))).containsOnly(
                    entry("code", "Mã sai, ví dụ: 0912 (Sai kiểu dữ liệu, ^[0-9]+$)"));
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertThat(validate(validator, new WithArgs("ab"))).containsOnly(
                    entry("code", "Mã sai, ví dụ: 0912 (Invalid data type, ^[0-9]+$)"));
        });
    }

    private static Map<String, String> validate(Validator validator, Object request) {
        return ConstraintViolations.toMap(validator.validate(request));
    }
}
