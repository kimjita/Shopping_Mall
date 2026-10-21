package com.sprint.mission.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 본문은 앞뒤 공백을 뗀 길이가 10자 이상이어야 하는데, 이 조건은 서비스가 확인한다. */
public record ReviewWriteRequest(
        @NotNull(message = "주문 상품을 선택해 주세요.")
        Long orderItemId,

        @Min(value = 1, message = "별점은 1~5여야 합니다.")
        @Max(value = 5, message = "별점은 1~5여야 합니다.")
        int rating,

        @NotBlank(message = "리뷰 내용은 10~1,000자여야 합니다.")
        @Size(max = 1000, message = "리뷰 내용은 10~1,000자여야 합니다.")
        String content,

        @Size(max = 5, message = "사진은 5개 이하여야 합니다.")
        List<@NotBlank(message = "이미지 URL을 입력해 주세요.")
        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^https?://.+", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.") String> imageUrls) {

    /** 사진을 안 보내면 빈 목록이다. */
    public ReviewWriteRequest {
        if (imageUrls == null) {
            imageUrls = List.of();
        }
    }
}
