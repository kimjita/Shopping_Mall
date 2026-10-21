# Phase 9b — 랭킹·판매순·피부타입 추천·배너·메인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- 최근 7일 판매량을 매시간 상품 컬럼에 집계해 랭킹 API(전체·카테고리별 TOP 20)와 검색의 "판매순" 정렬에 쓴다.
- 피부타입별 추천 상품을 매일 집계 테이블에 저장해 공개 API로 내놓는다.
- 관리자가 기간과 순서를 정해 등록하는 배너와 공개 배너 목록을 만든다.
- 메인 화면은 섹션별 API 조합으로 구성하고, 그 조합을 문서로 정리한다.

**Architecture:**
- **판매량:**
  - `product.sales_7d`(읽기 전용 매핑)를 둔다.
  - 매시간 배치가 계산한다. 계산 대상은 결제 승인 시각(`payment.approved_at`)이 `(now − 168시간, now]`이고 주문 상태가 PAID~CONFIRMED인 주문상품의 수량 합이다.
  - 값이 바뀐 상품만 id 오름차순으로 1,000개씩 묶어 갱신한다. 묶음마다 트랜잭션을 따로 쓰고, 데드락이 나면 그 묶음을 다시 시도한다(이월 노트 Phase 3).
- **추천:**
  - 매일 배치가 `skin_recommendation`을 한 트랜잭션에서 지우고 다시 채운다(SQL 한 문장의 window 함수).
  - 조회는 그 테이블과 상품 카드만 읽는다.
- **카드:** 랭킹과 추천은 상품 id 목록을 정한 뒤 기존 `ProductSearchRepository.findCards`로 카드를 만든다(가격·세일·품절·평점 계산을 다시 짜지 않는다).
- **메인:** 새 API는 배너·랭킹·추천뿐이다. 오늘의 특가는 검색 `onlyOnSale=true`, 신상품은 검색 `sort=NEWEST`를 쓴다.

**Tech Stack:** Phase 9a와 같다. Flyway V19를 쓴다. 새 의존성은 없다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("추천", "랭킹", "메인", "관리자"의 배너, "검색"의 판매순 정렬, "스케줄러"). 이월 노트의 Phase 3·8·9a 항목을 반영한다:
- 정렬용 비정규화 컬럼 + `(status, col desc, id desc)` 인덱스
- 매시간 bulk UPDATE는 id 순서로 나누고 재시도
- 판매량은 PAID~CONFIRMED만 센다
- 추천은 `review.skin_type` 스냅샷 기준, 리뷰 5건 이상

## Global Constraints
- Phase 0~9a 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, DTO는 `record`
  - 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속, setter 없음, id 참조
  - 에러는 `BusinessException(ErrorCode)`
  - 문서·주석은 평서체, 에러·검증 메시지는 "~니다"체
  - 벌크 쿼리는 `@Modifying(flushAutomatically = true)`만 쓴다
  - FK 행 잠금은 `FOR NO KEY UPDATE`
  - 시각은 `Clock` 빈
  - 스케줄러는 `SchedulingConfig`(`app.scheduling.enabled`)를 따르고, 테스트는 배치 메서드를 직접 호출한다
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 새 코드 위치:
  - `com.sprint.mission.ranking`
  - `com.sprint.mission.recommendation`
  - `com.sprint.mission.banner`
- 공개 GET(permitAll)에 `/api/rankings`, `/api/recommendations`, `/api/banners`를 더한다.
- **판매량 배치:**
  - `@Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")`이고, `now` 하나를 쓴다.
  - 판매량 식은 다음과 같다. 회원·주문·상품 상태와 상관없이 모든 상품의 판매량을 계산한다.

    ```sql
    select oi.product_id, sum(oi.quantity) from order_item oi
    join orders o on o.id = oi.order_id join payment p on p.order_id = o.id
    where o.status in ('PAID','PREPARING','SHIPPING','DELIVERED','CONFIRMED')
      and p.approved_at > :from and p.approved_at <= :now
    group by oi.product_id
    ```

    - `:from = now − 168시간`이다.
    - 대상은 (새 판매량이 있는 상품) ∪ (지금 `sales_7d > 0`인 상품)이다. 결과에 없는 상품은 0이다.
  - 갱신은 `update product set sales_7d = ? where id = ? and sales_7d <> ?`를 batch로 실행한다. `updated_at`은 건드리지 않는다.
  - 1,000개 묶음마다 `TransactionTemplate`을 쓴다. `PessimisticLockingFailureException`(데드락·잠금 실패)이면 그 묶음을 최대 3번 다시 시도하고, 그래도 실패하면 예외를 던진다.
  - 반환값은 바뀐 상품 수다.
