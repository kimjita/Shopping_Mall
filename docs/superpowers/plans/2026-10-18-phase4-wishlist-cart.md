# Phase 4 — 찜·최근 본 상품·장바구니 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원 전용 찜(상품 단위), 최근 본 상품(서버 저장, 최근 50개), 장바구니(SKU 단위, 재고까지만 담기)를 만든다.

**Architecture:**
- 세 기능이 상품 정보를 같은 방식으로 읽는다. Phase 3 목록 쿼리의 조회 부분을 꺼내 `ProductSearchRepository.findCards(ids, now)`로 만들고, 상품·브랜드·대표 이미지·품절 여부를 쿼리 한 번으로 읽는다. 그래서 목록 길이와 상관없이 쿼리 수가 일정하다.
- 장바구니 가격은 SKU마다 `Sale.apply(sku.price(basePrice))`를 요청당 하나의 `now`로 계산한다. `min_price`는 쓰지 않는다.
- 같은 회원의 장바구니 쓰기는 Phase 1의 회원 행 잠금(`MemberService.getActiveForUpdate`)으로 직렬화한다. 배송지와 같은 방식이다.
- "찜했는지"와 "최근 본 상품 기록"은 회원 전용 API로 따로 둔다. 공개 상품 API에 선택적 로그인을 붙이지 않는다.

**Tech Stack:** Phase 3과 같다(Spring Boot 3.5.16, QueryDSL 6.12, Flyway V9~V11, Testcontainers, REST Docs). 새 의존성은 없다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("찜 / 최근 본 상품 / 장바구니", Phase 4 항목). 이월 노트의 Phase 4 항목을 반영한다(SKU별 가격 계산, `getActive`로 탈퇴 회원 차단).

## Global Constraints
- Phase 0~3 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, DTO는 `record`
  - 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속, setter 없음, 다른 애그리거트는 id 컬럼으로 참조
  - 에러는 `BusinessException(ErrorCode)`
  - 문서·주석·REST Docs 설명은 평서체, 에러 메시지는 "~니다"체
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 새 코드는 `com.sprint.mission.wishlist`, `com.sprint.mission.recentview`, `com.sprint.mission.cart`에 두고, 각각 `controller / service / domain / repository / dto`로 나눈다.
- 모든 API는 `/api/members/me/...` 아래에 있고 로그인이 필요하다. 서비스는 먼저 `MemberService.getActive`를 부른다(쓰기는 `getActiveForUpdate`). 그래서 탈퇴 회원은 404 `MEMBER_NOT_FOUND`다.
- 다른 회원의 장바구니 항목 id는 존재를 드러내지 않는 404 `CART_ITEM_NOT_FOUND`다.
- 상품 노출 규칙:
  - 찜·최근 본 상품 기록은 `ON_SALE` 상품에만 할 수 있다(아니면 404 `PRODUCT_NOT_FOUND`).
  - 목록에서 `STOPPED`는 `available=false`로 보여주고, `DELETED`는 빼고 보여준다.
- 장바구니 규칙:
  - SKU 단위이고 `(member_id, sku_id)`는 한 줄이다. 같은 SKU를 다시 담으면 수량을 더한다.
  - 줄당 수량은 1~99, 회원당 최대 50줄이다.
  - 담기·수량 변경 결과가 현재 재고보다 많으면 409 `STOCK_NOT_ENOUGH`이고, 기존 수량은 그대로다. 실제 재고 차감은 Phase 6이다.
  - 조회 응답의 각 줄 `status`:
    - `AVAILABLE`
    - `NOT_ENOUGH_STOCK`: 재고 > 0이지만 담은 수량보다 적다
    - `SOLD_OUT`: 재고 0
    - `UNAVAILABLE`: 상품이 STOPPED·DELETED
    - 재고 수량 자체는 내보내지 않는다.
  - 가격 필드:
    - `unitPrice` = `sku.price(basePrice)`
    - `unitSalePrice` = 활성 세일이면 `Sale.apply(unitPrice)`, 아니면 `unitPrice`
    - `lineTotal` = `unitSalePrice × quantity`
    - 합계는 서버가 내려주지 않는다. 선택 항목 합계는 프론트가 계산하고, 쿠폰·배송비는 Phase 5 `PriceCalculator`가 맡는다.
