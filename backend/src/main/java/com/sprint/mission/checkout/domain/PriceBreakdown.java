package com.sprint.mission.checkout.domain;

/** 주문 금액 계산 결과다. paymentAmount = itemsSalePrice - couponDiscount + shippingFee - pointsUsed 이다. */
public record PriceBreakdown(
        long itemsPrice,
        long saleDiscount,
        long itemsSalePrice,
        long couponDiscount,
        long shippingFee,
        long pointsUsed,
        long paymentAmount) {
}
