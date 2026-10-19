package com.sprint.mission.checkout.service;

import com.sprint.mission.cart.domain.CartLine;
import com.sprint.mission.checkout.domain.PriceBreakdown;
import com.sprint.mission.coupon.service.UsableCoupon;
import java.util.List;

/**
 * 검증을 마친 주문서다. 미리보기 응답의 재료이면서 주문 생성이 그대로 이어받는 값이다.
 * coupon은 쿠폰을 쓰지 않으면 null이고, pointBalance는 검증에 쓴 시점의 잔액이다.
 */
public record Checkout(List<CartLine> lines, UsableCoupon coupon, PriceBreakdown breakdown, long pointBalance) {
}
