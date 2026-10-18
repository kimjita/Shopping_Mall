package com.sprint.mission.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 옵션(SKU)을 뺀 상품 정보 수정 요청이다. 이미지는 보낸 목록으로 통째로 바뀐다. */
public record ProductUpdateRequest(
        @NotNull(message = "브랜드를 선택해 주세요.")
        Long brandId,

        @NotNull(message = "카테고리를 선택해 주세요.")
        Long categoryId,

        @NotBlank(message = "상품명은 1~100자여야 합니다.")
        @Size(max = 100, message = "상품명은 1~100자여야 합니다.")
        String name,

        @Size(max = 200, message = "요약은 200자 이하여야 합니다.")
        String summary,

        @NotBlank(message = "상세 설명을 입력해 주세요.")
        String description,

        String ingredients,

        @Min(value = 100, message = "정가는 100~10,000,000원이어야 합니다.")
        @Max(value = 10_000_000, message = "정가는 100~10,000,000원이어야 합니다.")
        long basePrice,

        @NotEmpty(message = "이미지는 1~10개여야 합니다.")
        @Size(max = 10, message = "이미지는 1~10개여야 합니다.")
        List<@NotBlank(message = "이미지 URL을 입력해 주세요.")
        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^https?://.+", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.") String> imageUrls) {

    /** 앞뒤 공백을 지운 값으로 검증하고 저장하도록 생성할 때 상품명을 다듬는다. */
    public ProductUpdateRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
