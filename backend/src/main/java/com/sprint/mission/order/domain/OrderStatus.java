package com.sprint.mission.order.domain;

import java.util.Set;

/** 주문 상태다. 허용되는 전이만 {@link #canTransitionTo}로 열어 둔다. */
public enum OrderStatus {
    PENDING, PAID, PREPARING, SHIPPING, DELIVERED, CONFIRMED, CANCELED;

    /** 아직 끝나지 않은 주문의 상태다. 이 상태의 주문이 있으면 회원은 탈퇴할 수 없다. */
    public static final Set<OrderStatus> IN_PROGRESS = Set.of(PENDING, PAID, PREPARING, SHIPPING, DELIVERED);

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == PAID || next == CANCELED;
            case PAID -> next == PREPARING || next == CANCELED;
            case PREPARING -> next == SHIPPING || next == CANCELED;
            case SHIPPING -> next == DELIVERED;
            case DELIVERED -> next == CONFIRMED;
            case CONFIRMED, CANCELED -> false;
        };
    }
}
