package com.sprint.mission.checkout.dto;

import com.sprint.mission.checkout.domain.PriceBreakdown;
import java.util.List;

/** 주문서 미리보기 응답이다. pointBalance는 계산에 쓴 시점의 적립금 잔액이다. */
public record CheckoutPreviewResponse(List<CheckoutLine> lines, PriceBreakdown price, long pointBalance) {
}
