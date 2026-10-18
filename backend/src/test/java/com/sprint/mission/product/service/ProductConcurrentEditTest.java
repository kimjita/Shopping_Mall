package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 상품 행을 동시에 고쳐도 상태·세일·최저가가 유실되지 않는지 확인한다. 스레드마다 트랜잭션이 갈려야 하므로 @Transactional을 붙이지 않는다. */
class ProductConcurrentEditTest extends IntegrationTest {

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    AdminSkuService adminSkuService;

    @Autowired
    BrandService brandService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private Long register(long basePrice, SkuRequest... skus) {
        return adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "피부결을 정돈하는 토너다.", null, basePrice, List.of("https://img.example.com/main.jpg"),
                List.of(skus)));
    }

    @Test
    void 옵션을_추가하는_동안_판매중지해도_상태가_유지된다() throws Exception {
        Long productId = register(10_000, new SkuRequest("300ml", 3_000, 10));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                // 최저가를 바꾸는 트랜잭션이 상품을 ON_SALE로 읽은 뒤, 다른 스레드가 판매 중지를 커밋한다.
                Product product = productRepository.findById(productId).orElseThrow();
                try {
                    executor.submit(() -> adminProductService.changeStatus(productId,
                            new ProductStatusRequest(ProductStatus.STOPPED))).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                product.refreshMinPrice(List.of(Sku.create(productId, "150ml", 0, 10)));
            });
        } finally {
            executor.shutdown();
        }

        assertThat(jdbcTemplate.queryForObject("select status from product where id = ?", String.class, productId))
                .isEqualTo("STOPPED");
        assertThat(jdbcTemplate.queryForObject("select min_price from product where id = ?", Long.class, productId))
                .isEqualTo(10_000L);
    }

    @Test
    void 동시에_옵션을_추가해도_최저가가_맞다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                Long productId = register(10_000, new SkuRequest("300ml", 3_000, 10));
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (long extra : new long[] {0, 1_000}) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        return adminSkuService.add(productId, new SkuRequest("추가" + extra, extra, 10));
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }

                assertThat(jdbcTemplate.queryForObject("select min_price from product where id = ?", Long.class,
                        productId)).isEqualTo(10_000L);
                assertThat(jdbcTemplate.queryForObject("select count(*) from sku where product_id = ?",
                        Integer.class, productId)).isEqualTo(3);
            }
        } finally {
            executor.shutdown();
        }
    }
}
