package com.sprint.mission.payment.client;

import java.util.Optional;

/** 결제사 연동 경계. 서비스는 이 인터페이스만 안다. */
public interface PaymentGateway {

    /** 결제를 승인한다. 거절이면 {@link PaymentGatewayException}, 시간 초과면 {@link PaymentTimeoutException}이다. */
    PaymentResult confirm(String paymentKey, String orderId, long amount);

    /** 주문번호로 결제를 조회한다. 결제가 없으면 비어 있다. */
    Optional<PaymentResult> findByOrderId(String orderId);

    /** 전체 취소한다. 이미 취소된 결제면 조용히 성공한다. */
    void cancel(String paymentKey, String cancelReason);
}