- **랭킹 API:**
  - `GET /api/rankings?categoryId=`
  - ON_SALE이고 `sales_7d > 0`인 상품을 `sales_7d desc, id desc`로 최대 20개 고른다.
  - `categoryId`가 있으면 그 아래 말단 카테고리 전부다(`CategoryService.leafIdsUnder`). 없는 카테고리는 404 `CATEGORY_NOT_FOUND`(기존 동작)다.
- **판매순 정렬:** `ProductSort.SALES` = `sales_7d desc, id desc`이고, 커서 정렬값은 0 이상 정수다.
- **추천 배치:**
  - `@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")`다.
  - 한 트랜잭션에서 `delete from skin_recommendation` 뒤 다음 insert를 한다.

    ```sql
    insert into skin_recommendation (skin_type, rank, product_id, review_count, rating_avg, created_at)
    select skin_type, rn, product_id, cnt, avg_rating, :now from (
      select r.skin_type, r.product_id, count(*) cnt, round(avg(r.rating)::numeric, 2) avg_rating,
             row_number() over (partition by r.skin_type
                                order by avg(r.rating) desc, count(*) desc, r.product_id desc) rn
      from review r join product p on p.id = r.product_id
      where r.skin_type is not null and p.status = 'ON_SALE'
      group by r.skin_type, r.product_id
      having count(*) >= 5) t
    where rn <= 20
    ```

- **추천 API:**
  - `GET /api/recommendations?skinType=`(필수, `SkinType` 이름이 아니면 400)
  - rank 오름차순으로 돌려주고, 조회 시점에 ON_SALE이 아닌 상품은 뺀다.
- **배너:**
  - 필드:
    - `title` 1~50자(strip)
    - `imageUrl`은 `^https?://.+`, 500자 이하
    - `linkUrl`은 `^(https?://|/).+`, 500자 이하
    - `startsAt` < `endsAt`(아니면 400 `INVALID_INPUT`), 두 시각의 연도는 2000~9999(아니면 400)
    - `sortOrder` 0~1,000
  - 공개 목록은 `startsAt <= now < endsAt`인 배너를 `sortOrder asc, id asc`로 돌려준다.
  - 관리자 API는 등록·수정(전체 교체)·삭제·전체 목록(`sortOrder asc, id asc`)이다.
- **새 ErrorCode:** `BANNER_NOT_FOUND(404, "배너를 찾을 수 없습니다.")`

**의도적으로 하지 않는 것:**
- 로그인 선택 인증(`@CurrentMemberId` nullable). 추천은 `skinType` 파라미터로만 받는다.
- 한 번에 묶은 `/api/main` 응답, 랭킹 순위 변동(▲▼) 표시, 랭킹 이력
- 배너 이미지 업로드(S3) → Phase 11, 배너 클릭 통계
- 쿠폰 DB CHECK 보강(Phase 5 이월). 이번 마이그레이션 주제와 달라 그대로 이월한다.

## Review Focus
1. 7일 경계: 결제 승인이 정확히 `now − 168시간`이면 빠지고, 1ms 뒤면 들어간다. 집계 뒤 취소된 주문은 다음 실행에서 빠진다. 판매가 끊긴 상품은 0으로 내려간다 → Task 1 테스트
2. 판매량 배치가 도는 동안 관리자 브랜드 이름 변경(같은 상품들의 `search_text` bulk UPDATE)이 겹친다 → 데드락이 나도 묶음 재시도로 끝나고 값이 맞다. 재시도를 다 써도 실패하면 예외를 던지고 이미 커밋된 묶음은 남는다 → Task 1 테스트(재시도는 실패를 주입해서 확인)
3. 판매순 커서로 페이지를 넘기는데 판매량이 같은 상품이 페이지 경계에 걸린다 → 빠지거나 겹치는 상품이 없다. 위조 커서는 400이다 → Task 2 테스트
4. 추천 기준 경계: 같은 피부타입 리뷰 4건인 상품은 빠지고 5건이면 들어간다. 다른 피부타입 리뷰는 세지 않는다. 판매 중지 상품은 배치와 조회 양쪽에서 빠진다. 배치를 다시 돌려도 결과가 같다 → Task 3 테스트
5. 배너 기간 경계: `startsAt` 정각에는 보이고, `endsAt` 정각에는 안 보인다. 시작이 종료보다 늦거나 같으면 400이고, 연도 +300000이면 500이 아니라 400이다 → Task 4 테스트

