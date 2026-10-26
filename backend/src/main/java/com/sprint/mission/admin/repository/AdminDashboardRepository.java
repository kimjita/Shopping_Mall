package com.sprint.mission.admin.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 대시보드 지표를 집계하는 네이티브 SQL이다. 지표 묶음마다 쿼리 한 번으로 읽는다. */
@Repository
@RequiredArgsConstructor
public class AdminDashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    /** 승인 시각이 [from, to)이고 주문이 PAID~CONFIRMED인 결제의 {주문 수, 결제 금액 합}이다. 판매량 집계와 같은 기준이다. */
    public long[] sumPaidBetween(Instant from, Instant to) {
        return jdbcTemplate.queryForObject(
                "select count(*), coalesce(sum(o.payment_amount), 0) from orders o join payment p on p.order_id = o.id "
                        + "where o.status in ('PAID', 'PREPARING', 'SHIPPING', 'DELIVERED', 'CONFIRMED') "
                        + "and p.approved_at >= ? and p.approved_at < ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)}, Timestamp.from(from), Timestamp.from(to));
    }

    /** 상태별 주문 수다. 주문이 없는 상태는 결과에 없다. */
    public Map<String, Long> countOrdersByStatus() {
        Map<String, Long> counts = new HashMap<>();
        jdbcTemplate.query("select status, count(*) from orders where status in ('PAID', 'PREPARING', 'SHIPPING') "
                + "group by status", rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                });
        return counts;
    }

    /** 판매 중 상품의 옵션 중 재고가 threshold 이하인 수다. */
    public long countLowStockSkus(int threshold) {
        return jdbcTemplate.queryForObject("select count(*) from sku s join product p on p.id = s.product_id "
                + "where p.status = 'ON_SALE' and s.stock <= ?", Long.class, threshold);
    }

    /** 가입 시각이 [from, to)인 회원 수다. 탈퇴 회원도 센다. */
    public long countSignupsBetween(Instant from, Instant to) {
        return jdbcTemplate.queryForObject("select count(*) from member where created_at >= ? and created_at < ?",
                Long.class, Timestamp.from(from), Timestamp.from(to));
    }
}
