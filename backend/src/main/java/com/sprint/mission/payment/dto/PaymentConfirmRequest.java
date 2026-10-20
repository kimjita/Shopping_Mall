package com.sprint.mission.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 토스 결제창이 successUrl로 넘긴 값이다. orderId는 경로의 주문번호와 같으므로 받지 않는다. */
public record PaymentConfirmRequest(@NotBlank @Size(max = 200) String paymentKey, @Positive long amount) {
}
