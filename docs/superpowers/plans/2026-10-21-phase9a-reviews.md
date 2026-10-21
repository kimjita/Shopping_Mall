# Phase 9a — 리뷰와 평점 정렬 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- 구매확정된 주문상품마다 리뷰를 하나 쓰고 고치고 지울 수 있게 한다. 리뷰에는 별점 1~5, 본문 10~1,000자, 사진 URL 최대 5개, 작성 시점 피부 프로필 스냅샷이 들어간다.
- 상품의 리뷰 수와 평균 평점을 원자적 UPDATE로 비정규화한다.
- 공개 리뷰 목록(정렬·피부타입 필터), 내 리뷰 목록, 작성 가능한 상품 목록을 만든다.
- 검색에 "평점순"과 "리뷰 많은 순" 정렬을 추가한다.

**Architecture:**
- **평점 비정규화:**
  - `product`에 `review_count`, `rating_sum`, `rating_avg`(소수 둘째 자리, 리뷰가 없으면 NULL)를 둔다.
  - 리뷰 쓰기·수정·삭제는 같은 트랜잭션에서 SQL 한 문장(`review_count + Δ`, `rating_sum + Δ`, 평균 재계산)으로 바꾼다.
  - 엔티티에서는 읽기 전용 매핑이다. 그래서 관리자 상품 수정이 덮어쓰지 않는다(`Member.pointBalance`와 같은 방식).
- **동시성:**
  - 같은 회원의 리뷰 쓰기는 회원 행 잠금으로 직렬화하고, `order_item_id` unique를 마지막 방어선으로 둔다.
  - 다른 회원들이 같은 상품에 동시에 써도 상품 행 UPDATE가 행 잠금으로 줄을 서므로 합계가 맞는다.
  - 잠금 순서는 회원 → 상품이다.
- **정렬:**
  - `ProductSort`에 `RATING`(평균 내림차순, 리뷰 없는 상품은 맨 뒤)과 `REVIEW_COUNT`(리뷰 수 내림차순)를 더한다. 동률은 id 내림차순이다.
  - 커서는 기존 `ProductCursor`를 넓힌다.

**Tech Stack:** Phase 8과 같다. Flyway V18을 쓴다. 새 의존성은 없다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("리뷰", "검색"의 평점·리뷰 많은 순 정렬). 이월 노트의 Phase 3 항목(정렬용 비정규화 컬럼 + `(status, col desc, id desc)` 인덱스, 소수 커서는 BigDecimal 문자열)과 Phase 8 항목(CONFIRMED 주문상품만, 주문상품당 하나)을 반영한다. 랭킹·추천·배너·메인은 Phase 9b다.

## Global Constraints
- Phase 0~8 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, DTO는 `record`
  - 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속, setter 없음, id 참조
  - 에러는 `BusinessException(ErrorCode)`
  - 문서·주석은 평서체, 에러 메시지는 "~니다"체
  - 벌크 쿼리는 `@Modifying(flushAutomatically = true)`만 쓴다
  - FK가 걸린 행 잠금은 `FOR NO KEY UPDATE`
  - 시각은 `Clock` 빈
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 새 코드 위치: `com.sprint.mission.review`(controller/service/domain/repository/dto)
- **작성 규칙:**
  - 리뷰는 내 주문의 주문상품이고, 그 주문이 CONFIRMED일 때만 쓸 수 있다. 기한은 없다.
  - 주문상품 하나에 리뷰 하나다(`review.order_item_id` unique).
  - 삭제하면 같은 주문상품에 다시 쓸 수 있다(행을 지운다).
  - 상품이 판매 중지·삭제되어도 쓸 수 있다.
- **입력:**
  - `rating`은 1~5 정수다.
  - `content`는 앞뒤 공백을 뗀 길이가 10~1,000자다. 저장할 때도 뗀 값을 저장한다.
  - `imageUrls`는 0~5개이고, 각 항목은 `^https?://.+`이며 500자 이하다(상품 이미지 검증 메시지와 같은 형식). 순서를 지킨다.
