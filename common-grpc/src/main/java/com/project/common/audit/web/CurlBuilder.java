package com.project.common.audit.web;

import java.util.List;
import java.util.Map;

/**
 * Dựng lại request thành lệnh curl để copy chạy lại khi debug. Header nhạy cảm đã bị che từ trước,
 * nên muốn chạy lại phải tự điền token.
 */
public final class CurlBuilder {

    private CurlBuilder() {
    }

    public static String build(String method, String url, List<Map.Entry<String, String>> headers, String body) {
        StringBuilder sb = new StringBuilder("curl -X ").append(method).append(' ').append(quote(url));
        for (Map.Entry<String, String> header : headers) {
            sb.append(" \\\n  -H ").append(quote(header.getKey() + ": " + header.getValue()));
        }
        if (body != null && !body.isEmpty()) {
            sb.append(" \\\n  --data-raw ").append(quote(body));
        }
        return sb.toString();
    }

    /** Bọc trong dấu nháy đơn của shell: ' -> '\'' */
    static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