- 최근 본 상품 규칙:
  - 회원당 최대 50개이고, 같은 상품을 다시 기록하면 시각만 갱신해서 맨 앞으로 온다.
  - 51번째를 기록하면 가장 오래된 것부터 지운다.
  - 기록은 `insert ... on conflict (member_id, product_id) do update` 한 문장이다.
- 찜 규칙:
  - 상품 단위이고 `(member_id, product_id)`는 하나다.
  - 추가(PUT)와 삭제(DELETE)는 멱등이고 항상 204다(삭제는 대상이 없어도 204).
  - 목록은 찜한 순서의 역순(`wishlist.id desc`)이고 커서(마지막 wishlist id, 숫자 문자열)로 이어 읽는다. size는 기본 20, 1~50이다.
  - 찜 여부 조회 `productIds`는 1~100개다.

**의도적으로 하지 않는 것:**
- 장바구니 합계·배송비 → Phase 5
- 선택 삭제 일괄 API(프론트가 항목별 DELETE를 호출한다)
- 비회원 장바구니와 로그인 시 합치기(설계서상 비회원은 구경만 한다)
- 찜 개수 집계 → Phase 9 랭킹에서 필요하면 추가
- 상품 잠금을 `FOR NO KEY UPDATE`로 바꾸기(관리자 트랜잭션이 짧아 지금은 그대로 둔다)

## Review Focus
1. 같은 SKU를 두 번 담거나 더블클릭으로 동시에 담기 → 한 줄로 합쳐지고 수량 합이 정확하다. 합이 재고나 99를 넘으면 거절되고 기존 수량은 그대로다 → Task 3 테스트(동시성 포함)
2. 담은 뒤 재고가 줄거나 상품이 판매중지·삭제되면 → 조회 시 각 줄 `status`가 맞게 바뀌고 500이 나지 않는다 → Task 3 테스트
3. 추가금이 있는 SKU에 세일이 걸리면 → 장바구니 `unitSalePrice`가 공개 상세의 같은 SKU `salePrice`와 같다 → Task 3 테스트
4. 다른 회원의 장바구니 항목 수정·삭제 → 404, 원래 항목은 그대로다 → Task 3 테스트
5. 최근 본 상품 51번째 기록과 같은 상품 재기록 → 50개 유지, 가장 오래된 것이 빠지고 재기록한 상품이 맨 앞이다 → Task 2 테스트

---

### Task 1: 공통 상품 카드 조회 + 찜

**Files:**
- Modify: `product/repository/ProductSearchRepository.java` (조회 부분을 private 메서드로 꺼내 `search`와 `findCards`가 함께 쓴다)
- Create: `product/dto/ProductCard.java`
- Create: `backend/src/main/resources/db/migration/V9__create_wishlist.sql`
- Create:
  - `wishlist/domain/Wishlist.java`
  - `wishlist/repository/WishlistRepository.java`
  - `wishlist/service/WishlistService.java`
  - `wishlist/controller/WishlistController.java`
  - `wishlist/dto/WishlistItem.java`, `WishlistPage.java`, `WishedProductIds.java`
- Modify: `index.adoc` (`== 찜`). 새 ErrorCode는 없다(기존 `PRODUCT_NOT_FOUND`, `INVALID_INPUT`을 쓴다).
- Test: `product/repository/ProductCardQueryTest.java` (`IntegrationTest`), `wishlist/controller/WishlistApiTest.java`

