package com.sprint.mission.product.dto;

import java.util.List;

/** 관리자 상품 한 페이지다. 마지막 페이지면 nextCursor가 null이다. */
public record AdminProductPage(List<AdminProductSummary> items, String nextCursor) {
}
