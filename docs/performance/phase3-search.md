# Phase 3 상품 검색 성능 기록

상품 목록 검색(`GET /api/products`)에 쓰는 인덱스 두 개(V8)가 10만 건 데이터에서 실제로 얼마나 효과가 있는지 `EXPLAIN (ANALYZE, BUFFERS)`로 측정한 기록이다. 이 문서의 숫자는 모두 아래 절차로 직접 측정한 값이다.

## 1. 환경

| 항목 | 값 |
| --- | --- |
| 실행 환경 | 로컬 macOS(Apple Silicon), OrbStack 위의 `compose.yaml`의 `postgres` 컨테이너 |
| DB | PostgreSQL 17.11 (`postgres:17-alpine`, aarch64) |
| 설정 | 기본값 그대로다. `shared_buffers` 128MB, `work_mem` 4MB, `effective_cache_size` 4GB, `random_page_cost` 4, `max_parallel_workers_per_gather` 2 |
| 스키마 | Flyway V1~V8 |
| 데이터 | 브랜드 300개, 상품 100,000개, SKU 250,154개, 이미지 200,126개 |
| 상품 분포 | 말단 카테고리 30개에 고르게 분배, 진행 중인 세일 19,950개(약 20%), `ON_SALE` 94,872개(약 95%), `created_at`은 최근 1년에 분산 |
| 크기 | `product` 35MB, `sku` 20MB, `product_image` 16MB, `idx_product_search_text_trgm` 6.9MB, `idx_product_status_created` 4.0MB |

`search_text`(`브랜드명 + " " + 상품명`)는 형용사·성분·제품유형 단어를 섞어 만들었다. 한글 2글자 단어(토너, 크림, 세럼, 병풀 등)와 3글자 이상 단어(히알루론, 나이아신아마이드, 에센스 등)가 모두 들어 있다. 이 데이터에서 `히알루론`을 포함한 상품은 4,942개(약 4.9%), `토너`를 포함한 상품은 7,871개(약 7.9%)다.

## 2. 측정 절차

데이터 적재와 측정은 모두 `scripts/perf/`의 스크립트로 한다.

1. 로컬 DB를 띄우고 마이그레이션을 적용한다(`docker compose up -d postgres` 후 애플리케이션을 한 번 실행하면 Flyway가 V8까지 적용한다).
2. 더미 데이터를 한 번만 넣는다. 끝에서 `ANALYZE`를 실행한다.
   ```
   docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/seed-catalog.sql
   ```
3. 인덱스가 있는 상태("후")에서 `scripts/perf/explain-search.sql`을 실행한다. 첫 실행은 캐시를 데우는 용도라 버리고, 이어서 3번 실행한다.
   ```
   docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/explain-search.sql
   ```
4. 인덱스 두 개를 지우고("전") 같은 방식으로 워밍업 1번, 측정 3번을 실행한다.
   ```sql
   DROP INDEX idx_product_search_text_trgm;
   DROP INDEX idx_product_status_created;
   ```
5. V8과 같은 정의로 다시 만든다.
   ```sql
   CREATE INDEX idx_product_search_text_trgm ON product USING gin (search_text gin_trgm_ops);
   CREATE INDEX idx_product_status_created ON product (status, created_at DESC, id DESC);
   ```

유의할 점은 다음과 같다.

- "전" 상태에도 V7의 `idx_product_category_id`, `idx_product_brand_id`와 기본 키 인덱스는 남아 있다. 지운 것은 V8의 두 개뿐이다.
- 표의 시간은 서버가 보고한 `Execution Time`의 3회 중앙값이다. 쿼리 계획 시간, JDBC 왕복, JSON 직렬화는 포함하지 않는다.
- 모든 측정에서 읽은 페이지가 `shared_buffers`에 있었다(`Buffers: shared hit`만 나왔다). 디스크 캐시가 비어 있는 상태는 측정하지 않았다.
- `explain-search.sql`의 쿼리는 Task 3의 QueryDSL 쿼리가 만드는 SQL을 그대로 옮기고 파라미터를 리터럴로 바꾼 것이다. 요청 시각 파라미터는 `now()`로 대신했다. 한 페이지는 `size + 1 = 21`행이다.

## 3. 결과

아래 표는 이 문서에 쓴 주 측정 세션의 결과다. 쿼리 번호는 `explain-search.sql`의 라벨과 같다. "버퍼"는 `Buffers: shared hit`의 합이다.

