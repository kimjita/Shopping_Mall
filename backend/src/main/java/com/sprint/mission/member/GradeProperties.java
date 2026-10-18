package com.sprint.mission.member;

import com.sprint.mission.member.domain.Grade;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 등급별 기준 금액(최근 3개월 구매확정 금액, 원)과 적립률(basis point, 50 = 0.5%).
 * 설정이 잘못되면 시작할 때 실패한다.
 */
@ConfigurationProperties("app.grade")
public record GradeProperties(Map<Grade, Rule> rules) {

    public record Rule(long threshold, int earnRateBp) {
    }

    public GradeProperties {
        if (rules == null) {
            throw new IllegalArgumentException("app.grade.rules 설정이 필요하다.");
        }
        Map<Grade, Rule> copy = new EnumMap<>(Grade.class);
        long previousThreshold = -1;
        for (Grade grade : Grade.values()) {
            Rule rule = rules.get(grade);
            if (rule == null) {
                throw new IllegalArgumentException("등급 설정이 없다: " + grade);
            }
            if (rule.earnRateBp() < 0) {
                throw new IllegalArgumentException("적립률은 0 이상이어야 한다: " + grade);
            }
            if (grade == Grade.BABY && rule.threshold() != 0) {
                throw new IllegalArgumentException("BABY 기준 금액은 0이어야 한다.");
            }
            if (rule.threshold() <= previousThreshold) {
                throw new IllegalArgumentException("기준 금액은 등급이 오를수록 커야 한다: " + grade);
            }
            previousThreshold = rule.threshold();
            copy.put(grade, rule);
        }
        rules = Map.copyOf(copy);
    }
}
