package com.sprint.mission.checkout.domain;

import com.sprint.mission.coupon.domain.CouponTerms;
import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import java.util.List;

/** 주문 금액을 계산하는 상태 없는 순수 계산기다. 적립금 잔액 확인은 호출하는 서비스가 한다. */
public final class PriceCalculator {

    private PriceCalculator() {
    }

    public static PriceBreakdown calculate(List<PriceLine> lines, CouponTerms couponOrNull, long points) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("주문 줄이 비어 있다");
        }
        if (points < 0) {
            throw new IllegalArgumentException("적립금은 음수일 수 없다");
        }

        long itemsPrice = 0;
        long itemsSalePrice = 0;
        for (PriceLine line : lines) {
            itemsPrice += line.unitPrice() * line.quantity();
            itemsSalePrice += line.unitSalePrice() * line.quantity();
        }
        long saleDiscount = itemsPrice - itemsSalePrice;

        long couponDiscount = couponOrNull == null ? 0 : couponDiscount(couponOrNull, itemsSalePrice);

        long afterCoupon = itemsSalePrice - couponDiscount;
        long shippingFee = afterCoupon >= ShippingPolicy.FREE_SHIPPING_THRESHOLD ? 0 : ShippingPolicy.SHIPPING_FEE;

        long paymentAmount = afterCoupon + shippingFee - points;
        if (paymentAmount < ShippingPolicy.MIN_PAYMENT_AMOUNT) {
            throw new BusinessException(ErrorCode.POINTS_EXCEED_LIMIT);
        }
        return new PriceBreakdown(itemsPrice, saleDiscount, itemsSalePrice,
                couponDiscount, shippingFee, points, paymentAmount);
    }

    private static long couponDiscount(CouponTerms coupon, long itemsSalePrice) {
        if (itemsSalePrice < coupon.minOrderAmount()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE);
        }
        if (coupon.type() == DiscountType.FIXED) {
            return Math.min(coupon.value(), itemsSalePrice);
        }
        long rateDiscount = itemsSalePrice * coupon.value() / 100 / 10 * 10;
        return Math.min(rateDiscount, coupon.maxDiscount());
    }
}
