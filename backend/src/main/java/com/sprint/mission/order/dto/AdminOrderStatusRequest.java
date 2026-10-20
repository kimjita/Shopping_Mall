package com.sprint.mission.order.dto;

import com.sprint.mission.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record AdminOrderStatusRequest(
        @NotNull(message = "상태를 선택해 주세요.")
        OrderStatus status) {
}
