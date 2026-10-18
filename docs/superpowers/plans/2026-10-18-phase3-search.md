# Phase 3 — 상품 검색·필터·정렬 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 비회원도 쓰는 상품 목록 API를 만든다. 검색어와 필터(하위 카테고리 포함·브랜드·가격대·세일 중), 정렬(신상품·가격), 커서 페이지네이션을 지원한다. 10만 건 더미 데이터로 인덱스 효과를 EXPLAIN으로 증명한다.

**Architecture:**
- 목록은 QueryDSL 한 번의 조회로 만든다. 대표 이미지·브랜드명·품절 여부를 한 쿼리에 담아서 N+1이 없다.
- 상품에 비정규화 컬럼 두 개를 둔다:
  - `min_price`: SKU 최저 정가
  - `search_text`: 브랜드명 + 상품명
- 현재 판매가(세일 반영)는 SQL CASE로 계산하고, 그 식은 `Sale.apply`와 똑같다.
- 검색은 `search_text ILIKE '%검색어%'`에 `pg_trgm` GIN 인덱스를 쓴다.
- 카테고리는 바뀌지 않는 시드이므로 메모리에 캐시한다.

**Tech Stack:** QueryDSL `io.github.openfeign.querydsl` **6.12**(Hibernate 6.6 기준 빌드, Boot 3.5와 맞음. 7.x는 Hibernate 7 기준이라 쓰지 않는다), Flyway V8, PostgreSQL `pg_trgm`(V1에서 설치), Testcontainers, REST Docs

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("검색", Phase 3 항목). 이월 노트의 Phase 3 항목(카테고리 캐시·브랜드 일괄 조회·세일가 CASE·대표 가격·하위 카테고리·세일 CHECK)을 반영한다.

## Global Constraints
- Phase 0~2 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, 상품 코드는 `com.sprint.mission.product`
  - DTO는 `record`, 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티 setter 없음, 에러는 `BusinessException(ErrorCode)`
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 문서·주석·REST Docs 설명은 평서체(~다)로 쓰고, 에러 메시지는 "~니다"체로 쓴다.
- **현재 판매가** = 활성 세일(`startsAt <= now < endsAt`, now는 `Clock`)이 있으면 `(min_price × (100 − rate) / 100) / 10 × 10`, 없으면 `min_price`. 정수 나눗셈이고 `Sale.apply`와 결과가 항상 같다. 가격 정렬·가격대 필터·목록 표시가 모두 이 값을 쓴다.
- `min_price`는 그 상품 SKU들의 `basePrice + additionalPrice` 최솟값이다. 상품 등록·수정(정가 변경)·SKU 추가·SKU 수정 때 같은 트랜잭션에서 다시 계산한다.
- `search_text`는 `브랜드명 + " " + 상품명`이다. 상품 등록·수정, 브랜드 이름 변경 때 같은 트랜잭션에서 갱신한다(브랜드 변경은 bulk UPDATE).
- 목록에는 `ON_SALE` 상품만 나온다.
- 공개 API이고 `GET /api/products`는 이미 permitAll이다(Phase 2).
- 요청 한도:
  - `keyword`: 앞뒤 공백 제거 후 1~50자
  - `brandIds`: 최대 20개
  - `minPrice`·`maxPrice`: 0 이상, `minPrice <= maxPrice`
  - `size`: 기본 20, 1~50
  - `sort`: `NEWEST`(기본) | `PRICE_ASC` | `PRICE_DESC`
  - `onSale`: `true`면 활성 세일 상품만 거른다. 없거나 `false`면 거르지 않는다.
  - `categoryId`: 어느 단계든 받고, 그 아래 말단 전체로 거른다. 없는 id면 404 `CATEGORY_NOT_FOUND`다.
