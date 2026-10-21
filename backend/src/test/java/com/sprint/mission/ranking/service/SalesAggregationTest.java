package com.sprint.mission.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.product.domain.Product;
import com.sprint.mission.product.repository.ProductRepository;
import com.sprint.mission.ranking.repository.SalesBatchRepository;
import com.sprint.mission.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/** 배치는 묶음마다 트랜잭션을 나누므로 클래스에 @Transactional을 붙이지 않는다. */
class SalesAggregationTest extends IntegrationTest {

    static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    SalesAggregationService service;

    @MockitoSpyBean
    SalesBatchRepository salesBatchRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbcTemplate;

    final AtomicInteger sequence = new AtomicInteger();
    Long brandId;
    Long categoryId;
    Long memberId;
    Long skuId;

    @BeforeEach
    void setUp() {
        brandId = jdbcTemplate.queryForObject(
                "insert into brand (name, created_at, updated_at) values ('브랜드', now(), now()) returning id",
                Long.class);
        categoryId = jdbcTemplate.queryForObject("select id from category limit 1", Long.class);
        memberId = jdbcTemplate.queryForObject(
                "insert into member (email, password, name, phone, role, status, created_at, updated_at) "
                        + "values ('m@test.com', 'pw', '회원', '01000000000', 'USER', 'ACTIVE', now(), now()) "
                        + "returning id", Long.class);
    }

    private Long product() {
        Long productId = jdbcTemplate.queryForObject(
                "insert into product (brand_id, category_id, name, description, base_price, status, "
                        + "min_price, search_text, created_at, updated_at) "
                        + "values (?, ?, '수분 크림', '설명', 20000, 'ON_SALE', 20000, '브랜드 수분 크림', now(), now()) "
                        + "returning id", Long.class, brandId, categoryId);
        if (skuId == null) {
            skuId = jdbcTemplate.queryForObject("insert into sku (product_id, name, stock, created_at, updated_at) "
                    + "values (?, '50ml', 10, now(), now()) returning id", Long.class, productId);
        }
        return productId;
    }

    /** 결제 승인 시각이 approvedAt인 주문 하나에 상품 하나를 quantity개 담는다. 주문 id를 돌려준다. */
    private Long paid(Long productId, int quantity, String status, Instant approvedAt) {
        int n = sequence.incrementAndGet();
        Long orderId = jdbcTemplate.queryForObject(
                "insert into orders (order_number, member_id, status, items_price, sale_discount, items_sale_price, "
                        + "coupon_discount, shipping_fee, points_used, payment_amount, recipient, phone, zip_code, "
                        + "address1, expires_at, created_at, updated_at) "
                        + "values (?, ?, ?, 20000, 0, 20000, 0, 0, 0, 20000, '회원', '01012345678', '06236', '서울', "
                        + "now(), now(), now()) returning id", Long.class, "ORD-" + n, memberId, status);
        jdbcTemplate.update(
                "insert into order_item (order_id, product_id, sku_id, product_name, sku_name, unit_price, "
                        + "unit_sale_price, quantity, line_total, created_at, updated_at) "
                        + "values (?, ?, ?, '수분 크림', '50ml', 20000, 20000, ?, 20000, now(), now())",
                orderId, productId, skuId, quantity);
        jdbcTemplate.update("insert into payment (order_id, payment_key, amount, method, status, approved_at, "
                + "created_at, updated_at) values (?, ?, 20000, 'CARD', 'DONE', ?, now(), now())",
                orderId, "pk-" + n, Timestamp.from(approvedAt));
        return orderId;
    }

    private Long paid(Long productId, int quantity, String status) {
        return paid(productId, quantity, status, NOW.minus(Duration.ofHours(1)));
    }

    private int sales(Long productId) {
        return jdbcTemplate.queryForObject("select sales_7d from product where id = ?", Integer.class, productId);
    }

    @Test
    void 결제된_주문상품_수량을_7일치_합산한다() {
        Long productId = product();
        paid(productId, 2, "PAID");
        paid(productId, 3, "CONFIRMED");

        assertThat(service.aggregate(NOW)).isEqualTo(1);

        assertThat(sales(productId)).isEqualTo(5);
    }

    @Test
    void 결제대기와_취소_주문은_세지_않는다() {
        Long productId = product();
        paid(productId, 2, "PENDING");
        paid(productId, 3, "CANCELED");
        paid(productId, 1, "PREPARING");

        service.aggregate(NOW);

        assertThat(sales(productId)).isEqualTo(1);
    }

    @Test
    void 승인_시각이_정확히_168시간_전이면_빠지고_1ms_뒤면_들어간다() {
        Long onBoundary = product();
        Long justInside = product();
        Long atNow = product();
        Instant from = NOW.minus(Duration.ofHours(168));
        paid(onBoundary, 1, "PAID", from);
        paid(justInside, 1, "PAID", from.plusMillis(1));
        paid(atNow, 1, "PAID", NOW);

        service.aggregate(NOW);

        assertThat(sales(onBoundary)).isZero();
        assertThat(sales(justInside)).isEqualTo(1);
        assertThat(sales(atNow)).isEqualTo(1);
    }

    @Test
    void 판매가_끊긴_상품은_0으로_내려간다() {
        Long productId = product();
        paid(productId, 4, "PAID", NOW.minus(Duration.ofHours(100)));
        service.aggregate(NOW);
        assertThat(sales(productId)).isEqualTo(4);

        // 100시간 전 결제가 창 밖으로 밀려난다.
        assertThat(service.aggregate(NOW.plus(Duration.ofHours(100)))).isEqualTo(1);

        assertThat(sales(productId)).isZero();
    }

