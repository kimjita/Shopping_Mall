package com.sprint.mission.review.dto;

import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import java.time.Instant;

/** 아직 리뷰를 쓰지 않은 구매확정 주문상품이다. 상품명·옵션명·썸네일은 주문 시점의 스냅샷이다. */
public record WritableReviewItem(Long orderItemId, String orderNumber, Long productId, String productName,
        String skuName, String thumbnailUrl, Instant confirmedAt) {

    public static WritableReviewItem of(OrderItem item, Order order) {
        return new WritableReviewItem(item.getId(), order.getOrderNumber(), item.getProductId(),
                item.getProductName(), item.getSkuName(), item.getThumbnailUrl(), order.getConfirmedAt());
    }
}
