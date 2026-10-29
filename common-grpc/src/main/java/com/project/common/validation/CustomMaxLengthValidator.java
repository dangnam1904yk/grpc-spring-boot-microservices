package com.project.common.validation;

import jakarta.validation.ConstraintValidatorContext;

public class CustomMaxLengthValidator extends LabeledConstraintValidator<CustomMaxLength, CharSequence> {

    private int max;
    private boolean trim;

    @Override
    public void initialize(CustomMaxLength annotation) {
        if (annotation.max() < 0) {
            throw new IllegalArgumentException("@CustomMaxLength: max phải >= 0");
        }
        setLabel(annotation.label());
        setArgs(annotation.args());
        max = annotation.max();
        trim = annotation.trim();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = trim ? value.toString().strip().length() : value.length();
        return length <= max || invalid(context);
    }
}