### 3.1 목록 쿼리 (실제 쿼리와 같은 모양)

| 쿼리 | 전(인덱스 없음) | 후(인덱스 있음) |
| --- | --- | --- |
| a1. `히알루론` + 신상품순 첫 페이지 | 69.3ms. `Parallel Seq Scan on product`(워커당 31,760행 제거), 버퍼 7,382 | 48.9ms. `Index Scan using idx_product_status_created`로 21행을 읽고 557행을 걸러냄, 버퍼 3,310 |
| a2. `히알루론` + 스킨케어(`categoryId=1`) + 가격 낮은순 | 7.9ms. `Bitmap Heap Scan`(`idx_product_category_id`), 워커당 5,297행 제거, 버퍼 4,180 | 23.8ms. `Parallel Seq Scan on product`(워커당 33,074행 제거), 버퍼 4,814 |
| a3. 드문 검색어 `더미브랜드150`(약 330건) + 신상품순 | 25.1ms. `Parallel Seq Scan on product`(워커당 33,221행 제거), 버퍼 4,887 | 6.2ms. `Index Scan using idx_product_status_created`, 6,163행 제거 후 21행, 버퍼 6,388 |
| b1. `토너` + 신상품순 첫 페이지 | 63.4ms. `Parallel Seq Scan`(워커당 30,840행 제거), 버퍼 7,382 | 40.7ms. `Index Scan using idx_product_status_created`, 219행 제거 후 21행, 버퍼 2,965 |
| b2. `토너` + 가격 낮은순 첫 페이지 | 66.4ms. `Parallel Seq Scan`, 버퍼 7,368 | 65.6ms. `Parallel Seq Scan`(trigram 인덱스를 쓰지 않음), 버퍼 7,368 |
| c. 스킨케어 말단(8~12) + 신상품순 첫 페이지 | 53.5ms. `Bitmap Index Scan on idx_product_category_id` 후 `Bitmap Heap Scan`(워커당 5,274행), 버퍼 6,689 | 39.6ms. `Index Scan using idx_product_status_created`, 103행 제거 후 21행, 버퍼 2,851 |
| d. 가격 낮은순 + 현재 판매가 10,000~30,000원 | 13.8ms. `Parallel Seq Scan`(워커당 9,636행 통과) 후 정렬, 버퍼 4,873 | 13.3ms. 같은 계획, 버퍼 4,873 |
| e1. 신상품순 5,000번째 뒤 21행: 커서 | 66.4ms. `Parallel Seq Scan` 후 외부 병합 정렬(디스크 3MB), 버퍼 7,320 | 38.9ms. `Index Scan using idx_product_status_created`로 21행만 읽음, 버퍼 2,748 |
| e2. 같은 위치: offset 5000 | 75.1ms. `Parallel Seq Scan` 후 외부 병합 정렬, 버퍼 28,160 | 49.9ms. `Index Scan using idx_product_status_created`(워커 3개가 합쳐 5,000행 이상을 읽음), 버퍼 31,590 |
| e3. 가격 낮은순 5,000번째 뒤 21행: 커서 | 66.1ms. `Parallel Seq Scan` 후 정렬, 버퍼 7,368 | 71.7ms. 같은 계획(외부 병합 정렬, 디스크 3.7MB), 버퍼 7,368 |
| e4. 같은 위치: offset 5000 | 69.4ms. `Parallel Seq Scan` 후 정렬, 버퍼 28,146 | 76.3ms. 같은 계획, 버퍼 28,146 |

### 3.2 품절 여부 서브쿼리를 뺀 쿼리 (비용 분리용)

3.1의 쿼리 대부분에서 시간의 큰 부분이 인덱스와 무관한 품절 여부 서브쿼리(`not exists (select 1 from sku ...)`)였다. 4절에서 설명한다. 아래는 같은 쿼리에서 `sold_out` 컬럼만 뺀 값이다.

