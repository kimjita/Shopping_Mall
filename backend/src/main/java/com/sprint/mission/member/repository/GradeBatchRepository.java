package com.sprint.mission.member.repository;

import com.sprint.mission.member.domain.Grade;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 월간 등급 배치가 쓰는 네이티브 SQL이다. 엔티티를 거치지 않고 묶음 단위로 읽고 쓴다. */
@Repository
@RequiredArgsConstructor
public class GradeBatchRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    /** afterId보다 큰 ACTIVE 회원 id를 오름차순으로 limit개 돌려준다. */
    public List<Long> findActiveMemberIds(Long afterId, int limit) {
        return jdbcTemplate.queryForList(
                "select id from member where status = 'ACTIVE' and id > ? order by id limit ?",
                Long.class, afterId, limit);
    }

    /**
     * 기간 안(from 이상, to 미만)에 구매확정된 주문의 적립 기준 금액을 회원별로 합친다. 주문이 없는 회원은 결과에 없다.
     * 적립 기준 금액은 {@code Order.earnBase()}와 같은 {@code greatest(결제금액 - 배송비, 0)}이며 둘을 함께 고친다.
     */
    public Map<Long, Long> sumConfirmedAmounts(Collection<Long> memberIds, Instant from, Instant to) {
        Map<Long, Long> sums = new HashMap<>();
        namedJdbcTemplate.query(
                "select member_id, sum(greatest(payment_amount - shipping_fee, 0)) as amount from orders "
                        + "where member_id in (:ids) and status = 'CONFIRMED' "
                        + "and confirmed_at >= :from and confirmed_at < :to group by member_id",
                new MapSqlParameterSource("ids", memberIds)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to)),
                rs -> {
                    sums.put(rs.getLong("member_id"), rs.getLong("amount"));
                });
        return sums;
    }

    /**
     * 등급이 다른 회원만 바꾸고 실제로 바뀐 수를 돌려준다. 주문 흐름이 회원 행 하나씩 잠그는 순서와 같도록
     * id 오름차순으로 갱신해 교착을 피한다.
     */
    public int updateGrades(Map<Long, Grade> grades, Instant now) {
        Timestamp updatedAt = Timestamp.from(now);
        List<Object[]> args = new TreeMap<>(grades).entrySet().stream()
                .map(e -> new Object[] {e.getValue().name(), updatedAt, e.getKey(), e.getValue().name()})
                .toList();
        int changed = 0;
        for (int count : jdbcTemplate.batchUpdate(
                "update member set grade = ?, updated_at = ? where id = ? and grade <> ?", args)) {
            changed += count;
        }
        return changed;
    }
}
