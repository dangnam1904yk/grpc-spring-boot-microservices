package com.project.common.validation;

import jakarta.validation.ConstraintValidatorContext;

public class CustomMinLengthValidator extends LabeledConstraintValidator<CustomMinLength, CharSequence> {

    private int min;
    private boolean trim;

    @Override
    public void initialize(CustomMinLength annotation) {
        if (annotation.min() < 0) {
            throw new IllegalArgumentException("@CustomMinLength: min phải >= 0");
        }
        setLabel(annotation.label());
        setArgs(annotation.args());
        min = annotation.min();
        trim = annotation.trim();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        int length = trim ? value.toString().strip().length() : value.length();
        return length >= min || invalid(context);
    }
}
