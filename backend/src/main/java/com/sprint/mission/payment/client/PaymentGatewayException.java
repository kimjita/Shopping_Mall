package com.sprint.mission.payment.client;

import lombok.Getter;

/** 결제사가 오류 본문을 준 경우다. {@code code}는 토스 오류 코드이고, 본문을 읽지 못하면 UNKNOWN이다. */
@Getter
public class PaymentGatewayException extends RuntimeException {

    private final String code;

    public PaymentGatewayException(String code, String message) {
        super(code + ": " + message);
        this.code = code;
    }
}
