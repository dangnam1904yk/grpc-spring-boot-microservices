package com.project.common.id;

import java.util.Map;
import java.util.UUID;

import com.project.common.exception.AppException;
import com.project.common.exception.ErrorCode;
import com.project.common.i18n.LocalizedMessage;

/** Chuyển id dạng chuỗi (nhận qua gRPC / tham số) sang UUID. */
public final class Ids {

    private Ids() {
    }

    /** Chuỗi không phải UUID -> INVALID_ARGUMENT (HTTP 400 / gRPC INVALID_ARGUMENT), không phải lỗi 500. */
    public static UUID parse(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT,
                    LocalizedMessage.of("error.not-uuid.detail", field, String.valueOf(value)),
                    Map.of(field, LocalizedMessage.of("error.not-uuid")));
        }
    }
}
