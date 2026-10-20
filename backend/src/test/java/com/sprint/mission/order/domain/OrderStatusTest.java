package com.sprint.mission.order.domain;

import static com.sprint.mission.order.domain.OrderStatus.CANCELED;
import static com.sprint.mission.order.domain.OrderStatus.CONFIRMED;
import static com.sprint.mission.order.domain.OrderStatus.DELIVERED;
import static com.sprint.mission.order.domain.OrderStatus.PAID;
import static com.sprint.mission.order.domain.OrderStatus.PENDING;
import static com.sprint.mission.order.domain.OrderStatus.PREPARING;
import static com.sprint.mission.order.domain.OrderStatus.SHIPPING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderStatusTest {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PENDING, Set.of(PAID, CANCELED),
            PAID, Set.of(PREPARING, CANCELED),
            PREPARING, Set.of(SHIPPING, CANCELED),
            SHIPPING, Set.of(DELIVERED),
            DELIVERED, Set.of(CONFIRMED),
            CONFIRMED, Set.of(),
            CANCELED, Set.of());

    @Test
    void 허용된_전이만_가능하다() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void 진행_중_상태는_다섯_개다() {
        assertThat(OrderStatus.IN_PROGRESS).containsExactlyInAnyOrder(PENDING, PAID, PREPARING, SHIPPING, DELIVERED);
    }
}
