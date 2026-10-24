package com.project.common.audit;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Che dữ liệu nhạy cảm (mật khẩu, token...) và cắt bớt nội dung dài trước khi ghi log.
 * Log được nhiều người đọc và giữ lâu, nên không bao giờ được chứa secret.
 */
public class Sanitizer {

    public static final String MASK = "***";

    /** "key": "value" trong JSON (chỉ che giá trị dạng chuỗi). */
    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\"([^\"\\\\]*)\"(\\s*:\\s*)\"(?:[^\"\\\\]|\\\\.)*\"");

    /** key=value trong form / query string. */
    private static final Pattern FORM_FIELD = Pattern.compile("(^|&)([^=&]*)=([^&]*)");

    private final Set<String> maskedHeaders;
    private final List<String> maskedFields;
    private final int maxLength;

    public Sanitizer(AuditProperties properties) {
        this.maskedHeaders = properties.getMaskedHeaders().stream()
                .map(h -> h.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.maskedFields = properties.getMaskedFields().stream()
                .map(f -> f.toLowerCase(Locale.ROOT))
                .toList();
        this.maxLength = properties.getHttp().getMaxBodyLength();
    }

    public boolean isSensitiveField(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return maskedFields.stream().anyMatch(lower::contains);
    }

    /** Authorization: "Bearer eyJ..." -> "Bearer ***" (giữ loại token để còn biết request có gửi token). */
    public String header(String name, String value) {
        if (!maskedHeaders.contains(name.toLowerCase(Locale.ROOT))) {
            return value;
        }
        int space = value.indexOf(' ');
        return space > 0 ? value.substring(0, space) + " " + MASK : MASK;
    }

    public String json(String body) {
        if (body == null) {
            return null;
        }
        Matcher m = JSON_STRING_FIELD.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = isSensitiveField(m.group(1))
                    ? "\"" + m.group(1) + "\"" + m.group(2) + "\"" + MASK + "\""
                    : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public String form(String query) {
        if (query == null) {
            return null;
        }
        Matcher m = FORM_FIELD.matcher(query);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = isSensitiveField(m.group(2))
                    ? m.group(1) + m.group(2) + "=" + MASK
                    : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Che field nhạy cảm theo loại nội dung rồi cắt bớt. */
    public String body(String body, String contentType) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        String lowerType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String masked = lowerType.contains("x-www-form-urlencoded") ? form(body) : json(body);
        return truncate(masked);
    }

    public String truncate(String value) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(đã cắt, tổng " + value.length() + " ký tự)";
    }
}
