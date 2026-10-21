package com.sprint.mission.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.member.domain.SkinConcern;
import com.sprint.mission.member.domain.SkinProfile;
import com.sprint.mission.member.domain.SkinTone;
import com.sprint.mission.member.domain.SkinType;
import com.sprint.mission.review.ReviewFixtures;
import com.sprint.mission.review.domain.Review;
import com.sprint.mission.support.IntegrationTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Import(ReviewFixtures.class)
class ReviewPersistenceTest extends IntegrationTest {

    @Autowired
    ReviewFixtures fixtures;

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 저장한_뒤_새_트랜잭션에서_읽어도_사진_순서와_피부_스냅샷이_남는다() {
        Long productId = fixtures.product("독도 토너");
        Long memberId = fixtures.member("user@mall.com");
        Long itemId = fixtures.orderItem(memberId, productId, "CONFIRMED");
        List<String> images = List.of("https://img.example.com/3.jpg", "https://img.example.com/1.jpg",
                "https://img.example.com/2.jpg");

        Long id = tx.execute(status -> reviewRepository.save(Review.write(itemId, memberId, productId, 4,
                "촉촉하고 순해서 매일 쓰기 좋아요.", images, new SkinProfile(SkinType.OILY, SkinTone.COOL),
                Set.of(SkinConcern.PORE, SkinConcern.TROUBLE))).getId());

        tx.executeWithoutResult(status -> {
            Review loaded = reviewRepository.findById(id).orElseThrow();
            assertThat(loaded.getImageUrls()).containsExactlyElementsOf(images);
            assertThat(loaded.getSkinProfile().getSkinType()).isEqualTo(SkinType.OILY);
            assertThat(loaded.getSkinProfile().getSkinTone()).isEqualTo(SkinTone.COOL);
            assertThat(loaded.getSkinConcerns()).containsExactlyInAnyOrder(SkinConcern.PORE, SkinConcern.TROUBLE);
        });
    }
}
