package com.sprint.mission.recommendation.repository;

import com.sprint.mission.member.domain.SkinType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 피부 타입별 추천 테이블을 통째로 다시 만들고 읽는 네이티브 SQL이다. 엔티티를 거치지 않는다. */
@Repository
@RequiredArgsConstructor
public class RecommendationRepository {

    /** 저장된 추천 한 줄이다. */
    public record Row(int rank, Long productId, int reviewCount, BigDecimal ratingAvg) {
    }

    private final JdbcTemplate jdbcTemplate;

    /**
     * 기존 행을 지우고 다시 넣어 넣은 행 수를 돌려준다. 호출자의 트랜잭션 안에서 실행해야
     * 읽는 쪽이 커밋 전까지 이전 결과를 계속 본다. 판매 중이고 같은 피부 타입 리뷰가 5건 이상인 상품 중
     * 평균 평점, 리뷰 수, 상품 id 내림차순으로 타입마다 20개를 고른다.
     */
    public int rebuild(Instant now) {
        jdbcTemplate.update("delete from skin_recommendation");
        return jdbcTemplate.update(
                "insert into skin_recommendation (skin_type, rank, product_id, review_count, rating_avg, created_at) "
                        + "select skin_type, rn, product_id, cnt, avg_rating, ? from ("
                        + "select r.skin_type, r.product_id, count(*) cnt, round(avg(r.rating)::numeric, 2) avg_rating, "
                        + "row_number() over (partition by r.skin_type "
                        + "order by avg(r.rating) desc, count(*) desc, r.product_id desc) rn "
                        + "from review r join product p on p.id = r.product_id "
                        + "where r.skin_type is not null and p.status = 'ON_SALE' "
                        + "group by r.skin_type, r.product_id "
                        + "having count(*) >= 5) t "
                        + "where rn <= 20",
                Timestamp.from(now));
    }

    /** 순위 오름차순이다. */
    public List<Row> findBySkinType(SkinType skinType) {
        return jdbcTemplate.query(
                "select rank, product_id, review_count, rating_avg from skin_recommendation "
                        + "where skin_type = ? order by rank",
                (rs, i) -> new Row(rs.getInt("rank"), rs.getLong("product_id"), rs.getInt("review_count"),
                        rs.getBigDecimal("rating_avg")),
                skinType.name());
    }
}
