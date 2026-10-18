package com.sprint.mission.coupon;

import com.sprint.mission.coupon.dto.CouponCreateRequest;
import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.member.domain.Grade;
import java.time.Duration;
import java.time.Instant;

/** 쿠폰 테스트 공용 픽스처다. 기본값은 "지금 받을 수 있고 지금 쓸 수 있는" 정액 3,000원 쿠폰이다. */
public final class CouponFixtures {

    private CouponFixtures() {
    }

    public static CouponCreateRequest fixed(String name, int totalQuantity, Grade requiredGrade) {
        Instant now = Instant.now();
        return new CouponCreateRequest(name, DiscountType.FIXED, 3_000L, null, 10_000L, totalQuantity,
                requiredGrade, now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)),
                now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(30)));
    }

    /** 발급 기간과 사용 기간을 직접 정한다. */
    public static CouponCreateRequest fixed(String name, int totalQuantity, Grade requiredGrade,
            Instant issueStartsAt, Instant issueEndsAt, Instant useStartsAt, Instant useEndsAt) {
        return new CouponCreateRequest(name, DiscountType.FIXED, 3_000L, null, 10_000L, totalQuantity,
                requiredGrade, issueStartsAt, issueEndsAt, useStartsAt, useEndsAt);
    }
}
