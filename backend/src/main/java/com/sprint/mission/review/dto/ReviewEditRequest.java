package com.sprint.mission.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 쓸 때와 같은 규칙이다. 사진 목록은 통째로 바꾸고, 비우면 사진을 모두 지운다. */
public record ReviewEditRequest(
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

    public ReviewEditRequest {
        if (imageUrls == null) {
            imageUrls = List.of();
        }
    }
}