- 검색어의 `%`, `_`, `\`는 이스케이프해서 글자 그대로 찾는다.
- 커서는 불투명한 Base64url 문자열이다. 안에는 정렬 종류, 마지막 항목의 정렬값, id가 들어 있다. 정렬이 다르거나 해석할 수 없는 커서는 400 `INVALID_INPUT`이다. 동률은 id로 끊는다(NEWEST는 `created_at desc, id desc`, 가격은 `현재판매가, id`).

**의도적으로 하지 않는 것:**
- 판매순·평점순·리뷰순 → Phase 6/9
- 관리자 상품 목록 → Phase 11
- 검색 관련도(similarity) 정렬
- pg_bigm. 한글 2글자 검색어의 한계는 성능 문서에 기록만 한다.
- offset 페이지 번호와 전체 개수
- k6 부하 테스트 → Phase 12(이번에는 EXPLAIN만)

## Review Focus
1. 검색어 `100%`나 `a_b` → 와일드카드가 아니라 글자 그대로 찾는다 → Task 3 테스트
2. 세일 중인 상품의 가격 정렬·가격대 필터는 세일가 기준이고, 목록의 `salePrice`가 상세 API의 같은 SKU 최저 세일가와 같다 → Task 3 테스트
3. 상위 카테고리("스킨케어", "메이크업") 필터 → 그 아래 모든 말단의 상품이 나온다 → Task 2·3 테스트
4. 커서로 끝까지 이어 읽기 → 같은 가격·같은 등록 시각이 있어도 중복·누락이 없다 → Task 3 테스트
5. 판매중지·삭제 상품은 목록에 나오지 않고, 브랜드 이름을 바꾸면 새 이름으로 검색된다 → Task 1·3 테스트

---

### Task 1: QueryDSL 설정 + V8 비정규화 컬럼·제약·인덱스

**Files:**
- Modify: `backend/build.gradle`
  - `implementation 'io.github.openfeign.querydsl:querydsl-jpa:6.12'`
  - `annotationProcessor 'io.github.openfeign.querydsl:querydsl-apt:6.12:jpa'`
  - `annotationProcessor 'jakarta.persistence:jakarta.persistence-api'`
- Create: `global/config/QueryDslConfig.java` (`@Bean JPAQueryFactory`)
- Create: `backend/src/main/resources/db/migration/V8__product_search_columns.sql`
- Modify: `product/domain/Product.java`, `product/service/AdminProductService.java`, `product/service/AdminSkuService.java`, `product/service/BrandService.java`, `product/repository/ProductRepository.java`
- Test: `product/service/ProductDenormalizationTest.java` (`IntegrationTest`)

**Interfaces:**
- Produces:
  - V8:
    - `product.min_price bigint`. 기존 행은 `update product p set min_price = (select min(p.base_price + s.additional_price) from sku s where s.product_id = p.id)`로 채우고, 그다음 `not null`로 바꾼다.
    - `product.search_text text`. 기존 행은 `brand.name || ' ' || product.name`으로 채우고 `not null`로 바꾼다.
    - `check ((sale_rate is null and sale_starts_at is null and sale_ends_at is null) or (sale_rate between 1 and 90 and sale_starts_at is not null and sale_ends_at > sale_starts_at))`
    - 인덱스:
      - `create index idx_product_search_text_trgm on product using gin (search_text gin_trgm_ops)`
      - `create index idx_product_status_created on product (status, created_at desc, id desc)`
  - `Product`:
    - `long minPrice`, `String searchText` 필드
    - `void refreshMinPrice(List<Sku> skus)`: SKU가 없으면 `IllegalStateException`
    - `void refreshSearchText(String brandName)`
    - `create`·`update`의 시그니처는 바꾸지 않는다. 서비스가 저장 직후 두 refresh를 부른다.
  - `ProductRepository`: `@Modifying(clearAutomatically = true, flushAutomatically = true) int refreshSearchTextByBrand(Long brandId, String brandName, Instant now)`. JPQL은 `update Product p set p.searchText = concat(:brandName, ' ', p.name), p.updatedAt = :now where p.brandId = :brandId`이다.
  - 호출 지점:
    - `AdminProductService.create`·`update` → `refreshMinPrice(skus)` + `refreshSearchText(brand.getName())`
    - `AdminSkuService` 옵션 추가·수정 → 상품의 `refreshMinPrice(전체 SKU)`
    - `BrandService.update`에서 이름이 바뀌면 → `refreshSearchTextByBrand`
    - 재고 조정은 가격과 상관없으므로 부르지 않는다.
  - `JPAQueryFactory` 빈(`EntityManager` 주입)
- 생성된 `Q*` 클래스는 `build/generated/sources/annotationProcessor`에 있고 커밋하지 않는다.

- [ ] **Step 1: 실패 테스트 작성** (`ProductDenormalizationTest`)
  - `등록하면_최저_정가와_검색_문자열이_채워진다`: base 12,900, SKU 추가금 0/2,000 → `minPrice 12900`, `searchText "라운드랩 1025 독도 토너"`
  - `더_싼_옵션을_추가하면_최저가가_바뀐다`: base 10,000에 SKU 추가금 +3,000 하나만 있음 → min 13,000. 추가금 0인 옵션을 추가 → 10,000
  - `정가를_수정하면_최저가가_다시_계산된다`
  - `브랜드_이름을_바꾸면_검색_문자열도_바뀐다` (Review Focus 5)
  - `세일_컬럼은_일부만_채울_수_없다`: `JdbcTemplate`로 `sale_rate`만 넣으면 `DataIntegrityViolationException`
  - `QueryDSL_빈이_동작한다`: `JPAQueryFactory`로 `QProduct.product` count 조회가 0
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*ProductDenormalizationTest'` → 컴파일 실패(`minPrice`, `QProduct` 없음)
- [ ] **Step 3: 구현.** QueryDSL 설정 → V8 → 엔티티 필드 → 서비스 호출 지점 순서로 구현한다. 6.12가 컴파일이나 쿼리 실행에 실패하면 원인을 보고에 남기고 BLOCKED로 멈춘다(다른 버전으로 바꾸지 않는다).
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과(Phase 2의 183개 포함)
- [ ] **Step 5: 커밋.** `feat: QueryDSL 설정과 상품 검색용 비정규화 컬럼·인덱스 추가`

