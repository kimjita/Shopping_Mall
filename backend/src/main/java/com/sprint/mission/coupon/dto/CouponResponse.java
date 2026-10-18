package com.sprint.mission.coupon.dto;

import com.sprint.mission.coupon.domain.Coupon;
import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.member.domain.Grade;
import java.time.Instant;

/** 관리자가 보는 쿠폰 정의다. */
public record CouponResponse(
        Long id,
        String name,
        DiscountType discountType,
        long discountValue,
        Long maxDiscount,
        long minOrderAmount,
        int totalQuantity,
        int issuedQuantity,
        Grade requiredGrade,
        Instant issueStartsAt,
        Instant issueEndsAt,
        Instant useStartsAt,
        Instant useEndsAt,
        Instant createdAt) {

    public static CouponResponse from(Coupon c) {
        return new CouponResponse(c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMaxDiscount(), c.getMinOrderAmount(), c.getTotalQuantity(), c.getIssuedQuantity(),
                c.getRequiredGrade(), c.getIssueStartsAt(), c.getIssueEndsAt(), c.getUseStartsAt(), c.getUseEndsAt(),
                c.getCreatedAt());
    }
}
