package com.sprint.mission.order.dto;

import com.sprint.mission.checkout.domain.PriceBreakdown;
import com.sprint.mission.order.domain.CancelReason;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.domain.ShippingInfo;
import java.time.Instant;
import java.util.List;

/** 주문 상세다. 취소되지 않은 주문이면 canceledAt과 cancelReason이 null이다. */
public record OrderDetailResponse(
        String orderNumber,
        OrderStatus status,
        Instant orderedAt,
        Instant expiresAt,
        List<OrderItemResponse> items,
        PriceBreakdown price,
        String couponName,
        ShippingInfo shipping,
        Instant canceledAt,
        CancelReason cancelReason) {

    public static OrderDetailResponse of(Order order, List<OrderItem> items) {
        return new OrderDetailResponse(order.getOrderNumber(), order.getStatus(), order.getCreatedAt(),
                order.getExpiresAt(), items.stream().map(OrderItemResponse::from).toList(), order.price(),
                order.getCouponName(), order.getShipping(), order.getCanceledAt(), order.getCancelReason());
    }
}
