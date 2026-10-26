package com.sprint.mission.product.dto;

import com.sprint.mission.global.validation.InstantRange;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** 세일 설정 요청이다. 종료가 시작보다 늦는지는 서비스에서 확인한다. */
public record SaleRequest(
        @NotNull(message = "할인율을 입력해 주세요.")
        @Min(value = 1, message = "할인율은 1~90%여야 합니다.")
        @Max(value = 90, message = "할인율은 1~90%여야 합니다.")
        Integer rate,

        @NotNull(message = "세일 시작 시각을 입력해 주세요.")
        @InstantRange
        Instant startsAt,

        @NotNull(message = "세일 종료 시각을 입력해 주세요.")
        @InstantRange
        Instant endsAt) {
}
