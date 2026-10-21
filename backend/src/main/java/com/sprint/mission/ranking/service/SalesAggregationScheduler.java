package com.sprint.mission.ranking.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매시 정각(서울)에 최근 7일 판매량을 다시 집계한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesAggregationScheduler {

    private final SalesAggregationService salesAggregationService;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void run() {
        int changed = salesAggregationService.aggregate(clock.instant());
        log.info("판매량 집계 배치 완료: changed={}", changed);
    }
}
