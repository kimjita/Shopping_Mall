package com.sprint.mission.order.dto;

import com.sprint.mission.order.domain.OrderItem;

/** 주문상품 한 줄이다. 상품명·옵션명·썸네일·가격은 주문 시점의 스냅샷이다. */
public record OrderItemResponse(
        Long productId,
        Long skuId,
        String productName,
        String skuName,
        String thumbnailUrl,
        long unitPrice,
        long unitSalePrice,
        int quantity,
        long lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getSkuId(), item.getProductName(), item.getSkuName(),
                item.getThumbnailUrl(), item.getUnitPrice(), item.getUnitSalePrice(), item.getQuantity(),
                item.getLineTotal());
    }
}
