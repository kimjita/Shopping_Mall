package com.sprint.mission.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Grade;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CouponTest {

    static final Instant ISSUE_START = Instant.parse("2026-10-01T00:00:00Z");
    static final Instant ISSUE_END = Instant.parse("2026-10-10T00:00:00Z");
    static final Instant USE_START = Instant.parse("2026-10-05T00:00:00Z");
    static final Instant USE_END = Instant.parse("2026-10-31T00:00:00Z");

    private Coupon coupon(Grade requiredGrade) {
        return Coupon.create("가을 쿠폰", DiscountType.FIXED, 3_000, null, 10_000, 100, requiredGrade,
                ISSUE_START, ISSUE_END, USE_START, USE_END);
    }

    @Test
    void 발급_기간_경계() {
        Coupon coupon = coupon(null);

        assertThat(coupon.isIssuable(ISSUE_START.minusMillis(1), Grade.BABY)).isFalse();
        assertThat(coupon.isIssuable(ISSUE_START, Grade.BABY)).isTrue();
        assertThat(coupon.isIssuable(ISSUE_END.minusMillis(1), Grade.BABY)).isTrue();
        assertThat(coupon.isIssuable(ISSUE_END, Grade.BABY)).isFalse();
    }

    @Test
    void 등급_미달이면_발급_불가() {
        Instant now = ISSUE_START.plus(Duration.ofDays(1));
        Coupon pinkOnly = coupon(Grade.PINK);

        assertThat(pinkOnly.isIssuable(now, Grade.BABY)).isFalse();
        assertThat(pinkOnly.isIssuable(now, Grade.PINK)).isTrue();
        assertThat(pinkOnly.isIssuable(now, Grade.GOLD)).isTrue();
        assertThat(coupon(null).isIssuable(now, Grade.BABY)).isTrue();
    }

    @Test
    void 사용_기간_경계() {
        Coupon coupon = coupon(null);

        assertThat(coupon.isUsable(USE_START.minusMillis(1))).isFalse();
        assertThat(coupon.isUsable(USE_START)).isTrue();
        assertThat(coupon.isUsable(USE_END.minusMillis(1))).isTrue();
        assertThat(coupon.isUsable(USE_END)).isFalse();
    }

    @Test
    void 계산기_입력인_terms를_만든다() {
        Coupon rate = Coupon.create("정률", DiscountType.RATE, 15, 5_000L, 20_000, 10, null,
                ISSUE_START, ISSUE_END, USE_START, USE_END);

        assertThat(rate.terms()).isEqualTo(new CouponTerms(DiscountType.RATE, 15, 5_000L, 20_000));
        assertThat(coupon(null).terms()).isEqualTo(new CouponTerms(DiscountType.FIXED, 3_000, null, 10_000));
    }

    @Test
    void 정의가_잘못되면_INVALID_INPUT이다() {
        assertInvalid(DiscountType.RATE, 95, 5_000L, 0, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.RATE, 0, 5_000L, 0, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.RATE, 10, null, 0, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.RATE, 10, 0L, 0, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 3_000, 5_000L, 0, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 99, null, 0, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 1_000_001, null, 0, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 3_000, null, -1, 10, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 3_000, null, 0, 0, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 3_000, null, 0, 1_000_001, ISSUE_START, ISSUE_END, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 3_000, null, 0, 10, ISSUE_END, ISSUE_START, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 3_000, null, 0, 10, ISSUE_START, ISSUE_START, USE_START, USE_END);
        assertInvalid(DiscountType.FIXED, 3_000, null, 0, 10, ISSUE_START, ISSUE_END, USE_END, USE_START);
    }

    @Test
    void 경계_값은_만들어진다() {
        Coupon.create("최소", DiscountType.FIXED, 100, null, 0, 1, null, ISSUE_START, ISSUE_END, USE_START, USE_END);
        Coupon.create("최대", DiscountType.FIXED, 1_000_000, null, 0, 1_000_000, null,
                ISSUE_START, ISSUE_END, USE_START, USE_END);
        Coupon.create("정률 1", DiscountType.RATE, 1, 1L, 0, 1, null, ISSUE_START, ISSUE_END, USE_START, USE_END);
        Coupon.create("정률 90", DiscountType.RATE, 90, 1L, 0, 1, null, ISSUE_START, ISSUE_END, USE_START, USE_END);
    }

    private void assertInvalid(DiscountType type, long value, Long maxDiscount, long minOrderAmount, int quantity,
            Instant issueStart, Instant issueEnd, Instant useStart, Instant useEnd) {
        assertThatThrownBy(() -> Coupon.create("쿠폰", type, value, maxDiscount, minOrderAmount, quantity, null,
                issueStart, issueEnd, useStart, useEnd))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
    }
}
