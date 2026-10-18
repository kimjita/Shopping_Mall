-- 적립금 잔액은 조건부 UPDATE로만 바꾼다. CHECK가 음수 잔액을 마지막으로 막는다.
ALTER TABLE member ADD COLUMN point_balance BIGINT NOT NULL DEFAULT 0 CHECK (point_balance >= 0);

CREATE TABLE point_history (
    id            BIGSERIAL    PRIMARY KEY,
    member_id     BIGINT       NOT NULL REFERENCES member (id),
    type          VARCHAR(20)  NOT NULL,
    amount        BIGINT       NOT NULL CHECK (amount > 0),
    balance_after BIGINT       NOT NULL,
    reason        VARCHAR(100) NOT NULL,
    -- 주문 테이블은 Phase 6에서 만들고, 그때 외래 키를 추가한다.
    order_id      BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- 내역은 회원별로 최근 순(id 내림차순)으로 읽는다.
CREATE INDEX idx_point_history_member_id_desc ON point_history (member_id, id DESC);