| 쿼리 | 전 | 후 |
| --- | --- | --- |
| f1. c와 같음(스킨케어 + 신상품순) | 6.9ms. `Bitmap Heap Scan`(`idx_product_category_id`), 버퍼 4,110 | 0.24ms. `Index Scan using idx_product_status_created`, 버퍼 272 |
| f2. e1과 같음(신상품순 커서) | 14.4ms. `Parallel Seq Scan` 후 정렬, 버퍼 4,741 | 0.14ms. `Index Scan using idx_product_status_created`, 버퍼 169 |
| f3. b1과 같음(`토너` + 신상품순) | 23.7ms. `Parallel Seq Scan`, 버퍼 4,803 | 0.34ms. `Index Scan using idx_product_status_created`, 219행 제거, 버퍼 386 |
| f4. 신상품순 offset 5000 | 23.6ms. `Parallel Seq Scan` 후 정렬, 버퍼 25,581 | 10.1ms. `Index Scan using idx_product_status_created`, 버퍼 28,977 |
| f5. 가격 낮은순 커서 | 24.3ms. `Parallel Seq Scan` 후 정렬, 버퍼 4,789 | 25.1ms. 같은 계획, 버퍼 4,789 |
| f6. 가격 낮은순 offset 5000 | 28.6ms. `Parallel Seq Scan` 후 정렬, 버퍼 25,567 | 28.5ms. 같은 계획, 버퍼 25,567 |

### 3.3 검색 조건만 떼어 본 count 쿼리

정렬·조인 없이 `count(*)`만 실행해 인덱스가 검색 조건에 주는 효과를 본다.

| 쿼리 | 전 | 후 |
| --- | --- | --- |
| g1. `히알루론`(3글자 이상), `ON_SALE` | 57.7ms. `Seq Scan on product`(95,279행 제거), 버퍼 4,509 | 5.3ms. `Bitmap Index Scan on idx_product_search_text_trgm`(4,942행) 후 `Bitmap Heap Scan`, 버퍼 2,863 |
| g2. `토너`(2글자), `ON_SALE` | 55.3ms. `Seq Scan on product`(92,519행 제거), 버퍼 4,509 | 56.1ms. `Seq Scan on product`(플래너가 trigram 인덱스를 고르지 않음), 버퍼 4,509 |
| g3. `토너`(2글자), 시퀀셜 스캔 금지(`enable_seqscan = off`)로 인덱스를 강제 | 104.8ms. 인덱스가 없어 `Seq Scan`, 버퍼 4,509 | 93.4ms. `Bitmap Index Scan on idx_product_search_text_trgm`이 100,000행(테이블 전체)을 돌려주고 `Bitmap Heap Scan`이 92,129행을 재검사로 제거, 버퍼 4,829 |

g3에서 인덱스가 있는 쪽이 강제로 인덱스를 쓰고도 시퀀셜 스캔(g2, 56.1ms)보다 느리다.

## 4. 관찰: 이 측정에서 드러난 두 가지

### 4.1 품절 여부 서브쿼리가 해시 서브플랜이 되면 쿼리당 약 40ms가 고정으로 든다

목록 쿼리의 `case when not exists (select 1 from sku where product_id = p.id and stock > 0)`는 플래너가 바깥 행 수를 많게 추정하면 `SubPlan`을 해시로 만든다. 이때 `sku` 전체(재고가 있는 225,086행)를 `Seq Scan`으로 읽어 해시 테이블을 먼저 만든다. 서브플랜의 `Seq Scan on sku`만 약 16ms이고, 해시 구축까지 포함하면 쿼리당 약 40ms가 든다. 페이지 크기(21행)와 무관하다.

- 인덱스 후 신상품순 커서 조회: sold_out 포함 38.9ms(e1), 뺀 쿼리 0.14ms(f2).
- 반대로 바깥 행 수 추정이 작으면 서브플랜은 출력되는 21행마다 `sku_product_id_name_key`를 조회하는 방식(0.003ms × 21)이 된다. a2, a3, d의 서브플랜이 이 경우다.
- SKU 수에 비례해 느려진다. SKU 250,154개에서 약 40ms다.

애플리케이션 코드는 이번 작업에서 바꾸지 않았다. 이 항목은 후속으로 다룰 후보다(예: 21행을 확정한 뒤 품절 여부만 따로 조회하거나 품절 여부를 비정규화).

### 4.2 `ILIKE '%...%'`의 행 수 추정이 `ANALYZE`마다 흔들려 계획이 바뀐다

