package com.sprint.mission.cart.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
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

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
class CartConcurrencyTest extends IntegrationTest {

    @Autowired
    CartService cartService;

    @Autowired
    MemberService memberService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long memberId;
    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private Long skuWithStock(int stock) {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "토너", null,
                "설명이다.", null, 10_000, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, stock))));
        return jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    /** 같은 요청을 동시에 보내 실패한 ErrorCode를 모은다(성공은 null이라 빠진다). */
    private List<ErrorCode> addConcurrently(Long skuId, int quantity, int threads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    cartService.add(memberId, new CartAddRequest(skuId, quantity));
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

    private List<Integer> quantities(Long skuId) {
        return jdbcTemplate.queryForList("select quantity from cart_item where member_id = ? and sku_id = ?",
                Integer.class, memberId, skuId);
    }

    @Test
    void 더블클릭으로_동시에_담아도_한_줄이고_수량이_정확하다() throws Exception {
        Long skuId = skuWithStock(10);

        List<ErrorCode> failures = addConcurrently(skuId, 2, 5);

        assertThat(failures).isEmpty();
        assertThat(quantities(skuId)).containsExactly(10);
    }

    @Test
    void 동시에_담아도_재고를_넘지_않는다() throws Exception {
        Long skuId = skuWithStock(5);

        List<ErrorCode> failures = addConcurrently(skuId, 2, 5);

        assertThat(failures).hasSize(3).containsOnly(ErrorCode.STOCK_NOT_ENOUGH);
        assertThat(quantities(skuId)).containsExactly(4);
    }
}
