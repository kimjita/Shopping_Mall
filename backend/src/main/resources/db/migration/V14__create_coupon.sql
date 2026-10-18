-- 선착순 다운로드 쿠폰. issued_quantity는 조건부 UPDATE(issued_quantity < total_quantity)로만 늘리고,
-- CHECK가 초과 발급을 마지막으로 막는다.
CREATE TABLE coupon (
    id               BIGSERIAL    PRIMARY KEY,
    name             VARCHAR(50)  NOT NULL,
    discount_type    VARCHAR(10)  NOT NULL,
    discount_value   BIGINT       NOT NULL,
    max_discount     BIGINT,
    min_order_amount BIGINT       NOT NULL DEFAULT 0,
    total_quantity   INT          NOT NULL,
    issued_quantity  INT          NOT NULL DEFAULT 0,
    -- null이면 누구나 받을 수 있다.
    required_grade   VARCHAR(20),
    issue_starts_at  TIMESTAMPTZ  NOT NULL,
    issue_ends_at    TIMESTAMPTZ  NOT NULL,
    use_starts_at    TIMESTAMPTZ  NOT NULL,
    use_ends_at      TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_coupon_quantity CHECK (issued_quantity BETWEEN 0 AND total_quantity),
    CONSTRAINT ck_coupon_issue_period CHECK (issue_ends_at > issue_starts_at),
    CONSTRAINT ck_coupon_use_period CHECK (use_ends_at > use_starts_at),
    CONSTRAINT ck_coupon_discount CHECK (
        (discount_type = 'FIXED' AND max_discount IS NULL)
        OR (discount_type = 'RATE' AND discount_value BETWEEN 1 AND 90 AND max_discount > 0))
);

-- 만료는 저장하지 않는다. status는 AVAILABLE/USED만 쓰고 EXPIRED는 읽을 때 use_ends_at으로 계산한다.
CREATE TABLE member_coupon (
    id            BIGSERIAL   PRIMARY KEY,
    member_id     BIGINT      NOT NULL REFERENCES member (id),
    coupon_id     BIGINT      NOT NULL REFERENCES coupon (id),
    status        VARCHAR(20) NOT NULL,
    -- 주문 테이블은 Phase 6에서 만들고, 그때 외래 키를 추가한다.
    used_order_id BIGINT,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    -- 1인 1장. 동시에 받아도 insert ... on conflict do nothing이 한 건만 통과시킨다.
    CONSTRAINT uk_member_coupon_member_coupon UNIQUE (member_id, coupon_id)
);

-- 내 쿠폰은 회원별로 최신 순(id 내림차순)으로 읽는다.
CREATE INDEX idx_member_coupon_member_id_desc ON member_coupon (member_id, id DESC);
