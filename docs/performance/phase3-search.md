# Phase 3 상품 검색 성능 기록

상품 목록 검색(`GET /api/products`)에 쓰는 인덱스 두 개(V8)가 10만 건 데이터에서 실제로 얼마나 효과가 있는지 `EXPLAIN (ANALYZE, BUFFERS)`로 측정한 기록이다. 이 문서의 숫자는 모두 아래 절차로 직접 측정한 값이며, 원본 출력은 `docs/performance/raw/phase3/`에 있다.

## 1. 환경

| 항목 | 값 |
| --- | --- |
| 실행 환경 | 로컬 macOS(Apple Silicon), OrbStack 위의 `compose.yaml`의 `postgres` 컨테이너 |
| DB | PostgreSQL 17.11 (`postgres:17-alpine`, aarch64) |
| 설정 | 기본값 그대로다. `shared_buffers` 128MB, `work_mem` 4MB, `effective_cache_size` 4GB, `random_page_cost` 4, `max_parallel_workers_per_gather` 2 |
| 스키마 | Flyway V1~V8 |
| 데이터 | 브랜드 300개, 상품 100,000개, SKU 250,154개, 이미지 200,126개 |
| 상품 분포 | 말단 카테고리 30개에 고르게 분배, 진행 중인 세일 19,950개(약 20%), `ON_SALE` 94,872개(약 95%) |
| 시간 분포 | `created_at`은 최근 1년에 걸쳐 id와 함께 증가한다(최대 2시간 지터). `pg_stats` 상관계수는 `id`·`created_at` 모두 0.9989다 |
| 크기 | `product` 31MB, `sku` 20MB, `product_image` 16MB, `idx_product_search_text_trgm` 7.0MB, `idx_product_status_created` 4.0MB |

`search_text`(`브랜드명 + " " + 상품명`)는 형용사·성분·제품유형 단어를 섞어 만들었다. 한글 2글자 단어(토너, 크림, 세럼, 병풀 등)와 3글자 이상 단어(히알루론, 나이아신아마이드, 에센스 등)가 모두 들어 있다. 이 데이터에서 `히알루론`을 포함한 상품은 4,942개(약 4.9%), `토너`를 포함한 상품은 7,871개(약 7.9%), `더미브랜드150`을 포함한 상품은 371개(`ON_SALE` 337개)다.

## 2. 측정 절차

데이터 적재와 측정은 모두 `scripts/perf/`의 스크립트로 한다.

1. 로컬 DB를 띄우고 마이그레이션을 적용한다(`docker compose up -d postgres` 후 애플리케이션을 한 번 실행하면 Flyway가 V8까지 적용한다).
2. 더미 데이터를 한 번만 넣는다. 끝에서 `ANALYZE`를 실행한다. 이후 측정 중에는 `ANALYZE`를 다시 실행하지 않는다.
   ```
   docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/seed-catalog.sql
   ```
3. 인덱스가 있는 상태("후")에서 `scripts/perf/explain-search.sql`을 실행한다. 첫 실행은 캐시를 데우는 용도라 버리고, 이어서 7번 실행한다.
   ```
   docker compose exec -T postgres psql -U mall -d shopping_mall -v ON_ERROR_STOP=1 < scripts/perf/explain-search.sql
   ```
4. 인덱스 두 개를 지우고("전") 같은 방식으로 워밍업 1번, 측정 7번을 실행한다.
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

- "전" 상태에도 V7의 `idx_product_category_id`, `idx_product_brand_id`, 기본 키와 `sku (product_id, name)` 유니크 인덱스는 남아 있다. 지운 것은 V8의 두 개뿐이다.
- 표의 시간은 서버가 보고한 `Execution Time`의 7회 중앙값이고 괄호는 최소~최대다. 쿼리 계획 시간, JDBC 왕복, JSON 직렬화는 포함하지 않는다. 같은 계획을 7번 반복해도 최대값이 중앙값의 몇 배까지 튀는 경우가 있어(예: `g1` 후 최대 19.0ms) 평균 대신 중앙값을 썼다. 계획은 상태별로 7번 모두 같았다.
- 모든 측정에서 읽은 페이지가 `shared_buffers`에 있었다(`Buffers: shared hit`만 나왔다). 디스크 캐시가 비어 있는 상태는 측정하지 않았다.
- `explain-search.sql`의 쿼리는 Task 3의 QueryDSL 쿼리가 만드는 SQL을 그대로 옮기고 파라미터를 리터럴로 바꾼 것이다. 요청 시각은 `now()`로 대신했다. 애플리케이션은 파라미터를 바인딩하므로 JDBC의 서버 측 준비 문장이 제너릭 플랜을 쓰게 되면 실제 계획은 여기서 본 것과 다를 수 있다. 한 페이지는 `size + 1 = 21`행이다.
- 품절 여부는 상품별 최대 재고를 구하는 스칼라 서브쿼리(`coalesce((select max(stock) from sku where product_id = p.id), 0) <= 0`)로 계산한다. 이 형태로 바꾼 이유는 4.1에 있다.

