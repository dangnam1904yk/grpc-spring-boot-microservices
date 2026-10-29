package com.project.common.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;

/**
 * Chuyển lỗi Bean Validation (khi service dùng {@code @Validated}) sang field -&gt; message cho handler lỗi chung.
 * Message lấy nguyên từ annotation của service.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** Mỗi field giữ lỗi đầu tiên. */
    public static Map<String, String> toMap(Set<? extends ConstraintViolation<?>> violations) {
        Map<String, String> errors = new LinkedHashMap<>();
        violations.forEach(v -> errors.putIfAbsent(fieldOf(v.getPropertyPath()), v.getMessage()));
        return errors;
    }

    /**
     * {@code items[0].name}. Bỏ tên method và tên tham số chứa field, chỉ giữ tên tham số khi chính nó bị vi phạm
     * ({@code create.req.name} -&gt; {@code name}, {@code find.code} -&gt; {@code code}).
     */
    static String fieldOf(Path path) {
        List<Path.Node> nodes = new ArrayList<>();
        path.forEach(nodes::add);
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            Path.Node node = nodes.get(i);
            switch (node.getKind()) {
                case METHOD, CONSTRUCTOR, CROSS_PARAMETER, RETURN_VALUE:
                    continue;
                case PARAMETER:
                    if (i < nodes.size() - 1) {
                        continue;
                    }
                    break;
                default:
                    break;
            }
            if (node.isInIterable() && !field.isEmpty()) {
                field.append('[').append(node.getIndex() != null ? node.getIndex() : node.getKey()).append(']');
            }
            String name = node.getName();
            if (name != null && !name.startsWith("<")) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(name);
            }
        }
        return field.toString();
    }
}
