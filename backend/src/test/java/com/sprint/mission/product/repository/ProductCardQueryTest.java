package com.sprint.mission.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.product.domain.ProductStatus;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCard;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductSearchCondition;
import com.sprint.mission.product.dto.ProductStatusRequest;
import com.sprint.mission.product.dto.ProductSummary;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.product.service.ProductSearchService;
import com.sprint.mission.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductCardQueryTest extends IntegrationTest {

    @Autowired
    ProductSearchRepository productSearchRepository;

    @Autowired
    ProductSearchService productSearchService;

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private Long register(String name, long basePrice, int stock) {
        return adminProductService.create(new ProductCreateRequest(brandId, categoryId, name, null,
                "설명이다.", null, basePrice, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, stock))));
    }

    @Test
    void 여러_상품을_한_번의_쿼리로_읽는다() {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            ids.add(register("토너 " + i, 10_000L * i, 10));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Map<Long, ProductCard> cards = productSearchRepository.findCards(ids, Instant.now());

        assertThat(cards).hasSize(5);
        assertThat(cards.keySet()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(cards.get(ids.get(2)).summary().name()).isEqualTo("토너 3");
        assertThat(cards.get(ids.get(2)).summary().brandName()).isEqualTo("라운드랩");
        assertThat(cards.get(ids.get(2)).summary().thumbnailUrl()).isEqualTo("https://img.example.com/main.jpg");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void 모든_상태의_상품을_status와_함께_돌려준다() {
        Long onSale = register("판매중", 10_000, 10);
        Long stopped = register("중지", 10_000, 10);
        Long deleted = register("삭제", 10_000, 10);
        adminProductService.changeStatus(stopped, new ProductStatusRequest(ProductStatus.STOPPED));
        adminProductService.changeStatus(deleted, new ProductStatusRequest(ProductStatus.DELETED));

        Map<Long, ProductCard> cards = productSearchRepository.findCards(
                List.of(onSale, stopped, deleted), Instant.now());

        assertThat(cards).hasSize(3);
        assertThat(cards.get(onSale).status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(cards.get(stopped).status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(cards.get(deleted).status()).isEqualTo(ProductStatus.DELETED);
    }

    @Test
    void 빈_목록이면_쿼리를_실행하지_않는다() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Map<Long, ProductCard> cards = productSearchRepository.findCards(List.of(), Instant.now());

        assertThat(cards).isEmpty();
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void 없는_상품_id는_결과에_없다() {
        Long id = register("토너", 10_000, 10);

        Map<Long, ProductCard> cards = productSearchRepository.findCards(List.of(id, id + 1_000), Instant.now());

        assertThat(cards.keySet()).containsExactly(id);
    }

    @Test
    void 세일가와_품절이_목록_API와_같다() {
        Long sale = register("세일 토너", 18_000, 10);
        Long soldOut = register("품절 토너", 12_000, 0);
        Instant now = Instant.now();
        adminProductService.startSale(sale, new SaleRequest(10, now.minusSeconds(3_600), now.plusSeconds(3_600)));

        List<ProductSummary> listed = productSearchService.search(
                new ProductSearchCondition(null, null, null, null, null, null, null, null, null)).items();
        Map<Long, ProductCard> cards = productSearchRepository.findCards(List.of(sale, soldOut), Instant.now());

        assertThat(listed).hasSize(2);
        for (ProductSummary summary : listed) {
            assertThat(cards.get(summary.id()).summary()).isEqualTo(summary);
        }
        assertThat(cards.get(sale).summary().salePrice()).isEqualTo(16_200);
        assertThat(cards.get(sale).summary().saleRate()).isEqualTo(10);
        assertThat(cards.get(soldOut).summary().soldOut()).isTrue();
    }
}
