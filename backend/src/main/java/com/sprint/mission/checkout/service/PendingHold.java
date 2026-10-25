package com.sprint.mission.checkout.service;

import com.sprint.mission.order.domain.Order;

/**
 * 회원의 결제 대기 주문이 잡아 둔 쿠폰과 적립금이다. 새 주문을 만들면 그 주문이 취소되며 되돌아오므로,
 * 미리보기는 이것을 되돌린 것처럼 계산한다.
 */
public record PendingHold(Long orderId, Long memberCouponId, long pointsUsed) {

    public static final PendingHold NONE = new PendingHold(null, null, 0);

    static PendingHold of(Order order) {
        return new PendingHold(order.getId(), order.getMemberCouponId(), order.getPointsUsed());
    }
}
