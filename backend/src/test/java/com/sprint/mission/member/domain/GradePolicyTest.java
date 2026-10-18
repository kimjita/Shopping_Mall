package com.sprint.mission.member.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.member.GradeProperties;
import com.sprint.mission.member.GradeProperties.Rule;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sprint.mission.support.IntegrationTest;

/** 기본 설정(application.yml)으로 만든 정책을 검증한다. */
class GradePolicyTest extends IntegrationTest {

    @Autowired
    GradePolicy gradePolicy;

    @Test
    void 기준_금액_경계에서_등급이_바뀐다() {
        assertThat(gradePolicy.gradeFor(0)).isEqualTo(Grade.BABY);
        assertThat(gradePolicy.gradeFor(99_999)).isEqualTo(Grade.BABY);
        assertThat(gradePolicy.gradeFor(100_000)).isEqualTo(Grade.PINK);
        assertThat(gradePolicy.gradeFor(199_999)).isEqualTo(Grade.PINK);
        assertThat(gradePolicy.gradeFor(200_000)).isEqualTo(Grade.GREEN);
        assertThat(gradePolicy.gradeFor(400_000)).isEqualTo(Grade.BLACK);
        assertThat(gradePolicy.gradeFor(799_999)).isEqualTo(Grade.BLACK);
        assertThat(gradePolicy.gradeFor(800_000)).isEqualTo(Grade.GOLD);
    }

    @Test
    void 음수_금액은_거부한다() {
        assertThatThrownBy(() -> gradePolicy.gradeFor(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 적립_포인트는_내림한다() {
        // 12,345 * 150 / 10000 = 185.175
        assertThat(gradePolicy.earnPoints(Grade.GREEN, 12_345)).isEqualTo(185);
        assertThat(gradePolicy.earnPoints(Grade.BABY, 199)).isEqualTo(0);
        assertThat(gradePolicy.earnRateBp(Grade.GOLD)).isEqualTo(300);
    }

    @Test
    void 등급_높낮이를_비교한다() {
        assertThat(Grade.GOLD.isAtLeast(Grade.PINK)).isTrue();
        assertThat(Grade.PINK.isAtLeast(Grade.PINK)).isTrue();
        assertThat(Grade.BABY.isAtLeast(Grade.PINK)).isFalse();
    }

    @Test
    void 잘못된_설정은_시작에_실패한다() {
        Map<Grade, Rule> valid = Map.of(
                Grade.BABY, new Rule(0, 50),
                Grade.PINK, new Rule(100_000, 100),
                Grade.GREEN, new Rule(200_000, 150),
                Grade.BLACK, new Rule(400_000, 200),
                Grade.GOLD, new Rule(800_000, 300));
        new GradeProperties(valid);

        // 기준 금액이 거꾸로
        assertThatThrownBy(() -> new GradeProperties(Map.of(
                Grade.BABY, new Rule(0, 50),
                Grade.PINK, new Rule(300_000, 100),
                Grade.GREEN, new Rule(200_000, 150),
                Grade.BLACK, new Rule(400_000, 200),
                Grade.GOLD, new Rule(800_000, 300)))).isInstanceOf(IllegalArgumentException.class);
        // 기준 금액이 같으면 오르지 않은 것이다
        assertThatThrownBy(() -> new GradeProperties(Map.of(
                Grade.BABY, new Rule(0, 50),
                Grade.PINK, new Rule(100_000, 100),
                Grade.GREEN, new Rule(100_000, 150),
                Grade.BLACK, new Rule(400_000, 200),
                Grade.GOLD, new Rule(800_000, 300)))).isInstanceOf(IllegalArgumentException.class);
        // BABY 기준이 0이 아님
        assertThatThrownBy(() -> new GradeProperties(Map.of(
                Grade.BABY, new Rule(1, 50),
                Grade.PINK, new Rule(100_000, 100),
                Grade.GREEN, new Rule(200_000, 150),
                Grade.BLACK, new Rule(400_000, 200),
                Grade.GOLD, new Rule(800_000, 300)))).isInstanceOf(IllegalArgumentException.class);
        // 등급 누락
        assertThatThrownBy(() -> new GradeProperties(Map.of(Grade.BABY, new Rule(0, 50))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GradeProperties(null)).isInstanceOf(IllegalArgumentException.class);
        // 음수 적립률
        assertThatThrownBy(() -> new GradeProperties(Map.of(
                Grade.BABY, new Rule(0, -1),
                Grade.PINK, new Rule(100_000, 100),
                Grade.GREEN, new Rule(200_000, 150),
                Grade.BLACK, new Rule(400_000, 200),
                Grade.GOLD, new Rule(800_000, 300)))).isInstanceOf(IllegalArgumentException.class);
    }
}
