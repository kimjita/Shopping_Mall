package com.sprint.mission.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.domain.QProduct;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.ProductUpdateRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.dto.SkuUpdateRequest;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 검색용으로 비정규화한 min_price·search_text가 등록·수정 때마다 같은 트랜잭션에서 맞춰지는지 확인한다. */
class ProductDenormalizationTest extends IntegrationTest {

    private static final List<String> IMAGES = List.of("https://img.example.com/main.jpg");

    @Autowired
    BrandService brandService;

    @Autowired
    AdminProductService adminProductService;

    @Autowired
    AdminSkuService adminSkuService;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JPAQueryFactory queryFactory;

    Long brandId;
    Long tonerId;

    @BeforeEach
    void setUp() {
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        tonerId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private Long register(String name, long basePrice, SkuRequest... skus) {
        return adminProductService.create(new ProductCreateRequest(brandId, tonerId, name, null,
                "피부결을 정돈하는 토너다.", null, basePrice, IMAGES, List.of(skus)));
    }

    private Product product(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    @Test
    void 등록하면_최저_정가와_검색_문자열이_채워진다() {
        Long id = register("1025 독도 토너", 12_900, new SkuRequest("150ml", 0, 10), new SkuRequest("300ml", 2_000, 10));

        Product product = product(id);

        assertThat(product.getMinPrice()).isEqualTo(12_900);
        assertThat(product.getSearchText()).isEqualTo("라운드랩 1025 독도 토너");
    }

    @Test
    void 더_싼_옵션을_추가하면_최저가가_바뀐다() {
        Long id = register("1025 독도 토너", 10_000, new SkuRequest("300ml", 3_000, 10));
        assertThat(product(id).getMinPrice()).isEqualTo(13_000);

        adminSkuService.add(id, new SkuRequest("150ml", 0, 10));

        assertThat(product(id).getMinPrice()).isEqualTo(10_000);
    }

    @Test
    void 옵션_추가금을_수정하면_최저가가_다시_계산된다() {
        Long id = register("1025 독도 토너", 10_000, new SkuRequest("150ml", 0, 10), new SkuRequest("300ml", 3_000, 10));
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ? and name = '150ml'",
                Long.class, id);

        adminSkuService.update(id, skuId, new SkuUpdateRequest("150ml", 5_000));

        assertThat(product(id).getMinPrice()).isEqualTo(13_000);
    }

    @Test
    void 정가를_수정하면_최저가가_다시_계산된다() {
        Long id = register("1025 독도 토너", 10_000, new SkuRequest("150ml", 0, 10), new SkuRequest("300ml", 3_000, 10));

        adminProductService.update(id, new ProductUpdateRequest(brandId, tonerId, "1025 독도 토너 리뉴얼", null,
                "피부결을 정돈하는 토너다.", null, 15_000, IMAGES));

        Product product = product(id);
        assertThat(product.getMinPrice()).isEqualTo(15_000);
        assertThat(product.getSearchText()).isEqualTo("라운드랩 1025 독도 토너 리뉴얼");
    }

    @Test
    void 브랜드_이름을_바꾸면_검색_문자열도_바뀐다() {
        Long first = register("1025 독도 토너", 12_900, new SkuRequest("150ml", 0, 10));
        Long second = register("자작나무 수분 크림", 20_000, new SkuRequest("80ml", 0, 10));
        Long otherBrandId = brandService.create(new BrandRequest("아이소이", null)).id();
        Long other = adminProductService.create(new ProductCreateRequest(otherBrandId, tonerId, "불가리안 로즈 토너",
                null, "장미 토너다.", null, 30_000, IMAGES, List.of(new SkuRequest("200ml", 0, 10))));

        brandService.update(brandId, new BrandRequest("라운드랩 코리아", null));

        assertThat(product(first).getSearchText()).isEqualTo("라운드랩 코리아 1025 독도 토너");
        assertThat(product(second).getSearchText()).isEqualTo("라운드랩 코리아 자작나무 수분 크림");
        assertThat(product(other).getSearchText()).isEqualTo("아이소이 불가리안 로즈 토너");
    }

    @Test
    void 브랜드_설명만_바꾸면_검색_문자열은_그대로다() {
        Long id = register("1025 독도 토너", 12_900, new SkuRequest("150ml", 0, 10));

        brandService.update(brandId, new BrandRequest("라운드랩", "새 설명"));

        assertThat(product(id).getSearchText()).isEqualTo("라운드랩 1025 독도 토너");
    }

    @Test
    void 세일_컬럼은_일부만_채울_수_없다() {
        Long id = register("1025 독도 토너", 12_900, new SkuRequest("150ml", 0, 10));

        assertThatThrownBy(() -> jdbcTemplate.update("update product set sale_rate = 10 where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void QueryDSL_빈이_동작한다() {
        Long count = queryFactory.select(QProduct.product.count()).from(QProduct.product).fetchOne();

        assertThat(count).isZero();
    }
}