---

### Task 1: 판매량 컬럼과 매시간 집계 배치

**Files:**
- Create:
  - `V19__ranking_recommendation_banner.sql`
  - `ranking/repository/SalesBatchRepository.java`(JdbcTemplate)
  - `ranking/service/SalesAggregationService.java`, `ranking/service/SalesAggregationScheduler.java`
- Modify: `product/domain/Product.java`(`sales7d` 읽기 전용, `getSales7d()`)
- Test: `ranking/service/SalesAggregationTest.java`(`IntegrationTest`)

**Interfaces:**
- Produces:
  - **V19:**
    - `alter table product add column sales_7d int not null default 0 check (sales_7d >= 0)`
    - `create index idx_product_status_sales on product (status, sales_7d desc, id desc)`
    - `create index idx_payment_approved_at on payment (approved_at)`
    - `skin_recommendation(skin_type varchar(20) not null, rank int not null check (rank between 1 and 20), product_id bigint not null → product, review_count int not null, rating_avg numeric(3,2) not null, created_at timestamptz not null, primary key (skin_type, rank))`
    - `banner(id bigserial, title varchar(50) not null, image_url varchar(500) not null, link_url varchar(500) not null, starts_at timestamptz not null, ends_at timestamptz not null, sort_order int not null check (sort_order between 0 and 1000), created_at, updated_at)`, CHECK `ends_at > starts_at`, 인덱스 `(sort_order, id)`
  - `SalesBatchRepository`:
    - `Map<Long, Integer> sumPaidQuantities(Instant from, Instant now)`: 위 SQL
    - `List<Long> findProductIdsWithSales()`: `sales_7d > 0`
    - `int updateSales(Map<Long, Integer> sales)`: 위 batch UPDATE를 id 오름차순으로 실행하고, 실제로 바뀐 행 수를 돌려준다(음수 코드는 0으로 센다).
  - `SalesAggregationService.aggregate(Instant now) → int`: 1,000개 묶음, 묶음마다 트랜잭션, 데드락이면 최대 3번 재시도
  - `SalesAggregationScheduler.run()`: `@Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")`이고, `aggregate(clock.instant())`를 부른다.

