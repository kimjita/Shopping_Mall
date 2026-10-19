package com.sprint.mission.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.coupon.service.AdminCouponService;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
import com.sprint.mission.support.MutableClock;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
class OrderExpiryTest extends IntegrationTest {

    /** 결제 기한을 넘기려고 시간을 흘려보낸다. */
    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Autowired
    MutableClock clock;

    @Autowired
    OrderExpiryScheduler orderExpiryScheduler;

    @Autowired
    OrderService orderService;

    @Autowired
    MemberService memberService;

    @Autowired
    AddressService addressService;

    @Autowired
    CartService cartService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    AdminCouponService adminCouponService;

    @Autowired
    CouponService couponService;

    @Autowired
    PointService pointService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationContext applicationContext;

    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        // DB는 마이크로초까지 저장하므로 canceled_at을 그대로 비교할 수 있게 맞춘다.
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    /** 20,000원짜리 옵션 하나의 상품을 만들고 sku id를 돌려준다. */
    private Long sku(String name, int stock) {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, 20_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, stock))));
        return jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    /** 장바구니 줄 하나와 배송지 하나를 가진 회원이다. */
    private record Buyer(Long memberId, Long addressId, Long lineId) {
    }

    private Buyer buyer(int i, Long skuId, int quantity) {
        Long memberId = memberService.signup("user" + i + "@mall.com", "password1", "회원" + i, "01012345678");
        Long addressId = addressService.create(memberId, new AddressRequest("회원" + i, "01012345678", "06236",
                "서울시 강남구 테헤란로 1", null, true)).id();
        cartService.add(memberId, new CartAddRequest(skuId, quantity));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                Long.class, memberId, skuId);
        return new Buyer(memberId, addressId, lineId);
    }

    private Long memberCouponId(Long memberId, Long couponId) {
        couponService.download(memberId, couponId);
        return jdbcTemplate.queryForObject("select id from member_coupon where member_id = ? and coupon_id = ?",
                Long.class, memberId, couponId);
    }

    private Long order(Buyer buyer, Long memberCouponId, long points) {
        String orderNumber = orderService.create(buyer.memberId(),
                new OrderCreateRequest(List.of(buyer.lineId()), memberCouponId, points, buyer.addressId()))
                .orderNumber();
        return jdbcTemplate.queryForObject("select id from orders where order_number = ?", Long.class, orderNumber);
    }

    private int stock(Long skuId) {
        return jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId);
    }

    private long balance(Long memberId) {
        return jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId);
    }

    private Map<String, Object> orderRow(Long orderId) {
        return jdbcTemplate.queryForMap("select status, cancel_reason, canceled_at from orders where id = ?", orderId);
    }

    private int restoreCount(Long orderId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from point_history where type = 'RESTORE' and order_id = ?", Integer.class, orderId);
    }

    @Test
    void 주문_후_15분이_지난_대기_주문을_취소하고_모두_되돌린다() {
        Long skuId = sku("독도 토너", 10);
        Buyer buyer = buyer(0, skuId, 2);
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 10, null)).id();
        Long memberCouponId = memberCouponId(buyer.memberId(), couponId);
        pointService.grant(buyer.memberId(), 1_000, "이벤트");
        Long orderId = order(buyer, memberCouponId, 1_000);
        assertThat(stock(skuId)).isEqualTo(8);

        // 기한 시각 정각부터 만료다.
        clock.advance(Order.PAYMENT_WINDOW);
        Instant now = clock.instant();

        assertThat(orderExpiryScheduler.expireDue()).isEqualTo(1);

        Map<String, Object> row = orderRow(orderId);
        assertThat(row).containsEntry("status", "CANCELED").containsEntry("cancel_reason", "EXPIRED");
        assertThat(((Timestamp) row.get("canceled_at")).toInstant()).isEqualTo(now);
        assertThat(stock(skuId)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForMap("select status, used_order_id from member_coupon where id = ?",
                memberCouponId)).containsEntry("status", "AVAILABLE").containsEntry("used_order_id", null);
        assertThat(balance(buyer.memberId())).isEqualTo(1_000);
        assertThat(restoreCount(orderId)).isEqualTo(1);
    }

    @Test
    void 주문_후_14분_59초에는_취소하지_않는다() {
        Long skuId = sku("독도 토너", 10);
        Buyer buyer = buyer(0, skuId, 1);
        Long orderId = order(buyer, null, 0);

        clock.advance(Order.PAYMENT_WINDOW.minusSeconds(1));

        assertThat(orderExpiryScheduler.expireDue()).isZero();
        assertThat(orderRow(orderId)).containsEntry("status", "PENDING").containsEntry("cancel_reason", null);
        assertThat(stock(skuId)).isEqualTo(9);
    }

    @Test
    void 한_번에_100건을_넘는_만료도_모두_처리한다() {
        Long skuId = sku("독도 토너", 0);
        Long productId = jdbcTemplate.queryForObject("select product_id from sku where id = ?", Long.class, skuId);
        Timestamp past = Timestamp.from(clock.instant().minus(Duration.ofMinutes(1)));
        // 회원 150명이 1개씩 주문해 재고가 0이 된 상태를 SQL로 바로 만든다.
        for (int i = 0; i < 150; i++) {
            Long memberId = jdbcTemplate.queryForObject(
                    "insert into member (email, password, name, phone, role, status, created_at, updated_at) "
                            + "values (?, 'x', '회원', '01012345678', 'USER', 'ACTIVE', now(), now()) returning id",
                    Long.class, "bulk" + i + "@mall.com");
            Long orderId = jdbcTemplate.queryForObject(
                    "insert into orders (order_number, member_id, status, items_price, sale_discount, "
                            + "items_sale_price, coupon_discount, shipping_fee, points_used, payment_amount, "
                            + "recipient, phone, zip_code, address1, expires_at, created_at, updated_at) "
                            + "values (?, ?, 'PENDING', 20000, 0, 20000, 0, 0, 0, 20000, '회원', '01012345678', "
                            + "'06236', '서울', ?, now(), now()) returning id",
                    Long.class, String.format("20261019-%08d", i), memberId, past);
            jdbcTemplate.update(
                    "insert into order_item (order_id, product_id, sku_id, product_name, sku_name, unit_price, "
                            + "unit_sale_price, quantity, line_total, created_at, updated_at) "
                            + "values (?, ?, ?, '독도 토너', '기본', 20000, 20000, 1, 20000, now(), now())",
                    orderId, productId, skuId);
        }

        assertThat(orderExpiryScheduler.expireDue()).isEqualTo(150);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders where status = 'CANCELED' and cancel_reason = 'EXPIRED'",
                Integer.class)).isEqualTo(150);
        assertThat(stock(skuId)).isEqualTo(150);
    }

    @Test
    void 한_주문이_실패해도_나머지는_처리한다() {
        Long skuId = sku("독도 토너", 10);
        List<Long> orderIds = new ArrayList<>();
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Buyer buyer = buyer(i, skuId, 1);
            memberIds.add(buyer.memberId());
            orderIds.add(order(buyer, null, 0));
        }
        // 가운데 회원을 탈퇴 상태로 바꿔 적립금 복원(활성 회원 확인)이 실패하게 한다.
        jdbcTemplate.update("update member set status = 'WITHDRAWN', withdrawn_at = now() where id = ?",
                memberIds.get(1));
        clock.advance(Order.PAYMENT_WINDOW);

        assertThat(orderExpiryScheduler.expireDue()).isEqualTo(2);

        assertThat(orderRow(orderIds.get(0))).containsEntry("cancel_reason", "EXPIRED");
        assertThat(orderRow(orderIds.get(2))).containsEntry("cancel_reason", "EXPIRED");
        // 실패한 주문은 재고 복원까지 함께 되돌아간다.
        assertThat(orderRow(orderIds.get(1))).containsEntry("status", "PENDING").containsEntry("canceled_at", null);
        assertThat(stock(skuId)).isEqualTo(9);
    }

    @Test
    void 만료_처리와_새_주문이_동시에_와도_복원은_한_번이다() throws Exception {
        Long skuId = sku("독도 토너", 100);
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 100, null)).id();
        List<Buyer> buyers = new ArrayList<>();
        List<Long> memberCouponIds = new ArrayList<>();
        List<Long> oldOrderIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Buyer buyer = buyer(i, skuId, 1);
            Long memberCouponId = memberCouponId(buyer.memberId(), couponId);
            pointService.grant(buyer.memberId(), 1_000, "이벤트");
            buyers.add(buyer);
            memberCouponIds.add(memberCouponId);
            oldOrderIds.add(order(buyer, memberCouponId, 1_000));
        }
        clock.advance(Order.PAYMENT_WINDOW);

        // 같은 회원마다 만료 처리와 같은 쿠폰·적립금을 쓰는 새 주문이 겹친다.
        AtomicInteger expired = new AtomicInteger();
        List<Callable<?>> tasks = new ArrayList<>();
        tasks.add(() -> expired.addAndGet(orderExpiryScheduler.expireDue()));
        for (int i = 0; i < 10; i++) {
            Buyer buyer = buyers.get(i);
            Long memberCouponId = memberCouponIds.get(i);
            tasks.add(() -> order(buyer, memberCouponId, 1_000));
        }

        List<Throwable> results = runConcurrently(tasks);

        assertThat(results).containsOnlyNulls();
        for (int i = 0; i < 10; i++) {
            Long oldOrderId = oldOrderIds.get(i);
            assertThat(orderRow(oldOrderId)).containsEntry("status", "CANCELED");
            assertThat((String) orderRow(oldOrderId).get("cancel_reason")).isIn("EXPIRED", "REPLACED");
            assertThat(restoreCount(oldOrderId)).isEqualTo(1);
            Long memberId = buyers.get(i).memberId();
            Long pendingId = jdbcTemplate.queryForObject(
                    "select id from orders where member_id = ? and status = 'PENDING'", Long.class, memberId);
            assertThat(jdbcTemplate.queryForMap("select status, used_order_id from member_coupon where id = ?",
                    memberCouponIds.get(i))).containsEntry("status", "USED").containsEntry("used_order_id", pendingId);
            assertThat(balance(memberId)).isZero();
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders where cancel_reason = 'EXPIRED'", Integer.class)).isEqualTo(expired.get());
        // 이전 주문 10건의 차감은 한 번씩 되돌아가고 새 주문 10건만 남는다.
        assertThat(stock(skuId)).isEqualTo(90);
    }

    @Test
    void 스케줄러는_테스트_프로필에서_꺼져_있다() {
        assertThat(applicationContext.getBeansOfType(OrderExpiryScheduler.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
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
