package com.sprint.mission.point.domain;

/** 적립금 내역의 종류다. 금액은 항상 양수로 저장하고 증감은 종류로 구분한다. */
public enum PointType {
    /** 구매 적립(+) */
    EARN,
    /** 주문 사용(-) */
    USE,
    /** 주문 취소 등으로 사용분 복원(+) */
    RESTORE,
    /** 관리자 지급(+) */
    ADMIN_GRANT;

    public boolean isDeduction() {
        return this == USE;
    }
}
