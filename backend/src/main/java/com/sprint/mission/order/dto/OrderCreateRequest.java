package com.sprint.mission.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 주문 생성 요청이다. 앞의 세 값은 주문서 미리보기 요청과 같은 규칙이고(cartItemIds 중복은 서비스가 검증한다),
 * addressId는 내 주소록의 배송지다.
 */
public record OrderCreateRequest(
        @NotEmpty @Size(max = 50) List<@NotNull Long> cartItemIds,
        Long memberCouponId,
        @PositiveOrZero long points,
        @NotNull Long addressId) {
}
