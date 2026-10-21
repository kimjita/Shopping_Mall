package com.sprint.mission.recommendation;

import org.springframework.jdbc.core.JdbcTemplate;

/** 추천 테스트용 SQL 픽스처다. 리뷰 한 건마다 주문·주문상품을 직접 넣어 같은 피부 타입 리뷰를 빠르게 쌓는다. */
public class RecommendationFixtures {

    private final JdbcTemplate jdbcTemplate;
    private final Long brandId;
    private final Long categoryId;
    private final Long memberId;
    private int sequence;

    public RecommendationFixtures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.brandId = jdbcTemplate.queryForObject(
                "insert into brand (name, created_at, updated_at) values ('브랜드', now(), now()) returning id",
                Long.class);
        this.categoryId = jdbcTemplate.queryForObject("select id from category limit 1", Long.class);
        this.memberId = jdbcTemplate.queryForObject(
                "insert into member (email, password, name, phone, role, status, created_at, updated_at) "
                        + "values ('m@test.com', 'pw', '회원', '01000000000', 'USER', 'ACTIVE', now(), now()) "
                        + "returning id", Long.class);
    }

    /** 판매 중인 상품을 만들고 id를 돌려준다. */
    public Long product(String name) {
        Long productId = jdbcTemplate.queryForObject(
                "insert into product (brand_id, category_id, name, description, base_price, status, "
                        + "min_price, search_text, created_at, updated_at) "
                        + "values (?, ?, ?, '설명', 20000, 'ON_SALE', 20000, ?, now(), now()) returning id",
                Long.class, brandId, categoryId, name, name);
        jdbcTemplate.update("insert into sku (product_id, name, stock, created_at, updated_at) "
                + "values (?, '50ml', 10, now(), now())", productId);
        return productId;
    }

    public void stop(Long productId) {
        jdbcTemplate.update("update product set status = 'STOPPED' where id = ?", productId);
    }

    /** 리뷰 count건을 같은 별점·피부 타입(null 가능)으로 쌓는다. */
    public void reviews(Long productId, String skinType, int rating, int count) {
        for (int i = 0; i < count; i++) {
            review(productId, skinType, rating);
        }
    }

    private void review(Long productId, String skinType, int rating) {
        int n = ++sequence;
        Long skuId = jdbcTemplate.queryForObject("select id from sku where product_id = ?", Long.class, productId);
        Long orderId = jdbcTemplate.queryForObject(
                "insert into orders (order_number, member_id, status, items_price, sale_discount, items_sale_price, "
                        + "coupon_discount, shipping_fee, points_used, payment_amount, recipient, phone, zip_code, "
                        + "address1, expires_at, created_at, updated_at) "
                        + "values (?, ?, 'CONFIRMED', 20000, 0, 20000, 0, 0, 0, 20000, '회원', '01012345678', "
                        + "'06236', '서울', now(), now(), now()) returning id", Long.class, "ORD-" + n, memberId);
        Long orderItemId = jdbcTemplate.queryForObject(
                "insert into order_item (order_id, product_id, sku_id, product_name, sku_name, unit_price, "
                        + "unit_sale_price, quantity, line_total, created_at, updated_at) "
                        + "values (?, ?, ?, '상품', '50ml', 20000, 20000, 1, 20000, now(), now()) returning id",
                Long.class, orderId, productId, skuId);
        jdbcTemplate.update("insert into review (order_item_id, member_id, product_id, rating, content, skin_type, "
                + "created_at, updated_at) values (?, ?, ?, ?, '좋다', ?, now(), now())",
                orderItemId, memberId, productId, rating, skinType);
    }
}
