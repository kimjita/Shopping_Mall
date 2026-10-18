package com.sprint.mission.coupon.service;

import com.sprint.mission.coupon.domain.CouponTerms;

/** 주문이 쓸 수 있다고 확인된 쿠폰이다. 금액 계산기에는 {@code terms}를 넘긴다. */
public record UsableCoupon(Long memberCouponId, Long couponId, String name, CouponTerms terms) {
}
