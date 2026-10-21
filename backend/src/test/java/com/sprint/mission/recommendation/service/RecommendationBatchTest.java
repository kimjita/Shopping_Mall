package com.sprint.mission.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.recommendation.RecommendationFixtures;
import com.sprint.mission.recommendation.repository.RecommendationRepository;
import com.sprint.mission.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RecommendationBatchTest extends IntegrationTest {

    @Autowired
    RecommendationService service;

    @Autowired
    RecommendationRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    RecommendationFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new RecommendationFixtures(jdbcTemplate);
    }

    private List<Long> productIds(SkinType skinType) {
        return repository.findBySkinType(skinType).stream().map(RecommendationRepository.Row::productId).toList();
    }

    @Test
    void 같은_피부타입_리뷰가_5건_이상인_상품만_평균_순으로_고른다() {
        Long four = fixtures.product("4건");
        fixtures.reviews(four, "OILY", 5, 4);
        Long five = fixtures.product("5건");
        fixtures.reviews(five, "OILY", 3, 5);
        Long best = fixtures.product("최고");
        fixtures.reviews(best, "OILY", 5, 5);
        // 지성 3건 + 건성 2건은 어느 타입으로도 5건이 안 된다.
        Long mixed = fixtures.product("섞임");
        fixtures.reviews(mixed, "OILY", 5, 3);
        fixtures.reviews(mixed, "DRY", 5, 2);

        assertThat(service.rebuild()).isEqualTo(2);

        assertThat(productIds(SkinType.OILY)).containsExactly(best, five);
        assertThat(productIds(SkinType.DRY)).isEmpty();
        List<RecommendationRepository.Row> rows = repository.findBySkinType(SkinType.OILY);
        assertThat(rows.get(0).rank()).isEqualTo(1);
        assertThat(rows.get(0).reviewCount()).isEqualTo(5);
        assertThat(rows.get(0).ratingAvg()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(rows.get(1).rank()).isEqualTo(2);
        assertThat(rows.get(1).ratingAvg()).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    void 동률이면_리뷰_수_그다음_상품_id_내림차순이다() {
        Long first = fixtures.product("먼저");
        fixtures.reviews(first, "DRY", 4, 5);
        Long many = fixtures.product("리뷰 많음");
        fixtures.reviews(many, "DRY", 4, 6);
        Long later = fixtures.product("나중");
        fixtures.reviews(later, "DRY", 4, 5);

        service.rebuild();

        assertThat(productIds(SkinType.DRY)).containsExactly(many, later, first);
    }

    @Test
    void 타입마다_20개까지다() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 22; i++) {
            Long id = fixtures.product("상품 " + i);
            fixtures.reviews(id, "NORMAL", 4, 5);
            ids.add(id);
        }
        Long other = fixtures.product("다른 타입");
        fixtures.reviews(other, "DRY", 4, 5);

        assertThat(service.rebuild()).isEqualTo(21);

        // 평균·리뷰 수가 같으니 id가 큰 20개가 남고, 가장 오래된 둘이 빠진다.
        assertThat(productIds(SkinType.NORMAL)).hasSize(20).doesNotContain(ids.get(0), ids.get(1));
        assertThat(repository.findBySkinType(SkinType.NORMAL).get(19).rank()).isEqualTo(20);
        assertThat(productIds(SkinType.DRY)).containsExactly(other);
    }

    @Test
    void 판매_중지_상품은_빠지고_다시_돌려도_결과가_같다() {
        Long selling = fixtures.product("판매 중");
        fixtures.reviews(selling, "SENSITIVE", 4, 5);
        Long stopped = fixtures.product("판매 중지");
        fixtures.reviews(stopped, "SENSITIVE", 5, 5);
        fixtures.stop(stopped);

        service.rebuild();
        List<RecommendationRepository.Row> first = repository.findBySkinType(SkinType.SENSITIVE);
        service.rebuild();

        assertThat(first).extracting(RecommendationRepository.Row::productId).containsExactly(selling);
        assertThat(repository.findBySkinType(SkinType.SENSITIVE)).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject("select count(*) from skin_recommendation", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 피부타입이_없는_리뷰는_세지_않는다() {
        Long product = fixtures.product("타입 없음");
        fixtures.reviews(product, null, 5, 5);

        assertThat(service.rebuild()).isZero();

        assertThat(jdbcTemplate.queryForObject("select count(*) from skin_recommendation", Integer.class)).isZero();
    }
}
