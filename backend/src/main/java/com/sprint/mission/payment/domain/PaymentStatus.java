package com.sprint.mission.payment.domain;

/** 결제 상태다. 부분 취소가 없으므로 승인(DONE)과 전체 취소(CANCELED)뿐이다. */
public enum PaymentStatus {
    DONE, CANCELED
}
