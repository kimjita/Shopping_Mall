package com.sprint.mission.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank(message = "브랜드 이름은 1~50자여야 합니다.")
        @Size(max = 50, message = "브랜드 이름은 1~50자여야 합니다.")
        String name,

        @Size(max = 500, message = "브랜드 설명은 500자 이하여야 합니다.")
        String description) {

    /** 앞뒤 공백을 지운 값으로 검증하고 저장하도록 생성할 때 이름을 다듬는다. */
    public BrandRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
