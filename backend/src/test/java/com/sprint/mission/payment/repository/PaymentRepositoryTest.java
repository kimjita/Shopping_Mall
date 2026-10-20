package com.sprint.mission.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.cart.repository.CartItemRepository;
import com.sprint.mission.checkout.domain.PriceBreakdown;
import com.sprint.mission.order.domain.Order;
import com.sprint.mission.order.domain.ShippingInfo;
import com.sprint.mission.order.repository.OrderRepository;
import com.sprint.mission.payment.client.PaymentResult;
import com.sprint.mission.payment.domain.Payment;
import com.sprint.mission.support.IntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentRepositoryTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    static final ShippingInfo SHIPPING = new ShippingInfo("홍길동", "01012345678", "06236", "서울 강남구 테헤란로 1", null);

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    CartItemRepository cartItemRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    TransactionTemplate transactionTemplate;

    private Long member(String email) {
        return jdbcTemplate.queryForObject(
                "insert into member (email, password, name, phone, role, status, created_at, updated_at) "
                        + "values (?, 'pw', '회원', '01000000000', 'USER', 'ACTIVE', now(), now()) returning id",
                Long.class, email);
    }

    private Long order(Long memberId, String number) {
        PriceBreakdown price = new PriceBreakdown(30_000, 0, 30_000, 0, 0, 0, 30_000);
        return orderRepository.save(Order.place(memberId, number, price, null, null, SHIPPING, NOW)).getId();
    }

    private Payment payment(Long orderId, String paymentKey) {
        return Payment.approve(orderId, new PaymentResult(paymentKey, "n", "DONE", 30_000, "카드", NOW), NOW);
    }

    @Test
    void 주문_id로_결제를_읽는다() {
        Long orderId = order(member("a@test.com"), "20261020-AAAAAAA1");
        paymentRepository.save(payment(orderId, "key_1"));

        assertThat(paymentRepository.findByOrderId(orderId)).get().extracting(Payment::getPaymentKey).isEqualTo("key_1");
        assertThat(paymentRepository.findByOrderId(orderId + 1)).isEmpty();
    }

    @Test
    void 같은_payment_key를_두_번_넣으면_unique_위반이다() {
        Long a = order(member("a@test.com"), "20261020-AAAAAAA1");
        Long b = order(member("b@test.com"), "20261020-BBBBBBB1");
        paymentRepository.save(payment(a, "key_1"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment(b, "key_1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_주문에_결제를_두_번_넣으면_unique_위반이다() {
        Long a = order(member("a@test.com"), "20261020-AAAAAAA1");
        paymentRepository.save(payment(a, "key_1"));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(payment(a, "key_2")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 결제_금액이_100원_미만이면_CHECK_위반이다() {
        Long a = order(member("a@test.com"), "20261020-AAAAAAA1");
        Payment tiny = Payment.approve(a, new PaymentResult("key_1", "n", "DONE", 99, "카드", NOW), NOW);

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(tiny))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 회원과_SKU로_장바구니_줄만_지운다() {
        Long a = member("a@test.com");
        Long b = member("b@test.com");
        List<Long> skus = skus(3);
        cart(a, skus.get(0));
        cart(a, skus.get(1));
        cart(a, skus.get(2));
        cart(b, skus.get(0));

        int deleted = transactionTemplate.execute(
                s -> cartItemRepository.deleteByMemberIdAndSkuIdIn(a, List.of(skus.get(0), skus.get(1))));

        assertThat(deleted).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("select member_id || ':' || sku_id from cart_item order by id", String.class))
                .containsExactly(a + ":" + skus.get(2), b + ":" + skus.get(0));
    }

    private void cart(Long memberId, Long skuId) {
        jdbcTemplate.update("insert into cart_item (member_id, sku_id, quantity, created_at, updated_at) "
                + "values (?, ?, 1, now(), now())", memberId, skuId);
    }

    private List<Long> skus(int count) {
        Long brandId = jdbcTemplate.queryForObject(
                "insert into brand (name, created_at, updated_at) values ('브랜드', now(), now()) returning id", Long.class);
        Long categoryId = jdbcTemplate.queryForObject("select id from category limit 1", Long.class);
        Long productId = jdbcTemplate.queryForObject(
                "insert into product (brand_id, category_id, name, description, base_price, status, "
                        + "min_price, search_text, created_at, updated_at) "
                        + "values (?, ?, '수분 크림', '설명', 20000, 'ON_SALE', 20000, '수분 크림', now(), now()) returning id",
                Long.class, brandId, categoryId);
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update("insert into sku (product_id, name, stock, created_at, updated_at) "
                    + "values (?, ?, 10, now(), now())", productId, "옵션" + i);
        }
        return jdbcTemplate.queryForList("select id from sku order by id", Long.class);
    }
}
