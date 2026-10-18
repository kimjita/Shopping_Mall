package com.sprint.mission.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void 등록하면_ON_SALE이다() {
        Product product = Product.create(1L, 2L, "토너", "순한 토너", "설명", null, 12_900,
                List.of("https://img.example.com/1.jpg"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.isVisibleToPublic()).isTrue();
        assertThat(product.isDeleted()).isFalse();
        assertThat(product.getImageUrls()).containsExactly("https://img.example.com/1.jpg");
    }

    @Test
    void SKU_가격은_정가에_추가금액을_더한다() {
        Sku sku = Sku.create(1L, "300ml", 2_000, 5);

        assertThat(sku.price(12_900)).isEqualTo(14_900);
    }

    @Test
    void 재고_0이면_품절이다() {
        assertThat(Sku.create(1L, "300ml", 0, 0).isSoldOut()).isTrue();
        assertThat(Sku.create(1L, "150ml", 0, 1).isSoldOut()).isFalse();
    }
}
