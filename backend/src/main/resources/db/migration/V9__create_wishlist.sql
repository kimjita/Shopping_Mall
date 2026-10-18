CREATE TABLE wishlist (
    id         BIGSERIAL   PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES member (id),
    product_id BIGINT      NOT NULL REFERENCES product (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_wishlist_member_product UNIQUE (member_id, product_id)
);

-- 찜 목록은 회원별로 최근에 찜한 순서(id 내림차순)로 읽는다.
CREATE INDEX idx_wishlist_member_id_desc ON wishlist (member_id, id DESC);