## 3. 결과

쿼리 번호는 `explain-search.sql`의 라벨과 같다. "버퍼"는 `Buffers: shared hit`의 합이다. 시간은 ms다.

### 3.1 목록 쿼리 (실제 쿼리와 같은 모양)

| 쿼리 | 전(인덱스 없음) | 후(인덱스 있음) |
| --- | --- | --- |
| a1. `히알루론` + 신상품순 첫 페이지 | 31.3 (26.1~63.8). `Parallel Seq Scan`(워커당 31,760행 제거) 후 정렬, 버퍼 4,321 | 0.51 (0.41~0.56). `Index Scan using idx_product_status_created`로 21행을 읽고 328행을 걸러냄, 버퍼 316 |
| a2. `히알루론` + 스킨케어(`categoryId=1`) + 가격 낮은순 | 10.5 (8.3~19.8). `Bitmap Heap Scan`(`idx_product_category_id`), 워커당 5,297행 제거, 버퍼 4,172 | 4.8 (4.0~5.3). `Bitmap Index Scan on idx_product_search_text_trgm`(4,942행)와 `idx_product_category_id`(16,669행)를 합쳐 `Bitmap Heap Scan`, 버퍼 990 |
| a3. 드문 검색어 `더미브랜드150`(371건) + 신상품순 | 80.1 (72.0~124.7). `Seq Scan on product`(99,663행 제거), 버퍼 5,391 | 3.8 (3.7~4.1). `Bitmap Index Scan on idx_product_search_text_trgm`(371행) 후 `Bitmap Heap Scan`(337행, 재검사 12행 제거), 버퍼 1,913 |
| a4. 같은 검색어 + 가격 낮은순 | 76.6 (72.1~87.7). `Seq Scan on product`, 버퍼 5,391 | 3.3 (2.8~3.6). a3과 같은 trigram `Bitmap Index Scan`, 버퍼 1,910 |
| b1. `토너` + 신상품순 첫 페이지 | 27.4 (23.9~41.6). `Parallel Seq Scan`(워커당 30,840행 제거), 버퍼 4,321 | 0.28 (0.24~0.45). `Index Scan using idx_product_status_created`, 158행 제거 후 21행, 버퍼 260 |
| b2. `토너` + 가격 낮은순 첫 페이지 | 27.0 (24.3~32.9). `Parallel Seq Scan`, 버퍼 4,307 | 25.7 (24.2~38.2). `Parallel Seq Scan`(trigram 인덱스를 쓰지 않음), 버퍼 4,307 |
| c. 스킨케어 말단(8~12) + 신상품순 첫 페이지 | 9.6 (7.1~22.9). `Bitmap Heap Scan`(`idx_product_category_id`), 버퍼 4,186 | 0.16 (0.15~0.22). `Index Scan using idx_product_status_created`, 93행 제거 후 21행, 버퍼 244 |
| d. 가격 낮은순 + 현재 판매가 10,000~30,000원 | 17.6 (12.9~22.5). `Parallel Seq Scan`(워커당 9,636행 통과) 후 정렬, 버퍼 4,307 | 12.9 (12.8~44.9). 같은 계획, 버퍼 4,307 |
| e1. 신상품순 5,000번째 뒤 21행: 커서 | 15.2 (14.3~51.9). `Parallel Seq Scan` 후 정렬, 버퍼 4,251 | 0.15 (0.14~2.7). `Index Scan using idx_product_status_created`로 21행만 읽음, 버퍼 221 |
| e2. 같은 위치: offset 5000 | 33.0 (26.6~50.0). `Parallel Seq Scan` 후 정렬, 버퍼 44,880 | 12.6 (12.1~39.1). `Parallel Index Scan using idx_product_status_created`(워커당 약 1,900행), 5,021행의 품절 서브쿼리까지 계산, 버퍼 46,604 |
| e3. 가격 낮은순 5,000번째 뒤 21행: 커서 | 28.5 (24.6~46.3). `Parallel Seq Scan` 후 외부 병합 정렬(디스크 3.7MB), 버퍼 4,307 | 25.6 (24.3~42.4). 같은 계획, 버퍼 4,307 |
| e4. 같은 위치: offset 5000 | 52.7 (40.6~141.2). `Parallel Seq Scan` 후 정렬(디스크 3.2MB), 버퍼 44,875 | 42.4 (39.4~73.0). 같은 계획, 버퍼 44,875 |

