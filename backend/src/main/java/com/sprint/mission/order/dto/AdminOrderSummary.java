package com.sprint.mission.order.dto;

import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import java.time.Instant;

/** 관리자 주문 목록의 한 줄이다. 대표 상품은 첫 번째 주문상품이고, itemCount는 주문상품 줄 수다. */
public record AdminOrderSummary(
        String orderNumber,
        OrderStatus status,
        Long memberId,
        String memberEmail,
        Instant orderedAt,
        String firstProductName,
        int itemCount,
        long paymentAmount) {

    public static AdminOrderSummary of(Order order, String memberEmail, OrderItem first, int itemCount) {
        return new AdminOrderSummary(order.getOrderNumber(), order.getStatus(), order.getMemberId(), memberEmail,
                order.getCreatedAt(), first.getProductName(), itemCount, order.getPaymentAmount());
    }
}
