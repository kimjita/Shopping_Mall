package com.sprint.mission.ranking.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 판매량 배치가 쓰는 네이티브 SQL이다. 엔티티를 거치지 않고 묶음 단위로 읽고 쓴다. */
@Repository
@RequiredArgsConstructor
public class SalesBatchRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 승인 시각이 (from, now]인 결제의 주문상품 수량을 상품별로 합친다. 회원·주문·상품 상태와 상관없이 센다.
     * 판매가 없는 상품은 결과에 없다.
     */
    public Map<Long, Integer> sumPaidQuantities(Instant from, Instant now) {
        Map<Long, Integer> sums = new HashMap<>();
        jdbcTemplate.query(
                "select oi.product_id, sum(oi.quantity) as quantity from order_item oi "
                        + "join orders o on o.id = oi.order_id join payment p on p.order_id = o.id "
                        + "where o.status in ('PAID', 'PREPARING', 'SHIPPING', 'DELIVERED', 'CONFIRMED') "
                        + "and p.approved_at > ? and p.approved_at <= ? group by oi.product_id",
                rs -> {
                    sums.put(rs.getLong("product_id"), rs.getInt("quantity"));
                }, Timestamp.from(from), Timestamp.from(now));
        return sums;
    }

    /** 지금 판매량이 0보다 큰 상품 id다. 판매가 끊긴 상품을 0으로 내리려고 읽는다. */
    public List<Long> findProductIdsWithSales() {
        return jdbcTemplate.queryForList("select id from product where sales_7d > 0", Long.class);
    }

    /**
     * 판매량이 다른 상품만 바꾸고 실제로 바뀐 수를 돌려준다. 같은 상품 행을 잠그는 다른 쓰기와 순서가 같도록
     * id 오름차순으로 갱신해 교착을 피한다. updated_at은 건드리지 않는다.
     */
    public int updateSales(Map<Long, Integer> sales) {
        List<Object[]> args = new TreeMap<>(sales).entrySet().stream()
                .map(e -> new Object[] {e.getValue(), e.getKey(), e.getValue()})
                .toList();
        int changed = 0;
        for (int count : jdbcTemplate.batchUpdate(
                "update product set sales_7d = ? where id = ? and sales_7d <> ?", args)) {
            // SUCCESS_NO_INFO(-2) 같은 음수는 건수를 알 수 없으므로 0으로 센다.
            changed += Math.max(count, 0);
        }
        return changed;
    }
}
