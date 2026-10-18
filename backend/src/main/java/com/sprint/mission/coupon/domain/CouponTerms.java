package com.sprint.mission.coupon.domain;

/** 금액 계산기가 쿠폰에서 읽는 값이다. maxDiscount는 정률 쿠폰에만 있다. */
public record CouponTerms(DiscountType type, long value, Long maxDiscount, long minOrderAmount) {
}
