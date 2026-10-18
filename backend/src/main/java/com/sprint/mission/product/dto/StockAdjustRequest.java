package com.sprint.mission.product.dto;

import com.sprint.mission.global.validation.NonZero;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** {@code delta}는 양수면 입고, 음수면 출고다. */
public record StockAdjustRequest(
        @NonZero(message = "재고 증감량은 0이 아닌 -100,000~100,000이어야 합니다.")
        @Min(value = -100_000, message = "재고 증감량은 0이 아닌 -100,000~100,000이어야 합니다.")
        @Max(value = 100_000, message = "재고 증감량은 0이 아닌 -100,000~100,000이어야 합니다.")
        int delta) {
}
