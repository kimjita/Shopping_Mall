package com.sprint.mission.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 2000-01-01T00:00:00Z 이상 9999-12-31T23:59:59Z 이하의 시각만 허용한다. DB 시각 범위를 넘는 먼 연도를 막는다. null은 통과한다. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = InstantRangeValidator.class)
public @interface InstantRange {

    String message() default "시각은 2000년부터 9999년 사이여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
