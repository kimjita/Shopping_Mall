package com.sprint.mission.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.checkout.domain.PriceBreakdown;
import com.sprint.mission.order.domain.CancelReason;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.OrderItem;
import com.sprint.mission.order.domain.OrderStatus;
import com.sprint.mission.order.domain.ShippingInfo;
import com.sprint.mission.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderRepositoryTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    static final ShippingInfo SHIPPING = new ShippingInfo("홍길동", "01012345678", "06236", "서울 강남구 테헤란로 1", null);

    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderItemRepository orderItemRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long member(String email) {
        return jdbcTemplate.queryForObject(
                "insert into member (email, password, name, phone, role, status, created_at, updated_at) "
                        + "values (?, 'pw', '회원', '01000000000', 'USER', 'ACTIVE', now(), now()) returning id",
                Long.class, email);
    }

    private PriceBreakdown price(long paymentAmount) {
        return new PriceBreakdown(paymentAmount, 0, paymentAmount, 0, 0, 0, paymentAmount);
    }

    private Order place(Long memberId, String number, Instant now) {
        return orderRepository.save(Order.place(memberId, number, price(30_000), null, null, SHIPPING, now));
    }

    private void insertOrderRow(Long memberId, String number, String status, long paymentAmount) {
        jdbcTemplate.update(
                "insert into orders (order_number, member_id, status, items_price, sale_discount, items_sale_price, "
                        + "coupon_discount, shipping_fee, points_used, payment_amount, recipient, phone, zip_code, "
                        + "address1, expires_at, created_at, updated_at) "
                        + "values (?, ?, ?, 0, 0, 0, 0, 0, 0, ?, '홍길동', '01012345678', '06236', '서울', now(), now(), now())",
                number, memberId, status, paymentAmount);
    }

    @Test
    void orders와_order_item_테이블이_생긴다() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name in ('orders', 'order_item')", Integer.class);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void 같은_회원의_PENDING_주문을_두_건_넣으면_unique_위반이다() {
        Long memberId = member("a@test.com");
        insertOrderRow(memberId, "20261019-AAAAAAA1", "PENDING", 30_000);

        assertThatThrownBy(() -> insertOrderRow(memberId, "20261019-AAAAAAA2", "PENDING", 30_000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void PENDING이_아니거나_다른_회원이면_PENDING과_함께_넣을_수_있다() {
        Long a = member("a@test.com");
        Long b = member("b@test.com");

        insertOrderRow(a, "20261019-AAAAAAA1", "PENDING", 30_000);
        insertOrderRow(a, "20261019-AAAAAAA2", "CANCELED", 30_000);
        insertOrderRow(a, "20261019-AAAAAAA3", "CANCELED", 30_000);
        insertOrderRow(b, "20261019-BBBBBBB1", "PENDING", 30_000);

        assertThat(orderRepository.count()).isEqualTo(4);
    }

    @Test
    void 주문번호는_unique다() {
        Long a = member("a@test.com");
        Long b = member("b@test.com");
        insertOrderRow(a, "20261019-AAAAAAA1", "PENDING", 30_000);

        assertThatThrownBy(() -> insertOrderRow(b, "20261019-AAAAAAA1", "PENDING", 30_000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 결제_금액이_100원_미만이면_CHECK_위반이다() {
        Long memberId = member("a@test.com");

        assertThatThrownBy(() -> insertOrderRow(memberId, "20261019-AAAAAAA1", "PENDING", 99))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 적립금과_쿠폰의_주문_외래_키가_생긴다() {
        List<String> tables = jdbcTemplate.queryForList(
                "select conrelid::regclass::text from pg_constraint "
                        + "where contype = 'f' and confrelid = 'orders'::regclass", String.class);

        assertThat(tables).contains("point_history", "member_coupon", "order_item");
    }

    @Test
    void 주문을_저장하고_다시_읽으면_값이_같다() {
        Long memberId = member("a@test.com");
        PriceBreakdown price = new PriceBreakdown(40_000, 5_000, 35_000, 3_000, 0, 1_000, 31_000);
        Order saved = orderRepository.save(Order.place(memberId, "20261019-7K3Q9ZP2", price, null, null, SHIPPING, NOW));

        Order found = orderRepository.findByOrderNumberAndMemberId("20261019-7K3Q9ZP2", memberId).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.price()).isEqualTo(price);
        assertThat(found.getShipping()).isEqualTo(SHIPPING);
        assertThat(found.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(orderRepository.findByOrderNumberAndMemberId("20261019-7K3Q9ZP2", memberId + 1)).isEmpty();
    }

    @Test
    void 주문_상품을_주문_id로_읽는다() {
        Long memberId = member("a@test.com");
        Long productId = productAndSku();
        Long skuId = jdbcTemplate.queryForObject("select id from sku", Long.class);
        Order order = place(memberId, "20261019-AAAAAAA1", NOW);
        orderItemRepository.save(OrderItem.create(order.getId(), productId, skuId, "수분 크림", "50ml", null, 20_000, 17_000, 2));

        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getLineTotal()).isEqualTo(34_000);
        assertThat(orderItemRepository.findByOrderIdInOrderByIdAsc(List.of(order.getId(), 999L))).hasSize(1);
    }

    @Test
    void 만료_대상은_PENDING이고_expiresAt이_now_이하이며_id_오름차순이다() {
        Long a = member("a@test.com");
        Long b = member("b@test.com");
        Long c = member("c@test.com");
        Long d = member("d@test.com");
        Order due1 = place(a, "20261019-AAAAAAA1", NOW);
        Order due2 = place(b, "20261019-BBBBBBB1", NOW);
        place(c, "20261019-CCCCCCC1", NOW.plusSeconds(1));
        Order canceled = place(d, "20261019-DDDDDDD1", NOW);
        canceled.cancel(CancelReason.REPLACED, NOW);
        orderRepository.save(canceled);
        Instant now = NOW.plus(Duration.ofMinutes(15));

        assertThat(orderRepository.findDueIds(now, 0L, Limit.of(10))).containsExactly(due1.getId(), due2.getId());
        assertThat(orderRepository.findDueIds(now, due1.getId(), Limit.of(10))).containsExactly(due2.getId());
        assertThat(orderRepository.findDueIds(now, 0L, Limit.of(1))).containsExactly(due1.getId());
        assertThat(orderRepository.findDueIds(now.minusMillis(1), 0L, Limit.of(10))).isEmpty();
    }

    @Test
    void 회원_id와_상태로_조회한다() {
        Long a = member("a@test.com");
        Long b = member("b@test.com");
        Order pending = place(a, "20261019-AAAAAAA1", NOW);

        assertThat(orderRepository.findMemberIdById(pending.getId())).contains(a);
        assertThat(orderRepository.findMemberIdById(999L)).isEmpty();
        assertThat(orderRepository.findByMemberIdAndStatus(a, OrderStatus.PENDING)).isPresent();
        assertThat(orderRepository.findByMemberIdAndStatus(b, OrderStatus.PENDING)).isEmpty();
        assertThat(orderRepository.existsByMemberIdAndStatusIn(a, OrderStatus.IN_PROGRESS)).isTrue();
        assertThat(orderRepository.existsByMemberIdAndStatusIn(b, OrderStatus.IN_PROGRESS)).isFalse();
        assertThat(orderRepository.existsByMemberIdAndStatusIn(a, Set.of(OrderStatus.PAID))).isFalse();
    }

    @Test
    void 커서로_최신_순_목록을_읽는다() {
        Long a = member("a@test.com");
        Order first = place(a, "20261019-AAAAAAA1", NOW);
        first.cancel(CancelReason.REPLACED, NOW);
        orderRepository.save(first);
        Order second = place(a, "20261019-AAAAAAA2", NOW);
        second.cancel(CancelReason.REPLACED, NOW);
        orderRepository.save(second);
        Order third = place(a, "20261019-AAAAAAA3", NOW);

        assertThat(orderRepository.findByMemberIdOrderByIdDesc(a, Limit.of(2)))
                .extracting(Order::getId).containsExactly(third.getId(), second.getId());
        assertThat(orderRepository.findByMemberIdAndIdLessThanOrderByIdDesc(a, second.getId(), Limit.of(2)))
                .extracting(Order::getId).containsExactly(first.getId());
    }

    private Long productAndSku() {
        Long brandId = jdbcTemplate.queryForObject(
                "insert into brand (name, created_at, updated_at) values ('브랜드', now(), now()) returning id", Long.class);
        Long categoryId = jdbcTemplate.queryForObject("select id from category limit 1", Long.class);
        Long productId = jdbcTemplate.queryForObject(
                "insert into product (brand_id, category_id, name, description, base_price, status, "
                        + "min_price, search_text, created_at, updated_at) "
                        + "values (?, ?, '수분 크림', '설명', 20000, 'ON_SALE', 20000, '수분 크림', now(), now()) returning id",
                Long.class, brandId, categoryId);
        jdbcTemplate.update("insert into sku (product_id, name, stock, created_at, updated_at) "
                + "values (?, '50ml', 10, now(), now())", productId);
        return productId;
    }
}
