package com.project.auth.service;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.util.StringUtils;

import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;

/** Kiểm tra dùng chung cho các service phân quyền. */
final class AuthzChecks {

    private AuthzChecks() {
    }

    static AppException notFound(String what, Object id) {
        return new AppException(ErrorCode.NOT_FOUND, what + " id=" + id + " không tồn tại");
    }

    /** Optimistic lock: client phải gửi version đang giữ, khác version hiện tại thì phải tải lại. */
    static void requireVersion(Long current, Long requested, String what) {
        if (!current.equals(requested)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT,
                    what + " đã bị thay đổi bởi request khác (version hiện tại=" + current + ")");
        }
    }

    /** INVALID_ARGUMENT liệt kê các giá trị không có trong {@code known}. */
    static void requireKnown(Collection<?> values, Collection<?> known, String what) {
        Set<String> unknown = new TreeSet<>();
        for (Object value : values) {
            if (!known.contains(value)) {
                unknown.add(String.valueOf(value));
            }
        }
        if (!unknown.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT, what + " không tồn tại: " + unknown);
        }
    }

    static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
