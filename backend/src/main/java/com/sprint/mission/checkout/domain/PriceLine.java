package com.sprint.mission.checkout.domain;

/** 계산기에 넘기는 주문 줄이다. 단가는 SKU 한 개의 정가와 세일가다. */
public record PriceLine(long unitPrice, long unitSalePrice, int quantity) {
}