### 3.2 검색 조건만 떼어 본 count 쿼리

정렬·조인 없이 `count(*)`만 실행해 인덱스가 검색 조건에 주는 효과를 본다.

| 쿼리 | 전 | 후 |
| --- | --- | --- |
| g1. `히알루론`(3글자 이상), `ON_SALE` | 60.4 (56.9~97.1). `Seq Scan on product`(95,279행 제거), 버퍼 3,955 | 5.3 (5.2~19.0). `Bitmap Index Scan on idx_product_search_text_trgm`(4,942행) 후 `Bitmap Heap Scan`, 버퍼 2,857 |
| g2. `토너`(2글자), `ON_SALE` | 59.1 (55.8~78.6). `Seq Scan on product`(92,519행 제거), 버퍼 3,955 | 56.2 (54.6~63.8). `Seq Scan on product`(플래너가 trigram 인덱스를 고르지 않음), 버퍼 3,955 |
| g3. `토너`(2글자), `enable_seqscan = off`로 인덱스를 강제(status 조건 없음) | 130.2 (105.9~151.0). `Seq Scan`. 아래 설명대로 비교에 쓰지 않는다 | 90.6 (88.7~121.6). `Bitmap Index Scan on idx_product_search_text_trgm`이 100,000행(테이블 전체)을 돌려주고 `Bitmap Heap Scan`이 92,129행을 재검사로 제거, 버퍼 4,943 |
| g4. `더미브랜드150`(371건), `ON_SALE` | 63.7 (61.8~66.9). `Seq Scan on product`(99,663행 제거), 버퍼 3,955 | 2.0 (1.9~3.6). `Bitmap Index Scan on idx_product_search_text_trgm`(371행), 버퍼 474 |

g3의 "전" 값은 비교에 쓸 수 없다. 인덱스가 없는 상태에서 `enable_seqscan = off`를 걸면 시퀀셜 스캔 비용이 10^10으로 부풀려져 JIT 컴파일이 켜지고, 그 비용(원본의 `JIT` 항목, 약 42~64 ms(7회))이 실행 시간에 들어간다. 7번 모두 JIT가 켜졌다. 2글자 검색의 "전" 기준은 g2(59.1ms)다.

## 4. 관찰

### 4.1 품절 여부 서브쿼리는 `not exists`가 아니라 최대 재고 스칼라 서브쿼리로 계산한다

처음 구현은 `case when not exists (select 1 from sku where product_id = p.id and stock > 0)`였다. 이 형태는 플래너가 바깥 행 수를 많게 추정하면 해시 서브플랜이 되어, `sku`에서 재고가 있는 225,086행을 `Seq Scan`으로 읽고 해시 테이블을 먼저 만든다. 이전 측정(`created_at`이 id와 무관하던 첫 데이터셋)에서 이 서브플랜만 `Seq Scan on sku`로 약 16ms였고, 해시 구축을 포함해 쿼리당 약 40ms가 페이지 크기와 무관하게 들었다. 같은 쿼리에서 이 컬럼만 빼면 신상품순 커서 조회가 0.14ms였다(이전 측정, 이 문서의 이전 버전).

`coalesce((select max(stock) from sku where product_id = p.id), 0) <= 0`로 바꾸면 출력되는 행마다 `sku_product_id_name_key` 인덱스를 조회하는 서브플랜이 된다. 이 문서의 모든 측정은 새 형태로 했다. 모든 "후" 계획에서 서브플랜은 `Index Scan using sku_product_id_name_key`(페이지 쿼리는 21 loops)다. 신상품순 커서 조회가 0.15ms다(e1).

offset 쿼리(e2, e4)는 건너뛸 5,000행에도 select 목록의 서브플랜을 계산하므로 서브플랜이 5,021 loops로 돌고 버퍼가 4만 이상이 된다. 이것이 커서와 offset의 버퍼 차이를 키우는 이유 중 하나다(5.5).

### 4.2 `ILIKE '%...%'`의 행 수 추정이 `ANALYZE`마다 흔들려 계획이 바뀐다

