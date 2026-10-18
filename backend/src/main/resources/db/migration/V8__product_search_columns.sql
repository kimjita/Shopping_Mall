-- 검색·정렬용 비정규화 컬럼. 기존 행을 채운 뒤 NOT NULL로 바꾼다.
ALTER TABLE product ADD COLUMN min_price BIGINT;
ALTER TABLE product ADD COLUMN search_text TEXT;

UPDATE product p
SET min_price = (SELECT MIN(p.base_price + s.additional_price) FROM sku s WHERE s.product_id = p.id);

-- SKU 없이 남은 행이 있어도 NOT NULL 전환이 실패하지 않도록 정가로 채운다.
UPDATE product SET min_price = base_price WHERE min_price IS NULL;

UPDATE product p
SET search_text = b.name || ' ' || p.name
FROM brand b
WHERE b.id = p.brand_id;

ALTER TABLE product ALTER COLUMN min_price SET NOT NULL;
ALTER TABLE product ALTER COLUMN search_text SET NOT NULL;

-- 세일 컬럼은 모두 NULL이거나 모두 유효해야 한다.
ALTER TABLE product ADD CONSTRAINT chk_product_sale_consistency CHECK (
    (sale_rate IS NULL AND sale_starts_at IS NULL AND sale_ends_at IS NULL)
    OR (sale_rate BETWEEN 1 AND 90 AND sale_starts_at IS NOT NULL AND sale_ends_at > sale_starts_at)
);

CREATE INDEX idx_product_search_text_trgm ON product USING gin (search_text gin_trgm_ops);
CREATE INDEX idx_product_status_created ON product (status, created_at DESC, id DESC);