패턴 매칭의 선택도는 히스토그램 경계값 표본으로 추정하므로 `ANALYZE`를 다시 할 때마다 추정이 달라진다. 같은 데이터에서 `ANALYZE` 표본만 다른 세 번의 측정 세션을 비교하면 다음과 같다. 세션 A는 seed 스크립트 끝의 `ANALYZE` 직후이고, 세션 B와 C는 인덱스를 다시 만든 뒤 `ANALYZE product`를 한 번씩 더 실행한 상태다. 세션 C가 3절의 표다.

| 세션 | a1 추정 행수 | a1 전 → 후 | a2 전 → 후 | a3 전 → 후 |
| --- | --- | --- | --- | --- |
| A (seed 직후 통계) | 957 | 28.3 → 1.6ms | 8.5 → 5.4ms | 72.6 → 7.9ms |
| B (재 `ANALYZE`) | 4,785 | 66.8 → 43.9ms | 7.9 → 25.5ms | 73.1 → 3.7ms |
| C (재 `ANALYZE`, 주 측정) | 5,756 | 69.3 → 48.9ms | 7.9 → 23.8ms | 25.1 → 6.2ms |

실제 일치 행은 4,942개(`ON_SALE` 4,721개)다. 추정이 957일 때는 서브플랜이 행별 인덱스 조회가 되어 a1이 1.6ms였고, 추정이 4,785 이상일 때는 해시 서브플랜이 되어 40ms 이상이 되었다. a2는 세션 A에서 trigram 비트맵과 카테고리 비트맵의 `BitmapAnd`(5.4ms)를 썼지만 B·C에서는 `Parallel Seq Scan`(23.8~25.5ms)을 골랐고, 이 경우 인덱스가 없는 쪽(7.9ms)이 더 빨랐다. 따라서 3.1의 a계열은 인덱스 자체의 효과가 아니라 플래너의 계획 선택이 섞인 값이다. 인덱스 효과만 보려면 3.3의 g1과 3.2의 f계열을 본다.

## 5. 결론

### 5.1 trigram GIN은 3글자 이상 검색을 얼마나 줄였는가

검색 조건만 떼어 본 g1에서 57.7ms(`Seq Scan`)가 5.3ms(`Bitmap Index Scan` + `Bitmap Heap Scan`)로 줄었다. 약 11배다. 일치율이 약 4.9%인 `히알루론`에서의 값이며 버퍼 읽기도 4,509에서 2,863으로 줄었다. 일치 건수가 더 적은 검색어는 효과가 더 크다. 세션 B에서 `더미브랜드150`(약 330건)은 73.1ms에서 3.7ms로, 세션 A에서는 72.6ms에서 7.9ms로 줄었다.

다만 신상품순 첫 페이지(a1, a3)는 일치 건수가 충분하면 플래너가 trigram 인덱스를 쓰지 않고 `idx_product_status_created`를 따라 걸으며 필터로 21행을 채운다. 이때는 trigram이 필요 없다. trigram 인덱스가 실제로 일을 하는 경우는 일치 집합을 끝까지 구해야 하는 경우(가격 정렬, 일치 건수가 적은 검색어, 개수 조회)다. 이번 측정에서 가격 정렬 + 키워드(a2)는 4.2의 이유로 trigram을 쓴 세션(5.4ms)과 쓰지 않은 세션(23.8~25.5ms)이 갈렸다.

### 5.2 2글자 검색은 왜 인덱스가 효과가 없는가

`토너` 검색(g2)은 인덱스가 있어도 `Seq Scan`이고 시간이 같다(55.3ms 대 56.1ms). 인덱스를 강제한 g3에서는 `Bitmap Index Scan`이 테이블 전체인 100,000행을 돌려주고 재검사가 92,129행을 버려서 93.4ms로 오히려 느렸다.

이유는 `ILIKE '%토너%'`의 패턴에서 trigram을 뽑을 수 없기 때문이다. pg_trgm은 3글자 단위(trigram)로 인덱싱하고, 패턴 앞뒤가 `%`이면 단어 경계 패딩도 붙지 않아 2글자 패턴에서는 인덱스로 후보를 좁힐 키가 하나도 나오지 않는다. 같은 DB에서 `show_trgm('토너')`는 단어 전체를 인덱싱할 때 쓰는 패딩된 trigram 3개를 돌려주지만, 부분 문자열 패턴의 질의에는 쓰이지 않는다. 그래서 인덱스는 전체 행을 후보로 돌려준다.

