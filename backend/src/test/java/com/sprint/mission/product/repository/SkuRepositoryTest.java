package com.sprint.mission.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.product.domain.Sku;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class SkuRepositoryTest extends IntegrationTest {

    @Autowired
    SkuRepository skuRepository;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @PersistenceContext
    EntityManager entityManager;

    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private Long registerSku(int stock) {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "토너", null,
                "설명이다.", null, 10_000L, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, stock))));
        return skuRepository.findAllByProductIdOrderByIdAsc(productId).get(0).getId();
    }

    /** 벌크 UPDATE는 서비스의 트랜잭션 안에서 호출되므로 테스트도 트랜잭션으로 감싼다. */
    private int inTransaction(IntSupplier call) {
        return transactionTemplate.execute(status -> call.getAsInt());
    }

    @Test
    void 재고_조정_뒤에도_같은_트랜잭션의_엔티티가_분리되지_않는다() {
        Long skuId = registerSku(10);

        boolean managed = transactionTemplate.execute(status -> {
            Sku sku = skuRepository.findById(skuId).orElseThrow();
            skuRepository.adjustStock(skuId, 5, Instant.now());
            return entityManager.contains(sku);
        });

        assertThat(managed).isTrue();
        assertThat(skuRepository.findStockById(skuId)).contains(15);
    }

    @Test
    void 복원은_상한_없이_더한다() {
        Long skuId = registerSku(99_999);

        int updated = inTransaction(() -> skuRepository.restoreStock(skuId, 2, Instant.now()));

        assertThat(updated).isEqualTo(1);
        assertThat(skuRepository.findStockById(skuId)).contains(100_001);
    }

    @Test
    void 복원은_0이하_수량을_거절한다() {
        Long skuId = registerSku(10);

        // 저장소 프록시가 IllegalArgumentException을 InvalidDataAccessApiUsageException으로 감싸 돌려준다.
        assertThatThrownBy(() -> inTransaction(() -> skuRepository.restoreStock(skuId, 0, Instant.now())))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inTransaction(() -> skuRepository.restoreStock(skuId, -1, Instant.now())))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(skuRepository.findStockById(skuId)).contains(10);
    }

    @Test
    void adjustStock은_여전히_100000을_넘지_못한다() {
        Long skuId = registerSku(100_000);

        int updated = inTransaction(() -> skuRepository.adjustStock(skuId, 1, Instant.now()));

        assertThat(updated).isZero();
        assertThat(skuRepository.findStockById(skuId)).contains(100_000);
    }

    @Test
    void 복원으로_상한을_넘은_재고도_차감은_된다() {
        Long skuId = registerSku(100_000);
        inTransaction(() -> skuRepository.restoreStock(skuId, 2, Instant.now()));

        int updated = inTransaction(() -> skuRepository.adjustStock(skuId, -1, Instant.now()));

        assertThat(updated).isEqualTo(1);
        assertThat(skuRepository.findStockById(skuId)).contains(100_001);
    }

    @Test
    void 없는_옵션의_재고는_비어_있다() {
        assertThat(skuRepository.findStockById(-1L)).isEmpty();
    }
}
