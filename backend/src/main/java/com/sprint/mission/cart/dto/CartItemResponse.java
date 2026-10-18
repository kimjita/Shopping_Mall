package com.sprint.mission.cart.dto;

import com.sprint.mission.cart.domain.CartItemStatus;

/**
 * 장바구니 한 줄이다. unitPrice는 옵션 정가, unitSalePrice는 진행 중인 세일을 적용한 가격이고
 * lineTotal은 unitSalePrice × quantity다. 재고 수량은 내보내지 않고 status로만 알린다.
 */
public record CartItemResponse(
        Long id,
        Long skuId,
        String skuName,
        Long productId,
        String productName,
        String brandName,
        String thumbnailUrl,
        int quantity,
        long unitPrice,
        long unitSalePrice,
        long lineTotal,
        CartItemStatus status) {
}
