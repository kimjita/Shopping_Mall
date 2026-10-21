package com.sprint.mission.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 평점 갱신이 한 문장으로 합계와 평균을 맞추고, 상품 엔티티의 낡은 값이 그 컬럼을 덮어쓰지 않는지 확인한다. */
class ProductRatingTest extends IntegrationTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    BrandService brandService;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Long productId;

    @BeforeEach
    void setUp() {
        Long brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        Long categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
        productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "1025 독도 토너", null,
                "피부결을 정돈하는 토너다.", null, 12_900, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("150ml", 0, 10))));
    }

    private void change(int countDelta, int sumDelta) {
        tx.executeWithoutResult(s -> productRepository.changeRating(productId, countDelta, sumDelta));
    }

    private void assertRating(int count, int sum, String avg) {
        assertThat(jdbcTemplate.queryForMap("select review_count, rating_sum, rating_avg from product where id = ?",
                productId))
                .containsEntry("review_count", count)
                .containsEntry("rating_sum", sum)
                .containsEntry("rating_avg", avg == null ? null : new BigDecimal(avg));
    }

    @Test
    void 쓰기_수정_삭제가_합계와_평균을_정확히_바꾼다() {
        assertRating(0, 0, null);

        change(1, 5);
        assertRating(1, 5, "5.00");

        change(1, 4);
        assertRating(2, 9, "4.50");

        change(0, -3);
        assertRating(2, 6, "3.00");

        change(-1, -1);
        assertRating(1, 5, "5.00");

        change(-1, -5);
        assertRating(0, 0, null);
    }

    @Test
    void 평균은_소수_둘째_자리에서_반올림한다() {
        change(1, 5);
        change(1, 4);
        change(1, 4);
        assertRating(3, 13, "4.33");

        change(0, 1);
        assertRating(3, 14, "4.67");
    }

    @Test
    void 관리자_상품_수정이_평점을_덮어쓰지_않는다() {
        tx.executeWithoutResult(s -> {
            Product product = productRepository.findById(productId).orElseThrow();
            productRepository.changeRating(productId, 1, 5);
            product.update(product.getBrandId(), product.getCategoryId(), "새 이름", null, "설명이다.", null,
                    12_900, List.of("https://img.example.com/main.jpg"));
            productRepository.flush();
        });

        assertRating(1, 5, "5.00");
        assertThat(jdbcTemplate.queryForObject("select name from product where id = ?", String.class, productId))
                .isEqualTo("새 이름");
    }
}
