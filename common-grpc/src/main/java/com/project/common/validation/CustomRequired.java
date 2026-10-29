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
 * Bắt buộc có giá trị: không null; chuỗi không rỗng / toàn khoảng trắng; collection, map, mảng không rỗng.
 * <pre>
 * &#64;CustomRequired(label = "customer.full-name") String fullName   // "Họ tên là bắt buộc" / "Full name is required"
 * &#64;CustomRequired Long version                                     // "Trường thông tin là bắt buộc"
 * </pre>
 *
 * @see LabeledConstraintValidator
 */
@Documented
@Constraint(validatedBy = CustomRequiredValidator.class)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
public @interface CustomRequired {

    /** Key hoặc chữ thường; tham số {@code {fieldLabel}} của message. Trống = "Trường thông tin". */
    String label() default "";

    /** Key {@code {...}} trong file message, dùng được {@code {fieldLabel}}, {@code {arg0}}... */
    String message() default "{validation.required}";

    /**
     * Tham số thêm cho message: {@code {arg0}}, {@code {arg1}}... Mỗi giá trị là key (được dịch) hoặc chữ thường.
     */
    String[] args() default {};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
