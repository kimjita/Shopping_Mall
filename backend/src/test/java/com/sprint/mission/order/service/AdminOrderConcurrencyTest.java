package com.sprint.mission.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.payment.FakePaymentGateway;
import com.sprint.mission.payment.dto.PaymentConfirmRequest;
import com.sprint.mission.payment.service.PaymentService;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
@Import(FakePaymentGateway.Config.class)
class AdminOrderConcurrencyTest extends IntegrationTest {

    private static final String KEY = "tgen_20261020_key1";

    /** 20,000원 1개 - 적립금 1,000원이다(배송비 무료). */
    private static final long AMOUNT = 19_000;

    @Autowired
    FakePaymentGateway gateway;

    @Autowired
    PaymentService paymentService;

    @Autowired
    AdminOrderService adminOrderService;

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
    PointService pointService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;
    Long skuId;
    String orderNumber;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "설명이다.", null, 20_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));
        skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        pointService.grant(memberId, 1_000, "이벤트");
        Long addressId = addressService.create(memberId, new AddressRequest("홍길동", "01012345678", "06236",
                "서울시 강남구 테헤란로 1", null, true)).id();
        cartService.add(memberId, new CartAddRequest(skuId, 1));
        Long lineId = jdbcTemplate.queryForObject("select id from cart_item where member_id = ?", Long.class,
                memberId);
        orderNumber = orderService.create(memberId, new OrderCreateRequest(List.of(lineId), null, 1_000, addressId))
                .orderNumber();
        paymentService.confirm(memberId, orderNumber, new PaymentConfirmRequest(KEY, AMOUNT));
        gateway.reset();
        // 먼저 잡은 쪽이 회원 잠금을 쥔 채 토스를 부르는 동안 다른 쪽이 기다리게 한다.
        gateway.delayCancel(Duration.ofMillis(200));
    }

    @AfterEach
    void resetGateway() {
        gateway.reset();
    }

    private String orderStatus() {
        return jdbcTemplate.queryForObject("select status from orders", String.class);
    }

    private int restoreCount() {
        return jdbcTemplate.queryForObject("select count(*) from point_history where type = 'RESTORE'",
                Integer.class);
    }

    private int stock() {
        return jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId);
    }

    private static ErrorCode errorCode(Throwable error) {
        assertThat(error).isInstanceOf(BusinessException.class);
        return ((BusinessException) error).getErrorCode();
    }

    @Test
    void 관리자_취소와_사용자_취소가_동시에_와도_토스_취소와_복원은_한_번이다() throws Exception {
        List<Throwable> results = runConcurrently(List.of(
                () -> {
                    paymentService.adminCancel(orderNumber);
                    return null;
                },
                () -> paymentService.cancel(memberId, orderNumber)));

        assertThat(gateway.cancels()).hasSize(1);
        assertThat(restoreCount()).isEqualTo(1);
        assertThat(stock()).isEqualTo(10);
        assertThat(orderStatus()).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject("select status from payment", String.class)).isEqualTo("CANCELED");
        String reason = jdbcTemplate.queryForObject("select cancel_reason from orders", String.class);
        // 이긴 쪽만 성공하고, 진 쪽은 이미 다른 이유로 취소된 주문이라 409다.
        int loser = reason.equals("ADMIN") ? 1 : 0;
        assertThat(results.get(1 - loser)).isNull();
        assertThat(errorCode(results.get(loser))).isEqualTo(ErrorCode.ORDER_NOT_CANCELABLE);
        assertThat(gateway.cancels().get(0).cancelReason()).isEqualTo(reason.equals("ADMIN") ? "판매자 취소" : "고객 요청");
    }

    @Test
    void 관리자_상품_준비와_사용자_취소가_동시에_와도_상태가_일관된다() throws Exception {
        List<Throwable> results = runConcurrently(List.of(
                () -> adminOrderService.changeStatus(orderNumber, OrderStatus.PREPARING),
                () -> paymentService.cancel(memberId, orderNumber)));

        if (orderStatus().equals("CANCELED")) {
            assertThat(jdbcTemplate.queryForObject("select cancel_reason from orders", String.class))
                    .isEqualTo("USER");
            assertThat(errorCode(results.get(0))).isEqualTo(ErrorCode.INVALID_ORDER_STATUS_TRANSITION);
            assertThat(results.get(1)).isNull();
            assertThat(gateway.cancels()).hasSize(1);
            assertThat(restoreCount()).isEqualTo(1);
            assertThat(stock()).isEqualTo(10);
        } else {
            assertThat(orderStatus()).isEqualTo("PREPARING");
            assertThat(results.get(0)).isNull();
            assertThat(errorCode(results.get(1))).isEqualTo(ErrorCode.ORDER_NOT_CANCELABLE);
            assertThat(gateway.cancels()).isEmpty();
            assertThat(restoreCount()).isZero();
            assertThat(stock()).isEqualTo(9);
        }
        assertThat(results).filteredOn(Objects::isNull).hasSize(1);
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
