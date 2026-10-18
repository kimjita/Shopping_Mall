-- 상품 검색 성능 측정용 더미 데이터(브랜드 300개, 상품 100,000개, SKU·이미지 포함)를 만든다.
--
-- 주의: 로컬 개발 DB에 "한 번만" 실행하는 스크립트다. Flyway 마이그레이션이 아니며 테스트와도 무관하다.
-- 이미 상품이 있거나 더미 브랜드가 있으면 즉시 실패하고 아무것도 넣지 않는다(트랜잭션 롤백).
-- 다시 넣으려면 로컬 볼륨을 지우고(docker compose down -v) 마이그레이션부터 다시 적용한다.
--
-- 실행:
--   docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/seed-catalog.sql
--
-- random()은 setseed로 고정하므로 같은 스키마에서는 같은 분포로 만들어진다(now()에 따른 시각만 다르다).

\set ON_ERROR_STOP on
\timing on

BEGIN;

SELECT setseed(0.42);

-- 두 번 실행하거나 이미 상품이 있는 DB에서는 실패시킨다.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM product) THEN
        RAISE EXCEPTION 'product 테이블이 비어 있지 않다. 새 로컬 DB에서만 실행한다.';
    END IF;
END $$;

-- 브랜드 300개. 이름이 unique라서 두 번째 실행은 여기서도 실패한다.
INSERT INTO brand (name, description, created_at, updated_at)
SELECT '더미브랜드' || lpad(g::text, 3, '0'), '성능 측정용 더미 브랜드', now(), now()
FROM generate_series(1, 300) AS g;

-- 상품 100,000개의 기본 속성을 임시 테이블에 먼저 만든다(random() 값을 한 번만 뽑아 재사용하려는 목적이다).
CREATE TEMP TABLE seed_product ON COMMIT DROP AS
WITH words AS (
    SELECT
        -- 2글자(촉촉, 탄력, 진정, 톤업)와 3글자 이상(저자극, 브라이트닝 등)을 섞는다.
        ARRAY['촉촉', '순한', '맑은', '탄력', '진정', '톤업', '수분가득', '쫀쫀한', '산뜻한', '깊은보습',
              '브라이트닝', '저자극', '매끈한', '데일리', '고농축', '약산성'] AS adjectives,
        -- 성분: 2글자(병풀, 시카, 녹차, 쌀겨)부터 8글자(나이아신아마이드)까지 섞는다.
        ARRAY['히알루론', '세라마이드', '나이아신아마이드', '콜라겐', '병풀', '시카', '비타민', '레티놀',
              '펩타이드', '판테놀', '녹차', '달팽이', '쌀겨', '어성초', '마데카소사이드', '프로폴리스',
              '티트리', '알로에', '제주감귤', '스쿠알란'] AS ingredients,
        -- 제품유형: 2글자(토너, 크림, 세럼 등)와 3글자 이상(에센스, 선크림, 마스크팩 등)을 섞는다.
        ARRAY['토너', '크림', '세럼', '에센스', '앰플', '로션', '미스트', '선크림', '클렌저', '마스크팩',
              '립밤', '쿠션', '샴푸'] AS types,
        (SELECT array_agg(id ORDER BY name) FROM brand WHERE name LIKE '더미브랜드%') AS brand_ids,
        -- 말단 카테고리는 하드코딩하지 않고 자식이 없는 카테고리를 조회한다.
        (SELECT array_agg(c.id ORDER BY c.id) FROM category c
          WHERE NOT EXISTS (SELECT 1 FROM category x WHERE x.parent_id = c.id)) AS leaf_ids
),
base AS (
    SELECT
        g AS n,
        w.*,
        random() AS r_adj, random() AS r_ing, random() AS r_type, random() AS r_brand,
        random() AS r_price, random() AS r_status, random() AS r_sale, random() AS r_rate,
        random() AS r_start, random() AS r_end, random() AS r_created, random() AS r_sku, random() AS r_img
    FROM generate_series(1, 100000) AS g CROSS JOIN words w
)
SELECT
    n,
    brand_ids[1 + floor(r_brand * array_length(brand_ids, 1))::int] AS brand_id,
    -- 말단 카테고리에 고르게 나눈다.
    leaf_ids[1 + (n % array_length(leaf_ids, 1))] AS category_id,
    adjectives[1 + floor(r_adj * array_length(adjectives, 1))::int] || ' '
        || ingredients[1 + floor(r_ing * array_length(ingredients, 1))::int] || ' '
        || types[1 + floor(r_type * array_length(types, 1))::int] || ' ' || n AS name,
    -- 5,000원~79,900원, 100원 단위
    (5000 + floor(r_price * 750) * 100)::bigint AS base_price,
    CASE WHEN r_status < 0.95 THEN 'ON_SALE' WHEN r_status < 0.98 THEN 'STOPPED' ELSE 'DELETED' END AS status,
    -- 20%는 지금 진행 중인 세일이다.
    (r_sale < 0.20) AS on_sale,
    (5 + floor(r_rate * 66))::int AS sale_rate,
    now() - ((1 + floor(r_start * 30)) || ' days')::interval AS sale_starts_at,
    now() + ((1 + floor(r_end * 30)) || ' days')::interval AS sale_ends_at,
    -- 최근 1년에 흩뿌린다.
    now() - (r_created * interval '365 days') AS created_at,
    1 + floor(r_sku * 4)::int AS sku_count,
    1 + floor(r_img * 3)::int AS image_count
