CREATE TABLE cart_item (
    id         BIGSERIAL   PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES member (id),
    sku_id     BIGINT      NOT NULL REFERENCES sku (id),
    quantity   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- 수량 범위는 서비스가 먼저 검증한다. 이 제약은 마지막 방어선이다.
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99),
    -- 같은 옵션은 한 줄이고, 다시 담으면 수량을 더한다.
    CONSTRAINT uq_cart_item_member_sku UNIQUE (member_id, sku_id)
);

-- 장바구니는 회원별로 담은 순서(id 오름차순)로 읽는다.
CREATE INDEX idx_cart_item_member_id ON cart_item (member_id, id);
