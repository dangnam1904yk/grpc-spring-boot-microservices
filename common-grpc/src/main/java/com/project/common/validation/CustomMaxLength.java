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

/**
 * Chuỗi không dài quá {@link #max()} ký tự. Null / rỗng là hợp lệ (bắt buộc thì thêm {@link CustomRequired}).
 * <pre>
 * &#64;CustomMaxLength(max = 255, label = "customer.full-name")   // "Họ tên không được vượt quá 255 ký tự"
 * &#64;CustomMaxLength(max = 20, trim = true)                    // bỏ khoảng trắng hai đầu trước khi đếm
 * </pre>
 */
@Documented
@Constraint(validatedBy = CustomMaxLengthValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface CustomMaxLength {

    /** Số ký tự tối đa; tham số {@code {max}} của message. */
    int max();

    /** Bỏ khoảng trắng hai đầu trước khi đếm. */
    boolean trim() default false;

    /** Key hoặc chữ thường; tham số {@code {fieldLabel}} của message. Trống = "Trường thông tin". */
    String label() default "";

    /** Key {@code {...}} trong file message, dùng được {@code {fieldLabel}}, {@code {max}}, {@code {arg0}}... */
    String message() default "{validation.max-length}";

    /**
     * Tham số thêm cho message: {@code {arg0}}, {@code {arg1}}... Mỗi giá trị là key (được dịch) hoặc chữ thường.
     */
    String[] args() default {};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
