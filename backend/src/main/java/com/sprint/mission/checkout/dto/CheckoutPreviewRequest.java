package com.sprint.mission.checkout.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 주문서 미리보기 요청이다. cartItemIds는 서로 달라야 하고(서비스가 검증한다), memberCouponId가 null이면 쿠폰을 쓰지 않는다. */
public record CheckoutPreviewRequest(
        @NotEmpty @Size(max = 50) List<@NotNull Long> cartItemIds,
        Long memberCouponId,
        @PositiveOrZero long points) {
}
