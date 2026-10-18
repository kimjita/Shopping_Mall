package com.sprint.mission.coupon.dto;

import com.sprint.mission.coupon.domain.Coupon;
import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.coupon.domain.MemberCoupon;
import com.sprint.mission.coupon.domain.MemberCouponStatus;
import java.time.Instant;

/** 내가 받은 쿠폰 한 건이다. id는 주문에 쓰는 회원 쿠폰 ID다. */
public record MyCouponResponse(
        Long id,
        Long couponId,
        String name,
        DiscountType discountType,
        long discountValue,
        Long maxDiscount,
        long minOrderAmount,
        Status status,
        Instant useStartsAt,
        Instant useEndsAt,
        Instant usedAt,
        Instant issuedAt) {

    /** 화면 상태다. EXPIRED는 저장하지 않고 읽을 때 계산한다. */
    public enum Status {
        AVAILABLE, USED, EXPIRED
    }

    public static MyCouponResponse of(MemberCoupon mc, Coupon c, Instant now) {
        Status status = mc.getStatus() == MemberCouponStatus.USED ? Status.USED
                : !now.isBefore(c.getUseEndsAt()) ? Status.EXPIRED
                : Status.AVAILABLE;
        return new MyCouponResponse(mc.getId(), c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMaxDiscount(), c.getMinOrderAmount(), status, c.getUseStartsAt(), c.getUseEndsAt(),
                mc.getUsedAt(), mc.getCreatedAt());
    }
}
