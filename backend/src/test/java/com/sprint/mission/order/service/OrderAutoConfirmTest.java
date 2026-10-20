package com.sprint.mission.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.support.IntegrationTest;
import com.sprint.mission.support.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/** 동시성 테스트가 있으므로 클래스에 @Transactional을 붙이지 않는다. */
class OrderAutoConfirmTest extends IntegrationTest {

    /** 배송 완료 7일 뒤로 시간을 흘려보낸다. */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    private static final Duration WAIT = Duration.ofDays(7);

    @Autowired
    MutableClock clock;

    @Autowired
    OrderAutoConfirmScheduler scheduler;

    @Autowired
    OrderConfirmService orderConfirmService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationContext applicationContext;

    int sequence;

    @BeforeEach
    void setUp() {
        // DB는 마이크로초까지 저장하므로 confirmed_at을 그대로 비교할 수 있게 맞춘다.
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    private Long member() {
        return jdbcTemplate.queryForObject(
                "insert into member (email, password, name, phone, role, status, created_at, updated_at) "
                        + "values (?, 'x', '회원', '01012345678', 'USER', 'ACTIVE', now(), now()) returning id",
                Long.class, "auto" + sequence++ + "@mall.com");
    }

    /** 결제금액 22,500원, 배송비 2,500원(기준 20,000원)인 배송 완료 주문을 SQL로 넣는다. */
    private Long deliveredOrder(Long memberId, Instant deliveredAt) {
        sequence++;
        return jdbcTemplate.queryForObject(
                "insert into orders (order_number, member_id, status, items_price, sale_discount, "
                        + "items_sale_price, coupon_discount, shipping_fee, points_used, payment_amount, "
                        + "recipient, phone, zip_code, address1, expires_at, delivered_at, created_at, updated_at) "
                        + "values (?, ?, 'DELIVERED', 20000, 0, 20000, 0, 2500, 0, 22500, '회원', '01012345678', "
                        + "'06236', '서울', now(), ?, now(), now()) returning id",
                Long.class, String.format("20261019-%08d", sequence), memberId, Timestamp.from(deliveredAt));
    }

    private String status(Long orderId) {
        return jdbcTemplate.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    private long balance(Long memberId) {
        return jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId);
    }

    private int earnCount(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from point_history where type = 'EARN' and order_id = ?", Integer.class, orderId);
    }

    @Test
    void 배송_완료_7일_뒤에_자동으로_확정하고_적립한다() {
        Long memberId = member();
        Long orderId = deliveredOrder(memberId, clock.instant());

        // 배송 완료 시각 + 7일 정각부터 대상이다.
        clock.advance(WAIT);
        Instant now = clock.instant();

        assertThat(scheduler.confirmDue()).isEqualTo(1);

        assertThat(status(orderId)).isEqualTo("CONFIRMED");
        assertThat(((Timestamp) jdbcTemplate.queryForMap("select confirmed_at from orders where id = ?", orderId)
                .get("confirmed_at")).toInstant()).isEqualTo(now);
        assertThat(earnCount(orderId)).isEqualTo(1);
        assertThat(balance(memberId)).isEqualTo(100);
    }

    @Test
    void 매일_03시_스케줄로_등록되고_테스트_프로필에서는_꺼져_있다() {
        assertThat(applicationContext.getBeansOfType(OrderAutoConfirmScheduler.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }

    @Test
    void 배송_완료_7일이_안_됐으면_그대로다() {
        Long memberId = member();
        Long orderId = deliveredOrder(memberId, clock.instant());

        clock.advance(WAIT.minusSeconds(1));

        assertThat(scheduler.confirmDue()).isZero();
        assertThat(status(orderId)).isEqualTo("DELIVERED");
        assertThat(earnCount(orderId)).isZero();
        assertThat(balance(memberId)).isZero();
    }

    @Test
    void 이미_확정됐거나_배송_완료가_아닌_주문은_건드리지_않는다() {
        Long memberId = member();
        Long shipping = deliveredOrder(memberId, clock.instant().minus(WAIT));
        jdbcTemplate.update("update orders set status = 'SHIPPING', delivered_at = null where id = ?", shipping);

        assertThat(scheduler.confirmDue()).isZero();
        assertThat(status(shipping)).isEqualTo("SHIPPING");
    }

    @Test
    void 구매확정은_100건을_넘어도_모두_확정한다() {
        Long memberId = member();
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            orderIds.add(deliveredOrder(memberId, clock.instant().minus(WAIT)));
        }

        assertThat(scheduler.confirmDue()).isEqualTo(150);

        assertThat(jdbcTemplate.queryForObject("select count(*) from orders where status = 'CONFIRMED'",
                Integer.class)).isEqualTo(150);
        assertThat(balance(memberId)).isEqualTo(150 * 100);
    }

    @Test
    void 한_주문이_실패해도_나머지는_확정한다() {
        List<Long> memberIds = new ArrayList<>();
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Long memberId = member();
            memberIds.add(memberId);
            orderIds.add(deliveredOrder(memberId, clock.instant().minus(WAIT)));
        }
        // 가운데 회원을 탈퇴 상태로 바꿔 적립(활성 회원 확인)이 실패하게 한다.
        jdbcTemplate.update("update member set status = 'WITHDRAWN', withdrawn_at = now() where id = ?",
                memberIds.get(1));

        assertThat(scheduler.confirmDue()).isEqualTo(2);

        assertThat(status(orderIds.get(0))).isEqualTo("CONFIRMED");
        assertThat(status(orderIds.get(2))).isEqualTo("CONFIRMED");
        // 실패한 주문은 확정도 함께 되돌아간다.
        assertThat(status(orderIds.get(1))).isEqualTo("DELIVERED");
        assertThat(earnCount(orderIds.get(1))).isZero();
        assertThat(balance(memberIds.get(0))).isEqualTo(100);
    }

    @Test
    void 사용자_확정과_자동_확정이_동시에_와도_적립은_한_번이다() throws Exception {
        // 겹치는 순간이 짧아서, 주문 하나마다 사용자 확정과 배치(두 번)를 동시에 보내는 판을 여러 번 반복한다.
        for (int round = 0; round < 20; round++) {
            Long memberId = member();
            Long orderId = deliveredOrder(memberId, clock.instant().minus(WAIT));
            String orderNumber = jdbcTemplate.queryForObject("select order_number from orders where id = ?",
                    String.class, orderId);

            List<Throwable> results = runConcurrently(List.of(
                    scheduler::confirmDue,
                    scheduler::confirmDue,
                    () -> orderConfirmService.confirmByMember(memberId, orderNumber)));

            // 누가 먼저 이기든 사용자는 200(멱등)이고 적립은 한 번이다.
            assertThat(results).containsOnlyNulls();
            assertThat(status(orderId)).isEqualTo("CONFIRMED");
            assertThat(earnCount(orderId)).isEqualTo(1);
            assertThat(balance(memberId)).isEqualTo(100);
        }
    }

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
}
