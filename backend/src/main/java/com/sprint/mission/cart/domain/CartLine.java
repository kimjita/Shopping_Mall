package com.sprint.mission.cart.domain;

import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.ProductCard;

/**
 * 장바구니 한 줄과 가격 계산에 필요한 옵션·상품·카드를 묶은 읽기 전용 값이다.
 * 장바구니 응답과 결제 미리보기·주문이 같은 줄 계산을 쓰도록 한 곳에서 만든다.
 * unitPrice는 옵션 정가, unitSalePrice는 읽은 시각에 진행 중인 세일을 적용한 가격이다.
 */
public record CartLine(CartItem item, Sku sku, Product product, ProductCard card, long unitPrice,
        long unitSalePrice, CartItemStatus status) {

    /** 줄 합계다. 세일가에 수량을 곱한다. */
    public long lineTotal() {
        return unitSalePrice * item.getQuantity();
    }
}
