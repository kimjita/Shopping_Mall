-- 상품 목록 쿼리의 EXPLAIN (ANALYZE, BUFFERS) 측정 스크립트다.
-- ProductSearchRepository가 만드는 SQL(Task 3)과 같은 모양이며, 파라미터는 리터럴로 바꿨다.
-- 현재 판매가 식, ILIKE ... escape '\', 정렬 동률 처리, 키셋 조건을 그대로 옮겼다.
-- 한 페이지는 size(20) + 1 = 21행을 읽는다.
--
-- 실행(더미 데이터 적재 후):
--   docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/explain-search.sql
-- 인덱스 전/후 비교 절차는 docs/performance/phase3-search.md에 적었다.

\set ON_ERROR_STOP on
\pset pager off

-- 애플리케이션은 요청당 한 번 찍은 Clock 값을 파라미터로 넘기며, 여기서는 now()로 대신한다.

\echo
\echo === a1: 3글자 이상 검색어 히알루론, 신상품순 첫 페이지
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.search_text ilike '%히알루론%' escape '\'
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === a2: 3글자 이상 검색어 히알루론 + 스킨케어(categoryId=1) + 가격 낮은순 첫 페이지
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.search_text ilike '%히알루론%' escape '\'
  and p.category_id in (8, 9, 10, 11, 12)
order by case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end, p.id
fetch first 21 rows only;

\echo
\echo === a3: 드문 검색어 더미브랜드150(약 330건), 신상품순 첫 페이지
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.search_text ilike '%더미브랜드150%' escape '\'
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === b1: 2글자 검색어 토너, 신상품순 첫 페이지
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.search_text ilike '%토너%' escape '\'
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === b2: 2글자 검색어 토너 + 가격 낮은순 첫 페이지
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.search_text ilike '%토너%' escape '\'
order by case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end, p.id
fetch first 21 rows only;

\echo
\echo === c: 대분류 카테고리(스킨케어 말단 8~12) + 신상품순 첫 페이지
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.category_id in (8, 9, 10, 11, 12)
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === d: 가격 낮은순 + 가격대 필터(현재 판매가 10,000~30,000원) 첫 페이지
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end >= 10000
  and case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end <= 30000
order by case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end, p.id
fetch first 21 rows only;


-- (e) 깊은 페이지: 신상품순 5,000번째 항목 뒤를 커서와 offset으로 읽는다.
-- 5,000번째 항목의 정렬값을 먼저 구한다(측정 대상이 아니다).
select created_at as cur_created, id as cur_id
from product where status = 'ON_SALE'
order by created_at desc, id desc offset 4999 limit 1 \gset

\echo
\echo === e1: 신상품순 5,000번째 뒤 21행 - 커서(키셋)
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and (p.created_at <= :'cur_created'::timestamptz
       and (p.created_at < :'cur_created'::timestamptz or p.id < :cur_id))
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === e2: 신상품순 5,000번째 뒤 21행 - offset
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
order by p.created_at desc, p.id desc
offset 5000 rows fetch first 21 rows only;


-- 가격 낮은순도 같은 위치(5,000번째)에서 비교한다.
select (case when (q.sale_rate is not null and q.sale_starts_at <= now() and q.sale_ends_at > now()) then ((((q.min_price * (100 - q.sale_rate)) / 100) / 10) * 10) else q.min_price end) as cur_price, q.id as cur_pid
from product q where q.status = 'ON_SALE'
order by (case when (q.sale_rate is not null and q.sale_starts_at <= now() and q.sale_ends_at > now()) then ((((q.min_price * (100 - q.sale_rate)) / 100) / 10) * 10) else q.min_price end), q.id offset 4999 limit 1 \gset

\echo
\echo === e3: 가격 낮은순 5,000번째 뒤 21행 - 커서(키셋)
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and (case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end > :cur_price
       or (case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end = :cur_price and p.id > :cur_pid))
order by case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end, p.id
fetch first 21 rows only;

\echo
\echo === e4: 가격 낮은순 5,000번째 뒤 21행 - offset
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       case when (not exists (select 1 from sku s where s.product_id = p.id and s.stock > 0)) then true else false end as sold_out,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
order by case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end, p.id
offset 5000 rows fetch first 21 rows only;


-- (f) 품절 여부(not exists sku) 컬럼을 뺀 비교용 쿼리다. 실제 쿼리에는 이 컬럼이 있으며, 그 비용을 분리해 보려는 목적이다.

\echo
\echo === f1: c와 같고 sold_out 컬럼 없음
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.category_id in (8, 9, 10, 11, 12)
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === f2: e1과 같고 sold_out 컬럼 없음
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and (p.created_at <= :'cur_created'::timestamptz
       and (p.created_at < :'cur_created'::timestamptz or p.id < :cur_id))
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === f3: b1과 같고 sold_out 컬럼 없음
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and p.search_text ilike '%토너%' escape '\'
order by p.created_at desc, p.id desc
fetch first 21 rows only;

\echo
\echo === f4: e2와 같고 sold_out 컬럼 없음(offset)
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
order by p.created_at desc, p.id desc
offset 5000 rows fetch first 21 rows only;

\echo
\echo === f5: e3과 같고 sold_out 컬럼 없음(가격 커서)
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
  and (case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end > :cur_price
       or (case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end = :cur_price and p.id > :cur_pid))
order by case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end, p.id
fetch first 21 rows only;

\echo
\echo === f6: e4와 같고 sold_out 컬럼 없음(가격 offset)
explain (analyze, buffers)
select p.id, p.name, b.name, i.url, p.min_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
                 then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10)
                 else p.min_price end as current_price,
       case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now())
            then p.sale_rate else null end as sale_rate,
       p.created_at
from product p
join brand b on b.id = p.brand_id
left join product_image i on i.product_id = p.id and i.sort_order = 0
where p.status = 'ON_SALE'
order by case when (p.sale_rate is not null and p.sale_starts_at <= now() and p.sale_ends_at > now()) then ((((p.min_price * (100 - p.sale_rate)) / 100) / 10) * 10) else p.min_price end, p.id
offset 5000 rows fetch first 21 rows only;


-- (g) 검색 조건만 떼어 본 count 쿼리다. 정렬·조인 비용 없이 인덱스가 검색 자체에 주는 효과를 본다.
-- g3은 시퀀셜 스캔을 막고 status 조건도 빼서 trigram 인덱스가 2글자 패턴에서 어떻게 동작하는지 강제로 본다.
\echo
\echo === g1: count 히알루론(3글자 이상)
explain (analyze, buffers)
select count(*) from product p where p.status = 'ON_SALE' and p.search_text ilike '%히알루론%' escape '\';

\echo
\echo === g2: count 토너(2글자)
explain (analyze, buffers)
select count(*) from product p where p.status = 'ON_SALE' and p.search_text ilike '%토너%' escape '\';

\echo
\echo === g3: count 토너(2글자), status 조건 없이 시퀀셜 스캔 금지(enable_seqscan = off)
begin;
set local enable_seqscan = off;
explain (analyze, buffers)
select count(*) from product p where p.search_text ilike '%토너%' escape '\';
rollback;
