package com.sprint.mission.cart.domain;

import com.sprint.mission.product.domain.ProductStatus;

/** 장바구니 한 줄의 구매 가능 상태다. 조회할 때마다 상품 상태와 현재 재고로 계산하고 저장하지 않는다. */
public enum CartItemStatus {
    AVAILABLE,
    /** 재고는 있지만 담은 수량보다 적다. */
    NOT_ENOUGH_STOCK,
    SOLD_OUT,
    /** 상품이 판매 중지되었거나 삭제되었다. */
    UNAVAILABLE;

    /** 우선순위는 UNAVAILABLE, SOLD_OUT, NOT_ENOUGH_STOCK, AVAILABLE 순이다. */
    public static CartItemStatus of(ProductStatus productStatus, int stock, int quantity) {
        if (productStatus != ProductStatus.ON_SALE) {
            return UNAVAILABLE;
        }
        if (stock <= 0) {
            return SOLD_OUT;
        }
        if (stock < quantity) {
            return NOT_ENOUGH_STOCK;
        }
        return AVAILABLE;
    }
}