    @Test
    void 집계_뒤_취소되면_다음_실행에서_빠진다() {
        Long productId = product();
        Long orderId = paid(productId, 3, "PAID");
        paid(productId, 2, "PAID");
        service.aggregate(NOW);
        assertThat(sales(productId)).isEqualTo(5);

        jdbcTemplate.update("update orders set status = 'CANCELED' where id = ?", orderId);
        service.aggregate(NOW.plus(Duration.ofHours(1)));

        assertThat(sales(productId)).isEqualTo(2);
    }

    @Test
    void 바뀐_상품만_갱신하고_다시_돌리면_0이다() {
        Long changed = product();
        Long unchanged = product();
        Long noSales = product();
        paid(changed, 2, "PAID");
        paid(unchanged, 1, "PAID");
        jdbcTemplate.update("update product set sales_7d = 1 where id = ?", unchanged);
        Timestamp updatedAt = jdbcTemplate.queryForObject(
                "select updated_at from product where id = ?", Timestamp.class, changed);

        assertThat(service.aggregate(NOW)).isEqualTo(1);
        assertThat(service.aggregate(NOW)).isZero();

        assertThat(sales(changed)).isEqualTo(2);
        assertThat(sales(unchanged)).isEqualTo(1);
        assertThat(sales(noSales)).isZero();
        assertThat(jdbcTemplate.queryForObject("select updated_at from product where id = ?", Timestamp.class,
                changed)).isEqualTo(updatedAt);
    }

    @Test
    void 일천개를_넘어도_모두_갱신한다() {
        Long first = product();
        jdbcTemplate.update("insert into product (brand_id, category_id, name, description, base_price, status, "
                + "min_price, search_text, created_at, updated_at) "
                + "select ?, ?, '상품' || g, '설명', 10000, 'ON_SALE', 10000, '상품' || g, now(), now() "
                + "from generate_series(1, 2499) g", brandId, categoryId);
        // 주문 하나에 상품 2,500개를 각각 2개씩 담은 상태를 SQL로 바로 만든다.
        Long orderId = paid(first, 2, "PAID");
        jdbcTemplate.update(
                "insert into order_item (order_id, product_id, sku_id, product_name, sku_name, unit_price, "
                        + "unit_sale_price, quantity, line_total, created_at, updated_at) "
                        + "select ?, id, ?, '상품', '50ml', 10000, 10000, 2, 10000, now(), now() "
                        + "from product where id <> ?", orderId, skuId, first);

        assertThat(service.aggregate(NOW)).isEqualTo(2_500);

        assertThat(jdbcTemplate.queryForObject("select count(*) from product where sales_7d = 2", Integer.class))
                .isEqualTo(2_500);
    }

    @Test
    void 묶음이_데드락으로_실패하면_다시_시도해서_끝낸다() {
        Long productId = product();
        paid(productId, 2, "PAID");
        doThrow(new DeadlockLoserDataAccessException("교착", null)).doCallRealMethod()
                .when(salesBatchRepository).updateSales(any());

        assertThat(service.aggregate(NOW)).isEqualTo(1);

        verify(salesBatchRepository, times(2)).updateSales(any());
        assertThat(sales(productId)).isEqualTo(2);
    }

    @Test
    void 재시도를_다_쓰면_예외를_던지고_앞_묶음은_남는다() {
        Long first = product();
        jdbcTemplate.update("insert into product (brand_id, category_id, name, description, base_price, status, "
                + "min_price, search_text, created_at, updated_at) "
                + "select ?, ?, '상품' || g, '설명', 10000, 'ON_SALE', 10000, '상품' || g, now(), now() "
                + "from generate_series(1, 1000) g", brandId, categoryId);
        Long orderId = paid(first, 1, "PAID");
        jdbcTemplate.update(
                "insert into order_item (order_id, product_id, sku_id, product_name, sku_name, unit_price, "
                        + "unit_sale_price, quantity, line_total, created_at, updated_at) "
                        + "select ?, id, ?, '상품', '50ml', 10000, 10000, 1, 10000, now(), now() "
                        + "from product where id <> ?", orderId, skuId, first);
        Long last = jdbcTemplate.queryForObject("select max(id) from product", Long.class);
        // 1,001개라 두 번째 묶음에는 마지막 상품 하나만 들어 있고, 그 묶음만 계속 실패한다.
        doAnswer(inv -> {
            Map<Long, Integer> chunk = inv.getArgument(0);
            if (chunk.containsKey(last)) {
                throw new CannotAcquireLockException("잠금 실패");
            }
            return inv.callRealMethod();
        }).when(salesBatchRepository).updateSales(any());

        assertThatThrownBy(() -> service.aggregate(NOW)).isInstanceOf(CannotAcquireLockException.class);

        // 첫 묶음 1번 + 두 번째 묶음 3번
        verify(salesBatchRepository, times(4)).updateSales(any());
        assertThat(jdbcTemplate.queryForObject("select count(*) from product where sales_7d = 1", Integer.class))
                .isEqualTo(1_000);
        assertThat(sales(last)).isZero();
    }

    @Test
    void 관리자_상품_수정이_판매량을_덮어쓰지_않는다() {
        Long productId = product();
        tx.executeWithoutResult(s -> {
            Product product = productRepository.findById(productId).orElseThrow();
            jdbcTemplate.update("update product set sales_7d = 7 where id = ?", productId);
            product.update(product.getBrandId(), product.getCategoryId(), "새 이름", null, "설명이다.", null,
                    12_900, List.of("https://img.example.com/main.jpg"));
            productRepository.flush();
        });

        assertThat(sales(productId)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("select name from product where id = ?", String.class, productId))
                .isEqualTo("새 이름");
    }
}
