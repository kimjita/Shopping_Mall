package com.sprint.mission.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.cart.domain.CartItemStatus;
import com.sprint.mission.cart.domain.CartLine;
import com.sprint.mission.cart.dto.CartAddRequest;
import com.sprint.mission.global.error.BusinessException;
import com.sprint.mission.global.error.ErrorCode;
import com.sprint.mission.member.service.MemberService;
import com.sprint.mission.product.dto.BrandRequest;
import com.sprint.mission.product.dto.ProductCreateRequest;
import com.sprint.mission.product.dto.SaleRequest;
import com.sprint.mission.product.dto.SkuRequest;
import com.sprint.mission.product.service.AdminProductService;
import com.sprint.mission.product.service.BrandService;
import com.sprint.mission.support.IntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class CartLineLoaderTest extends IntegrationTest {

    @Autowired
    CartLineLoader cartLineLoader;

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

    @Autowired
    EntityManagerFactory entityManagerFactory;

    Long memberId;
    Long otherId;
    Long brandId;
    Long categoryId;

    @BeforeEach
    void setUp() {
        memberId = memberService.signup("user@mall.com", "password1", "홍길동", "01012345678");
        otherId = memberService.signup("other@mall.com", "password1", "김철수", "01099998888");
        brandId = brandService.create(new BrandRequest("라운드랩", null)).id();
        categoryId = jdbcTemplate.queryForObject("select id from category where name = '토너'", Long.class);
    }

    private Long skuWithStock(int stock) {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "1025 독도 토너", null,
                "설명이다.", null, 14_900, List.of("https://img.example.com/main.jpg"),
                List.of(new SkuRequest("기본", 0, stock))));
        return jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
    }

    private Long cartItemOf(Long member, Long skuId, int quantity) {
        return cartService.add(member, new CartAddRequest(skuId, quantity)).id();
    }

    @Test
    void 선택한_줄만_요청_순서대로_읽는다() {
        Long first = cartItemOf(memberId, skuWithStock(10), 1);
        Long second = cartItemOf(memberId, skuWithStock(10), 2);
        Long third = cartItemOf(memberId, skuWithStock(10), 3);

        List<CartLine> lines = cartLineLoader.loadSelected(memberId, List.of(third, first), Instant.now());

        assertThat(lines).extracting(line -> line.item().getId()).containsExactly(third, first);
        assertThat(lines.get(0).lineTotal()).isEqualTo(44_700);
        assertThat(lines.get(0).status()).isEqualTo(CartItemStatus.AVAILABLE);
        assertThat(lines.get(0).card().summary().brandName()).isEqualTo("라운드랩");
        assertThat(cartLineLoader.loadAll(memberId, Instant.now()))
                .extracting(line -> line.item().getId()).containsExactly(first, second, third);
    }

    @Test
    void 세일_중인_상품은_세일가로_줄_합계를_낸다() {
        Long productId = adminProductService.create(new ProductCreateRequest(brandId, categoryId, "세일 토너", null,
                "설명이다.", null, 14_900, List.of(), List.of(new SkuRequest("대용량", 2_000, 10))));
        Instant now = Instant.now();
        adminProductService.startSale(productId, new SaleRequest(15, now.minusSeconds(3_600), now.plusSeconds(3_600)));
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        cartItemOf(memberId, skuId, 2);

        CartLine line = cartLineLoader.loadAll(memberId, now).get(0);

        assertThat(line.unitPrice()).isEqualTo(16_900);
        assertThat(line.unitSalePrice()).isEqualTo(14_360);
        assertThat(line.lineTotal()).isEqualTo(28_720);
    }

    @Test
    void 다른_회원의_줄이_섞이면_CART_ITEM_NOT_FOUND() {
        Long mine = cartItemOf(memberId, skuWithStock(10), 1);
        Long others = cartItemOf(otherId, skuWithStock(10), 1);

        assertThatThrownBy(() -> cartLineLoader.loadSelected(memberId, List.of(mine, others), Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        assertThatThrownBy(() -> cartLineLoader.loadSelected(memberId, List.of(mine, 999_999L), Instant.now()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void 줄_수와_상관없이_쿼리_수가_같다() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ids.add(cartItemOf(memberId, skuWithStock(10), 1));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        assertThat(cartLineLoader.loadSelected(memberId, ids, Instant.now())).hasSize(2);
        long selectedWithTwo = statistics.getPrepareStatementCount();
        statistics.clear();
        assertThat(cartLineLoader.loadAll(memberId, Instant.now())).hasSize(2);
        long allWithTwo = statistics.getPrepareStatementCount();

        for (int i = 0; i < 8; i++) {
            ids.add(cartItemOf(memberId, skuWithStock(10), 1));
        }
        statistics.clear();
        assertThat(cartLineLoader.loadSelected(memberId, ids, Instant.now())).hasSize(10);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(selectedWithTwo);
        statistics.clear();
        assertThat(cartLineLoader.loadAll(memberId, Instant.now())).hasSize(10);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(allWithTwo);
    }
}
