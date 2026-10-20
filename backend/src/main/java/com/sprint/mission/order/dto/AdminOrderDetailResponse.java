package com.sprint.mission.order.dto;

import com.sprint.mission.payment.domain.Payment;
import java.time.Instant;

/** 관리자 주문 상세다. 주문 본문에 주문자와 결제 정보를 더한다. 결제 전 주문이면 payment가 null이다. */
public record AdminOrderDetailResponse(
        OrderDetailResponse order,
        Long memberId,
        String memberEmail,
        AdminPayment payment) {

    /** 취소된 결제도 남아 있고, 그때는 canceledAt이 채워진다. */
    public record AdminPayment(String paymentKey, String method, long amount, Instant approvedAt, Instant canceledAt) {

        public static AdminPayment from(Payment payment) {
            return new AdminPayment(payment.getPaymentKey(), payment.getMethod(), payment.getAmount(),
                    payment.getApprovedAt(), payment.getCanceledAt());
        }
    }
}
