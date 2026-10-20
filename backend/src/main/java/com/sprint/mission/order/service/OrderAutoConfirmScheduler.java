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
 * 매일 03:00(서울)에 배송 완료 7일이 지난 주문을 구매확정한다. 주문마다 {@link OrderConfirmService#autoConfirm}을
 * 따로 불러 트랜잭션을 나누므로, 한 주문이 실패해도 나머지는 처리되고 실패한 주문은 다음 실행에서 다시 시도된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoConfirmScheduler {

    private static final int BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final OrderConfirmService orderConfirmService;
    private final Clock clock;

    /** 한 실행은 하나의 now로 대상을 고르고 확정한다. 확정한 건수를 돌려준다. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public int confirmDue() {
        Instant now = Instant.now(clock);
        Instant deliveredBefore = now.minus(OrderConfirmService.AUTO_CONFIRM_AFTER);
        int confirmed = 0;
        long afterId = 0L;
        while (true) {
            List<Long> ids = orderRepository.findConfirmDueIds(deliveredBefore, afterId, Limit.of(BATCH_SIZE));
            if (ids.isEmpty()) {
                return confirmed;
            }
            for (Long id : ids) {
                try {
                    if (orderConfirmService.autoConfirm(id, now)) {
                        confirmed++;
                    }
                } catch (RuntimeException e) {
                    log.warn("주문 자동 구매확정 실패: orderId={}", id, e);
                }
            }
            afterId = ids.get(ids.size() - 1);
        }
    }
}
