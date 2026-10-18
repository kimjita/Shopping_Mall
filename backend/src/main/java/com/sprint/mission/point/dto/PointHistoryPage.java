package com.sprint.mission.point.dto;

import java.util.List;

/** 커서 방식의 적립금 내역 한 페이지다. 마지막 페이지면 nextCursor가 null이다. */
public record PointHistoryPage(List<PointHistoryItem> items, String nextCursor) {
}
