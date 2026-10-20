package com.sprint.mission.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.payment.client.PaymentResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentTest {

    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    static final Instant APPROVED_AT = Instant.parse("2026-10-20T02:59:30Z");

    private PaymentResult result(Instant approvedAt) {
        return new PaymentResult("pay_key_1", "20261020-7K3Q9ZP2", "DONE", 31_000, "카드", approvedAt);
    }

    @Test
    void 승인_기록은_결과_필드를_복사한다() {
        Payment payment = Payment.approve(1L, result(APPROVED_AT), NOW);

        assertThat(payment.getOrderId()).isEqualTo(1L);
        assertThat(payment.getPaymentKey()).isEqualTo("pay_key_1");
        assertThat(payment.getAmount()).isEqualTo(31_000);
        assertThat(payment.getMethod()).isEqualTo("카드");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getApprovedAt()).isEqualTo(APPROVED_AT);
        assertThat(payment.getCanceledAt()).isNull();
    }

    @Test
    void 승인_시각이_없으면_now를_쓴다() {
        assertThat(Payment.approve(1L, result(null), NOW).getApprovedAt()).isEqualTo(NOW);
    }

    @Test
    void 취소하면_CANCELED와_취소_시각이_남는다() {
        Payment payment = Payment.approve(1L, result(APPROVED_AT), NOW);

        payment.cancel(NOW.plusSeconds(60));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCanceledAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void 두_번_취소하면_IllegalStateException() {
        Payment payment = Payment.approve(1L, result(APPROVED_AT), NOW);
        payment.cancel(NOW);

        assertThatThrownBy(() -> payment.cancel(NOW)).isInstanceOf(IllegalStateException.class);
    }
}
