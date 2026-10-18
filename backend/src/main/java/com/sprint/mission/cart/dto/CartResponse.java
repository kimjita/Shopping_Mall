package com.sprint.mission.cart.dto;

import java.util.List;

/** 담은 순서의 장바구니다. 합계는 내려주지 않는다. */
public record CartResponse(List<CartItemResponse> items) {
}