패턴 매칭의 선택도는 통계 표본으로 추정하므로 `ANALYZE`를 다시 할 때마다 추정이 달라진다. 측정을 끝낸 뒤 `ANALYZE product`를 5번 연달아 실행하고 `EXPLAIN`(실행 없이 계획만)을 비교했다(`raw/phase3/reanalyze.txt`).

| `ANALYZE` | a1 추정 행수 | a2 계획 | a3 추정 행수와 계획 |
| --- | --- | --- | --- |
| #1 | 2,871 | `Bitmap Heap Scan` | 957, `idx_product_status_created` 순회 |
| #2 | 2,868 | `Bitmap Heap Scan` | 9, `Bitmap Heap Scan` |
| #3 | 1,915 | `Bitmap Heap Scan` | 9, `Bitmap Heap Scan` |
| #4 | 4,801 | `Parallel Seq Scan` | 10, `Bitmap Heap Scan` |
| #5 | 2,871 | `Bitmap Heap Scan` | 957, `idx_product_status_created` 순회 |

`히알루론`의 실제 일치는 4,942행이다. 이처럼 추정이 실제와 최대 약 2.6배 차이 나고, 그에 따라 a2는 `Bitmap Heap Scan`과 `Parallel Seq Scan` 사이에서, a3는 trigram 비트맵과 상태 인덱스 순회 사이에서 계획이 바뀐다. 3절의 값은 `seed-catalog.sql` 끝의 `ANALYZE` 직후 통계로 측정했고, 상태별 7번 모두 같은 계획이었다. 다른 `ANALYZE` 표본에서는 같은 쿼리가 다른 계획으로 실행될 수 있다.

## 5. 결론

### 5.1 trigram GIN은 3글자 이상 검색을 얼마나 줄였는가

`idx_product_search_text_trgm`을 실제로 쓰는 계획(`Bitmap Index Scan on idx_product_search_text_trgm`)에서의 값이다.

- `히알루론` count(g1): 60.4ms → 5.3ms, 약 11배. 일치율 4.9%이고 버퍼는 3,955에서 2,857로 줄었다.
- 일치 건수가 적은 `더미브랜드150` count(g4): 63.7ms → 2.0ms, 약 32배. 목록 쿼리로는 신상품순(a3) 80.1ms → 3.8ms, 가격 낮은순(a4) 76.6ms → 3.3ms다.
- `히알루론` + 카테고리 + 가격 낮은순(a2): 10.5ms → 4.8ms. trigram 비트맵과 카테고리 비트맵을 합친다. "전"은 이미 카테고리 인덱스를 쓰므로 개선 폭이 작다.

일치 건수가 적을수록 효과가 크다. 반면 신상품순 첫 페이지(a1, b1, c)는 `idx_product_status_created`를 따라 걸으며 조건에 맞는 21행을 채우는 계획이고 trigram은 쓰이지 않는다. a1의 0.51ms는 상태 인덱스 덕분이다(31.3ms → 0.51ms).

### 5.2 2글자 검색은 왜 인덱스가 효과가 없는가

`토너` 검색(g2)은 인덱스가 있어도 `Seq Scan`이고 시간이 같다(59.1ms 대 56.2ms). 인덱스를 강제한 g3에서는 `Bitmap Index Scan`이 테이블 전체인 100,000행을 돌려주고 재검사가 92,129행을 버려서 90.6ms로, 인덱스가 없을 때의 g2(59.1ms)와 g2 후(56.2ms)보다 느리다. 가격 정렬 목록(b2)도 인덱스 전후 모두 `Parallel Seq Scan`이다(27.0ms 대 25.7ms).

이유는 `ILIKE '%토너%'` 패턴에서 trigram을 뽑을 수 없기 때문이다. pg_trgm은 3글자 단위(trigram)로 인덱싱한다. 같은 DB에서 `show_trgm('토너')`는 단어 전체를 인덱싱할 때 쓰는 패딩된 trigram 3개를 돌려주지만, 앞뒤가 `%`인 부분 문자열 패턴은 패딩이 붙지 않아 2글자에서는 인덱스로 후보를 좁힐 키가 나오지 않는다. 그래서 인덱스는 전체 행을 후보로 돌려준다(g3의 `rows=100000`).

신상품순 첫 페이지(b1)는 `토너`가 전체의 약 7.9%라서 상태 인덱스를 따라 걷다 158행만 걸러내고 21행을 채워 0.28ms였다. 일치율이 낮은 2글자 검색어는 더 많이 걸어야 하는데, 이는 측정하지 않았다.

### 5.3 대안

아래는 측정하지 않은 대안이다. 이번 단계에서는 도입하지 않는다.

