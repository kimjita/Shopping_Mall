package com.sprint.mission.member.domain;

import com.sprint.mission.member.GradeProperties;
import org.springframework.stereotype.Component;

/** 설정(app.grade)에 따라 등급을 정하고 적립 포인트를 계산한다. */
@Component
public class GradePolicy {

    private static final long BASIS_POINT_DENOMINATOR = 10_000L;

    private final GradeProperties properties;

    public GradePolicy(GradeProperties properties) {
        this.properties = properties;
    }

    /** 기준 금액 이상인 가장 높은 등급이다. */
    public Grade gradeFor(long threeMonthAmount) {
        if (threeMonthAmount < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 한다.");
        }
        Grade[] grades = Grade.values();
        for (int i = grades.length - 1; i >= 0; i--) {
            if (threeMonthAmount >= properties.rules().get(grades[i]).threshold()) {
                return grades[i];
            }
        }
        return Grade.BABY;
    }

    public int earnRateBp(Grade grade) {
        return properties.rules().get(grade).earnRateBp();
    }

    /** floor(금액 × bp / 10000). */
    public long earnPoints(Grade grade, long amount) {
        return amount * earnRateBp(grade) / BASIS_POINT_DENOMINATOR;
    }
}
