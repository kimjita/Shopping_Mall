package com.sprint.mission.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.auth.dto.IssuedTokens;
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
class RefreshTokenConcurrencyTest extends IntegrationTest {

    @Autowired
    MemberService memberService;

    @Autowired
    AuthService authService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 같은_토큰으로_동시에_재발급하면_하나만_성공한다() throws Exception {
        memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        String token = authService.login("user@mall.com", "password1").refreshToken();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> task = () -> {
                ready.countDown();
                start.await();
                try {
                    IssuedTokens issued = authService.refresh(token);
                    return issued.refreshToken() != null;
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
                    return false;
                }
            };
            outcomes.add(executor.submit(task));
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int success = 0;
        for (Future<Boolean> outcome : outcomes) {
            if (outcome.get()) {
                success++;
            }
        }
        assertThat(success).isEqualTo(1);

        Integer rows = jdbcTemplate.queryForObject("select count(*) from refresh_token", Integer.class);
        Integer notRevoked = jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where revoked_at is null", Integer.class);
        assertThat(rows).isEqualTo(2);
        assertThat(notRevoked).isZero();
    }
}
