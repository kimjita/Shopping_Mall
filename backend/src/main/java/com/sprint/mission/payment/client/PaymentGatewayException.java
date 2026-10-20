package com.sprint.mission.payment.client;

import lombok.Getter;

/**
 * 결제사가 오류를 주었거나 응답을 읽지 못한 경우다. {@code status}는 HTTP 상태이고, {@code code}는 토스 오류 코드이며
 * 본문을 읽지 못하면 UNKNOWN이다. 4xx에 토스 코드가 있을 때만 결제사가 확실히 처리하지 않은 것이다.
 */
@Getter
public class PaymentGatewayException extends RuntimeException {

    private final int status;
    private final String code;

    public PaymentGatewayException(int status, String code, String message) {
        super(status + " " + code + ": " + message);
        this.status = status;
        this.code = code;
    }
}
