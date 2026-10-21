-- 상품 평점은 리뷰를 쓰거나 고치거나 지울 때 같은 트랜잭션에서 한 문장으로 갱신하는 비정규화 값이다.
-- 리뷰가 없으면 rating_avg는 NULL이다.
ALTER TABLE product
    ADD COLUMN review_count INT NOT NULL DEFAULT 0,
    ADD COLUMN rating_sum   INT NOT NULL DEFAULT 0,
    ADD COLUMN rating_avg   NUMERIC(3, 2),
    ADD CONSTRAINT ck_product_review_count CHECK (review_count >= 0),
    ADD CONSTRAINT ck_product_rating_sum CHECK (rating_sum >= 0),
    ADD CONSTRAINT ck_product_rating_avg_null CHECK ((review_count = 0) = (rating_avg IS NULL));

-- 평점순·리뷰 많은 순 목록은 판매 중 상품만 읽고, 같은 값은 id 내림차순이다. 리뷰 없는 상품은 맨 뒤다.
CREATE INDEX idx_product_status_rating ON product (status, rating_avg DESC NULLS LAST, id DESC);
CREATE INDEX idx_product_status_review_count ON product (status, review_count DESC, id DESC);

-- 주문상품 하나에 리뷰 하나다. 지우면 행이 사라져 같은 주문상품에 다시 쓸 수 있다.
-- 피부 타입·톤·고민은 작성 시점 회원 프로필의 스냅샷이고, 수정해도 바뀌지 않는다.
CREATE TABLE review (
    id            BIGSERIAL     PRIMARY KEY,
    order_item_id BIGINT        NOT NULL REFERENCES order_item (id),
    member_id     BIGINT        NOT NULL REFERENCES member (id),
    product_id    BIGINT        NOT NULL REFERENCES product (id),
    rating        SMALLINT      NOT NULL,
    content       VARCHAR(1000) NOT NULL,
    skin_type     VARCHAR(20),
    skin_tone     VARCHAR(20),
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_review_order_item UNIQUE (order_item_id),
    CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5)
);

-- 상품 리뷰 목록은 최신 순, 별점 필터·정렬, 피부 타입 필터 모두 id 내림차순으로 읽는다.
CREATE INDEX idx_review_product_id_desc ON review (product_id, id DESC);
CREATE INDEX idx_review_product_rating_id_desc ON review (product_id, rating, id DESC);
CREATE INDEX idx_review_product_skin_type_id_desc ON review (product_id, skin_type, id DESC);
-- 내 리뷰는 회원별로 최신 순으로 읽는다.
CREATE INDEX idx_review_member_id_desc ON review (member_id, id DESC);

-- 첫 번째가 대표 사진이다.
CREATE TABLE review_image (
    review_id  BIGINT       NOT NULL REFERENCES review (id),
    sort_order INT          NOT NULL,
    url        VARCHAR(500) NOT NULL,
    PRIMARY KEY (review_id, sort_order)
);

CREATE TABLE review_skin_concern (
    review_id BIGINT      NOT NULL REFERENCES review (id),
    concern   VARCHAR(30) NOT NULL,
    PRIMARY KEY (review_id, concern)
);
