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
import org.hibernate.annotations.DynamicUpdate;

/**
 * 상품의 옵션이자 재고 단위다. 이름은 상품 안에서 중복될 수 없다.
 * 재고는 벌크 UPDATE로 바뀌므로, 옵션을 수정할 때 읽어 둔 낡은 재고를 덮어쓰지 않도록 바뀐 컬럼만 갱신한다.
 */
@Getter
@Entity
@DynamicUpdate
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

    /** 이름과 추가금액을 바꾼다. 재고는 {@code SkuRepository.adjustStock}으로만 바꾼다. */
    public void update(String name, long additionalPrice) {
        this.name = name;
        this.additionalPrice = additionalPrice;
    }

    /** 정가에 추가금액을 더한 옵션 가격이다. */
    public long price(long basePrice) {
        return basePrice + additionalPrice;
    }

    public boolean isSoldOut() {
        return stock == 0;
    }
}