---

### Task 2: 카테고리 캐시 + 하위 말단 조회

**Files:**
- Modify: `product/service/CategoryService.java`
- Test: `product/service/CategoryServiceTest.java`(추가)

**Interfaces:**
- Produces:
  - `CategoryService`는 처음 쓸 때 전체 카테고리를 한 번만 읽어 불변 맵(id → Category, parentId → 자식 목록)으로 들고 있는다. 카테고리는 Flyway 시드이고 런타임에 바뀌지 않는다(주석으로 남긴다).
  - 기존 `tree()`, `getLeaf`, `path`는 캐시를 쓰도록 바꾸고, 동작은 그대로 둔다.
  - `List<Long> leafIdsUnder(Long categoryId)`: 자기 자신이 말단이면 `[자기 id]`, 아니면 그 아래 모든 말단 id. 없는 id면 `CATEGORY_NOT_FOUND`.

- [ ] **Step 1: 실패 테스트 작성**
  - `말단은_자기_자신만_돌려준다`: "토너" → `[토너 id]`
  - `대분류는_아래_말단을_모두_돌려준다`: "스킨케어" → 5개(토너, 에센스·세럼, 크림, 로션, 미스트·오일)
  - `세_단계_대분류도_말단까지_내려간다`: "메이크업" → 9개(립·베이스·아이 아래 말단)
  - `없는_카테고리는_404`
  - `여러_번_불러도_카테고리를_다시_읽지_않는다`: Hibernate statistics 또는 Spy로 두 번째 호출 이후 쿼리 0회
- [ ] **Step 2: 실패 확인.** focused 테스트 → 컴파일 실패(`leafIdsUnder` 없음)
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `feat: 카테고리 캐시와 하위 말단 조회 추가`

---

### Task 3: 상품 목록 API (검색·필터·정렬·커서)

**Files:**
- Create:
  - `product/repository/ProductSearchRepository.java` (QueryDSL, `@Repository`)
  - `product/dto/ProductSearchCondition.java`, `ProductSort.java`, `ProductSummary.java`, `ProductPage.java`
  - `product/service/ProductSearchService.java`
  - `product/service/ProductCursor.java` (인코딩·디코딩)
- Modify: `product/controller/ProductController.java`, `index.adoc` (`=== 상품 목록`), `backend/src/test/resources/application-test.yml` (`spring.jpa.properties.hibernate.generate_statistics: true`)
- Test: `product/service/ProductCursorTest.java`, `product/controller/ProductSearchApiTest.java`

