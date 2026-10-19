package com.sprint.mission.order.domain;

import com.sprint.mission.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문한 상품 한 줄이다. 상품명·옵션명·썸네일·가격은 주문 시점의 스냅샷이다. */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long skuId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false, length = 50)
    private String skuName;

    @Column(length = 500)
    private String thumbnailUrl;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private long unitSalePrice;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long lineTotal;

    public static OrderItem create(Long orderId, Long productId, Long skuId, String productName, String skuName,
            String thumbnailUrl, long unitPrice, long unitSalePrice, int quantity) {
        OrderItem item = new OrderItem();
        item.orderId = orderId;
        item.productId = productId;
        item.skuId = skuId;
        item.productName = productName;
        item.skuName = skuName;
        item.thumbnailUrl = thumbnailUrl;
        item.unitPrice = unitPrice;
        item.unitSalePrice = unitSalePrice;
        item.quantity = quantity;
        item.lineTotal = unitSalePrice * quantity;
        return item;
    }
}
