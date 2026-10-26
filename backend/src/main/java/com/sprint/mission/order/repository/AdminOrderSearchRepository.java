package com.sprint.mission.order.repository;

import static com.sprint.mission.order.domain.QOrder.order;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 관리자 주문 목록이다. 상태와 회원은 있는 것만 AND로 걸고 id 내림차순으로 읽는다. */
@Repository
@RequiredArgsConstructor
public class AdminOrderSearchRepository {

    private final JPAQueryFactory queryFactory;

    /** cursorId보다 작은 id의 주문을 limit개까지 읽는다. status·memberId·cursorId는 null이면 거르지 않는다. */
    public List<Order> search(OrderStatus status, Long memberId, Long cursorId, int limit) {
        List<Predicate> where = new ArrayList<>();
        if (status != null) {
            where.add(order.status.eq(status));
        }
        if (memberId != null) {
            where.add(order.memberId.eq(memberId));
        }
        if (cursorId != null) {
            where.add(order.id.lt(cursorId));
        }
        return queryFactory.selectFrom(order)
                .where(where.toArray(Predicate[]::new))
                .orderBy(order.id.desc())
                .limit(limit)
                .fetch();
    }
}
