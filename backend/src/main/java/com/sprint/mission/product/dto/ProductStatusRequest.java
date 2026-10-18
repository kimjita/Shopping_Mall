package com.sprint.mission.product.dto;

import com.sprint.mission.product.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(
        @NotNull(message = "상태를 선택해 주세요.")
        ProductStatus status) {
}
