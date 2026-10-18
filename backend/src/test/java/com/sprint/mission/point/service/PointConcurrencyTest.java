package com.sprint.mission.point.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.domain.Member;
import com.sprint.mission.member.repository.MemberRepository;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
class PointConcurrencyTest extends IntegrationTest {

    @Autowired
    PointService pointService;

    @Autowired
    MemberService memberService;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    Long memberId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
    }

    private long storedBalance() {
        return jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId);
    }

    @Test
    void 동시에_써도_잔액이_음수가_되지_않는다() throws Exception {
        pointService.grant(memberId, 1_000, "지급");
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long orderId = i + 1;
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    pointService.use(memberId, 300, "주문 사용", orderId);
                    return null;
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            }));
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<ErrorCode> failures = new ArrayList<>();
        for (Future<ErrorCode> outcome : outcomes) {
            ErrorCode failure = outcome.get();
            if (failure != null) {
                failures.add(failure);
            }
        }
        assertThat(failures).hasSize(2).containsOnly(ErrorCode.POINTS_NOT_ENOUGH);
        assertThat(storedBalance()).isEqualTo(100);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from point_history where type = 'USE'", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList(
                "select balance_after from point_history where type = 'USE' order by balance_after desc",
                Long.class)).containsExactly(700L, 400L, 100L);
    }

    @Test
    void 프로필_수정과_적립금_지급이_동시에_일어나도_잔액이_유실되지_않는다() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // 수정 트랜잭션이 잔액 0인 회원을 읽은 뒤, 다른 스레드가 +500을 커밋한다.
                Member member = memberRepository.findById(memberId).orElseThrow();
                try {
                    executor.submit(() -> pointService.grant(memberId, 500, "테스트")).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                member.updateProfile("새이름", "01099998888");
                // 읽기 전용 매핑이 아니면 이 값이 UPDATE에 실려 잔액을 덮어쓴다.
                ReflectionTestUtils.setField(member, "pointBalance", 12_345L);
            });
        } finally {
            executor.shutdown();
        }

        assertThat(storedBalance()).isEqualTo(500);
        assertThat(jdbcTemplate.queryForObject("select name from member where id = ?", String.class, memberId))
                .isEqualTo("새이름");
    }

    @Test
    void 영속성_컨텍스트의_회원이_낡아도_잔액과_balance_after가_맞다() throws Exception {
        pointService.grant(memberId, 1_000, "처음 지급");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        long[] balanceAfterUse = new long[1];
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // 잔액 1,000인 회원이 1차 캐시에 올라간 뒤, 다른 스레드가 +500을 커밋한다.
                memberRepository.findById(memberId).orElseThrow();
                try {
                    executor.submit(() -> pointService.grant(memberId, 500, "끼어든 지급")).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                balanceAfterUse[0] = pointService.use(memberId, 300, "주문 사용", 1L);
            });
        } finally {
            executor.shutdown();
        }

        assertThat(balanceAfterUse[0]).isEqualTo(1_200);
        assertThat(storedBalance()).isEqualTo(1_200);
        assertThat(jdbcTemplate.queryForObject(
                "select balance_after from point_history where type = 'USE'", Long.class)).isEqualTo(1_200);
    }
}