FROM base;

-- SKU: 상품마다 1~4개, 첫 SKU의 추가금은 항상 0이다.
CREATE TEMP TABLE seed_sku ON COMMIT DROP AS
SELECT
    p.n AS product_n,
    s AS idx,
    '옵션' || s AS name,
    CASE WHEN s = 1 THEN 0 ELSE (floor(random() * 7) * 500)::bigint END AS additional_price,
    CASE WHEN random() < 0.10 THEN 0 ELSE 1 + floor(random() * 200)::int END AS stock
FROM seed_product p CROSS JOIN LATERAL generate_series(1, p.sku_count) AS s;

-- 상품. min_price는 SKU들의 base_price + additional_price 최솟값, search_text는 "브랜드명 상품명"이다.
INSERT INTO product (id, brand_id, category_id, name, summary, description, ingredients, base_price, status,
                     sale_rate, sale_starts_at, sale_ends_at, min_price, search_text, created_at, updated_at)
SELECT
    p.n, p.brand_id, p.category_id, p.name,
    '더미 상품 요약 ' || p.n,
    '성능 측정용 더미 상품 설명 ' || p.n,
    '정제수, 글리세린, 부틸렌글리콜',
    p.base_price, p.status,
    CASE WHEN p.on_sale THEN p.sale_rate END,
    CASE WHEN p.on_sale THEN p.sale_starts_at END,
    CASE WHEN p.on_sale THEN p.sale_ends_at END,
    p.base_price + m.min_additional,
    b.name || ' ' || p.name,
    p.created_at, p.created_at
FROM seed_product p
JOIN brand b ON b.id = p.brand_id
JOIN (SELECT product_n, min(additional_price) AS min_additional FROM seed_sku GROUP BY product_n) m
    ON m.product_n = p.n;

SELECT setval(pg_get_serial_sequence('product', 'id'), (SELECT max(id) FROM product));

INSERT INTO sku (product_id, name, additional_price, stock, created_at, updated_at)
SELECT s.product_n, s.name, s.additional_price, s.stock, p.created_at, p.created_at
FROM seed_sku s JOIN seed_product p ON p.n = s.product_n;

INSERT INTO product_image (product_id, sort_order, url)
SELECT p.n, i - 1, 'https://cdn.example.com/dummy/' || p.n || '/' || i || '.jpg'
FROM seed_product p CROSS JOIN LATERAL generate_series(1, p.image_count) AS i;

COMMIT;

ANALYZE brand;
ANALYZE product;
ANALYZE sku;
ANALYZE product_image;

SELECT
    (SELECT count(*) FROM brand WHERE name LIKE '더미브랜드%') AS brands,
    (SELECT count(*) FROM product) AS products,
    (SELECT count(*) FROM sku) AS skus,
    (SELECT count(*) FROM product_image) AS images,
    (SELECT count(*) FROM product WHERE sale_rate IS NOT NULL) AS on_sale_products,
    (SELECT count(*) FROM product WHERE status = 'ON_SALE') AS status_on_sale;
