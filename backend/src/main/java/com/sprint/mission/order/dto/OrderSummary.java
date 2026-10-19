package com.sprint.mission.order.dto;

import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import java.time.Instant;

/** 주문 목록의 한 줄이다. 대표 상품은 첫 번째 주문상품이고, itemCount는 주문상품 줄 수다. */
public record OrderSummary(
        String orderNumber,
        OrderStatus status,
        Instant orderedAt,
        String firstProductName,
        String thumbnailUrl,
        int itemCount,
        long paymentAmount) {

    public static OrderSummary of(Order order, OrderItem first, int itemCount) {
        return new OrderSummary(order.getOrderNumber(), order.getStatus(), order.getCreatedAt(),
                first.getProductName(), first.getThumbnailUrl(), itemCount, order.getPaymentAmount());
    }
}
