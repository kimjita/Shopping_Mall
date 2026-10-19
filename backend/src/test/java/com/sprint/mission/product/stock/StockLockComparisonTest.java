package com.sprint.mission.product.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.repository.SkuRepository;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재고 차감 방식 세 가지를 같은 경쟁 조건에서 비교한다. 시간은 출력만 하고 검증하지 않는다.
 * 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없으므로 일부러 붙이지 않는다.
 */
class StockLockComparisonTest extends IntegrationTest {

    private static final int STOCK = 100;
    private static final int THREADS = 200;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    BrandService brandService;

    @Autowired
    SkuRepository skuRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Clock clock;

    private Long registerSku(int stock) {
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "피부결을 정돈하는 토너다.", null, 12_900L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("150ml", 0, stock))));
        return jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    private StockDeductor deductor(String name) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return switch (name) {
            case "비관적" -> new PessimisticStockDeductor(jdbcTemplate, transactionTemplate);
            case "낙관적" -> new OptimisticStockDeductor(jdbcTemplate);
            case "조건부 UPDATE" -> new ConditionalUpdateStockDeductor(skuRepository, transactionTemplate, clock);
            default -> throw new IllegalArgumentException(name);
        };
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"비관적", "낙관적", "조건부 UPDATE"})
    void 재고_100에_200명이_1개씩_빼면_정확히_100명만_성공한다(String name) throws Exception {
        Long skuId = registerSku(STOCK);
        StockDeductor deductor = deductor(name);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> outcomes = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            outcomes.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return deductor.deduct(skuId, 1);
            }));
        }
        ready.await();
        long startedAt = System.nanoTime();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(120, TimeUnit.SECONDS)).isTrue();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        int succeeded = 0;
        int failed = 0;
        int errors = 0;
        for (Future<Boolean> outcome : outcomes) {
            try {
                if (outcome.get()) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                errors++;
            }
        }
        long retries = deductor instanceof OptimisticStockDeductor optimistic ? optimistic.retries() : 0;
        System.out.printf("| %s | %d | %d |%n", name, elapsedMillis, retries);

        assertThat(errors).as("차감 중 예외").isZero();
        assertThat(succeeded).isEqualTo(STOCK);
        assertThat(failed).isEqualTo(THREADS - STOCK);
        assertThat(skuRepository.findStockById(skuId)).contains(0);
    }
}
