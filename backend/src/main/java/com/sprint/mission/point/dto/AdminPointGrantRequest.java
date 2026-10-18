package com.sprint.mission.point.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 적립금 지급 요청이다. */
public record AdminPointGrantRequest(
        @Min(value = 1, message = "지급 금액은 1~1,000,000원이어야 합니다.")
        @Max(value = 1_000_000, message = "지급 금액은 1~1,000,000원이어야 합니다.")
        long amount,
        @NotBlank(message = "지급 사유를 입력해 주세요.")
        @Size(max = 100, message = "지급 사유는 100자 이하여야 합니다.")
        String reason) {
}
