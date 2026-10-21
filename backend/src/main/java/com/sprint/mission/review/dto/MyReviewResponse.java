package com.sprint.mission.review.dto;

import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinProfile;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.review.domain.Review;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 상품명·옵션명·썸네일은 주문상품 스냅샷이고, 피부 정보는 작성 시점 스냅샷이다. */
public record MyReviewResponse(Long reviewId, Long orderItemId, Long productId, String productName, String skuName,
        String thumbnailUrl, int rating, String content, List<String> imageUrls, SkinType skinType,
        SkinTone skinTone, List<SkinConcern> skinConcerns, Instant createdAt, Instant updatedAt) {

    public static MyReviewResponse of(Review review, OrderItem item) {
        SkinProfile profile = review.getSkinProfile();
        return new MyReviewResponse(review.getId(), review.getOrderItemId(), review.getProductId(),
                item.getProductName(), item.getSkuName(), item.getThumbnailUrl(), review.getRating(),
                review.getContent(), List.copyOf(review.getImageUrls()),
                profile == null ? null : profile.getSkinType(), profile == null ? null : profile.getSkinTone(),
                review.getSkinConcerns().stream().sorted(Comparator.comparing(Enum::name)).toList(),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
