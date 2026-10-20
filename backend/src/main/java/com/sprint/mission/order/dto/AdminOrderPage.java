package com.sprint.mission.order.dto;

import java.util.List;

/** 관리자 주문 한 페이지다. 마지막 페이지면 nextCursor가 null이다. */
public record AdminOrderPage(List<AdminOrderSummary> items, String nextCursor) {
}