**Interfaces:**
- Consumes: Task 1의 `min_price`·`search_text`·인덱스·`JPAQueryFactory`, Task 2의 `leafIdsUnder`, `Clock`
- Produces:
  - `enum ProductSort { NEWEST, PRICE_ASC, PRICE_DESC }`
  - `record ProductSearchCondition(String keyword, Long categoryId, List<Long> brandIds, Long minPrice, Long maxPrice, Boolean onSale, ProductSort sort, String cursor, Integer size)`: 컨트롤러가 `@ModelAttribute`로 받고 Global Constraints의 요청 한도로 검증한다. 콤마로 구분된 `brandIds=1,2`도 받는다.
  - `record ProductSummary(Long id, String name, String brandName, String thumbnailUrl, long price, long salePrice, Integer saleRate, boolean soldOut)`
    - `price` = `min_price`, `salePrice` = 현재 판매가
    - `saleRate`는 활성 세일이 없으면 null
    - `thumbnailUrl` = `product_image.sort_order = 0`
    - `soldOut` = 재고 > 0인 SKU가 없음
  - `record ProductPage(List<ProductSummary> items, String nextCursor)`: 마지막 페이지면 `nextCursor = null`. 다음 페이지가 있는지는 `size + 1`개를 조회해서 판단한다.
  - `ProductCursor`: `static String encode(ProductSort, Object sortValue, Long id)`, `static Decoded decode(String, ProductSort expected)`. 해석 실패나 정렬 불일치면 `BusinessException(INVALID_INPUT)`.
  - `ProductSearchRepository.search(ProductSearchCondition, List<Long> leafIds, Instant now, int limit) → List<ProductSummary>`
    - 쿼리 한 번이다: `product` + `brand`(id로 조인) + `product_image`(sort_order 0, left join), `soldOut`은 `not exists` 서브쿼리.
    - 현재 판매가는 QueryDSL `CaseBuilder`나 `Expressions.numberTemplate`으로 Global Constraints 식 그대로 만든다(나눗셈 순서를 바꾸지 않는다).
    - 검색어는 `Expressions.booleanTemplate("{0} ilike {1} escape '\\'", searchText, pattern)`처럼 쓴다. pattern은 이스케이프한 `%keyword%`이다.
  - API: `GET /api/products` (공개) → 200 `ProductPage`

- [ ] **Step 1: 실패 테스트 작성**
  - `ProductCursorTest`
    - `인코딩한_커서를_되돌린다`
    - `정렬이_다른_커서는_INVALID_INPUT`
    - `깨진_커서는_INVALID_INPUT`
  - `ProductSearchApiTest`: 브랜드 2개, 상품 여러 개를 서비스로 준비하고, 등록 시각 동률은 `JdbcTemplate`으로 `created_at`을 맞춰 만든다.
    - `비회원이_신상품순_목록을_본다`: 토큰 없이 200, 최신 등록이 먼저. REST Docs `product/search`(query-parameters + response-fields)
    - `검색어는_상품명과_브랜드명에서_찾는다`: "독도"는 상품명, "라운드"는 브랜드명으로 찾힌다
    - `퍼센트와_밑줄은_글자_그대로_찾는다` (Review Focus 1): 이름이 `"100% 퓨어 오일"`인 상품과 `"1000 오일"`인 상품이 있을 때 `keyword=100%` → 앞의 것만 나온다. `a_b` / `axb`도 같다.
    - `상위_카테고리로_하위_상품을_모두_찾는다` (Review Focus 3): 스킨케어 > 토너, 크림 상품이 "스킨케어" 필터에 모두 나오고, 메이크업 상품은 나오지 않는다
    - `브랜드_여러_개로_거른다`: `brandIds=1,2`
    - `세일_중인_상품만_거른다`: `onSale=true`
    - `가격대는_세일가_기준이다` (Review Focus 2): 정가 20,000·세일 50%(→10,000)인 상품이 `maxPrice=15000`에 나온다
    - `가격_낮은순은_세일가로_정렬한다` (Review Focus 2): 정가 30,000·세일 90%(→3,000)이 정가 5,000보다 먼저 나온다
    - `목록_세일가와_상세_세일가가_같다` (Review Focus 2): 정가 14,900·15% → 목록 `salePrice`와 상세 `min(skus[].salePrice)`가 모두 12,660
    - `커서로_끝까지_읽으면_중복도_누락도_없다` (Review Focus 4): 상품 7개(같은 created_at 3개, 같은 현재 판매가 3개 포함), `size=3`. NEWEST와 PRICE_ASC 각각 끝까지 읽으면 id 집합이 전체와 같고 중복이 없으며, 마지막 응답의 `nextCursor`가 null이다
    - `판매중지_삭제_상품은_목록에_없다` (Review Focus 5)
    - `품절_상품도_목록에_나오고_soldOut이_true다`
    - `목록_조회는_쿼리를_한_번만_실행한다`: Hibernate `Statistics.getPrepareStatementCount()`가 페이지 크기 3과 20에서 같다(카테고리 캐시는 미리 데운다)
    - `잘못된_요청은_400`: `size=0`, `size=51`, `minPrice > maxPrice`, `brandIds` 21개, `sort=POPULAR`, 51자 검색어
- [ ] **Step 2: 실패 확인.** focused 테스트 → 컴파일 실패
- [ ] **Step 3: 구현.** `index.adoc`에 `=== 상품 목록`을 추가한다. 쿼리 파라미터, 커서 사용법, 현재 판매가 정의, 동률 처리를 적는다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과
- [ ] **Step 5: 커밋.** `feat: 상품 목록 검색·필터·정렬과 커서 페이지네이션 추가`