- **피부 프로필 스냅샷:** 작성 시점 회원의 `skinType`·`skinTone`·`skinConcerns`를 복사한다(모두 비어 있을 수 있다). 수정해도 스냅샷은 바꾸지 않는다.
- **평점 갱신 SQL (한 문장, 같은 트랜잭션):**

  ```sql
  update product set review_count = review_count + :dc, rating_sum = rating_sum + :ds,
         rating_avg = case when review_count + :dc = 0 then null
                           else round((rating_sum + :ds)::numeric / (review_count + :dc), 2) end
  where id = :productId
  ```

  - 쓰기는 (+1, +rating), 수정은 (0, 새 별점 − 옛 별점), 삭제는 (−1, −rating)이다.
  - Δ가 둘 다 0이면 실행하지 않는다.
- **공개 리뷰 목록:**
  - `GET /api/products/{productId}/reviews`이고 permitAll이다.
  - `sort`는 `LATEST`(기본, id 내림차순), `RATING_DESC`(별점 내림차순 → id 내림차순), `RATING_ASC`(별점 오름차순 → id 내림차순) 중 하나다.
  - `skinType`은 선택이고, 스냅샷의 피부타입으로 거른다.
  - `cursor`가 있다. `size`는 기본 20, 1~50이다.
  - 작성자 이름은 첫 글자 + `**`로 가린다. 탈퇴 회원이면 `탈퇴한 회원`이다.
  - 없는 상품 id는 404 `PRODUCT_NOT_FOUND`다(DELETED 상품도 404).
- **새 ErrorCode:**
  - `REVIEW_NOT_FOUND(404, "리뷰를 찾을 수 없습니다.")`
  - `ORDER_ITEM_NOT_FOUND(404, "주문 상품을 찾을 수 없습니다.")`
  - `REVIEW_NOT_WRITABLE(409, "구매확정된 상품만 리뷰를 쓸 수 있습니다.")`
  - `REVIEW_ALREADY_EXISTS(409, "이미 리뷰를 작성한 상품입니다.")`

**의도적으로 하지 않는 것:**
- 사진 업로드(S3 presigned) → Phase 11. 지금은 URL 문자열만 받는다.
- 리뷰 "도움돼요", 신고, 관리자 숨김, 별점 분포 통계, 작성 적립금
- 베이지안 보정 평점, 로그인 선택 인증(`@CurrentMemberId` nullable)
- 랭킹·판매순·추천·배너·메인 → Phase 9b

## Review Focus
1. 서로 다른 회원 20명이 같은 상품에 동시에 리뷰를 쓰고, 일부는 동시에 수정·삭제한다 → 끝난 뒤 `review_count`·`rating_sum`·`rating_avg`가 남은 리뷰 행에서 다시 계산한 값과 정확히 같다 → Task 2 동시성 테스트
2. 같은 회원이 같은 주문상품에 리뷰 쓰기를 동시에 두 번 보낸다 → 하나만 201이고 다른 하나는 409 `REVIEW_ALREADY_EXISTS`다(500이 아니다). 상품 집계는 한 번만 오른다 → Task 2 동시성 테스트
3. 마지막 리뷰를 지운 상품 → `rating_avg`는 NULL, count와 sum은 0이고, 평점순 정렬에서 리뷰 없는 상품과 함께 맨 뒤에 온다. 평점이 같은 상품은 id 내림차순이다 → Task 2·4 테스트
4. 평점순 커서로 페이지를 넘기는데, 평균이 있는 상품과 NULL 상품의 경계가 페이지 사이에 걸린다 → 빠지거나 겹치는 상품이 없다. 위조된 커서(숫자가 아닌 평균, 5 초과)는 400이다 → Task 4 테스트
5. 남의 주문상품·CONFIRMED가 아닌 주문상품·남의 리뷰 수정과 삭제 → 각각 404·409·404이고, 존재 여부를 드러내지 않는다 → Task 2 테스트

---

### Task 1: 스키마 + 리뷰 도메인 + 평점 갱신 쿼리

**Files:**
- Create:
  - `V18__create_review.sql`
  - `review/domain/Review.java`, `review/domain/ReviewSort.java`
  - `review/repository/ReviewRepository.java`
- Modify:
  - `product/domain/Product.java`: `reviewCount`, `ratingSum`, `ratingAvg`를 읽기 전용 매핑(`insertable = false, updatable = false`)으로 둔다.
  - `product/repository/ProductRepository.java`: `changeRating`
