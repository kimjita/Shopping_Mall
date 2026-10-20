package com.sprint.mission.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.support.IntegrationTest;
import com.sprint.mission.support.MutableClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** 배치는 회원이 많아도 되도록 묶음마다 트랜잭션을 나누므로 클래스에 @Transactional을 붙이지 않는다. */
class GradeRecalculationTest extends IntegrationTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    GradeRecalculationService service;

    @Autowired
    GradeRecalculationScheduler scheduler;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbcTemplate;

    final AtomicInteger sequence = new AtomicInteger();

    private Long member(String grade, String status) {
        return jdbcTemplate.queryForObject(
                "insert into member (email, password, name, phone, role, status, grade, created_at, updated_at) "
                        + "values (?, 'pw', '회원', '01000000000', 'USER', ?, ?, now(), now()) returning id",
                Long.class, "m" + sequence.incrementAndGet() + "@test.com", status, grade);
    }

    private Long member(String grade) {
        return member(grade, "ACTIVE");
    }

    private Instant seoul(int month, int day, int hour, int minute) {
        return LocalDateTime.of(2026, month, day, hour, minute).atZone(SEOUL).toInstant();
    }

    private void order(Long memberId, String status, long paymentAmount, long shippingFee, Instant confirmedAt) {
        jdbcTemplate.update(
                "insert into orders (order_number, member_id, status, items_price, sale_discount, items_sale_price, "
                        + "coupon_discount, shipping_fee, points_used, payment_amount, recipient, phone, zip_code, "
                        + "address1, expires_at, confirmed_at, created_at, updated_at) "
                        + "values (?, ?, ?, 0, 0, 0, 0, ?, 0, ?, '홍길동', '01012345678', '06236', '서울', now(), ?, now(), now())",
                "ORD-" + sequence.incrementAndGet(), memberId, status, shippingFee, paymentAmount,
                confirmedAt == null ? null : Timestamp.from(confirmedAt));
    }

    private void confirmed(Long memberId, long amount, Instant confirmedAt) {
        order(memberId, "CONFIRMED", amount, 0, confirmedAt);
    }

    private String grade(Long memberId) {
        return jdbcTemplate.queryForObject("select grade from member where id = ?", String.class, memberId);
    }

    @Test
    void 직전_3개월_확정_금액으로_등급을_다시_매긴다() {
        Long a = member("BABY");
        Long b = member("PINK");
        Long c = member("BABY");
        // 결제금액 − 배송비가 기준이므로 202,500 − 2,500 = 200,000이다.
        order(a, "CONFIRMED", 202_500, 2_500, seoul(9, 10, 12, 0));
        confirmed(c, 500_000, seoul(8, 5, 12, 0));
        confirmed(c, 300_000, seoul(10, 20, 12, 0));

        int changed = service.recalculate(NOVEMBER);

        assertThat(grade(a)).isEqualTo("GREEN");
        assertThat(grade(b)).isEqualTo("BABY");
        assertThat(grade(c)).isEqualTo("GOLD");
        assertThat(changed).isEqualTo(3);
    }

    @Test
    void 기간은_서울_시각_달력_월이다() {
        Long beforeStart = member("BABY");
        Long atStart = member("BABY");
        Long beforeEnd = member("BABY");
        Long atEnd = member("BABY");
        confirmed(beforeStart, 100_000, seoul(7, 31, 23, 59));
        confirmed(atStart, 100_000, seoul(8, 1, 0, 0));
        confirmed(beforeEnd, 100_000, seoul(10, 31, 23, 59));
        confirmed(atEnd, 100_000, seoul(11, 1, 0, 0));

        service.recalculate(NOVEMBER);

        assertThat(grade(beforeStart)).isEqualTo("BABY");
        assertThat(grade(atStart)).isEqualTo("PINK");
        assertThat(grade(beforeEnd)).isEqualTo("PINK");
        assertThat(grade(atEnd)).isEqualTo("BABY");
    }

    @Test
    void 취소_주문과_확정_전_주문은_세지_않는다() {
        Long memberId = member("BABY");
        confirmed(memberId, 100_000, seoul(9, 1, 12, 0));
        order(memberId, "CANCELED", 100_000, 0, seoul(9, 2, 12, 0));
        order(memberId, "DELIVERED", 100_000, 0, null);
        order(memberId, "PAID", 100_000, 0, null);

        service.recalculate(NOVEMBER);

        assertThat(grade(memberId)).isEqualTo("PINK");
    }

    @Test
    void 결제금액이_배송비보다_작은_주문은_0원으로_센다() {
        Long memberId = member("PINK");
        order(memberId, "CONFIRMED", 100, 2_500, seoul(9, 1, 12, 0));

        service.recalculate(NOVEMBER);

        assertThat(grade(memberId)).isEqualTo("BABY");
    }

    @Test
    void 천_명을_넘어도_모두_처리한다() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 2_500; i++) {
            rows.add(new Object[] {"bulk" + i + "@test.com"});
        }
        jdbcTemplate.batchUpdate(
                "insert into member (email, password, name, phone, role, status, grade, created_at, updated_at) "
                        + "values (?, 'pw', '회원', '01000000000', 'USER', 'ACTIVE', 'PINK', now(), now())", rows);
        Long last = jdbcTemplate.queryForObject("select max(id) from member", Long.class);
        confirmed(last, 800_000, seoul(9, 1, 12, 0));

        int changed = service.recalculate(NOVEMBER);

        assertThat(changed).isEqualTo(2_500);
        assertThat(grade(last)).isEqualTo("GOLD");
        assertThat(jdbcTemplate.queryForObject("select count(*) from member where grade = 'BABY'", Integer.class))
                .isEqualTo(2_499);
    }

    @Test
    void 같은_달을_다시_돌려도_결과가_같고_바뀐_회원은_0이다() {
        Long memberId = member("BABY");
        confirmed(memberId, 250_000, seoul(9, 1, 12, 0));

        assertThat(service.recalculate(NOVEMBER)).isEqualTo(1);
        assertThat(service.recalculate(NOVEMBER)).isZero();

        assertThat(grade(memberId)).isEqualTo("GREEN");
    }

    @Test
    void 탈퇴_회원은_건드리지_않는다() {
        Long withdrawn = member("PINK", "WITHDRAWN");

        service.recalculate(NOVEMBER);

        assertThat(grade(withdrawn)).isEqualTo("PINK");
    }

    @Test
    void 스케줄러는_서울_기준_실행_월로_부른다() {
        // 서울 11/1 00:00은 UTC로는 아직 10월이다. UTC 월로 계산하면 7~9월을 읽어 등급이 달라진다.
        clock.set(seoul(11, 1, 0, 0));
        Long memberId = member("BABY");
        confirmed(memberId, 100_000, seoul(10, 15, 12, 0));

        scheduler.run();

        assertThat(grade(memberId)).isEqualTo("PINK");
    }
}
