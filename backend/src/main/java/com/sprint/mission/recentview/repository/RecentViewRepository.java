package com.sprint.mission.recentview.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 엔티티 없이 네이티브 SQL로만 다룬다. 쓰기는 모두 문장 하나로 끝나서 동시에 호출해도 안전하다. */
@Repository
@RequiredArgsConstructor
public class RecentViewRepository {

    /** 본 상품 한 건이다. */
    public record Viewed(Long productId, Instant viewedAt) {
    }

    private final JdbcTemplate jdbcTemplate;

    /** 이미 본 상품이면 본 시각만 갱신한다. 예외를 잡는 대신 {@code on conflict}로 멱등하게 만든다. */
    public void upsert(Long memberId, Long productId, Instant viewedAt) {
        jdbcTemplate.update("insert into recent_view (member_id, product_id, viewed_at) values (?, ?, ?) "
                        + "on conflict (member_id, product_id) do update set viewed_at = excluded.viewed_at",
                memberId, productId, Timestamp.from(viewedAt));
    }

    /** 최근에 본 keep개만 남기고 나머지를 지운다. */
    public void trim(Long memberId, int keep) {
        jdbcTemplate.update("delete from recent_view where member_id = ? and id not in ("
                        + "select id from recent_view where member_id = ? order by viewed_at desc, id desc limit ?)",
                memberId, memberId, keep);
    }

    public List<Viewed> findLatest(Long memberId, int limit) {
        return jdbcTemplate.query("select product_id, viewed_at from recent_view where member_id = ? "
                        + "order by viewed_at desc, id desc limit ?",
                (rs, rowNum) -> new Viewed(rs.getLong("product_id"), rs.getTimestamp("viewed_at").toInstant()),
                memberId, limit);
    }

    public void delete(Long memberId, Long productId) {
        jdbcTemplate.update("delete from recent_view where member_id = ? and product_id = ?", memberId, productId);
    }

    public void deleteAll(Long memberId) {
        jdbcTemplate.update("delete from recent_view where member_id = ?", memberId);
    }
}
