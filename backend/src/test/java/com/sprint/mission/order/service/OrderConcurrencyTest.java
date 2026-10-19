package com.sprint.mission.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.cart.service.CartService;
import com.sprint.mission.coupon.CouponFixtures;
import com.sprint.mission.coupon.service.AdminCouponService;
import com.sprint.mission.coupon.service.CouponService;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.dto.AddressRequest;
import com.sprint.mission.member.service.AddressService;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.order.dto.OrderCreateRequest;
import com.sprint.mission.point.service.PointService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
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
import org.springframework.jdbc.core.JdbcTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
class OrderConcurrencyTest extends IntegrationTest {

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

    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
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

    /** 배송지 하나를 가진 회원이다. */
    private record Buyer(Long memberId, Long addressId) {
    }

    private Buyer buyer(int i) {
        Long memberId = memberService.signup("user" + i + "@mall.com", "password1", "회원" + i, "01012345678");
        Long addressId = addressService.create(memberId, new AddressRequest("회원" + i, "01012345678", "06236",
                "서울시 강남구 테헤란로 1", null, true)).id();
        return new Buyer(memberId, addressId);
    }

    /** 옵션을 1개씩 담고, 담은 순서대로 cart_item id를 돌려준다. */
    private List<Long> addToCart(Long memberId, Long... skuIds) {
        List<Long> lineIds = new ArrayList<>();
        for (Long skuId : skuIds) {
            cartService.add(memberId, new CartAddRequest(skuId, 1));
            lineIds.add(jdbcTemplate.queryForObject("select id from cart_item where member_id = ? and sku_id = ?",
                    Long.class, memberId, skuId));
        }
        return lineIds;
    }

    private Long memberCouponId(Long memberId, Long couponId) {
        couponService.download(memberId, couponId);
        return jdbcTemplate.queryForObject("select id from member_coupon where member_id = ? and coupon_id = ?",
                Long.class, memberId, couponId);
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

    private int stock(Long skuId) {
        return jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId);
    }

    private long balance(Long memberId) {
        return jdbcTemplate.queryForObject("select point_balance from member where id = ?", Long.class, memberId);
    }

    private static long successes(List<Throwable> results) {
        return results.stream().filter(r -> r == null).count();
    }

    @Test
    void 재고_50에_100명이_동시에_주문하면_50건만_성공한다() throws Exception {
        Long skuId = sku("독도 토너", 50);
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 100, null)).id();
        List<Buyer> buyers = new ArrayList<>();
        List<Long> memberCouponIds = new ArrayList<>();
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Buyer buyer = buyer(i);
            Long lineId = addToCart(buyer.memberId(), skuId).get(0);
            Long memberCouponId = memberCouponId(buyer.memberId(), couponId);
            pointService.grant(buyer.memberId(), 1_000, "이벤트");
            buyers.add(buyer);
            memberCouponIds.add(memberCouponId);
            tasks.add(() -> orderService.create(buyer.memberId(),
                    new OrderCreateRequest(List.of(lineId), memberCouponId, 1_000, buyer.addressId())));
        }

        List<Throwable> results = runConcurrently(tasks);

        assertThat(successes(results)).isEqualTo(50);
        // 진 쪽은 조건부 UPDATE에서 지거나(STOCK_NOT_ENOUGH), 주문서 검증에서 이미 품절을 본다(CART_ITEM_UNAVAILABLE).
        assertThat(results).filteredOn(r -> r != null).hasSize(50)
                .allSatisfy(r -> assertThat(r).isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isIn(ErrorCode.STOCK_NOT_ENOUGH, ErrorCode.CART_ITEM_UNAVAILABLE)));
        assertThat(stock(skuId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject("select count(*) from order_item", Integer.class)).isEqualTo(50);
        for (int i = 0; i < 100; i++) {
            String couponStatus = jdbcTemplate.queryForObject("select status from member_coupon where id = ?",
                    String.class, memberCouponIds.get(i));
            if (results.get(i) == null) {
                assertThat(couponStatus).isEqualTo("USED");
                assertThat(balance(buyers.get(i).memberId())).isZero();
            } else {
                assertThat(couponStatus).isEqualTo("AVAILABLE");
                assertThat(balance(buyers.get(i).memberId())).isEqualTo(1_000);
            }
        }
    }

    @Test
    void 두_SKU를_엇갈려_담은_주문이_동시에_와도_데드락_없이_끝난다() throws Exception {
        Long skuA = sku("토너", 10);
        Long skuB = sku("로션", 10);
        List<Callable<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Buyer buyer = buyer(i);
            // 반은 A, B 순서로, 반은 B, A 순서로 담고 담은 순서대로 주문한다.
            List<Long> lineIds = i % 2 == 0 ? addToCart(buyer.memberId(), skuA, skuB)
                    : addToCart(buyer.memberId(), skuB, skuA);
            tasks.add(() -> orderService.create(buyer.memberId(),
                    new OrderCreateRequest(lineIds, null, 0, buyer.addressId())));
        }

        List<Throwable> results = runConcurrently(tasks);

        // 데드락(40P01)이면 BusinessException이 아닌 예외가 남는다.
        assertThat(results).filteredOn(r -> r != null).allSatisfy(r -> assertThat(r).isInstanceOf(BusinessException.class));
        long successes = successes(results);
        assertThat(successes).isEqualTo(10);
        assertThat(stock(skuA)).isEqualTo(10 - successes);
        assertThat(stock(skuB)).isEqualTo(10 - successes);
    }

    @Test
    void 같은_회원이_동시에_두_번_주문해도_대기_주문은_하나다() throws Exception {
        Long skuId = sku("독도 토너", 10);
        Buyer buyer = buyer(0);
        Long lineId = addToCart(buyer.memberId(), skuId).get(0);
        Long couponId = adminCouponService.create(CouponFixtures.fixed("3천원 쿠폰", 10, null)).id();
        Long memberCouponId = memberCouponId(buyer.memberId(), couponId);
        pointService.grant(buyer.memberId(), 1_000, "이벤트");
        OrderCreateRequest request = new OrderCreateRequest(List.of(lineId), memberCouponId, 1_000, buyer.addressId());

        List<Throwable> results = runConcurrently(List.of(
                () -> orderService.create(buyer.memberId(), request),
                () -> orderService.create(buyer.memberId(), request)));

        // 회원 잠금으로 줄을 서므로 나중 주문이 먼저 주문을 대체한다.
        assertThat(results).containsOnlyNulls();
        assertThat(jdbcTemplate.queryForObject("select count(*) from orders where status = 'PENDING'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders where status = 'CANCELED' and cancel_reason = 'REPLACED'",
                Integer.class)).isEqualTo(1);
        Long pendingId = jdbcTemplate.queryForObject("select id from orders where status = 'PENDING'", Long.class);
        assertThat(jdbcTemplate.queryForMap("select status, used_order_id from member_coupon where id = ?",
                memberCouponId)).containsEntry("status", "USED").containsEntry("used_order_id", pendingId);
        assertThat(stock(skuId)).isEqualTo(9);
        assertThat(balance(buyer.memberId())).isZero();
    }
}