- `pg_bigm`: 2글자 단위(bigram) GIN 인덱스라서 `LIKE '%토너%'` 같은 2글자 패턴도 인덱스로 좁힐 수 있다. 확장 설치가 필요하다.
- 카테고리·브랜드 사전 매칭: 검색어가 `토너`, `크림`처럼 카테고리 이름이나 브랜드 이름이면 키워드 검색 대신 `category_id`, `brand_id` 필터로 바꾼다. 카테고리 필터 + 신상품순(c)은 상태 인덱스로 0.16ms였다.

### 5.4 가격 정렬이 식 정렬이라 인덱스를 못 타는 트레이드오프

가격 정렬과 가격대 필터는 `min_price`가 아니라 현재 판매가(세일 구간에 `now()`가 들어 있으면 할인가, 아니면 `min_price`) 식으로 한다. 이 식은 `now()`에 따라 값이 바뀌므로 B-tree 인덱스를 만들 수 없다. 측정 결과도 이를 보여 준다.

- d(가격대 필터 + 가격 낮은순)는 인덱스 전후 계획이 같다(`Parallel Seq Scan` 후 정렬, 버퍼 4,307). 시간 차이(17.6ms 대 12.9ms)는 "전" 반복 범위(12.9~22.5ms) 안이다. `ON_SALE` 약 28,900행(워커당 9,636행)이 가격 범위에 들어와 정렬된다.
- 가격 정렬 커서(e3)도 전후 계획이 같고(28.5ms 대 25.6ms), 정렬 대상이 크면 `work_mem` 4MB를 넘어 디스크로 넘어간다(외부 병합 정렬 약 3.7MB, 임시 파일 읽기 801·쓰기 1,287 블록).

대신 세일을 `Sale.apply`와 같은 식으로 항상 정확하게 반영하고, 세일 시작·종료 때 컬럼을 갱신하는 배치가 필요 없다. 규모가 더 커지면 현재 판매가를 컬럼으로 두고 세일 시작·종료 시점에 갱신하는 방식을 검토해야 하며, 그만큼의 쓰기 복잡도가 든다.

### 5.5 커서 vs offset (깊은 페이지)

신상품순 5,000번째 뒤 21행을 읽을 때 인덱스가 있으면 커서(e1)는 0.15ms·버퍼 221, offset(e2)은 12.6ms·버퍼 46,604다. 커서는 `idx_product_status_created`의 시작 위치를 바로 찾아 21행만 읽고, offset은 5,021행을 읽고 5,000행을 버린다. 그 5,021행 각각에서 품절 서브쿼리도 계산하므로(4.1) 버퍼가 크게 늘어난다. 인덱스가 없으면(전) 둘 다 `ON_SALE` 전체를 정렬해서 15.2ms 대 33.0ms로 차이가 줄어든다. 둘 다 전체 스캔과 정렬이 비용의 대부분이기 때문이다.

가격 정렬은 정렬 키가 인덱스에 없어 둘 다 전체를 정렬한다. 커서(e3 25.6ms, 버퍼 4,307)가 offset(e4 42.4ms, 버퍼 44,875)보다 빠른 차이는 정렬 이후의 건너뛰기와 서브쿼리 평가 비용에서 온다. 5,000번째보다 깊은 위치는 측정하지 않았다.

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

`seed-catalog.sql`은 상품이 이미 있거나 더미 브랜드가 있으면 오류로 중단한다. 다시 넣으려면 `docker compose down -v`로 로컬 볼륨 전체를 지우고 1)부터 다시 하는 것이 가장 안전하다. 실제 브랜드나 이후 단계의 장바구니·찜·주문 데이터를 지우지 않으려면 `TRUNCATE ... CASCADE`를 쓰지 말고, `brand.name like '더미브랜드%'`인 더미 데이터만 지운다. 이 브랜드의 상품에 딸린 `sku`와 `product_image` 행을 먼저 지우고, 그다음 상품, 마지막으로 브랜드를 지운다.

## 7. 원본 출력

`docs/performance/raw/phase3/`에 `EXPLAIN (ANALYZE, BUFFERS)` 원본을 저장했다.

- `after-run1.txt` ~ `after-run7.txt`: 인덱스가 있는 상태의 측정 7회
- `before-run1.txt` ~ `before-run7.txt`: 인덱스 두 개를 지운 상태의 측정 7회
- `drop.txt`, `recreate.txt`: 인덱스 삭제와 재생성 결과(재생성 뒤 `\d product`로 V8 정의 확인)
- `reanalyze.txt`: 4.2의 `ANALYZE` 반복 비교(계획만, 실행 없음)
