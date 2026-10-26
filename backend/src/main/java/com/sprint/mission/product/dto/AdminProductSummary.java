package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.ProductStatus;
import java.time.Instant;

/**
 * 관리자 상품 목록의 한 줄이다. saleRate는 설정된 세일률이라 기간이 지났거나 아직 시작하지 않은 세일도 값이 있고,
 * 지금 진행 중인지는 saleActive로 본다. minStock은 옵션 중 가장 적은 재고다.
 */
public record AdminProductSummary(
        Long id,
        String name,
        String brandName,
        String thumbnailUrl,
        ProductStatus status,
        long basePrice,
        Integer saleRate,
        boolean saleActive,
        int skuCount,
        int minStock,
        Instant createdAt) {
}
