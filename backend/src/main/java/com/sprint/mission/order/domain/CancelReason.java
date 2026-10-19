package com.sprint.mission.order.domain;

/** 주문이 취소된 이유다. 사용자 취소(USER)는 Phase 7에서 추가한다. */
public enum CancelReason {
    /** 결제 기한이 지났다. */
    EXPIRED,
    /** 같은 회원이 새 주문을 만들어 이전 결제 대기 주문이 대체되었다. */
    REPLACED
}
