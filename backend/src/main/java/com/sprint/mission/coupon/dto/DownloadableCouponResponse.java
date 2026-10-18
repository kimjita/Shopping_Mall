package com.sprint.mission.coupon.dto;

import com.sprint.mission.coupon.domain.Coupon;
import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.member.domain.Grade;
import java.time.Instant;

/** 받을 수 있는 쿠폰 한 건이다. eligible이 false면 등급이 모자라 받기를 시도해도 거절된다. */
public record DownloadableCouponResponse(
        Long couponId,
        String name,
        DiscountType discountType,
        long discountValue,
        Long maxDiscount,
        long minOrderAmount,
        Grade requiredGrade,
        boolean eligible,
        int remainingQuantity,
        Instant issueEndsAt,
        Instant useStartsAt,
        Instant useEndsAt) {

    public static DownloadableCouponResponse of(Coupon c, Grade memberGrade) {
        return new DownloadableCouponResponse(c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMaxDiscount(), c.getMinOrderAmount(), c.getRequiredGrade(), c.isEligible(memberGrade),
                c.getTotalQuantity() - c.getIssuedQuantity(), c.getIssueEndsAt(), c.getUseStartsAt(),
                c.getUseEndsAt());
    }
}
