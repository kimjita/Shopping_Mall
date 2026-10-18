package com.sprint.mission.coupon.domain;

/** 저장하는 회원 쿠폰 상태다. 화면의 EXPIRED는 저장하지 않고 사용 종료 시각으로 계산한다. */
public enum MemberCouponStatus {
    AVAILABLE, USED
}
