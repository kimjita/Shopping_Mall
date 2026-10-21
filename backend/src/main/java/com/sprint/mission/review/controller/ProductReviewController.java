package com.sprint.mission.review.controller;

import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.review.domain.ReviewSort;
import com.sprint.mission.review.dto.ProductReviewPage;
import com.sprint.mission.review.service.ReviewQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 로그인하지 않아도 읽을 수 있는 상품 리뷰 목록이다. */
@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ReviewQueryService reviewQueryService;

    @GetMapping
    public ProductReviewPage list(@PathVariable Long productId,
            @RequestParam(defaultValue = "LATEST") ReviewSort sort,
            @RequestParam(required = false) SkinType skinType,
            @RequestParam(required = false) @Size(max = 100, message = "커서가 올바르지 않습니다.") String cursor,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size는 1~50이어야 합니다.")
            @Max(value = 50, message = "size는 1~50이어야 합니다.") int size) {
        return reviewQueryService.productReviews(productId, sort, skinType, cursor, size);
    }
}
