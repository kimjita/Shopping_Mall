package com.sprint.mission.review.dto;

import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinProfile;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.review.domain.Review;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** 공개 상품 리뷰 한 줄이다. 작성자는 이름을 가려서 보여 주고, 피부 정보는 작성 시점 스냅샷이다. */
public record ProductReviewResponse(Long reviewId, String authorName, int rating, String content,
        List<String> imageUrls, String skuName, SkinType skinType, SkinTone skinTone,
        List<SkinConcern> skinConcerns, Instant createdAt, Instant updatedAt) {

    public static ProductReviewResponse of(Review review, String authorName, String skuName) {
        SkinProfile profile = review.getSkinProfile();
        return new ProductReviewResponse(review.getId(), authorName, review.getRating(), review.getContent(),
                List.copyOf(review.getImageUrls()), skuName,
                profile == null ? null : profile.getSkinType(), profile == null ? null : profile.getSkinTone(),
                review.getSkinConcerns().stream().sorted(Comparator.comparing(Enum::name)).toList(),
                review.getCreatedAt(), review.getUpdatedAt());
    }
}
