-- 주문 금액 7개는 주문 시점의 스냅샷이다. CHECK가 결제 금액 하한(100원)과 음수 적립금 사용을 마지막으로 막는다.
CREATE TABLE orders (
    id               BIGSERIAL    PRIMARY KEY,
    -- yyyyMMdd-영숫자 8자. 토스 orderId로도 쓰므로 unique다.
    order_number     VARCHAR(20)  NOT NULL,
    member_id        BIGINT       NOT NULL REFERENCES member (id),
    status           VARCHAR(20)  NOT NULL,
    items_price      BIGINT       NOT NULL,
    sale_discount    BIGINT       NOT NULL,
    items_sale_price BIGINT       NOT NULL,
    coupon_discount  BIGINT       NOT NULL,
    shipping_fee     BIGINT       NOT NULL,
    points_used      BIGINT       NOT NULL,
    payment_amount   BIGINT       NOT NULL,
    -- 쿠폰을 쓰지 않은 주문은 둘 다 null이다. 쿠폰 이름은 주문 시점의 스냅샷이다.
    member_coupon_id BIGINT REFERENCES member_coupon (id),
    coupon_name      VARCHAR(50),
    -- 배송지는 주문 시점에 주소록에서 복사한다. 이후 주소를 고치거나 지워도 주문은 바뀌지 않는다.
    recipient        VARCHAR(30)  NOT NULL,
    phone            VARCHAR(11)  NOT NULL,
    zip_code         CHAR(5)      NOT NULL,
    address1         VARCHAR(200) NOT NULL,
    address2         VARCHAR(200),
    -- 결제 기한이다. expires_at <= now인 PENDING 주문이 만료 대상이다.
    expires_at       TIMESTAMPTZ  NOT NULL,
    canceled_at      TIMESTAMPTZ,
    cancel_reason    VARCHAR(20),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT ck_orders_payment_amount CHECK (payment_amount >= 100),
    CONSTRAINT ck_orders_points_used CHECK (points_used >= 0)
);

-- 내 주문은 회원별로 최신 순(id 내림차순)으로 읽는다.
CREATE INDEX idx_orders_member_id_desc ON orders (member_id, id DESC);
-- 만료 스케줄러는 결제 대기 주문만 기한 순으로 훑는다.
CREATE INDEX idx_orders_pending_expires_at ON orders (expires_at) WHERE status = 'PENDING';
-- 회원당 결제 대기 주문은 하나뿐이다. 서비스가 이전 주문을 먼저 취소하고, 이 인덱스가 마지막 방어선이다.
CREATE UNIQUE INDEX uk_orders_member_pending ON orders (member_id) WHERE status = 'PENDING';

-- 상품·옵션 정보는 주문 시점의 스냅샷이다. 이후 상품이 바뀌거나 지워져도 주문 내역은 그대로 보인다.
CREATE TABLE order_item (
    id              BIGSERIAL    PRIMARY KEY,
    order_id        BIGINT       NOT NULL REFERENCES orders (id),
    product_id      BIGINT       NOT NULL REFERENCES product (id),
    sku_id          BIGINT       NOT NULL REFERENCES sku (id),
    product_name    VARCHAR(100) NOT NULL,
    sku_name        VARCHAR(50)  NOT NULL,
    thumbnail_url   VARCHAR(500),
    unit_price      BIGINT       NOT NULL,
    unit_sale_price BIGINT       NOT NULL,
    quantity        INT          NOT NULL,
    line_total      BIGINT       NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);

-- V13, V14가 미뤄 둔 외래 키다.
ALTER TABLE point_history ADD CONSTRAINT fk_point_history_order FOREIGN KEY (order_id) REFERENCES orders (id);
ALTER TABLE member_coupon ADD CONSTRAINT fk_member_coupon_used_order FOREIGN KEY (used_order_id) REFERENCES orders (id);