- [ ] **Step 1: 실패 테스트 작성** (`SalesAggregationTest`)
  - `결제된_주문상품_수량을_7일치_합산한다`: PAID 2개 + CONFIRMED 3개 → 5
  - `결제대기와_취소_주문은_세지_않는다`
  - `승인_시각이_정확히_168시간_전이면_빠지고_1ms_뒤면_들어간다` (Review Focus 1)
  - `판매가_끊긴_상품은_0으로_내려간다` (Review Focus 1)
  - `집계_뒤_취소되면_다음_실행에서_빠진다` (Review Focus 1)
  - `바뀐_상품만_갱신하고_다시_돌리면_0이다`
  - `1000개를_넘어도_모두_갱신한다`: 상품 2,500개 SQL 일괄 생성
  - `묶음이_데드락으로_실패하면_다시_시도해서_끝낸다` (Review Focus 2): `@MockitoSpyBean SalesBatchRepository`가 첫 호출에 `PessimisticLockingFailureException`을 던진다.
  - `재시도를_다_쓰면_예외를_던지고_앞_묶음은_남는다` (Review Focus 2)
  - `관리자_상품_수정이_판매량을_덮어쓰지_않는다`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 최근 7일 판매량 집계 배치 추가`

---

### Task 2: 판매순 정렬과 랭킹 API

**Files:**
- Create: `ranking/controller/RankingController.java`, `ranking/service/RankingService.java`, `ranking/dto/RankingItem.java`, `RankingResponse.java`
- Modify:
  - `product/dto/ProductSort.java`(`SALES`), `product/service/ProductCursor.java`
  - `product/repository/ProductSearchRepository.java`: select에 `sales7d`를 넣고, `Row`에 `int sales7d`를 더하고, orderBy·afterCursor를 넓힌다.
  - `product/service/ProductSearchService.java`(커서)
  - `global/security/SecurityConfig.java`(`/api/rankings`)
  - `index.adoc`(검색 정렬 표에 SALES, `== 랭킹`)
- Test: `ranking/controller/RankingApiTest.java`, `product/controller/ProductSearchApiTest.java`(추가), `product/service/ProductCursorTest.java`(추가)

**Interfaces:**
- Consumes: Task 1 `sales_7d`, 인덱스
- Produces:
  - `ProductSort.SALES`
    - 키셋은 `sales_7d < v or (sales_7d = v and id < cid)`다.
    - 커서 값은 `Row.sales7d()`에서 읽는다. 판매량은 카드(`ProductSummary`)에 넣지 않는다.
  - `RankingService.top(Long categoryId) → RankingResponse`
    - 순서: id 목록 쿼리(ON_SALE, `sales_7d > 0`, 카테고리, `sales_7d desc, id desc`, `limit 20`) → `findCards(ids, now)` → 순서 유지
  - `record RankingItem(int rank, ProductSummary product)`, `record RankingResponse(List<RankingItem> items)`
  - API: `GET /api/rankings?categoryId=` → 200 (permitAll)

- [ ] **Step 1: 실패 테스트 작성**
  - `RankingApiTest`
    - `판매량_순으로_20개까지_순위를_매긴다`: 상품 22개 → 20개, rank 1~20, 동률이면 id 내림차순. REST Docs `ranking/list`
    - `판매가_없거나_판매_중지된_상품은_빠진다`
    - `카테고리를_주면_하위_카테고리까지_포함한다`
    - `없는_카테고리는_404`
    - `토큰_없이도_읽는다`
  - `ProductSearchApiTest`
    - `판매순으로_정렬한다`
    - `판매순_커서가_동률_경계를_넘어도_빠지거나_겹치지_않는다` (Review Focus 3)
    - `위조된_판매순_커서는_400`
  - `ProductCursorTest`: SALES 왕복, 음수·숫자 아님 거부
- [ ] **Step 2: 실패 확인 → Step 3: 구현.** 기존 정렬 테스트가 모두 그대로 통과해야 한다.
- [ ] **Step 4: 통과 확인 → Step 5: 커밋.** `feat: 판매순 정렬과 랭킹 API 추가`

---

### Task 3: 피부타입 추천 배치와 API

**Files:**
- Create:
  - `recommendation/repository/RecommendationRepository.java`(JdbcTemplate)
  - `recommendation/service/RecommendationService.java`, `RecommendationScheduler.java`
  - `recommendation/controller/RecommendationController.java`
  - `recommendation/dto/RecommendationItem.java`, `RecommendationResponse.java`
- Modify: `global/security/SecurityConfig.java`(`/api/recommendations`), `index.adoc`(`== 추천`)
- Test: `recommendation/service/RecommendationBatchTest.java`, `recommendation/controller/RecommendationApiTest.java`

**Interfaces:**
- Consumes: Task 1 `skin_recommendation`, Phase 9a `review`(skin_type 스냅샷), `ProductSearchRepository.findCards`
- Produces:
  - `RecommendationRepository`:
    - `int rebuild(Instant now)`: delete + 위 insert, 넣은 행 수
    - `List<Row> findBySkinType(SkinType)`: rank 오름차순, `Row(int rank, Long productId, int reviewCount, BigDecimal ratingAvg)`
  - `RecommendationService`:
    - `int rebuild()`(`@Transactional`)
    - `RecommendationResponse get(SkinType skinType)`(읽기 전용, 카드 상태가 ON_SALE인 것만)
  - `RecommendationScheduler.run()`: `@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")`
  - `record RecommendationItem(int rank, ProductSummary product, int sameSkinReviewCount, BigDecimal sameSkinRatingAvg)`, `record RecommendationResponse(SkinType skinType, List<RecommendationItem> items)`
  - API: `GET /api/recommendations?skinType=` → 200 (permitAll)

- [ ] **Step 1: 실패 테스트 작성**
  - `RecommendationBatchTest`
    - `같은_피부타입_리뷰가_5건_이상인_상품만_평균_순으로_고른다` (Review Focus 4): 4건 제외, 5건 포함, 다른 타입 리뷰는 안 셈
    - `동률이면_리뷰_수_그다음_상품_id_내림차순이다`
    - `타입마다_20개까지다`
    - `판매_중지_상품은_빠지고_다시_돌려도_결과가_같다` (Review Focus 4)
    - `피부타입이_없는_리뷰는_세지_않는다`
  - `RecommendationApiTest`
    - `피부타입별_추천을_순위대로_읽는다`: REST Docs `recommendation/list`
    - `배치_뒤_판매_중지된_상품은_조회에서_빠진다` (Review Focus 4)
    - `skinType이_없거나_틀리면_400`, `토큰_없이도_읽는다`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인**
- [ ] **Step 5: 커밋.** `feat: 피부타입별 추천 배치와 조회 API 추가`

---

### Task 4: 배너(관리자·공개)와 메인 구성 문서