- Test: `review/domain/ReviewTest.java`, `product/repository/ProductRatingTest.java`(`IntegrationTest`)

**Interfaces:**
- Produces:
  - **V18:**
    - `alter table product add column review_count int not null default 0 check (review_count >= 0), add column rating_sum int not null default 0 check (rating_sum >= 0), add column rating_avg numeric(3,2)`, 그리고 CHECK `(review_count = 0) = (rating_avg is null)`
    - 인덱스:
      - `idx_product_status_rating on product (status, rating_avg desc nulls last, id desc)`
      - `idx_product_status_review_count on product (status, review_count desc, id desc)`
    - `review` 테이블:
      - `id`, `order_item_id bigint not null unique → order_item`, `member_id → member`, `product_id → product`
      - `rating smallint not null check (rating between 1 and 5)`, `content varchar(1000) not null`
      - `skin_type varchar(20)`, `skin_tone varchar(20)`, `created_at`, `updated_at`
      - 인덱스:
        - `(product_id, id desc)`
        - `(product_id, rating, id desc)`
        - `(product_id, skin_type, id desc)`
        - `(member_id, id desc)`
    - `review_image(review_id → review, sort_order int, url varchar(500), primary key (review_id, sort_order))`
    - `review_skin_concern(review_id → review, concern varchar(30), primary key (review_id, concern))`. 회원의 `member_skin_concern`과 같은 모양이다.
  - `enum ReviewSort { LATEST, RATING_DESC, RATING_ASC }`
  - `Review`:
    - `static Review write(Long orderItemId, Long memberId, Long productId, int rating, String content, List<String> imageUrls, SkinProfile skinProfile, Set<SkinConcern> concerns)`
      - `content`는 strip해서 저장한다.
      - 규칙(rating 1~5, strip 길이 10~1,000, 이미지 0~5)을 어기면 `IllegalArgumentException`이다. 요청 검증은 DTO가 먼저 한다.
    - `int edit(int rating, String content, List<String> imageUrls)`: 옛 별점과의 차이를 돌려준다.
    - 이미지는 `@ElementCollection @OrderColumn(name = "sort_order")`이고, 피부 고민은 `@ElementCollection`이다.
  - `ReviewRepository`:
    - `boolean existsByOrderItemId(Long)`
    - `Optional<Review> findByIdAndMemberId(Long, Long)`
  - `ProductRepository.changeRating(Long productId, int countDelta, int sumDelta) → int`: 위 SQL의 native `@Modifying(flushAutomatically = true)`이고 `updated_at`은 건드리지 않는다.
  - `Product` getter: `getReviewCount()`, `getRatingAvg()`(`BigDecimal`, null 가능)

