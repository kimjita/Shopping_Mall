package com.sprint.mission.order.service;

import com.sprint.mission.order.repository.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 1분마다 결제 기한이 지난 대기 주문을 취소한다. 주문마다 {@link OrderExpiryService#expire}를 따로 불러
 * 트랜잭션을 나누므로, 한 주문이 실패해도 나머지는 처리되고 실패한 주문은 다음 실행에서 다시 시도된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderExpiryService orderExpiryService;
    private final Clock clock;

    /** 한 실행은 하나의 now로 대상을 고르고 취소한다. 취소한 건수를 돌려준다. */
    @Scheduled(fixedDelay = 60_000)
    public int expireDue() {
        Instant now = Instant.now(clock);
        int expired = 0;
        long afterId = 0L;
        while (true) {
            List<Long> ids = orderRepository.findDueIds(now, afterId, Limit.of(BATCH_SIZE));
            if (ids.isEmpty()) {
                return expired;
            }
            for (Long id : ids) {
                try {
                    if (orderExpiryService.expire(id, now)) {
                        expired++;
                    }
                } catch (RuntimeException e) {
                    log.warn("주문 만료 처리 실패: orderId={}", id, e);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
    }
}
