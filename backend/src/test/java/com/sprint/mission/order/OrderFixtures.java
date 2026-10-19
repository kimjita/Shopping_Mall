package com.sprint.mission.order;

import org.springframework.jdbc.core.JdbcTemplate;

/** 주문 행이 필요한 다른 도메인 테스트(적립금 원장 등)를 위한 값이다. */
public final class OrderFixtures {

    private OrderFixtures() {
    }

    /** id가 1부터 {@code lastOrderId}까지인 취소된 주문을 넣는다. 결제 대기 unique 인덱스에 걸리지 않는다. */
    public static void insertCanceledOrders(JdbcTemplate jdbcTemplate, Long memberId, int lastOrderId) {
        for (int id = 1; id <= lastOrderId; id++) {
            jdbcTemplate.update(
                    "insert into orders (id, order_number, member_id, status, items_price, sale_discount, "
                            + "items_sale_price, coupon_discount, shipping_fee, points_used, payment_amount, "
                            + "recipient, phone, zip_code, address1, expires_at, canceled_at, cancel_reason, "
                            + "created_at, updated_at) "
                            + "values (?, ?, ?, 'CANCELED', 0, 0, 0, 0, 0, 0, 100, '홍길동', '01012345678', '06236', "
                            + "'서울', now(), now(), 'REPLACED', now(), now())",
                    (long) id, "20261019-FIXTURE" + id, memberId);
        }
    }
}