**Interfaces:**
- Produces:
  - `record ProductCard(ProductSummary summary, ProductStatus status)`
  - `ProductSearchRepository.findCards(Collection<Long> productIds, Instant now) → Map<Long, ProductCard>`
    - 상태와 상관없이 있는 상품을 모두 돌려준다(DELETED 포함). 거르는 건 호출자가 `status`로 한다: 찜·최근 본 상품은 DELETED를 빼고, 장바구니는 UNAVAILABLE 줄로 보여준다. 빈 목록이면 쿼리 없이 빈 맵을 돌려준다.
    - 쿼리는 한 번이고, `search`와 같은 조회 식(현재 판매가 CASE, 품절 스칼라 서브쿼리, 대표 이미지 `index()` 조인)을 공유한다.
    - Phase 3 목록 API의 동작과 쿼리 수(정확히 1번)는 바뀌지 않는다.
  - 테이블 (V9) `wishlist`:
    - 컬럼: `id bigserial pk`, `member_id bigint not null references member(id)`, `product_id bigint not null references product(id)`, `created_at`, `updated_at`
    - 제약·인덱스: `unique(member_id, product_id)`, `index(member_id, id desc)`
  - `WishlistService`:
    - `void add(Long memberId, Long productId)`: ON_SALE이 아니면 PRODUCT_NOT_FOUND. 추가는 `insert into wishlist (...) values (...) on conflict (member_id, product_id) do nothing` 한 문장이다(트랜잭션 안에서 unique 위반 예외를 잡으면 rollback-only가 되어 커밋 때 500이 나므로 예외를 쓰지 않는다). created_at·updated_at은 `Clock`으로 넣는다.
    - `void remove(Long memberId, Long productId)`
    - `WishlistPage list(Long memberId, String cursor, int size)`
    - `WishedProductIds wished(Long memberId, List<Long> productIds)`
  - DTO:
    - `record WishlistItem(ProductSummary product, boolean available, Instant wishedAt)`
    - `record WishlistPage(List<WishlistItem> items, String nextCursor)`
    - `record WishedProductIds(List<Long> productIds)`
  - API:
    - `PUT /api/members/me/wishlist/{productId}` → 204
    - `DELETE /api/members/me/wishlist/{productId}` → 204
    - `GET /api/members/me/wishlist?cursor=&size=` → 200 `WishlistPage`
    - `GET /api/members/me/wishlist/ids?productIds=1,2,3` → 200 `WishedProductIds`(입력 순서 유지, 찜한 것만)

- [ ] **Step 1: 실패 테스트 작성**
  - `ProductCardQueryTest`
    - `여러_상품을_한_번의_쿼리로_읽는다`: 상품 5개 → `findCards` 결과 5개, Hibernate `getPrepareStatementCount() == 1`
    - `모든_상태의_상품을_status와_함께_돌려준다`: ON_SALE·STOPPED·DELETED 각 1개 → 3개, status가 맞다
    - `빈_목록이면_쿼리를_실행하지_않는다`
    - `세일가와_품절이_목록_API와_같다`: 같은 상품의 `findCards` summary가 `GET /api/products` 항목과 같다
  - `WishlistApiTest`
    - `찜하고_목록에서_본다`: REST Docs `wishlist/add`, `wishlist/list`
    - `같은_상품을_두_번_찜해도_하나다`
    - `찜을_취소한다`: REST Docs `wishlist/remove`. 없는 상품의 취소도 204다.
    - `판매중지_상품은_찜할_수_없다`: 404
    - `찜한_뒤_판매중지되면_available_false로_보인다`
    - `찜한_뒤_삭제된_상품은_목록에서_빠진다`
    - `커서로_끝까지_읽는다`: 찜 5개, size 2 → 3페이지, 중복·누락 없음, 마지막 `nextCursor` null
    - `찜_여부를_한_번에_확인한다`: `productIds=1,2,3` 중 1·3만 찜 → `[1,3]`. REST Docs `wishlist/ids`
    - `잘못된_요청은_400`: `size=0`, `size=51`, `productIds` 101개, 깨진 커서 `abc`, `productIds=%20`
    - `목록_조회의_쿼리_수는_페이지_크기와_무관하다`: size 2와 20의 statement 수가 같다
    - `토큰_없이_호출하면_401`, `탈퇴한_회원의_토큰이면_404`
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*ProductCardQueryTest' --tests '*WishlistApiTest'` → 컴파일 실패
- [ ] **Step 3: 구현.** 먼저 `findCards`를 꺼내고 `./gradlew :backend:test --tests '*ProductSearchApiTest'`로 Phase 3 목록이 그대로인지 확인한 뒤 찜을 구현한다. `index.adoc`에 `== 찜`을 추가한다.
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `feat: 공통 상품 카드 조회와 찜 API 추가`

