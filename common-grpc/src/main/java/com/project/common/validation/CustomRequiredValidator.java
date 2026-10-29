package com.project.common.validation;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.ConstraintValidatorContext;

public class CustomRequiredValidator extends LabeledConstraintValidator<CustomRequired, Object> {

    @Override
    public void initialize(CustomRequired annotation) {
        setLabel(annotation.label());
        setArgs(annotation.args());
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        return isPresent(value) || invalid(context);
    }

    static boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof CharSequence s) {
            return !s.toString().isBlank();
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (value instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (value instanceof Optional<?> o) {
            return o.isPresent();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }
}
