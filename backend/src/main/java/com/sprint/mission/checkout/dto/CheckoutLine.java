package com.sprint.mission.checkout.dto;

/** 주문서의 한 줄이다. 가격은 장바구니 응답과 같은 계산(옵션 정가, 진행 중인 세일가)이다. */
public record CheckoutLine(
        Long cartItemId,
        Long skuId,
        String productName,
        String skuName,
        String thumbnailUrl,
        int quantity,
        long unitPrice,
        long unitSalePrice,
        long lineTotal) {
}
