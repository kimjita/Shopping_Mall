package com.sprint.mission.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberCouponTest {

    static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");

    private MemberCoupon available() {
        MemberCoupon memberCoupon = new MemberCoupon();
        ReflectionTestUtils.setField(memberCoupon, "status", MemberCouponStatus.AVAILABLE);
        return memberCoupon;
    }

    @Test
    void 사용하면_USED와_주문_시각이_남는다() {
        MemberCoupon memberCoupon = available();

        memberCoupon.use(7L, NOW);

        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.USED);
        assertThat(memberCoupon.getUsedOrderId()).isEqualTo(7L);
        assertThat(memberCoupon.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void 복원하면_AVAILABLE이고_주문과_시각이_비워진다() {
        MemberCoupon memberCoupon = available();
        memberCoupon.use(7L, NOW);

        memberCoupon.restore();

        assertThat(memberCoupon.getStatus()).isEqualTo(MemberCouponStatus.AVAILABLE);
        assertThat(memberCoupon.getUsedOrderId()).isNull();
        assertThat(memberCoupon.getUsedAt()).isNull();
    }

    @Test
    void 사용된_쿠폰을_다시_사용하면_IllegalStateException() {
        MemberCoupon memberCoupon = available();
        memberCoupon.use(7L, NOW);

        assertThatThrownBy(() -> memberCoupon.use(8L, NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 사용하지_않은_쿠폰을_복원하면_IllegalStateException() {
        assertThatThrownBy(() -> available().restore()).isInstanceOf(IllegalStateException.class);
    }
}
