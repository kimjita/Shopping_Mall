package com.sprint.mission.recommendation.dto;

import com.sprint.mission.product.dto.ProductSummary;
import java.math.BigDecimal;

/** 추천 한 줄이다. rank는 배치가 매긴 순위이고, 평점·리뷰 수는 같은 피부 타입 리뷰만 센 값이다. */
public record RecommendationItem(int rank, ProductSummary product, int sameSkinReviewCount,
        BigDecimal sameSkinRatingAvg) {
}
