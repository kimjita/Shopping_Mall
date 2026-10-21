package com.sprint.mission.product.dto;

import java.math.BigDecimal;

/**
 * 상품 목록의 한 항목이다. price는 옵션 중 가장 싼 정가이고, salePrice는 현재 판매가다.
 * saleRate는 진행 중인 세일이 없으면 null이다. ratingAvg는 소수 둘째 자리까지의 평균 평점이고, 리뷰가 없으면 null이다.
 */
public record ProductSummary(
        Long id,
        String name,
        String brandName,
        String thumbnailUrl,
        long price,
        long salePrice,
        Integer saleRate,
        boolean soldOut,
        BigDecimal ratingAvg,
        int reviewCount) {
}
