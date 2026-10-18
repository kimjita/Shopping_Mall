package com.sprint.mission.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartAddRequest(
        @NotNull(message = "옵션을 선택해 주세요.")
        Long skuId,

        @Min(value = 1, message = "수량은 1~99개여야 합니다.")
        @Max(value = 99, message = "수량은 1~99개여야 합니다.")
        int quantity) {
}
