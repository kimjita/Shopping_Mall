package com.sprint.mission.point.dto;

import com.sprint.mission.point.domain.PointHistory;
import com.sprint.mission.point.domain.PointType;
import java.time.Instant;

/** 적립금 내역 한 줄이다. amount는 항상 양수이고 증감은 type으로 구분한다. */
public record PointHistoryItem(Long id, PointType type, long amount, long balanceAfter, String reason, Long orderId,
        Instant createdAt) {

    public static PointHistoryItem from(PointHistory history) {
        return new PointHistoryItem(history.getId(), history.getType(), history.getAmount(),
                history.getBalanceAfter(), history.getReason(), history.getOrderId(), history.getCreatedAt());
    }
}