---

### Task 2: 최근 본 상품

**Files:**
- Create: `V10__create_recent_view.sql`
- Create:
  - `recentview/repository/RecentViewRepository.java` (네이티브 upsert·정리 쿼리)
  - `recentview/service/RecentViewService.java`
  - `recentview/controller/RecentViewController.java`
  - `recentview/dto/RecentViewRequest.java`, `RecentViewItem.java`
- Modify: `index.adoc` (`== 최근 본 상품`)
- Test: `recentview/controller/RecentViewApiTest.java`

**Interfaces:**
- Consumes: Task 1의 `findCards`
- Produces:
  - 테이블 (V10) `recent_view`:
    - 컬럼: `id bigserial pk`, `member_id bigint not null references member(id)`, `product_id bigint not null references product(id)`, `viewed_at timestamptz not null`
    - 제약·인덱스: `unique(member_id, product_id)`, `index(member_id, viewed_at desc)`
    - 시각은 서비스가 `Clock`으로 넣는다. 엔티티 없이 네이티브 쿼리로만 다루므로 `BaseEntity` 컬럼은 두지 않는다.
  - `RecentViewRepository`:
    - `upsert(memberId, productId, viewedAt)`: `insert into recent_view (member_id, product_id, viewed_at) values (?, ?, ?) on conflict (member_id, product_id) do update set viewed_at = excluded.viewed_at`
    - `trim(memberId, keep)`: `delete from recent_view where member_id = ? and id not in (select id from recent_view where member_id = ? order by viewed_at desc, id desc limit ?)`
    - `findLatest(memberId, limit)`: product_id·viewed_at 목록, `viewed_at desc, id desc`
    - `delete(memberId, productId)`, `deleteAll(memberId)`
    - `JdbcTemplate`이나 `@Modifying @Query(nativeQuery = true)` 중 편한 쪽을 쓴다.
  - `RecentViewService.record(memberId, productId)`: ON_SALE이 아니면 PRODUCT_NOT_FOUND. upsert 후 trim(50)을 한 트랜잭션에서 실행한다.
  - `record RecentViewRequest(@NotNull Long productId)`, `record RecentViewItem(ProductSummary product, boolean available, Instant viewedAt)`
  - API:
    - `POST /api/members/me/recent-views` → 204
    - `GET /api/members/me/recent-views` → 200 `List<RecentViewItem>`(최대 50개, DELETED 제외)
    - `DELETE /api/members/me/recent-views/{productId}` → 204
    - `DELETE /api/members/me/recent-views` → 204(전체 삭제)

- [ ] **Step 1: 실패 테스트 작성** (`RecentViewApiTest`)
  - `본_상품이_최신순으로_쌓인다`: A, B, C 순서로 기록 → `[C, B, A]`. REST Docs `recent-view/record`, `recent-view/list`
  - `같은_상품을_다시_보면_맨_앞으로_온다` (Review Focus 5): A, B, A → `[A, B]`, 2개
  - `오십_한_번째를_기록하면_가장_오래된_것이_빠진다` (Review Focus 5): 51개 상품 기록 → 50개, 첫 상품이 없다.
  - 시각 동률로 순서가 흔들리지 않게, 이 테스트 클래스는 테스트 전용 `MutableClock` 빈(`@TestConfiguration`, `@Primary`)을 써서 기록 사이에 1초씩 시간을 진행한다.
  - `하나를_지우거나_전부_지운다`: REST Docs `recent-view/remove`, `recent-view/clear`
  - `판매중지_상품은_기록할_수_없다`: 404
  - `본_뒤_판매중지되면_available_false`, `본_뒤_삭제되면_빠진다`
  - `동시에_같은_상품을_기록해도_한_줄이다`: 스레드 5개 → 행 1개(upsert 확인, `IntegrationTest`로 별도 클래스 `RecentViewConcurrencyTest`)
  - `토큰_없이_호출하면_401`
- [ ] **Step 2: 실패 확인.** focused 테스트 → 컴파일 실패
- [ ] **Step 3: 구현.** `index.adoc`에 `== 최근 본 상품`을 추가한다. 프론트가 상품 상세를 열 때 기록 API를 호출한다는 사용법도 적는다.
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `feat: 최근 본 상품 API 추가`

