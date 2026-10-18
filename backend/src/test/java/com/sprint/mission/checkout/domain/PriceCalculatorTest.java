package com.sprint.mission.checkout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.coupon.domain.CouponTerms;
import com.sprint.mission.coupon.domain.DiscountType;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

    private static List<PriceLine> one(long itemsSalePrice) {
        return List.of(new PriceLine(itemsSalePrice, itemsSalePrice, 1));
    }

    private static CouponTerms fixed(long value, long minOrderAmount) {
        return new CouponTerms(DiscountType.FIXED, value, null, minOrderAmount);
    }

    private static CouponTerms rate(long percent, long maxDiscount, long minOrderAmount) {
        return new CouponTerms(DiscountType.RATE, percent, maxDiscount, minOrderAmount);
    }

    @Test
    void 쿠폰_적립금_없으면_세일가_합에_배송비만_더한다() {
        PriceBreakdown result = PriceCalculator.calculate(
                List.of(new PriceLine(20_000, 17_000, 1)), null, 0);

        assertThat(result).isEqualTo(new PriceBreakdown(20_000, 3_000, 17_000, 0, 2_500, 0, 19_500));
    }

    @Test
    void 배송비_경계() {
        // 쿠폰 적용 후 19,990원 -> 배송비 2,500원
        PriceBreakdown below = PriceCalculator.calculate(one(20_990), fixed(1_000, 0), 0);
        assertThat(below.couponDiscount()).isEqualTo(1_000);
        assertThat(below.shippingFee()).isEqualTo(2_500);
        assertThat(below.paymentAmount()).isEqualTo(22_490);

        // 쿠폰 적용 후 20,000원 -> 무료
        PriceBreakdown exact = PriceCalculator.calculate(one(21_000), fixed(1_000, 0), 0);
        assertThat(exact.shippingFee()).isZero();
        assertThat(exact.paymentAmount()).isEqualTo(20_000);
    }

    @Test
    void 정률_쿠폰은_10원_내림_후_상한을_적용한다() {
        // 33,333 x 15 / 100 = 4,999 -> 10원 내림 4,990
        assertThat(PriceCalculator.calculate(one(33_333), rate(15, 10_000, 0), 0).couponDiscount())
                .isEqualTo(4_990);
        assertThat(PriceCalculator.calculate(one(33_333), rate(15, 3_000, 0), 0).couponDiscount())
                .isEqualTo(3_000);
    }

    @Test
    void 정액_쿠폰은_세일가_합을_넘지_않는다() {
        PriceBreakdown result = PriceCalculator.calculate(one(5_000), fixed(10_000, 0), 0);

        assertThat(result.couponDiscount()).isEqualTo(5_000);
        assertThat(result.shippingFee()).isEqualTo(2_500);
        assertThat(result.paymentAmount()).isEqualTo(2_500);
    }

    @Test
    void 최소_주문금액과_같으면_적용되고_10원_모자라면_거절() {
        CouponTerms coupon = fixed(1_000, 30_000);

        assertThat(PriceCalculator.calculate(one(30_000), coupon, 0).couponDiscount()).isEqualTo(1_000);
        assertThatThrownBy(() -> PriceCalculator.calculate(one(29_990), coupon, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE));
    }

    @Test
    void 최소_주문금액은_쿠폰_할인_전_세일가_합으로_판단한다() {
        // 정가 합은 기준을 넘지만 세일가 합이 모자라면 거절한다.
        List<PriceLine> lines = List.of(new PriceLine(40_000, 29_990, 1));

        assertThatThrownBy(() -> PriceCalculator.calculate(lines, fixed(1_000, 30_000), 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE));
    }

    @Test
    void 적립금은_결제금액이_100원_남을_때까지() {
        List<PriceLine> lines = List.of(new PriceLine(20_000, 17_000, 1));

        PriceBreakdown ok = PriceCalculator.calculate(lines, null, 19_400);
        assertThat(ok.pointsUsed()).isEqualTo(19_400);
        assertThat(ok.paymentAmount()).isEqualTo(100);

        assertThatThrownBy(() -> PriceCalculator.calculate(lines, null, 19_410))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.POINTS_EXCEED_LIMIT));
    }

    @Test
    void 배송비는_적립금을_빼기_전_금액으로_판단한다() {
        // 쿠폰 후 20,000원이면 배송비 0, 적립금을 써도 배송비는 붙지 않는다.
        PriceBreakdown result = PriceCalculator.calculate(one(21_000), fixed(1_000, 0), 15_000);

        assertThat(result.shippingFee()).isZero();
        assertThat(result.paymentAmount()).isEqualTo(5_000);
    }

    @Test
    void 여러_줄의_합계() {
        List<PriceLine> lines = List.of(
                new PriceLine(10_000, 8_000, 2),
                new PriceLine(5_500, 5_500, 3));

        PriceBreakdown result = PriceCalculator.calculate(lines, null, 0);

        assertThat(result.itemsPrice()).isEqualTo(36_500);
        assertThat(result.itemsSalePrice()).isEqualTo(32_500);
        assertThat(result.saleDiscount()).isEqualTo(4_000);
        assertThat(result.shippingFee()).isZero();
        assertThat(result.paymentAmount()).isEqualTo(32_500);
    }

    @Test
    void 빈_목록은_IllegalArgumentException() {
        assertThatThrownBy(() -> PriceCalculator.calculate(List.of(), null, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 음수_적립금은_IllegalArgumentException() {
        assertThatThrownBy(() -> PriceCalculator.calculate(one(10_000), null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
