package com.sprint.mission.recommendation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 04:00(서울)에 피부 타입별 추천을 다시 만든다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationScheduler {

    private final RecommendationService recommendationService;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        int rows = recommendationService.rebuild();
        log.info("피부타입 추천 배치 완료: rows={}", rows);
    }
}
