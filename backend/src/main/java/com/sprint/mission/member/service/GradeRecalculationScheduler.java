package com.sprint.mission.member.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매월 1일 00:00(서울)에 그 달을 실행 월로 등급을 다시 매긴다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeRecalculationScheduler {

    private final GradeRecalculationService gradeRecalculationService;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")
    public void run() {
        // Clock.withZone을 거치지 않고 서울 날짜를 직접 구한다. 시계 구현에 따라 withZone이 존을 바꾸지 않을 수 있다.
        YearMonth runMonth = YearMonth.from(LocalDate.ofInstant(clock.instant(), GradeRecalculationService.SEOUL));
        int changed = gradeRecalculationService.recalculate(runMonth);
        log.info("월간 등급 배치 완료: runMonth={}, changed={}", runMonth, changed);
    }
}
