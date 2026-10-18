package com.sprint.mission.product.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품의 옵션이자 재고 단위다. 이름은 상품 안에서 중복될 수 없다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sku extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private long additionalPrice;

    @Column(nullable = false)
    private int stock;

    public static Sku create(Long productId, String name, long additionalPrice, int stock) {
        Sku sku = new Sku();
        sku.productId = productId;
        sku.name = name;
        sku.additionalPrice = additionalPrice;
        sku.stock = stock;
        return sku;
    }

    /** 정가에 추가금액을 더한 옵션 가격이다. */
    public long price(long basePrice) {
        return basePrice + additionalPrice;
    }

    public boolean isSoldOut() {
        return stock == 0;
    }
}
