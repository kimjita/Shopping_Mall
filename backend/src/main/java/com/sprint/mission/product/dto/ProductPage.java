package com.sprint.mission.product.dto;

import java.util.List;

/** 커서 방식의 상품 목록 한 페이지다. 마지막 페이지면 nextCursor가 null이다. */
public record ProductPage(List<ProductSummary> items, String nextCursor) {
}
