package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.StockAdjustRequest;
import com.sprint.mission.product.repository.SkuRepository;
import com.sprint.mission.support.IntegrationTest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 클래스에 @Transactional을 붙이면 스레드마다 트랜잭션이 갈려 의미가 없다. 일부러 붙이지 않는다. */
class SkuEditStockRaceTest extends IntegrationTest {

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    AdminSkuService adminSkuService;

    @Autowired
    BrandService brandService;

    @Autowired
    SkuRepository skuRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 옵션을_수정하는_동안_재고가_바뀌어도_덮어쓰지_않는다() throws Exception {
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "독도 토너", null,
                "피부결을 정돈하는 토너다.", null, 12_900L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("150ml", 500, 10))));
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // 수정 트랜잭션이 재고 10을 읽은 뒤, 다른 스레드가 재고를 7로 바꿔 커밋한다.
                Sku sku = skuRepository.findById(skuId).orElseThrow();
                try {
                    executor.submit(() -> adminSkuService.adjustStock(productId, skuId, new StockAdjustRequest(-3)))
                            .get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                sku.update("새 이름", sku.getAdditionalPrice());
            });
        } finally {
            executor.shutdown();
        }

        assertThat(jdbcTemplate.queryForObject("select stock from sku where id = ?", Integer.class, skuId))
                .isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("select name from sku where id = ?", String.class, skuId))
                .isEqualTo("새 이름");
    }
}
