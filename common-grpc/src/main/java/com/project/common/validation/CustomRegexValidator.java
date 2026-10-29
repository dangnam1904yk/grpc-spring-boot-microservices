package com.project.common.validation;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.validation.ConstraintValidatorContext;

public class CustomRegexValidator extends LabeledConstraintValidator<CustomRegex, CharSequence> {

    private Pattern pattern;

    @Override
    public void initialize(CustomRegex annotation) {
        setLabel(annotation.label());
        setArgs(annotation.args());
        int flags = 0;
        for (jakarta.validation.constraints.Pattern.Flag flag : annotation.flags()) {
            flags |= flag.getValue();
        }
        try {
            pattern = Pattern.compile(annotation.regexp(), flags);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("@CustomRegex: regexp không hợp lệ: " + annotation.regexp(), e);
        }
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return pattern.matcher(value).matches() || invalid(context);
    }
}
