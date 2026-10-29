package com.project.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;

/**
 * Chuỗi khớp toàn bộ {@link #regexp()}. Null / rỗng là hợp lệ (bắt buộc thì thêm {@link CustomRequired}).
 * <pre>
 * &#64;CustomRegex(regexp = "^\\+?[0-9]{8,15}$", label = "customer.phone")          // "Số điện thoại không đúng định dạng"
 * &#64;CustomRegex(regexp = "^[A-Z0-9_]+$", flags = Pattern.Flag.CASE_INSENSITIVE)
 * &#64;CustomRegex(regexp = PHONE, message = "{customer.phone.invalid}")             // message riêng của service
 * &#64;CustomRegex(regexp = PHONE, message = "{customer.phone.invalid}", args = {"0912345678"})
 *     // customer.phone.invalid=Số điện thoại không hợp lệ, ví dụ: {arg0}
 * </pre>
 */
@Documented
@Constraint(validatedBy = CustomRegexValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface CustomRegex {

    /** Biểu thức chính quy (khớp toàn chuỗi); tham số {@code {regexp}} của message. */
    String regexp();

    Pattern.Flag[] flags() default {};

    /** Key hoặc chữ thường; tham số {@code {fieldLabel}} của message. Trống = "Trường thông tin". */
    String label() default "";

    /** Key {@code {...}} trong file message, dùng được {@code {fieldLabel}}, {@code {regexp}}, {@code {arg0}}... */
    String message() default "{validation.regex}";

    /**
     * Tham số thêm cho message: {@code {arg0}}, {@code {arg1}}... Mỗi giá trị là key (được dịch) hoặc chữ thường.
     */
    String[] args() default {};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
