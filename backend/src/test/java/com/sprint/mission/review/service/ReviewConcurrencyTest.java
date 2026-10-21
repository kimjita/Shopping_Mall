package com.sprint.mission.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.review.ReviewFixtures;
import com.sprint.mission.review.dto.ReviewEditRequest;
import com.sprint.mission.review.dto.ReviewWriteRequest;
import com.sprint.mission.support.IntegrationTest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
@Import(ReviewFixtures.class)
class ReviewConcurrencyTest extends IntegrationTest {

    private static final String CONTENT = "촉촉하고 순해서 매일 쓰기 좋아요.";

    @Autowired
    ReviewService reviewService;

    @Autowired
    ReviewFixtures fixtures;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** 작업을 동시에 시작해 결과를 모은다. 성공은 null, 실패는 던진 예외다. */
    private List<Throwable> runConcurrently(List<Callable<?>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Throwable>> outcomes = new ArrayList<>();
        for (Callable<?> task : tasks) {
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    task.call();
                    return null;
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        List<Throwable> results = new ArrayList<>();
        for (Future<Throwable> outcome : outcomes) {
            results.add(outcome.get());
        }
        return results;
    }

    private static long successes(List<Throwable> results) {
        return results.stream().filter(r -> r == null).count();
    }

    private Long reviewId(Long memberId) {
        return jdbcTemplate.queryForObject("select id from review where member_id = ?", Long.class, memberId);
    }

    /** 남은 리뷰 행에서 다시 센 값이 상품 집계 컬럼과 같은지 본다. */
    private void assertAggregateMatchesRows(Long productId) {
        Long count = jdbcTemplate.queryForObject("select count(*) from review where product_id = ?", Long.class,
                productId);
        Long sum = jdbcTemplate.queryForObject("select coalesce(sum(rating), 0) from review where product_id = ?",
                Long.class, productId);
        assertThat(jdbcTemplate.queryForObject("select review_count from product where id = ?", Long.class, productId))
                .isEqualTo(count);
        assertThat(jdbcTemplate.queryForObject("select rating_sum from product where id = ?", Long.class, productId))
                .isEqualTo(sum);
        BigDecimal avg = jdbcTemplate.queryForObject("select rating_avg from product where id = ?", BigDecimal.class,
                productId);
        if (count == 0) {
            assertThat(avg).isNull();
        } else {
            assertThat(avg).isEqualByComparingTo(
                    BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));
        }
    }

    @Test
    void 여러_회원이_같은_상품에_동시에_쓰고_고치고_지워도_집계가_맞는다() throws Exception {
        Long productId = fixtures.product("독도 토너");
        List<Long> members = new ArrayList<>();
        List<Long> items = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            members.add(fixtures.member("user" + i + "@mall.com"));
            items.add(fixtures.orderItem(members.get(i), productId, "CONFIRMED"));
        }

        // 1단계: 20명이 동시에 쓴다. 별점은 1~5를 돌아가며 준다.
        List<Callable<?>> writes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int n = i;
            writes.add(() -> reviewService.write(members.get(n), new ReviewWriteRequest(items.get(n), n % 5 + 1,
                    CONTENT, List.of())));
        }
        assertThat(successes(runConcurrently(writes))).isEqualTo(20);
        assertAggregateMatchesRows(productId);

        // 2단계: 8명은 고치고(일부는 같은 별점), 6명은 지우고, 새 5명은 쓰는 일이 한꺼번에 일어난다.
        List<Callable<?>> mixed = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int n = i;
            int newRating = i < 2 ? n % 5 + 1 : (n + 2) % 5 + 1;
            mixed.add(() -> reviewService.edit(members.get(n), reviewId(members.get(n)),
                    new ReviewEditRequest(newRating, CONTENT, List.of())));
        }
        for (int i = 8; i < 14; i++) {
            int n = i;
            mixed.add(() -> {
                reviewService.delete(members.get(n), reviewId(members.get(n)));
                return null;
            });
        }
        for (int i = 20; i < 25; i++) {
            int n = i;
            mixed.add(() -> reviewService.write(members.get(n), new ReviewWriteRequest(items.get(n), 5, CONTENT,
                    List.of())));
        }
        assertThat(successes(runConcurrently(mixed))).isEqualTo(mixed.size());

        assertThat(jdbcTemplate.queryForObject("select count(*) from review where product_id = ?", Long.class,
                productId)).isEqualTo(19);
        assertAggregateMatchesRows(productId);
    }

    @Test
    void 같은_주문상품에_동시에_두_번_쓰면_하나만_성공한다() throws Exception {
        Long productId = fixtures.product("독도 토너");
        Long memberId = fixtures.member("user@mall.com");
        Long itemId = fixtures.orderItem(memberId, productId, "CONFIRMED");

        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            tasks.add(() -> reviewService.write(memberId, new ReviewWriteRequest(itemId, 4, CONTENT, List.of())));
        }
        List<Throwable> results = runConcurrently(tasks);

        assertThat(successes(results)).isEqualTo(1);
        Throwable failure = results.stream().filter(r -> r != null).findFirst().orElseThrow();
        assertThat(failure).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) failure).getErrorCode()).isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
        assertThat(jdbcTemplate.queryForObject("select count(*) from review", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select review_count || '/' || rating_sum from product where id = ?",
                String.class, productId)).isEqualTo("1/4");
        assertAggregateMatchesRows(productId);
    }
}
