package com.sprint.mission.order.domain;

/** 주문이 취소된 이유다. */
public enum CancelReason {
    /** 결제 기한이 지났다. */
    EXPIRED,
    /** 같은 회원이 새 주문을 만들어 이전 결제 대기 주문이 대체되었다. */
    REPLACED,
    /** 고객이 결제 완료 주문을 취소했다. */
    USER,
    /** 판매자가 결제 완료·상품 준비 중 주문을 취소했다. */
    ADMIN
}
