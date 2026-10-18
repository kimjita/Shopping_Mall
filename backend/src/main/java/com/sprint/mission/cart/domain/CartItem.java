package com.sprint.mission.cart.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 장바구니에 담은 옵션(SKU) 한 줄이다. (회원, SKU)는 하나뿐이다.
 * 수량 범위와 재고 검증은 서비스가 하고, 이 엔티티는 값만 바꾼다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long skuId;

    @Column(nullable = false)
    private int quantity;

    public static CartItem create(Long memberId, Long skuId, int quantity) {
        CartItem item = new CartItem();
        item.memberId = memberId;
        item.skuId = skuId;
        item.quantity = quantity;
        return item;
    }

    /** 수량을 대체한다. */
    public void changeQuantity(int quantity) {
        this.quantity = quantity;
    }

    /** 같은 옵션을 다시 담았을 때 수량을 더한다. */
    public void addQuantity(int quantity) {
        this.quantity += quantity;
    }
}
