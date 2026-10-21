-- 최근 7일(168시간) 판매량은 매시간 배치가 벌크 SQL로만 바꾸는 비정규화 값이다.
ALTER TABLE product
    ADD COLUMN sales_7d INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_product_sales_7d CHECK (sales_7d >= 0);

-- 랭킹·판매순 목록은 판매 중 상품만 읽고, 같은 값은 id 내림차순이다.
CREATE INDEX idx_product_status_sales ON product (status, sales_7d DESC, id DESC);

-- 판매량 배치는 승인 시각이 최근 7일 안인 결제만 훑는다.
CREATE INDEX idx_payment_approved_at ON payment (approved_at);

-- 피부 타입별 추천 상품은 매일 새벽 배치가 통째로 다시 만든다. rank는 1~20위다.
CREATE TABLE skin_recommendation (
    skin_type    VARCHAR(20)  NOT NULL,
    rank         INT          NOT NULL,
    product_id   BIGINT       NOT NULL REFERENCES product (id),
    review_count INT          NOT NULL,
    rating_avg   NUMERIC(3, 2) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (skin_type, rank),
    CONSTRAINT ck_skin_recommendation_rank CHECK (rank BETWEEN 1 AND 20)
);

-- 배너는 노출 기간(starts_at 이상, ends_at 미만) 안에서 sort_order 오름차순으로 보여 준다.
CREATE TABLE banner (
    id         BIGSERIAL    PRIMARY KEY,
    title      VARCHAR(50)  NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    link_url   VARCHAR(500) NOT NULL,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ  NOT NULL,
    sort_order INT          NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_banner_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_banner_sort_order CHECK (sort_order BETWEEN 0 AND 1000)
);

CREATE INDEX idx_banner_sort_order_id ON banner (sort_order, id);
