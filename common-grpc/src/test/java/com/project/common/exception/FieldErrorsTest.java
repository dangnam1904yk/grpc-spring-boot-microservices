package com.project.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.project.common.grpc.GrpcFieldErrors;
import com.project.common.i18n.LocalizedMessage;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

class FieldErrorsTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    record Item(@NotBlank(message = "Bắt buộc") String name) {
    }

    record Request(@Size(max = 2, message = "Quá {max} ký tự") String code, List<@Valid Item> items) {
    }

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void collectsFirstErrorPerFieldAndThrowsOnce() {
        FieldErrors errors = FieldErrors.create();
        errors.throwIfAny();

        errors.add("code", "error.required")
                .add("code", "không ghi đè lỗi đầu tiên")
                .add("name", "catalog.too-long", 255);

        assertThatThrownBy(errors::throwIfAny)
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT);
                    assertThat(e.getDetail()).isEqualTo(LocalizedMessage.of(FieldErrors.INVALID));
                    assertThat(e.getErrors()).containsExactly(
                            entry("code", LocalizedMessage.of("error.required")),
                            entry("name", LocalizedMessage.of("catalog.too-long", 255)));
                    assertThat(e.getMessage()).isEqualTo("error.invalid");
                });
        assertThat(FieldErrors.of("sort", "catalog.sort.invalid", "x").getErrors())
                .containsExactly(entry("sort", LocalizedMessage.of("catalog.sort.invalid", "x")));
    }

    @Test
    void constraintViolationsKeepServiceMessagesAndNestedPaths() {
        Request req = new Request("abc", List.of(new Item("ok"), new Item("")));

        assertThat(ConstraintViolations.toMap(FACTORY.getValidator().validate(req))).containsOnly(
                entry("code", "Quá 2 ký tự"),
                entry("items[1].name", "Bắt buộc"));
    }

    @Test
    void fieldErrorsSurviveGrpcRoundTrip() {
        Map<String, String> errors = Map.of("code", "Trường thông tin là bắt buộc");
        StatusRuntimeException e = GrpcFieldErrors.toException(Status.Code.INVALID_ARGUMENT, "Dữ liệu không hợp lệ",
                errors);

        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        assertThat(e.getStatus().getDescription()).isEqualTo("Dữ liệu không hợp lệ");
        assertThat(GrpcFieldErrors.from(e)).isEqualTo(errors);
        assertThat(GrpcFieldErrors.from(Status.NOT_FOUND.asRuntimeException())).isEmpty();
    }
}
