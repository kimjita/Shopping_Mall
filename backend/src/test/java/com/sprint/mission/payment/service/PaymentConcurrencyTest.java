package com.sprint.mission.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.order.dto.OrderDetailResponse;
import com.sprint.mission.order.repository.OrderRepository;
import com.sprint.mission.order.service.OrderExpiryScheduler;
import com.sprint.mission.order.service.OrderExpiryService;
import com.sprint.mission.order.service.OrderService;
import com.sprint.mission.payment.FakePaymentGateway;
import com.sprint.mission.payment.dto.PaymentConfirmRequest;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
import com.sprint.mission.support.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
@Import(FakePaymentGateway.Config.class)
class PaymentConcurrencyTest extends IntegrationTest {

    @TestConfiguration
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    private static final String KEY = "tgen_20261020_key1";

    /** 20,000원 1개라 배송비가 없다. */
    private static final long AMOUNT = 20_000;

    @Autowired
    MutableClock clock;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    PaymentService paymentService;

    @Autowired
    OrderService orderService;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    OrderExpiryService orderExpiryService;

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
    JdbcTemplate jdbcTemplate;

    Long memberId;
    Long skuId;

    @BeforeEach
    void setUp() {
        // DB는 마이크로초까지 저장하므로 만료 판정의 기한 시각이 DB 값과 같게 맞춘다.
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "설명이다.", null, 20_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
        skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    @AfterEach
    void resetGateway() {
        gateway.reset();
    }

    private OrderDetailResponse placeOrder() {
        Long addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", null, true)).id();
        cartService.add(memberId, new CartAddRequest(skuId, 1));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ?", Long.class,
                memberId);
        return orderService.create(memberId, new OrderCreateRequest(List.of(lineId), null, 0, addressId));
    }

    private int stock() {
        return jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId);
    }

    private int paymentCount() {
        return jdbcTemplate.queryForObject("select count(*) from payment", Integer.class);
    }

    @Test
    void 같은_승인_요청이_동시에_두_번_와도_토스는_한_번만_호출된다() throws Exception {
        String orderNumber = placeOrder().orderNumber();
        gateway.delayConfirm(Duration.ofMillis(200));
        PaymentConfirmRequest request = new PaymentConfirmRequest(KEY, AMOUNT);

        List<Throwable> results = runConcurrently(List.of(
                () -> paymentService.confirm(memberId, orderNumber, request),
                () -> paymentService.confirm(memberId, orderNumber, request)));

        assertThat(results).containsOnlyNulls();
        assertThat(gateway.confirms()).hasSize(1);
        assertThat(paymentCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from orders", String.class)).isEqualTo("PAID");
    }

    @Test
    void 기한_직전_승인과_만료_처리가_동시에_와도_상태가_일관된다() throws Exception {
        OrderDetailResponse order = placeOrder();
        assertThat(stock()).isEqualTo(9);
        // 승인은 기한 1ms 전의 시계로, 만료 처리는 기한 정각의 시계로 돈다.
        clock.advance(Order.PAYMENT_WINDOW.minusMillis(1));
        OrderExpiryScheduler scheduler = new OrderExpiryScheduler(orderRepository, orderExpiryService,
                Clock.fixed(order.expiresAt(), ZoneOffset.UTC));
        gateway.delayConfirm(Duration.ofMillis(200));
        AtomicInteger expired = new AtomicInteger();

        List<Throwable> results = runConcurrently(List.of(
                () -> paymentService.confirm(memberId, order.orderNumber(), new PaymentConfirmRequest(KEY, AMOUNT)),
                () -> expired.addAndGet(scheduler.expireDue())));

        String status = jdbcTemplate.queryForObject("select status from orders", String.class);
        if (status.equals("PAID")) {
            assertThat(results).containsOnlyNulls();
            assertThat(expired.get()).isZero();
            assertThat(paymentCount()).isEqualTo(1);
            assertThat(stock()).isEqualTo(9);
        } else {
            assertThat(status).isEqualTo("CANCELED");
            assertThat(jdbcTemplate.queryForObject("select cancel_reason from orders", String.class))
                    .isEqualTo("EXPIRED");
            assertThat(results.get(0)).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) results.get(0)).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE);
            assertThat(expired.get()).isEqualTo(1);
            assertThat(paymentCount()).isZero();
            assertThat(stock()).isEqualTo(10);
            // 토스를 부르지 않았거나, 불렀다면 보상 취소까지 했어야 한다.
            assertThat(gateway.cancels()).hasSameSizeAs(gateway.confirms());
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
