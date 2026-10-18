CREATE TABLE recent_view (
    id         BIGSERIAL   PRIMARY KEY,
    member_id  BIGINT      NOT NULL REFERENCES member (id),
    product_id BIGINT      NOT NULL REFERENCES product (id),
    viewed_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_recent_view_member_product UNIQUE (member_id, product_id)
);

-- 최근 본 상품은 회원별로 본 시각의 내림차순으로 읽는다.
CREATE INDEX idx_recent_view_member_viewed_at ON recent_view (member_id, viewed_at DESC);
