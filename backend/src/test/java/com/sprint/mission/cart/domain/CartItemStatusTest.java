package com.sprint.mission.cart.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;

class CartItemStatusTest {

    @Test
    void 판매중지는_재고와_상관없이_UNAVAILABLE() {
        assertThat(CartItemStatus.of(ProductStatus.STOPPED, 100, 1)).isEqualTo(CartItemStatus.UNAVAILABLE);
        assertThat(CartItemStatus.of(ProductStatus.STOPPED, 0, 1)).isEqualTo(CartItemStatus.UNAVAILABLE);
        assertThat(CartItemStatus.of(ProductStatus.DELETED, 0, 5)).isEqualTo(CartItemStatus.UNAVAILABLE);
        assertThat(CartItemStatus.of(ProductStatus.DELETED, 100, 1)).isEqualTo(CartItemStatus.UNAVAILABLE);
    }

    @Test
    void 재고_0은_SOLD_OUT() {
        assertThat(CartItemStatus.of(ProductStatus.ON_SALE, 0, 1)).isEqualTo(CartItemStatus.SOLD_OUT);
        assertThat(CartItemStatus.of(ProductStatus.ON_SALE, 0, 99)).isEqualTo(CartItemStatus.SOLD_OUT);
    }

    @Test
    void 재고가_담은_수량보다_적으면_NOT_ENOUGH_STOCK() {
        assertThat(CartItemStatus.of(ProductStatus.ON_SALE, 2, 3)).isEqualTo(CartItemStatus.NOT_ENOUGH_STOCK);
        assertThat(CartItemStatus.of(ProductStatus.ON_SALE, 1, 99)).isEqualTo(CartItemStatus.NOT_ENOUGH_STOCK);
    }

    @Test
    void 충분하면_AVAILABLE() {
        assertThat(CartItemStatus.of(ProductStatus.ON_SALE, 3, 3)).isEqualTo(CartItemStatus.AVAILABLE);
        assertThat(CartItemStatus.of(ProductStatus.ON_SALE, 100, 1)).isEqualTo(CartItemStatus.AVAILABLE);
    }
}
