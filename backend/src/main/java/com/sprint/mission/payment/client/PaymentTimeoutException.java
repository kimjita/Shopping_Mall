package com.sprint.mission.payment.client;

/** 결제사와의 연결·응답 시간 초과나 I/O 오류다. 결제사 쪽 처리 여부는 알 수 없다. */
public class PaymentTimeoutException extends RuntimeException {

    public PaymentTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
