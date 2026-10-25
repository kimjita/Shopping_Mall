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
        return of(mc, c, now, false);
    }

    /** released면 결제 대기 주문이 쓴 쿠폰을 되돌린 것처럼 AVAILABLE(또는 EXPIRED)로, usedAt은 null로 내린다. */
    public static MyCouponResponse of(MemberCoupon mc, Coupon c, Instant now, boolean released) {
        boolean used = mc.getStatus() == MemberCouponStatus.USED && !released;
        Status status = used ? Status.USED
                : !now.isBefore(c.getUseEndsAt()) ? Status.EXPIRED
                : Status.AVAILABLE;
        return new MyCouponResponse(mc.getId(), c.getId(), c.getName(), c.getDiscountType(), c.getDiscountValue(),
                c.getMaxDiscount(), c.getMinOrderAmount(), status, c.getUseStartsAt(), c.getUseEndsAt(),
                used ? mc.getUsedAt() : null, mc.getCreatedAt());
    }
}
