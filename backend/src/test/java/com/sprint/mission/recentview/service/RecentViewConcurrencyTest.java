package com.sprint.mission.recentview.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
class RecentViewConcurrencyTest extends IntegrationTest {

    @Autowired
    RecentViewService recentViewService;

    @Autowired
    MemberService memberService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 동시에_같은_상품을_기록해도_한_줄이다() throws Exception {
        Long memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "토너", null,
                "설명이다.", null, 10_000, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, 10))));

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                recentViewService.record(memberId, productId);
                return null;
            }));
        }
        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        for (Future<?> outcome : outcomes) {
            outcome.get();
        }

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from recent_view where member_id = ? and product_id = ?", Integer.class,
                memberId, productId);
        assertThat(rows).isEqualTo(1);
    }
}
