package com.project.common.validation;

import java.lang.annotation.Annotation;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import com.project.common.i18n.Messages;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;

/**
 * Lớp cha của validator cho {@code @CustomRequired}, {@code @CustomMaxLength}...: khi lỗi thì đưa tên field đã dịch
 * vào tham số {@value #LABEL_PARAMETER} của message ({@code validation.required={fieldLabel} là bắt buộc}), và
 * {@code args} của annotation vào {@code {arg0}}, {@code {arg1}}... (mỗi giá trị là key hoặc chữ thường).
 * <p>
 * {@code label} là key trong file message của service ({@code customer.full-name}) hoặc chữ thường; để trống thì là
 * {@value #DEFAULT_LABEL} ("Trường thông tin" / "This field"). {@link Messages} được Spring inject vào validator
 * (LocalValidatorFactoryBean tạo validator qua bean factory).
 */
public abstract class LabeledConstraintValidator<A extends Annotation, T> implements ConstraintValidator<A, T> {

    public static final String LABEL_PARAMETER = "fieldLabel";
    public static final String DEFAULT_LABEL = "validation.label.default";
    /** Tiền tố tham số của {@code args}: {@code {arg0}}, {@code {arg1}}... */
    public static final String ARG_PARAMETER_PREFIX = "arg";

    private Messages messages;
    private String label = "";
    private String[] args = {};

    @Autowired(required = false)
    public void setMessages(Messages messages) {
        this.messages = messages;
    }

    protected void setLabel(String label) {
        this.label = label == null ? "" : label.trim();
    }

    protected void setArgs(String[] args) {
        this.args = args == null ? new String[0] : args.clone();
    }

    /** Ghi tên field và {@code args} vào message rồi trả {@code false} (không hợp lệ). */
    protected boolean invalid(ConstraintValidatorContext context) {
        try {
            HibernateConstraintValidatorContext hibernate = context.unwrap(HibernateConstraintValidatorContext.class);
            hibernate.addMessageParameter(LABEL_PARAMETER, resolveLabel());
            for (int i = 0; i < args.length; i++) {
                hibernate.addMessageParameter(ARG_PARAMETER_PREFIX + i, resolve(args[i]));
            }
        } catch (ValidationException e) {
            // Không phải Hibernate Validator: message vẫn có, chỉ thiếu tên field
        }
        return false;
    }

    /** Key thì dịch, chữ thường (chưa có key) thì giữ nguyên. */
    private String resolve(String keyOrText) {
        return messages != null ? messages.get(keyOrText) : keyOrText;
    }

    private String resolveLabel() {
        String key = label.isEmpty() ? DEFAULT_LABEL : label;
        if (messages != null) {
            return messages.get(key);
        }
        return label.isEmpty() ? "Trường thông tin" : label;
    }
}
