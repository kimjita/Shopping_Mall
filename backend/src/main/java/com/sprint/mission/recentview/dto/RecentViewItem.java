package com.sprint.mission.recentview.dto;

import com.sprint.mission.product.dto.ProductSummary;
import java.time.Instant;

/** 최근 본 상품의 한 항목이다. available은 지금 판매 중인 상품인지 여부이고, 판매 중지된 상품은 false다. */
public record RecentViewItem(ProductSummary product, boolean available, Instant viewedAt) {
}