- [ ] **Step 1: 실패 테스트 작성**
  - `ReviewTest`
    - `본문은_앞뒤_공백을_떼고_저장한다`
    - `규칙을_어기면_IllegalArgumentException`: rating 0·6, strip 9자·1,001자, 이미지 6개
    - `수정하면_별점_차이를_돌려주고_스냅샷은_그대로다`
  - `ProductRatingTest`
    - `쓰기_수정_삭제가_합계와_평균을_정확히_바꾼다`: (+1,+5) → avg 5.00, (+1,+4) → 4.50, (0,−3) → 3.00, (−1,−1)… 마지막 삭제 뒤 avg NULL, count 0 (Review Focus 3)
    - `평균은_소수_둘째_자리에서_반올림한다`: 5,4,4 → 4.33, 5,5,4 → 4.67
    - `관리자_상품_수정이_평점을_덮어쓰지_않는다`: 상품 엔티티를 읽은 뒤 changeRating을 하고 같은 엔티티로 상품명을 수정해도 count가 유지된다.
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 리뷰 테이블과 상품 평점 비정규화 컬럼 추가`

---

### Task 2: 리뷰 쓰기·수정·삭제 API

**Files:**
- Create:
  - `review/service/ReviewService.java`, `review/controller/ReviewController.java`
  - `review/dto/ReviewWriteRequest.java`, `ReviewEditRequest.java`, `MyReviewResponse.java`
- Modify: `ErrorCode.java`(4개), `index.adoc`(`== 리뷰`, `=== 리뷰 쓰기`·`=== 리뷰 수정`·`=== 리뷰 삭제`)
- Test: `review/controller/ReviewWriteApiTest.java`, `review/service/ReviewConcurrencyTest.java`

**Interfaces:**
- Consumes:
  - Task 1
  - `MemberService.getActiveForUpdate`, `Member.getSkinProfile()`·피부 고민
  - `OrderItemRepository`, `OrderRepository`: 주문상품 → 주문 → 회원·상태 확인. 필요하면 `OrderItemRepository`에 조회 메서드를 추가한다.
- Produces:
  - `record ReviewWriteRequest(@NotNull Long orderItemId, @Min(1) @Max(5) int rating, @NotBlank @Size(max = 1000) String content, @Size(max = 5) List<@NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.+") String> imageUrls)`
    - strip 뒤 10자 미만은 서비스가 `INVALID_INPUT`으로 막는다.
    - `imageUrls`가 null이면 빈 목록이다.
  - `record ReviewEditRequest(@Min(1) @Max(5) int rating, 본문·이미지 규칙 같음)`
  - `ReviewService` (모두 `@Transactional`, 먼저 `getActiveForUpdate`):
    - `MyReviewResponse write(Long memberId, ReviewWriteRequest)`
      - 주문상품이 없거나 남의 것이면 `ORDER_ITEM_NOT_FOUND`
      - 주문이 CONFIRMED가 아니면 `REVIEW_NOT_WRITABLE`
      - 이미 있으면 `REVIEW_ALREADY_EXISTS`
      - 저장 → `changeRating(productId, +1, +rating)`
    - `MyReviewResponse edit(Long memberId, Long reviewId, ReviewEditRequest)`
      - 남의 리뷰나 없는 리뷰는 `REVIEW_NOT_FOUND`
      - `edit` 차이가 0이 아니면 `changeRating(productId, 0, diff)`
    - `void delete(Long memberId, Long reviewId)`: 삭제 → `changeRating(productId, −1, −rating)`
  - `record MyReviewResponse(Long reviewId, Long orderItemId, Long productId, String productName, String skuName, String thumbnailUrl, int rating, String content, List<String> imageUrls, SkinType skinType, SkinTone skinTone, List<SkinConcern> skinConcerns, Instant createdAt, Instant updatedAt)`
    - 상품명·옵션명·썸네일은 주문상품 스냅샷에서 가져온다.
    - `skinConcerns`는 enum 이름순이다.
  - API:
    - `POST /api/members/me/reviews` → 201, `Location: /api/members/me/reviews/{reviewId}`
    - `PUT /api/members/me/reviews/{reviewId}` → 200
    - `DELETE /api/members/me/reviews/{reviewId}` → 204

- [ ] **Step 1: 실패 테스트 작성**
  - `ReviewWriteApiTest`
    - `구매확정_상품에_리뷰를_쓰면_상품_평점이_오른다`: 피부 스냅샷, 이미지 순서, 상품 count/avg. REST Docs `review/write`
    - `수정하면_평점이_바뀌고_스냅샷은_그대로다`: 회원 피부 프로필을 바꾼 뒤 수정. REST Docs `review/edit`
    - `삭제하면_평점이_빠지고_다시_쓸_수_있다`: REST Docs `review/delete` (Review Focus 3)
    - `구매확정이_아니면_409_REVIEW_NOT_WRITABLE`: DELIVERED (Review Focus 5)
    - `남의_주문상품이면_404_ORDER_ITEM_NOT_FOUND` (Review Focus 5)
    - `이미_썼으면_409_REVIEW_ALREADY_EXISTS`
    - `남의_리뷰를_고치거나_지우면_404_REVIEW_NOT_FOUND` (Review Focus 5)
    - `잘못된_요청은_400`: rating 0·6, 본문 공백만·strip 9자·1,001자, 이미지 6개·ftp URL·501자
    - `토큰_없이_호출하면_401`
  - `ReviewConcurrencyTest`
    - `여러_회원이_같은_상품에_동시에_쓰고_고치고_지워도_집계가_맞는다` (Review Focus 1): 끝난 뒤 `select count(*), sum(rating) from review where product_id = ?`와 상품 컬럼, 반올림 평균을 비교한다.
    - `같은_주문상품에_동시에_두_번_쓰면_하나만_성공한다` (Review Focus 2)
- [ ] **Step 2: 실패 확인 → Step 3: 구현.** `index.adoc`에 작성 조건, 입력 규칙, 오류 우선순위(코드 순서), 평점 집계 규칙을 적는다.
- [ ] **Step 4: 통과 확인 → Step 5: 커밋.** `feat: 리뷰 쓰기·수정·삭제와 상품 평점 집계 추가`

---

### Task 3: 리뷰 조회 API (공개 목록, 내 리뷰, 작성 가능 목록)

**Files:**
- Create: `review/service/ReviewQueryService.java`, `review/service/ReviewCursor.java`, `review/dto/ProductReviewResponse.java`, `ProductReviewPage.java`, `MyReviewPage.java`, `WritableReviewItem.java`, `WritableReviewPage.java`
- Modify:
  - `review/controller/ReviewController.java`(또는 공개용 `ProductReviewController`)
  - `global/security/SecurityConfig.java`: GET `/api/products/*/reviews`를 permitAll로 연다.
  - `index.adoc`(`=== 상품 리뷰 목록`, `=== 내 리뷰 목록`, `=== 작성 가능한 리뷰`)
- Test: `review/controller/ReviewQueryApiTest.java`

**Interfaces:**
- Consumes: Task 1·2
- Produces:
  - `ReviewCursor`: `SORT|정렬값|id`를 Base64url로 인코딩한다(`ProductCursor`와 같은 방식).
    - 정렬값은 LATEST면 비우고, RATING_*이면 1~5다.
    - 틀리거나 정렬이 다르면 `INVALID_INPUT`이다.
  - `GET /api/products/{productId}/reviews?sort=&skinType=&cursor=&size=` → `record ProductReviewPage(List<ProductReviewResponse> items, String nextCursor)`
    - `record ProductReviewResponse(Long reviewId, String authorName, int rating, String content, List<String> imageUrls, String skuName, SkinType skinType, SkinTone skinTone, List<SkinConcern> skinConcerns, Instant createdAt, Instant updatedAt)`
    - 이미지·피부 고민·작성자는 페이지 단위로 한 번에 읽는다(N+1 없음).
    - 상품이 없거나 DELETED면 `PRODUCT_NOT_FOUND`다.
  - `GET /api/members/me/reviews?cursor=&size=` → `MyReviewPage(List<MyReviewResponse> items, String nextCursor)`: 최신순이고, 커서는 리뷰 id 10진수다(PointService 방식).
  - `GET /api/members/me/reviews/writable?cursor=&size=` → `WritableReviewPage(List<WritableReviewItem> items, String nextCursor)`
    - 내 CONFIRMED 주문의 주문상품 중 리뷰가 없는 것이다. 주문상품 id 내림차순이다.
    - `record WritableReviewItem(Long orderItemId, String orderNumber, Long productId, String productName, String skuName, String thumbnailUrl, Instant confirmedAt)`

- [ ] **Step 1: 실패 테스트 작성** (`ReviewQueryApiTest`)
  - `최신순으로_커서를_넘기며_읽는다`: REST Docs `review/product-list`
  - `별점_높은_순과_낮은_순은_동률이면_최신순이다`: 커서로 두 페이지
  - `피부타입으로_거른다`: 스냅샷 기준. 회원 프로필을 나중에 바꿔도 결과가 같다.
  - `작성자_이름을_가리고_탈퇴_회원은_탈퇴한_회원이다`
  - `토큰_없이도_읽을_수_있다`
  - `없거나_삭제된_상품은_404`
  - `잘못된_정렬_커서_size는_400`: 다른 정렬의 커서, 위조 커서
  - `목록_쿼리_수는_리뷰_수와_상관없다`: 2건과 10건
  - `내_리뷰를_최신순으로_읽는다`: REST Docs `review/my-list`
  - `작성_가능한_상품은_확정됐고_리뷰가_없는_주문상품뿐이다`: 확정 2개 중 1개에 리뷰 있음, DELIVERED 1개 → 1개. REST Docs `review/writable`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인**
- [ ] **Step 5: 커밋.** `feat: 상품 리뷰 목록·내 리뷰·작성 가능한 리뷰 조회 API 추가`

---

### Task 4: 평점순·리뷰 많은 순 검색 정렬과 상품 카드의 평점

**Files:**
- Modify:
  - `product/dto/ProductSort.java`(`RATING`, `REVIEW_COUNT`)
  - `product/service/ProductCursor.java`
  - `product/repository/ProductSearchRepository.java`(select·orderBy·afterCursor·Row)
  - `product/service/ProductSearchService.java`(커서 생성)
  - `product/dto/ProductSummary.java`(`BigDecimal ratingAvg`, `int reviewCount`)
  - `product/dto/ProductResponse.java`(상세에도 같은 두 필드)
  - 상품 카드나 상세를 문서화하는 REST Docs 테스트 전부. strict responseFields라서 `grep -rl "soldOut" src/test`로 찾는다(검색·상세·찜·장바구니·최근 본 상품 등).
  - `index.adoc`(정렬 표, 카드·상세 필드)
- Test: `product/controller/ProductSearchApiTest.java`(추가), `product/service/ProductCursorTest.java`(추가)

**Interfaces:**
- Consumes: Task 1 컬럼·인덱스
- Produces:
  - `ProductSort.RATING`: `rating_avg desc nulls last, id desc`
  - `ProductSort.REVIEW_COUNT`: `review_count desc, id desc`
  - `ProductCursor`:
    - RATING 정렬값은 `BigDecimal.toPlainString()`이고, 평균이 NULL이면 `-`다.
    - 디코드할 때 0.00~5.00 범위가 아니면 `INVALID_INPUT`이다.
    - REVIEW_COUNT 정렬값은 0 이상 정수다.
    - `Decoded`에 `bigDecimalValue()`(NULL이면 null)를 더한다.
  - 평점 키셋 조건:
    - 커서 평균이 v면 `rating_avg < v or (rating_avg = v and id < cid) or rating_avg is null`
    - 커서가 `-`면 `rating_avg is null and id < cid`
  - `ProductSearchRepository.Row`는 커서용 정렬값(createdAt, ratingAvg, reviewCount)을 함께 들고 있다.

- [ ] **Step 1: 실패 테스트 작성**
  - `ProductSearchApiTest`
    - `평점순은_평균_내림차순이고_리뷰_없는_상품은_맨_뒤다` (Review Focus 3): 평균 4.50 두 개는 id 내림차순
    - `평점순_커서가_평균과_NULL_경계를_넘어도_빠지거나_겹치지_않는다` (Review Focus 4): 상품 7개(평균 있음 4, NULL 3), size 3 → 3페이지 합집합이 전체와 같고 중복이 없다.
    - `리뷰_많은_순으로_정렬한다`
    - `상품_카드와_상세에_평균_평점과_리뷰_수가_나온다`: 리뷰 없는 상품은 ratingAvg null, reviewCount 0
    - `위조된_평점_커서는_400` (Review Focus 4): `abc`, `5.01`, `-1`
  - `ProductCursorTest`: RATING의 BigDecimal·`-` 왕복, REVIEW_COUNT 왕복
- [ ] **Step 2: 실패 확인 → Step 3: 구현.** 기존 NEWEST·가격 정렬 테스트가 그대로 통과해야 한다.
- [ ] **Step 4: 통과 확인.** `./gradlew clean :backend:build`
- [ ] **Step 5: 커밋.** `feat: 평점순·리뷰 많은 순 정렬과 상품 카드 평점 추가`

---

## Phase 9a 완료 기준
- `./gradlew clean :backend:build`가 통과한다(Phase 0~8 포함).
- REST Docs에 `== 리뷰`(쓰기·수정·삭제·상품 목록·내 리뷰·작성 가능)가 있고, 검색 정렬 표에 RATING·REVIEW_COUNT가 있다.
- 이월 노트에 Phase 9b 항목을 남긴다: 피부타입 추천은 리뷰 5건 이상, 같은 피부타입 스냅샷 리뷰의 평균 기준이다(`review.skin_type` 인덱스가 있다). 판매순(SALES) 정렬을 추가할 때도 `ProductCursor`·`Row`를 같은 방식으로 넓힌다.
