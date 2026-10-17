package com.sprint.mission.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
class AddressConcurrencyTest extends IntegrationTest {

    @Autowired
    MemberService memberService;

    @Autowired
    AddressService addressService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private AddressRequest request(String recipient) {
        return new AddressRequest(recipient, "01012345678", "06236", "서울시 강남구", null, false);
    }

    /** 동시에 create를 호출해 성공 수와 ErrorCode별 실패 수를 돌려준다. */
    private List<ErrorCode> createConcurrently(Long memberId, int threads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String recipient = "받는이" + i;
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    addressService.create(memberId, request(recipient));
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
        return failures;
    }

    @Test
    void 첫_배송지를_동시에_만들어도_기본은_하나다() throws Exception {
        Long memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");

        List<ErrorCode> failures = createConcurrently(memberId, 5);

        assertThat(failures).isEmpty();
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from address where member_id = ?", Integer.class, memberId);
        Integer defaults = jdbcTemplate.queryForObject(
                "select count(*) from address where member_id = ? and is_default = true", Integer.class, memberId);
        assertThat(rows).isEqualTo(5);
        assertThat(defaults).isEqualTo(1);
    }

    @Test
    void 동시에_만들어도_10개를_넘지_않는다() throws Exception {
        Long memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        for (int i = 0; i < 8; i++) {
            addressService.create(memberId, request("기존" + i));
        }

        List<ErrorCode> failures = createConcurrently(memberId, 5);

        assertThat(failures).hasSize(3).containsOnly(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from address where member_id = ?", Integer.class, memberId);
        assertThat(rows).isEqualTo(10);
    }
}
