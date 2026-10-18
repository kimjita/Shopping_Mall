package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.ProductStatus;

/**
 * 상품 id로 읽은 카드 한 장이다. 목록과 같은 요약에 상태를 붙여, 호출자가 판매 중지·삭제 상품을 다르게 다룰 수 있게 한다.
 */
public record ProductCard(ProductSummary summary, ProductStatus status) {
}
