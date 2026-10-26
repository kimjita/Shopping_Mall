package com.sprint.mission.admin.dto;

import java.time.LocalDate;

/** 관리자 대시보드 요약이다. date는 서울 기준 오늘이고, today* 지표는 그날 하루만 센다. */
public record AdminDashboardResponse(LocalDate date, long todayPaidCount, long todayPaidAmount, long paidCount,
        long preparingCount, long shippingCount, long lowStockSkuCount, long todaySignupCount) {
}
