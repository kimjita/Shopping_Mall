CREATE TABLE product (
    id             BIGSERIAL    PRIMARY KEY,
    brand_id       BIGINT       NOT NULL REFERENCES brand (id),
    category_id    BIGINT       NOT NULL REFERENCES category (id),
    name           VARCHAR(100) NOT NULL,
    summary        VARCHAR(200),
    description    TEXT         NOT NULL,
    ingredients    TEXT,
    base_price     BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    sale_rate      INT,
    sale_starts_at TIMESTAMPTZ,
    sale_ends_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_product_category_id ON product (category_id);
CREATE INDEX idx_product_brand_id ON product (brand_id);

CREATE TABLE product_image (
    product_id BIGINT       NOT NULL REFERENCES product (id),
    sort_order INT          NOT NULL,
    url        VARCHAR(500) NOT NULL,
    PRIMARY KEY (product_id, sort_order)
);

CREATE TABLE sku (
    id               BIGSERIAL   PRIMARY KEY,
    product_id       BIGINT      NOT NULL REFERENCES product (id),
    name             VARCHAR(50) NOT NULL,
    additional_price BIGINT      NOT NULL DEFAULT 0,
    stock            INT         NOT NULL CHECK (stock >= 0),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (product_id, name)
);