**Files:**
- Create:
  - `banner/domain/Banner.java`, `banner/repository/BannerRepository.java`
  - `banner/service/BannerService.java`
  - `banner/controller/AdminBannerController.java`, `BannerController.java`
  - `banner/dto/BannerRequest.java`, `BannerResponse.java`
- Modify:
  - `ErrorCode.java`(`BANNER_NOT_FOUND`)
  - `global/security/SecurityConfig.java`(GET `/api/banners`)
  - `index.adoc`: `== 관리자 - 배너`, `== 배너`, 그리고 `== 메인`
- Test: `banner/controller/AdminBannerApiTest.java`, `banner/controller/BannerApiTest.java`

**Interfaces:**
- Consumes: Task 1 `banner` 테이블
- Produces:
  - `record BannerRequest(@NotBlank @Size(max = 50) String title, @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.+") String imageUrl, @NotBlank @Size(max = 500) @Pattern(regexp = "^(https?://|/).+") String linkUrl, @NotNull Instant startsAt, @NotNull Instant endsAt, @Min(0) @Max(1000) int sortOrder)`. title strip 뒤 빈 값, 기간 역전, 연도 범위는 서비스가 `INVALID_INPUT`으로 막는다.
  - `record BannerResponse(Long id, String title, String imageUrl, String linkUrl, Instant startsAt, Instant endsAt, int sortOrder)`
  - `BannerService`:
    - `create`, `update(id, request)`(없으면 `BANNER_NOT_FOUND`), `delete(id)`(없으면 404)
    - `listAll()`(관리자), `listActive()`(`now`는 Clock에서)
  - API:
    - `POST /api/admin/banners` → 201 + Location
    - `PUT /api/admin/banners/{id}` → 200
    - `DELETE /api/admin/banners/{id}` → 204
    - `GET /api/admin/banners` → 200(`List<BannerResponse>`)
    - `GET /api/banners` → 200(`List<BannerResponse>`, permitAll)
  - `index.adoc` `== 메인`: 메인 섹션과 호출할 API 표를 둔다.

    | 섹션 | 호출할 API |
    |---|---|
    | 배너 | `GET /api/banners` |
    | 오늘의 특가 | `GET /api/products?onlyOnSale=true&size=…` |
    | 신상품 | `GET /api/products?sort=NEWEST&size=…` |
    | 랭킹 | `GET /api/rankings` |
    | 피부타입 추천 | `GET /api/recommendations?skinType=` |

    - 피부타입 추천: 로그인했으면 `GET /api/members/me`의 피부타입을, 없으면 사용자가 고른 타입을 넣는다.
    - 섹션마다 따로 실패해도 메인이 깨지지 않게 부른다는 안내를 적는다.

- [ ] **Step 1: 실패 테스트 작성**
  - `AdminBannerApiTest`
    - `배너를_등록_수정_삭제_목록_조회한다`: REST Docs `admin-banner/create`, `update`, `delete`, `list`
    - `시작이_종료보다_늦거나_같으면_400` (Review Focus 5)
    - `아주_먼_연도는_500이_아니라_400` (Review Focus 5): `+300000-01-01T00:00:00Z`
    - `잘못된_URL과_제목_정렬순서는_400`: ftp URL, javascript: 링크, 공백 제목, 1001
    - `없는_배너는_404`, `일반_회원은_403`
  - `BannerApiTest` (`MutableClock`)
    - `노출_기간_안의_배너만_순서대로_보인다` (Review Focus 5): startsAt 정각 포함, endsAt 정각 제외, sortOrder 동률이면 id 오름차순. REST Docs `banner/list`
    - `토큰_없이도_읽는다`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인.** `./gradlew clean :backend:build`
- [ ] **Step 5: 커밋.** `feat: 배너 관리와 공개 배너 API, 메인 구성 문서 추가`

---

## Phase 9b 완료 기준
- `./gradlew clean :backend:build`가 통과한다(Phase 0~9a 포함).
- REST Docs에 `== 랭킹`, `== 추천`, `== 배너`, `== 관리자 - 배너`, `== 메인`이 있고, 검색 정렬 표에 SALES가 있다.
- 이월 노트에 Phase 10 항목을 남긴다:
  - 메인 섹션별 호출 조합
  - 추천은 `GET /api/members/me` 피부타입을 넘기는 방식
  - 랭킹은 매시간 정각 갱신(화면에 집계 기준 안내)
- 이월 노트에 Phase 12 항목을 남긴다:
  - 판매량 집계 쿼리의 EXPLAIN(`payment.approved_at` 인덱스)
  - 배치들이 단일 스케줄러 스레드를 같이 쓰는 점