2글자 검색은 신상품순 첫 페이지(b1)에서는 상태 인덱스를 따라 걷다가 21행을 채워 40.7ms였고, 가격 정렬(b2)에서는 `Parallel Seq Scan`으로 65.6ms였다. 이 데이터 규모에서는 쓸 만하지만 상품 수에 비례해 느려진다.

### 5.3 대안

아래는 측정하지 않은 대안이다. 이번 단계에서는 도입하지 않는다.

- `pg_bigm`: 2글자 단위(bigram) GIN 인덱스라서 `LIKE '%토너%'` 같은 2글자 패턴도 인덱스로 좁힐 수 있다. 확장 설치가 필요하다.
- 카테고리·브랜드 사전 매칭: 검색어가 `토너`, `크림`처럼 카테고리 이름이나 브랜드 이름이면 키워드 검색 대신 `category_id`, `brand_id` 필터로 바꾼다. 이번 측정에서 카테고리 필터(c)와 `idx_product_status_created` 조합은 f1에서 0.24ms였다.

### 5.4 가격 정렬이 식 정렬이라 인덱스를 못 타는 트레이드오프

가격 정렬과 가격대 필터는 `min_price`가 아니라 현재 판매가(세일 구간에 `now()`가 들어 있으면 할인가, 아니면 `min_price`) 식으로 한다. 이 식은 `now()`에 따라 값이 바뀌므로 B-tree 인덱스를 만들 수 없다. 측정 결과도 이를 보여 준다.

- d(가격대 필터 + 가격 낮은순)는 인덱스 전후 13.8ms와 13.3ms로 계획이 같다. `ON_SALE` 약 94,872행을 모두 읽어 식을 계산하고 정렬한다.
- 가격 정렬 커서(f5, 24.3ms 대 25.1ms)와 offset(f6, 28.6ms 대 28.5ms)도 인덱스 전후가 같다.
- 정렬 대상이 크면 `work_mem` 4MB를 넘어 디스크로 넘어간다(e3: 외부 병합 정렬 약 3.7MB, 임시 파일 읽기 801·쓰기 1,288 블록).

대신 세일을 `Sale.apply`와 같은 식으로 항상 정확하게 반영하고, 세일 시작·종료 때 컬럼을 갱신하는 배치가 필요 없다. 규모가 더 커지면 현재 판매가를 컬럼으로 두고 세일 시작·종료 시점에 갱신하는 방식을 검토해야 하며, 그만큼의 쓰기 복잡도가 든다.

### 5.5 커서 vs offset (깊은 페이지)

신상품순 5,000번째 뒤 21행을 읽을 때 품절 서브쿼리를 뺀 쿼리(f2, f4)에서 커서는 0.14ms·버퍼 169, offset은 10.1ms·버퍼 28,977이다. 커서는 `idx_product_status_created`의 시작 위치를 바로 찾아 21행만 읽고, offset은 5,000행을 모두 읽은 뒤 버린다(`Index Scan`이 워커 3개에 걸쳐 5,000행 이상을 읽었다). 품절 서브쿼리를 포함한 실제 쿼리(e1, e2)에서도 38.9ms 대 49.9ms로 커서가 빠르지만 4.1의 고정 비용이 격차를 가린다. 인덱스가 없으면(전) 커서도 전체를 정렬해서 e1이 66.4ms로 e2의 75.1ms와 비슷하다. 커서의 이점은 신상품순 인덱스가 있을 때 나온다.

가격 정렬은 정렬 키가 인덱스에 없어 커서와 offset의 차이가 없다(e3 71.7ms 대 e4 76.3ms, f5 25.1ms 대 f6 28.5ms). 깊은 페이지의 offset 비용은 오프셋 크기에 비례하므로 5,000보다 깊으면 격차가 더 벌어질 것이다. 이는 측정하지 않았다.

## 6. 재현

```
# 1) 로컬 DB와 마이그레이션 (Flyway가 V8까지 적용)
docker compose up -d postgres
./gradlew :backend:bootRun        # 기동 로그를 확인한 뒤 종료한다

# 2) 더미 데이터 (한 번만)
docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/seed-catalog.sql

# 3) 측정
docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/explain-search.sql
```

`seed-catalog.sql`은 상품이 이미 있거나 더미 브랜드가 있으면 오류로 중단한다. 다시 넣으려면 `docker compose down -v`로 볼륨을 지우고 1)부터 다시 한다.
