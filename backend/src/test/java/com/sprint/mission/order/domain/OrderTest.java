package com.sprint.mission.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.checkout.domain.PriceBreakdown;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderTest {

    static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    static final PriceBreakdown PRICE = new PriceBreakdown(40_000, 5_000, 35_000, 3_000, 0, 1_000, 31_000);
    static final ShippingInfo SHIPPING = new ShippingInfo("홍길동", "01012345678", "06236", "서울 강남구 테헤란로 1", "101호");

    private Order order() {
        return Order.place(1L, "20261019-7K3Q9ZP2", PRICE, 10L, "가을 쿠폰", SHIPPING, NOW);
    }

    @Test
    void 생성하면_PENDING이고_15분_뒤_만료된다() {
        Order order = order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(order.isExpired(NOW.plusSeconds(14 * 60 + 59))).isFalse();
        assertThat(order.isExpired(NOW.plus(Duration.ofMinutes(15)))).isTrue();
    }

    @Test
    void 취소하면_CANCELED와_사유_시각이_남는다() {
        Order order = order();

        order.cancel(CancelReason.EXPIRED, NOW.plusSeconds(900));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(order.getCancelReason()).isEqualTo(CancelReason.EXPIRED);
        assertThat(order.getCanceledAt()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    void 취소된_주문을_다시_취소하면_IllegalStateException() {
        Order order = order();
        order.cancel(CancelReason.REPLACED, NOW);

        assertThatThrownBy(() -> order.cancel(CancelReason.EXPIRED, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 가격_스냅샷을_그대로_돌려준다() {
        assertThat(order().price()).isEqualTo(PRICE);
    }

    @Test
    void 주문번호_쿠폰_배송지를_보관한다() {
        Order order = order();

        assertThat(order.getOrderNumber()).isEqualTo("20261019-7K3Q9ZP2");
        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.getMemberCouponId()).isEqualTo(10L);
        assertThat(order.getCouponName()).isEqualTo("가을 쿠폰");
        assertThat(order.getShipping()).isEqualTo(SHIPPING);
    }

    @Test
    void 주문_상품의_합계는_판매가_곱하기_수량이다() {
        OrderItem item = OrderItem.create(1L, 2L, 3L, "수분 크림", "50ml", "https://img/1.png", 20_000, 17_000, 3);

        assertThat(item.getLineTotal()).isEqualTo(51_000);
        assertThat(item.getUnitPrice()).isEqualTo(20_000);
        assertThat(item.getUnitSalePrice()).isEqualTo(17_000);
        assertThat(item.getQuantity()).isEqualTo(3);
    }
}
