package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.StockAdjustRequest;
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
class StockAdjustConcurrencyTest extends IntegrationTest {

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    AdminSkuService adminSkuService;

    @Autowired
    BrandService brandService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long productId;
    private Long skuId;

    private void register(int stock) {
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "피부결을 정돈하는 토너다.", null, 12_900L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("150ml", 0, stock))));
        skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    /** 동시에 adjustStock을 호출해 실패한 ErrorCode를 모은다. 성공은 null이다. */
    private List<ErrorCode> adjustConcurrently(int delta, int threads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ErrorCode>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    adminSkuService.adjustStock(productId, skuId, new StockAdjustRequest(delta));
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
        List<ErrorCode> results = new ArrayList<>();
        for (Future<ErrorCode> outcome : outcomes) {
            results.add(outcome.get());
        }
        return results;
    }

    private int stock() {
        return jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId);
    }

    @Test
    void 동시에_빼도_음수가_되지_않는다() throws Exception {
        register(5);

        List<ErrorCode> results = adjustConcurrently(-1, 10);

        assertThat(results).filteredOn(code -> code == null).hasSize(5);
        assertThat(results).filteredOn(code -> code != null).hasSize(5).containsOnly(ErrorCode.STOCK_NOT_ENOUGH);
        assertThat(stock()).isZero();
    }

    @Test
    void 동시에_넣고_빼도_갱신이_사라지지_않는다() throws Exception {
        register(0);

        List<ErrorCode> results = adjustConcurrently(3, 10);

        assertThat(results).containsOnlyNulls();
        assertThat(stock()).isEqualTo(30);
    }
}
