package com.project.common.i18n;

import java.util.Arrays;
import java.util.Objects;

import org.springframework.context.MessageSourceResolvable;

/**
 * Message chưa dịch: key trong {@code i18n/messages_xx.properties} của service + tham số ({@code {0}}, {@code {1}}...).
 * Dịch theo ngôn ngữ của request lúc trả lỗi ({@link Messages}).
 * <p>
 * Key không có trong file thì trả nguyên chuỗi: code cũ truyền thẳng câu tiếng Việt vẫn chạy, chuyển dần sang key.
 * Tham số là {@code LocalizedMessage} khác cũng được dịch ({@code of("catalog.not-found", of("catalog.unit"), code)}).
 */
public final class LocalizedMessage implements MessageSourceResolvable {

    private final String key;
    private final Object[] args;

    private LocalizedMessage(String key, Object[] args) {
        this.key = Objects.requireNonNull(key, "key");
        this.args = args == null ? new Object[0] : args.clone();
    }

    public static LocalizedMessage of(String key, Object... args) {
        return new LocalizedMessage(key, args);
    }

    public String key() {
        return key;
    }

    public Object[] args() {
        return args.clone();
    }

    @Override
    public String[] getCodes() {
        return new String[] { key };
    }

    @Override
    public Object[] getArguments() {
        return args.clone();
    }

    @Override
    public String getDefaultMessage() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LocalizedMessage other && key.equals(other.key) && Arrays.equals(args, other.args);
    }

    @Override
    public int hashCode() {
        return 31 * key.hashCode() + Arrays.hashCode(args);
    }

    /** Dùng cho log (không dịch). */
    @Override
    public String toString() {
        return args.length == 0 ? key : key + " " + Arrays.toString(args);
    }
}
