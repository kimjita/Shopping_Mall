package com.sprint.mission.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkuRequest(
        @NotBlank(message = "옵션 이름은 1~50자여야 합니다.")
        @Size(max = 50, message = "옵션 이름은 1~50자여야 합니다.")
        String name,

        @Min(value = 0, message = "추가금액은 0~10,000,000원이어야 합니다.")
        @Max(value = 10_000_000, message = "추가금액은 0~10,000,000원이어야 합니다.")
        long additionalPrice,

        @Min(value = 0, message = "재고는 0~100,000개여야 합니다.")
        @Max(value = 100_000, message = "재고는 0~100,000개여야 합니다.")
        int stock) {

    /** 앞뒤 공백을 지운 값으로 검증하고 저장하도록 생성할 때 이름을 다듬는다. */
    public SkuRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