---

### Task 3: 장바구니

**Files:**
- Create: `V11__create_cart_item.sql`
- Create:
  - `cart/domain/CartItem.java`, `CartItemStatus.java`
  - `cart/repository/CartItemRepository.java`
  - `cart/service/CartService.java`
  - `cart/controller/CartController.java`
  - `cart/dto/CartAddRequest.java`, `CartQuantityRequest.java`, `CartItemResponse.java`, `CartResponse.java`
- Modify: `ErrorCode.java`, `index.adoc` (`== 장바구니`)
- Test: `cart/domain/CartItemStatusTest.java`, `cart/controller/CartApiTest.java`, `cart/service/CartConcurrencyTest.java` (`IntegrationTest`, 트랜잭션 없음)

**Interfaces:**
- Consumes: Task 1의 `findCards`, `SkuRepository`, `ProductRepository`, `Sale`, `MemberService.getActive`·`getActiveForUpdate`, `Clock`
- Produces:
  - 테이블 (V11) `cart_item`:
    - 컬럼: `id bigserial pk`, `member_id bigint not null references member(id)`, `sku_id bigint not null references sku(id)`, `quantity int not null check (quantity between 1 and 99)`, `created_at`, `updated_at`
    - 제약·인덱스: `unique(member_id, sku_id)`, `index(member_id, id)`
  - `ErrorCode`:
    - `CART_ITEM_NOT_FOUND(404, "장바구니 상품을 찾을 수 없습니다.")`
    - `CART_LIMIT_EXCEEDED(400, "장바구니에는 최대 50개 상품을 담을 수 있습니다.")`
    - 재고 초과는 기존 `STOCK_NOT_ENOUGH`(409)를 쓴다.
  - `CartItem`: `static create(Long memberId, Long skuId, int quantity)`, `void changeQuantity(int)`, `void addQuantity(int)`
  - `enum CartItemStatus { AVAILABLE, NOT_ENOUGH_STOCK, SOLD_OUT, UNAVAILABLE }`
    - `static CartItemStatus of(ProductStatus productStatus, int stock, int quantity)`: 우선순위는 UNAVAILABLE → SOLD_OUT → NOT_ENOUGH_STOCK → AVAILABLE
  - `CartService`:
    - `CartResponse get(Long memberId)`: 쿼리 수는 항목 수와 상관없이 일정하다(cart_item 1 + `findCards` 1 + sku 1 + product(세일·정가) 1 정도).
      - 가격은 하나의 `now`로 계산하고, DELETED 상품의 줄도 `UNAVAILABLE`로 보여준다.
      - 상품명·브랜드·대표 이미지·상태는 `findCards`(DELETED 포함)에서 가져오고, 정가와 세일은 product 일괄 조회로 가져온다.
    - `CartItemResponse add(Long memberId, CartAddRequest)`:
      1. `getActiveForUpdate`로 회원 행을 잠근다.
      2. SKU를 찾는다(없으면 `SKU_NOT_FOUND`). 상품이 ON_SALE이 아니면 `PRODUCT_NOT_FOUND`다.
      3. 기존 줄이 있으면 수량을 더하고, 없으면 새 줄을 만든다(50줄 초과면 `CART_LIMIT_EXCEEDED`).
      4. 결과 수량이 99를 넘으면 400 `INVALID_INPUT`, 재고보다 많으면 409 `STOCK_NOT_ENOUGH`다.
    - `CartItemResponse changeQuantity(Long memberId, Long cartItemId, int quantity)`: 같은 잠금과 같은 재고 검증을 한다. 다른 회원의 id는 `CART_ITEM_NOT_FOUND`다.
    - `void remove(Long memberId, Long cartItemId)`: 다른 회원의 id는 `CART_ITEM_NOT_FOUND`다.
  - DTO:
    - `record CartAddRequest(@NotNull Long skuId, @Min(1) @Max(99) int quantity)`
    - `record CartQuantityRequest(@Min(1) @Max(99) int quantity)`
    - `record CartItemResponse(Long id, Long skuId, String skuName, Long productId, String productName, String brandName, String thumbnailUrl, int quantity, long unitPrice, long unitSalePrice, long lineTotal, CartItemStatus status)`
    - `record CartResponse(List<CartItemResponse> items)`: 담은 순서(`id asc`)
  - API:
    - `GET /api/members/me/cart` → 200 `CartResponse`
    - `POST /api/members/me/cart/items` → 201 `CartItemResponse`(합쳐진 경우에도 201이고, 합쳐진 줄을 돌려준다)
    - `PATCH /api/members/me/cart/items/{cartItemId}` → 200 `CartItemResponse`
    - `DELETE /api/members/me/cart/items/{cartItemId}` → 204

