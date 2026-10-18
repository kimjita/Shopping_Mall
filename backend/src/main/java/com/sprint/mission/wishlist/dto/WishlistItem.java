package com.sprint.mission.wishlist.dto;

import com.sprint.mission.product.dto.ProductSummary;
import java.time.Instant;

/** 찜 목록의 한 항목이다. available은 지금 판매 중인 상품인지 여부이고, 판매 중지된 상품은 false다. */
public record WishlistItem(ProductSummary product, boolean available, Instant wishedAt) {
}
