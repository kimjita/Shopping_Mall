package com.sprint.mission.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Instant;

public class InstantRangeValidator implements ConstraintValidator<InstantRange, Instant> {

    private static final Instant MIN = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("9999-12-31T23:59:59Z");

    @Override
    public boolean isValid(Instant value, ConstraintValidatorContext context) {
        return value == null || (!value.isBefore(MIN) && !value.isAfter(MAX));
    }
}
