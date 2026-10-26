package com.sprint.mission.admin.service;

import static com.sprint.mission.product.repository.AdminProductSearchRepository.LOW_STOCK_THRESHOLD;

import com.sprint.mission.admin.dto.AdminDashboardResponse;
import com.sprint.mission.admin.repository.AdminDashboardRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AdminDashboardRepository repository;
    private final Clock clock;

    /** 서울 오늘 [00:00, 내일 00:00)을 기준으로 지표를 모은다. */
    public AdminDashboardResponse summarize() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SEOUL);
        Instant from = today.atStartOfDay(SEOUL).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(SEOUL).toInstant();

        long[] paidToday = repository.sumPaidBetween(from, to);
        Map<String, Long> byStatus = repository.countOrdersByStatus();
        return new AdminDashboardResponse(today, paidToday[0], paidToday[1], byStatus.getOrDefault("PAID", 0L),
                byStatus.getOrDefault("PREPARING", 0L), byStatus.getOrDefault("SHIPPING", 0L),
                repository.countLowStockSkus(LOW_STOCK_THRESHOLD), repository.countSignupsBetween(from, to));
    }
}
