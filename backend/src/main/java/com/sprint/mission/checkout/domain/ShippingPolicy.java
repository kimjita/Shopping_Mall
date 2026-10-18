package com.sprint.mission.checkout.domain;

/** 배송비와 최소 결제금액 정책 상수다. */
public final class ShippingPolicy {

    /** 쿠폰 적용 후 금액이 이 값 이상이면 배송비가 무료다. */
    public static final long FREE_SHIPPING_THRESHOLD = 20_000;
    public static final long SHIPPING_FEE = 2_500;
    /** 적립금을 쓴 뒤에도 결제금액이 이 값 이상 남아야 한다. */
    public static final long MIN_PAYMENT_AMOUNT = 100;

    private ShippingPolicy() {
    }
}