---

### Task 4: 10만 건 더미 데이터 + EXPLAIN 성능 기록

**Files:**
- Create: `scripts/perf/seed-catalog.sql`
- Create: `scripts/perf/explain-search.sql`
- Create: `docs/performance/phase3-search.md`
- Modify: 없음(애플리케이션 코드는 바꾸지 않는다)

**Interfaces:**
- Consumes: V8 스키마, Task 3의 실제 쿼리(QueryDSL이 만든 SQL은 `spring.jpa.show-sql` 또는 로그로 확인해서 EXPLAIN 스크립트에 옮긴다)
- Produces:
  - `seed-catalog.sql`: 로컬 compose DB에 `docker compose exec -T postgres psql -U mall -d shopping_mall < scripts/perf/seed-catalog.sql`로 넣는다. 한 번만 넣는 용도이고 Flyway 마이그레이션이 아니다.
    - 브랜드 300개(이름 `더미브랜드001`…)
    - 상품 100,000개: 말단 카테고리에 고르게 나누고, 상품명은 형용사·성분·제품유형 단어를 조합해 만든다(예: "촉촉 히알루론 토너 12345"). 한글 2글자·3글자 이상 단어가 모두 섞이게 한다.
    - SKU는 상품마다 1~4개(약 25만 개)이고 추가금 0을 하나 이상 포함한다. 상품마다 이미지 1~3개.
    - 20%는 지금 진행 중인 세일이고, 상태는 95% ON_SALE이다.
    - `min_price`·`search_text`는 Global Constraints 정의대로 채운다.
    - `created_at`은 최근 1년에 흩뿌린다.
    - 맨 끝에 `ANALYZE`를 실행한다.
  - `explain-search.sql`: 아래 쿼리마다 `EXPLAIN (ANALYZE, BUFFERS)`를 실행한다.
    - (a) 3글자 이상 검색어("히알루론")
    - (b) 2글자 검색어("토너")
    - (c) 대분류 카테고리 + 신상품순 첫 페이지
    - (d) 가격 낮은순 + 가격대 필터
    - (e) 커서로 깊은 페이지(5,000번째 근처)를 offset 방식과 비교
  - `docs/performance/phase3-search.md`:
    - 환경(로컬 OrbStack, PostgreSQL 17, 데이터 규모)
    - 쿼리별 인덱스 전/후 실행 계획 요약과 실행 시간 표. 인덱스 "전"은 `DROP INDEX`로 측정하고 다시 만든다. 그 절차도 적는다.
    - 결론:
      - trigram GIN이 3글자 이상 검색을 얼마나 줄였는가
      - 2글자 검색은 왜 인덱스가 효과가 없는가(trigram이 만들어지지 않음)
      - 대안(pg_bigm, 카테고리·브랜드 사전 매칭)
      - 가격 정렬이 식 정렬이라 인덱스를 못 타는 트레이드오프
      - 커서 vs offset 깊은 페이지 비교
    - 숫자는 실제로 측정한 값만 쓴다.

- [ ] **Step 1: 더미 데이터 넣기.** `docker compose up -d postgres` → `./gradlew :backend:bootRun`으로 V8까지 적용한 뒤 종료 → seed 실행 → `select count(*) from product`가 100,000이다
- [ ] **Step 2: EXPLAIN 측정.** 인덱스가 있는 상태에서 (a)~(e)를 실행하고 결과를 저장한다 → 인덱스 두 개를 DROP하고 다시 측정한다 → 인덱스를 다시 만든다(V8과 같은 정의)
- [ ] **Step 3: 문서 작성.** 측정값으로 `phase3-search.md`를 쓴다(평서체)
- [ ] **Step 4: 확인.** `./gradlew :backend:test`가 그대로 통과한다(테스트 DB는 Testcontainers라 더미 데이터와 무관하다)
- [ ] **Step 5: 커밋.** `docs: 상품 검색 10만 건 더미 데이터와 EXPLAIN 성능 기록 추가`

---

## Phase 3 완료 기준
- `./gradlew clean :backend:build`가 통과한다.
- REST Docs에 `=== 상품 목록`이 있다.
- 로컬에서 `GET /api/products?keyword=히알루론&categoryId=1&sort=PRICE_ASC`가 10만 건 데이터에서 응답하고, 성능 문서에 인덱스 전후 수치가 있다.
