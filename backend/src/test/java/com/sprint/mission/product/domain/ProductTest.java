package com.sprint.mission.product.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

    @Test
    void 세일이_없으면_옵션_정가다() {
        Product product = Product.create(1L, 2L, "토너", null, "설명", null, 14_900, List.of());
        Sku sku = Sku.create(1L, "300ml", 2_000, 5);

        assertThat(product.unitPrice(sku)).isEqualTo(16_900);
        assertThat(product.unitSalePrice(sku, Instant.parse("2026-10-18T00:00:00Z"))).isEqualTo(16_900);
    }

    @Test
    void 세일_중이면_옵션_정가에_세일을_적용한다() {
        Product product = Product.create(1L, 2L, "토너", null, "설명", null, 14_900, List.of());
        Sku sku = Sku.create(1L, "300ml", 2_000, 5);
        Instant starts = Instant.parse("2026-10-18T00:00:00Z");
        Instant ends = Instant.parse("2026-10-19T00:00:00Z");
        product.startSale(new Sale(15, starts, ends));

        assertThat(product.unitSalePrice(sku, starts)).isEqualTo(14_360);
        assertThat(product.unitSalePrice(sku, ends)).isEqualTo(16_900);
    }
}
