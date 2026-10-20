-- 토스 결제 승인 기록이다. 주문당 결제는 한 건이고, 같은 payment_key를 두 번 받을 수 없다.
CREATE TABLE payment (
    id          BIGSERIAL    PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES orders (id),
    payment_key VARCHAR(200) NOT NULL,
    amount      BIGINT       NOT NULL,
    method      VARCHAR(30)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    approved_at TIMESTAMPTZ  NOT NULL,
    canceled_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_payment_order_id UNIQUE (order_id),
    CONSTRAINT uk_payment_payment_key UNIQUE (payment_key),
    -- 주문 결제 금액 하한(100원)과 같다. 마지막 방어선이다.
    CONSTRAINT ck_payment_amount CHECK (amount >= 100)
);
