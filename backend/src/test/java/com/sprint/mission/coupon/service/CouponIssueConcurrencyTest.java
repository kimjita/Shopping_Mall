package com.sprint.mission.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.IntegrationTest;
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
import org.springframework.jdbc.core.JdbcTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
class CouponIssueConcurrencyTest extends IntegrationTest {

    @Autowired
    CouponService couponService;

    @Autowired
    AdminCouponService adminCouponService;

    @Autowired
    MemberService memberService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** 모든 작업을 같은 순간에 시작시키고, 성공은 null, 실패는 ErrorCode로 모아 돌려준다. */
    private List<ErrorCode> runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        for (Runnable task : tasks) {
            Callable<ErrorCode> call = () -> {
                ready.countDown();
                start.await();
                try {
                    task.run();
                    return null;
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            };
            outcomes.add(executor.submit(call));
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        List<ErrorCode> results = new ArrayList<>();
        for (Future<ErrorCode> outcome : outcomes) {
            results.add(outcome.get());
        }
        return results;
    }

    @Test
    void 열_장을_백_명이_동시에_받으면_정확히_열_명이다() throws Exception {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("선착순 10장", 10, null)).id();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Long memberId = memberService.signup("user" + i + "@mall.com", "password1", "회원" + i, "01012345678");
            tasks.add(() -> couponService.download(memberId, couponId));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(results.stream().filter(r -> r == null).count()).isEqualTo(10);
        assertThat(results.stream().filter(r -> r == ErrorCode.COUPON_SOLD_OUT).count()).isEqualTo(90);
        assertThat(jdbcTemplate.queryForObject(
                "select issued_quantity from coupon where id = ?", Integer.class, couponId)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from member_coupon where coupon_id = ?", Integer.class, couponId)).isEqualTo(10);
    }

    @Test
    void 같은_회원이_동시에_다섯_번_받아도_한_장이다() throws Exception {
        Long couponId = adminCouponService.create(CouponFixtures.fixed("선착순 10장", 10, null)).id();
        Long memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> couponService.download(memberId, couponId));
        }

        List<ErrorCode> results = runConcurrently(tasks);

        assertThat(results.stream().filter(r -> r == null).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r == ErrorCode.COUPON_ALREADY_ISSUED).count()).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select issued_quantity from coupon where id = ?", Integer.class, couponId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from member_coupon where member_id = ?", Integer.class, memberId)).isEqualTo(1);
    }
}