- [ ] **Step 1: 실패 테스트 작성**
  - `CartItemStatusTest`
    - `판매중지는_재고와_상관없이_UNAVAILABLE`
    - `재고_0은_SOLD_OUT`
    - `재고가_담은_수량보다_적으면_NOT_ENOUGH_STOCK`
    - `충분하면_AVAILABLE`
  - `CartApiTest`
    - `장바구니에_담고_조회한다`: REST Docs `cart/add`, `cart/get`
    - `같은_SKU를_다시_담으면_수량이_합쳐진다` (Review Focus 1)
    - `합친_수량이_재고보다_많으면_409이고_기존_수량은_그대로다` (Review Focus 1): 재고 5, 3개 담고 3개 더 → 409, 수량 3
    - `수량_99를_넘으면_400` (Review Focus 1)
    - `수량을_바꾸고_뺀다`: REST Docs `cart/change-quantity`, `cart/remove`
    - `오십_한_번째_줄은_400_CART_LIMIT_EXCEEDED`
    - `담은_뒤_재고가_줄면_NOT_ENOUGH_STOCK_또는_SOLD_OUT` (Review Focus 2): `adjustStock`으로 줄인 뒤 GET
    - `담은_뒤_판매중지되거나_삭제되면_UNAVAILABLE` (Review Focus 2): GET이 200이다
    - `세일가는_SKU마다_계산한다` (Review Focus 3): base 14,900, SKU 추가금 0 / 2,000, 15% 세일 → `unitSalePrice` 12,660 / 14,360(=16,900×85/100=14,365 → 14,360). 공개 상세의 `skus[].salePrice`와 같다. `lineTotal = unitSalePrice × quantity`
    - `다른_회원의_항목은_수정도_삭제도_404` (Review Focus 4): 원래 항목의 수량이 그대로다
    - `판매중지_상품은_담을_수_없다`: 404 `PRODUCT_NOT_FOUND`
    - `없는_SKU는_404`
    - `조회의_쿼리_수는_항목_수와_무관하다`: 항목 2개와 10개의 statement 수가 같다
    - `토큰_없이_호출하면_401`, `탈퇴한_회원의_토큰이면_404`
  - `CartConcurrencyTest`
    - `더블클릭으로_동시에_담아도_한_줄이고_수량이_정확하다` (Review Focus 1): 재고 10, 같은 SKU를 2개씩 5개 스레드가 동시에 담음 → 한 줄, 수량 10
    - `동시에_담아도_재고를_넘지_않는다`: 재고 5, 2개씩 5개 스레드 → 성공 2, `STOCK_NOT_ENOUGH` 3, 수량 4
- [ ] **Step 2: 실패 확인.** focused 테스트 → 컴파일 실패
- [ ] **Step 3: 구현.** `index.adoc`에 `== 장바구니`를 추가한다. 상태 값의 의미, 가격 필드 정의, 합계는 프론트가 계산한다는 점, 실제 재고 차감은 주문 시점이라는 점을 적는다.
- [ ] **Step 4: 통과 확인.** `./gradlew clean :backend:build` 통과
- [ ] **Step 5: 커밋.** `feat: 장바구니 API 추가`

---

## Phase 4 완료 기준
- `./gradlew clean :backend:build`가 통과한다(Phase 0~3 테스트 포함).
- REST Docs에 `== 찜`, `== 최근 본 상품`, `== 장바구니`가 있다.
- Phase 3 목록 API의 응답과 쿼리 수(1번)가 바뀌지 않았다.
