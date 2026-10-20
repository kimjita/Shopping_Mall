package com.sprint.mission.payment.client;

import java.time.Instant;

/** 토스 결제 응답에서 쓰는 필드. 승인 전 상태이면 {@code approvedAt}이 null이다. */
public record PaymentResult(String paymentKey, String orderId, String status, long totalAmount, String method,
                            Instant approvedAt) {

    public boolean isDone() {
        return "DONE".equals(status);
    }
}
