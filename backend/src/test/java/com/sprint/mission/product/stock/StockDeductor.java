package com.sprint.mission.product.stock;

/** 재고 차감 방식을 비교하기 위한 테스트 전용 추상화다. */
public interface StockDeductor {

    /** 재고가 충분해 차감했으면 true, 부족해 차감하지 못했으면 false다. */
    boolean deduct(Long skuId, int quantity);
}
