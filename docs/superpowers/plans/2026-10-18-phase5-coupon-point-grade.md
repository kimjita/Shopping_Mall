# Phase 5 — 쿠폰·적립금·등급·금액 계산 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 등급 정책, 적립금 원장·잔액, 선착순 다운로드 쿠폰, 순수 도메인 금액 계산기(`PriceCalculator`), 주문서 금액 미리보기 API를 만든다. 실제 주문·적립금 사용·구매확정 적립·등급 배치는 Phase 6·8에서 이 부품을 쓴다.

**Architecture:**
- **가격의 단일 출처:** 가격은 `Product.unitSalePrice(Sku, Instant)` 하나로 계산한다. 상세·장바구니·미리보기(·주문)가 같은 메서드를 쓴다.
- **장바구니 줄 로더:** 장바구니 줄은 `CartLineLoader`가 회원 범위로 읽고 상태·가격을 붙여 돌려준다.
- **동시성:**
  - 적립금 잔액은 회원 행의 컬럼이고, 조건부 UPDATE로만 바뀐다. 잔액이 음수가 되지 않는다.
  - 엔티티에서는 잔액을 읽기 전용으로 매핑해, 프로필 수정이 잔액을 덮어쓰지 않게 한다. `Member`에는 `@DynamicUpdate`도 붙인다.
  - 쿠폰 발급은 `member_coupon` upsert(1인 1장)와 `coupon.issued_quantity` 조건부 증가(선착순)를 한 트랜잭션에서 처리한다.
- **`PriceCalculator`:** 스프링 의존이 없는 순수 클래스이고, 금액은 모두 `long`(원)이다.

**Tech Stack:** Phase 4와 같다(Spring Boot 3.5.16, Flyway V12~V14, Testcontainers, REST Docs). 새 의존성은 없다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("금액 계산 순서", "쿠폰", "적립금", "등급", Phase 5 항목). 이월 노트의 Phase 5 항목(세일가 식 통합, 장바구니 줄 로더)과 Phase 6 항목(회원 범위 조회, AVAILABLE 재검증)을 반영한다.

## Global Constraints
- Phase 0~4 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, DTO는 `record`
  - 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속, setter 없음, id 컬럼으로 참조
  - 에러는 `BusinessException(ErrorCode)`
  - 문서·주석·REST Docs 설명은 평서체, 에러 메시지는 "~니다"체
  - 트랜잭션 안에서 무결성 예외를 잡아 무시하지 않는다(`on conflict` 사용)
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 새 코드 위치:
  - `com.sprint.mission.coupon`, `com.sprint.mission.point`, `com.sprint.mission.checkout`(계산기·미리보기)
  - 등급은 `com.sprint.mission.member.domain`
- 회원 API는 `/api/members/me/...`, 관리자 API는 `/api/admin/...`다. 서비스는 먼저 `MemberService.getActive`(읽기)나 `getActiveForUpdate`(같은 회원의 쓰기를 직렬화할 때)를 부른다.
- **등급 정책:**
  - BABY → PINK → GREEN → BLACK → GOLD(enum 선언 순서가 높낮이다)
  - 기준 금액(최근 3개월 구매확정 금액)과 적립률은 `application.yml`의 `app.grade` 설정값이다. 기본값:

    | 등급 | 기준 금액 | 적립률 |
    |---|---|---|
    | BABY | 0원 | 0.5% |
    | PINK | 100,000원 | 1% |
    | GREEN | 200,000원 | 1.5% |
    | BLACK | 400,000원 | 2% |
    | GOLD | 800,000원 | 3% |

  - 적립률은 basis point(정수, 50 = 0.5%)로 둔다. 적립 포인트는 `floor(금액 × bp / 10000)`이다.
  - 신규 회원은 BABY다. 등급 재계산 배치는 Phase 8이다.
- **적립금:**
  - 회원 잔액은 `member.point_balance`(≥ 0, CHECK)이다.
  - 변경은 `update member set point_balance = point_balance + :delta, updated_at = :now where id = :id and point_balance + :delta >= 0` 한 문장이다. 0행이면 `POINTS_NOT_ENOUGH`다.
  - 같은 트랜잭션에서 `point_history`(type, amount 양수, balance_after, reason, order_id)를 남긴다.
  - type은 `EARN`(+), `USE`(−), `RESTORE`(+), `ADMIN_GRANT`(+)다. 관리자 지급 금액은 1~1,000,000이다.
- **쿠폰:**
  - 주문 전체에 1장만 쓰는 다운로드 쿠폰이다.
  - `FIXED`(정액, 100~1,000,000원), `RATE`(정률, 1~90%, `max_discount` 필수).
  - `min_order_amount` ≥ 0, `total_quantity` 1~1,000,000, `required_grade`는 null이면 누구나 받는다.
  - 기간은 발급 기간 `[issue_starts_at, issue_ends_at)`과 사용 기간 `[use_starts_at, use_ends_at)`이고, `now`는 `Clock`에서 가져온다.
  - 1인 1장(`unique(member_id, coupon_id)`)이다.
  - 선착순 발급은 `update coupon set issued_quantity = issued_quantity + 1 where id = ? and issued_quantity < total_quantity` 한 문장이다.
  - 회원 쿠폰 상태는 `AVAILABLE`/`USED`(저장)이고, 화면 상태 `EXPIRED`는 사용 종료 시각으로 계산한다.
- **금액 계산 순서 (`PriceCalculator`):**
  1. `itemsPrice` = Σ 정가×수량, `itemsSalePrice` = Σ 세일가×수량, `saleDiscount` = 차이
  2. 쿠폰은 `itemsSalePrice`에 적용한다.
     - `itemsSalePrice < min_order_amount`면 적용할 수 없다(`COUPON_NOT_APPLICABLE`). 같으면 적용된다.
     - FIXED: `min(value, itemsSalePrice)`
     - RATE: `min(itemsSalePrice × rate / 100 / 10 × 10, max_discount)`(정수 나눗셈, 10원 내림 후 상한)
  3. 배송비: `itemsSalePrice − couponDiscount ≥ 20,000`이면 0, 아니면 2,500
  4. 적립금: `points ≥ 0`이고 `itemsSalePrice − couponDiscount + shipping − points ≥ 100`이어야 한다(아니면 `POINTS_EXCEED_LIMIT`). 잔액 확인은 서비스가 한다.
  5. `paymentAmount = itemsSalePrice − couponDiscount + shippingFee − pointsUsed`

**의도적으로 하지 않는 것:**
- 등급 재계산 배치와 구매확정 적립 → Phase 8
- 실제 적립금 사용·복원과 쿠폰 사용 처리 → Phase 6(이번에는 서비스 메서드와 테스트만)
- 브랜드·카테고리 한정 쿠폰, 다운로드 후 N일 쿠폰, 쿠폰 중복 사용
- 적립금 소멸
- 관리자 쿠폰 수정·삭제(등록·목록만)

## Review Focus
1. 선착순 10장 쿠폰을 100명이 동시에 받기 → 정확히 10명, `issued_quantity = 10`이다. 같은 회원이 동시에 5번 받기 → 1장이다 → Task 4 테스트
2. 정률 쿠폰의 10원 내림·최대 할인 상한, 최소 주문금액과 정확히 같은 금액 → 계산이 정확하다 → Task 5 테스트
3. 배송비 경계: 쿠폰 적용 후 19,990원 → 2,500, 20,000원 → 0 → Task 5 테스트
4. 적립금으로 결제금액이 100원 미만이 되거나 잔액보다 많이 쓰기 → 거절. 프로필 수정과 적립금 변경이 동시에 일어나도 잔액이 유실되지 않는다 → Task 3·5·6 테스트
5. 다른 회원의 장바구니 줄이나 쿠폰으로 미리보기 → 존재를 드러내지 않는 404. AVAILABLE이 아닌 줄을 넣으면 409다 → Task 6 테스트

---

### Task 1: 가격 계산 통합 + 장바구니 줄 로더 (리팩터)

**Files:**
- Modify: `product/domain/Product.java`, `product/dto/ProductResponse.java`, `cart/service/CartService.java`, `cart/repository/CartItemRepository.java`
- Create: `cart/service/CartLineLoader.java`, `cart/domain/CartLine.java`
- Test: `product/domain/ProductTest.java`(추가), `cart/service/CartLineLoaderTest.java` (`IntegrationTest`)

**Interfaces:**
- Produces:
  - `Product.unitPrice(Sku sku)` = `sku.price(basePrice)`
  - `Product.unitSalePrice(Sku sku, Instant now)` = 활성 세일이면 `Sale.apply(unitPrice)`, 아니면 `unitPrice`
  - `ProductResponse.PublicSku.of`와 `CartService`가 이 메서드를 쓰고, 기존 식은 지운다.
  - `record CartLine(CartItem item, Sku sku, Product product, ProductCard card, long unitPrice, long unitSalePrice, CartItemStatus status)`, 그리고 `long lineTotal()` = `unitSalePrice × quantity`
  - `CartItemRepository.findAllByMemberIdAndIdIn(Long memberId, Collection<Long> ids)`
  - `CartLineLoader`(`@Component`):
    - `List<CartLine> loadAll(Long memberId, Instant now)`: 담은 순서
    - `List<CartLine> loadSelected(Long memberId, List<Long> cartItemIds, Instant now)`: 요청 순서. 하나라도 이 회원의 줄이 아니면 `CART_ITEM_NOT_FOUND`
    - 쿼리 수는 줄 수와 상관없이 일정하다(Phase 4 `toResponses`의 일괄 조회를 그대로 옮긴다).
  - `CartService.get`과 쓰기 응답은 `CartLineLoader`로 만든다. Phase 4 API 응답은 바뀌지 않는다.

- [ ] **Step 1: 실패 테스트 작성**
  - `ProductTest`
    - `세일이_없으면_옵션_정가다`
    - `세일_중이면_옵션_정가에_세일을_적용한다`: base 14,900 + 2,000, 15% → 14,360
  - `CartLineLoaderTest`
    - `선택한_줄만_요청_순서대로_읽는다`
    - `다른_회원의_줄이_섞이면_CART_ITEM_NOT_FOUND`
    - `줄_수와_상관없이_쿼리_수가_같다`: 2줄과 10줄
- [ ] **Step 2: 실패 확인.** focused 테스트 → 컴파일 실패
- [ ] **Step 3: 구현.** 리팩터 뒤 `./gradlew :backend:test --tests '*CartApiTest' --tests '*ProductApiTest'`로 Phase 2·4 응답이 그대로인지 먼저 확인한다.
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `refactor: 옵션 판매가 계산과 장바구니 줄 로딩을 한 곳으로 모음`

---

### Task 2: 등급 정책 + 회원 등급

**Files:**
- Create: `member/domain/Grade.java`, `member/domain/GradePolicy.java`, `member/GradeProperties.java`
- Create: `V12__member_grade.sql`
- Modify:
  - `member/domain/Member.java`(`grade` 필드)
  - `member/dto/MemberResponse.java`(`grade`)
  - `application.yml`(`app.grade` 기본값)
  - `index.adoc`(회원 응답 필드)
- Test: `member/domain/GradePolicyTest.java`, `member/controller/MemberApiTest.java`(응답 필드·REST Docs 갱신)

**Interfaces:**
- Produces:
  - `enum Grade { BABY, PINK, GREEN, BLACK, GOLD }`, `boolean isAtLeast(Grade other)`
  - `record GradeProperties(Map<Grade, Rule> rules)` (`@ConfigurationProperties("app.grade")`)
    - `record Rule(long threshold, int earnRateBp)`
    - 시작할 때 다섯 등급이 모두 있는지, BABY 기준이 0인지, 기준 금액이 등급 순서대로 오르는지 검증하고 아니면 시작에 실패한다.
  - `GradePolicy`(`@Component`):
    - `Grade gradeFor(long threeMonthAmount)`: 기준 이상인 가장 높은 등급
    - `int earnRateBp(Grade)`
    - `long earnPoints(Grade, long amount)` = `amount × bp / 10000`
  - V12: `alter table member add column grade varchar(20) not null default 'BABY'`
  - `Member`: `Grade grade`(enum STRING), `void changeGrade(Grade)`(Phase 8이 쓴다). 생성 시 BABY다.
  - `MemberResponse`에 `Grade grade`를 추가한다.

- [ ] **Step 1: 실패 테스트 작성** (`GradePolicyTest`, 기본 설정으로)
  - `기준_금액_경계에서_등급이_바뀐다`: 99,999 → BABY, 100,000 → PINK, 800,000 → GOLD, 0 → BABY
  - `적립_포인트는_내림한다`: GREEN, 12,345원 → 185(12,345 × 150 / 10000 = 185.175)
  - `등급_높낮이를_비교한다`: GOLD.isAtLeast(PINK) true, BABY.isAtLeast(PINK) false
  - `잘못된_설정은_시작에_실패한다`: 기준 금액이 거꾸로인 `Rule` 맵 → `IllegalArgumentException`
  - `MemberApiTest`: `내_정보에_등급이_나온다`(가입 직후 BABY)
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 회원 등급과 등급 정책 추가`

---

### Task 3: 적립금 원장·잔액 + 관리자 지급

**Files:**
- Create: `V13__create_point.sql`
- Create:
  - `point/domain/PointHistory.java`, `PointType.java`
  - `point/repository/PointHistoryRepository.java`
  - `point/service/PointService.java`
  - `point/controller/PointController.java`, `AdminPointController.java`
  - `point/dto/PointBalanceResponse.java`, `PointHistoryItem.java`, `PointHistoryPage.java`, `AdminPointGrantRequest.java`
- Modify:
  - `member/domain/Member.java`: `long pointBalance`는 `@Column(insertable = false, updatable = false)` 읽기 전용 매핑이라 엔티티 저장이 잔액을 절대 쓰지 않는다. 잔액은 조건부 UPDATE로만 바뀐다. `@DynamicUpdate`도 붙인다. Phase 8 등급 변경처럼 엔티티로 바꾸는 다른 컬럼끼리 서로 덮어쓰지 않게 하려는 것이다.
  - `member/repository/MemberRepository.java`(`changePointBalance`)
  - `ErrorCode.java`, `index.adoc`(`== 적립금`)
- Test: `point/service/PointServiceTest.java` (`IntegrationTest`), `point/controller/PointApiTest.java`, `point/service/PointConcurrencyTest.java`

**Interfaces:**
- Produces:
  - V13:
    - `alter table member add column point_balance bigint not null default 0 check (point_balance >= 0)`
    - `point_history`:
      - 컬럼: `id bigserial pk`, `member_id bigint not null references member(id)`, `type varchar(20) not null`, `amount bigint not null check (amount > 0)`, `balance_after bigint not null`, `reason varchar(100) not null`, `order_id bigint`(Phase 6에서 FK 추가), `created_at`, `updated_at`
      - 인덱스: `(member_id, id desc)`
  - `ErrorCode`:
    - `POINTS_NOT_ENOUGH(409, "적립금이 부족합니다.")`
    - `POINTS_EXCEED_LIMIT(400, "적립금은 결제금액이 100원 이상 남도록 사용할 수 있습니다.")`
  - `MemberRepository.changePointBalance(Long memberId, long delta, Instant now)`: Global Constraints의 조건부 UPDATE 한 문장이고, 반환값은 행 수다.
  - `PointService`:
    - `long earn(Long memberId, long amount, String reason, Long orderId)`
    - `long use(...)`, `long restore(...)`
    - `long grant(Long memberId, long amount, String reason)`
    - 공통 `private long change(memberId, PointType, amount, reason, orderId)`: 0행이면 `POINTS_NOT_ENOUGH`다. 바뀐 잔액을 다시 읽어 `balance_after`로 원장에 기록하고, 그 잔액을 반환한다.
    - `long balance(Long memberId)`
    - `PointHistoryPage history(Long memberId, String cursor, int size)`: id desc 커서(Phase 4 찜과 같은 방식)
  - API:
    - `GET /api/members/me/points` → `PointBalanceResponse(long balance)`
    - `GET /api/members/me/points/history?cursor=&size=` → `PointHistoryPage`
    - `POST /api/admin/members/{memberId}/points` (`AdminPointGrantRequest(long amount, String reason)`) → 200 `PointBalanceResponse`. 없거나 탈퇴한 회원이면 `MEMBER_NOT_FOUND`다.

- [ ] **Step 1: 실패 테스트 작성**
  - `PointServiceTest`
    - `지급하면_잔액이_늘고_원장이_남는다`
    - `사용하면_줄고_복원하면_돌아온다`
    - `잔액보다_많이_쓰면_POINTS_NOT_ENOUGH이고_잔액은_그대로다`
    - `원장의_balance_after가_순서대로_맞다`
  - `PointConcurrencyTest` (Review Focus 4)
    - `동시에_써도_잔액이_음수가_되지_않는다`: 잔액 1,000, 300씩 5개 스레드 → 성공 3, 실패 2, 잔액 100
    - `프로필_수정과_적립금_지급이_동시에_일어나도_잔액이_유실되지_않는다`: tx A가 회원을 읽고, 다른 스레드가 지급 +500을 커밋한 뒤, A가 `updateProfile`로 커밋 → 잔액 500, 프로필도 바뀐다(`SkuEditStockRaceTest`와 같은 방식). 읽기 전용 매핑을 빼면 실패해야 한다.
  - `PointApiTest`
    - `내_적립금과_내역을_본다`: REST Docs `point/balance`, `point/history`
    - `관리자가_적립금을_지급한다`: REST Docs `admin-point/grant`
    - `USER는_지급할_수_없다`: 403
    - `지급_금액이_0이거나_1000001이면_400`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 적립금 원장·잔액과 관리자 지급 추가`

---

### Task 4: 선착순 다운로드 쿠폰

**Files:**
- Create: `V14__create_coupon.sql`
- Create:
  - `coupon/domain/Coupon.java`, `DiscountType.java`, `MemberCoupon.java`, `MemberCouponStatus.java`
  - `coupon/repository/CouponRepository.java`, `MemberCouponRepository.java`
  - `coupon/service/CouponService.java`, `AdminCouponService.java`
  - `coupon/controller/CouponController.java`, `AdminCouponController.java`
  - `coupon/dto/CouponCreateRequest.java`, `CouponResponse.java`, `DownloadableCouponResponse.java`, `MyCouponResponse.java`
- Modify: `ErrorCode.java`, `index.adoc` (`== 쿠폰`, `== 관리자 - 쿠폰`)
- Test: `coupon/domain/CouponTest.java`, `coupon/controller/CouponApiTest.java`, `coupon/service/CouponIssueConcurrencyTest.java`

**Interfaces:**
- Produces:
  - V14:
    - `coupon`:
      - 컬럼: `id bigserial pk`, `name varchar(50) not null`, `discount_type varchar(10) not null`, `discount_value bigint not null`, `max_discount bigint`, `min_order_amount bigint not null default 0`, `total_quantity int not null`, `issued_quantity int not null default 0`, `required_grade varchar(20)`, `issue_starts_at`, `issue_ends_at`, `use_starts_at`, `use_ends_at`(모두 timestamptz not null), `created_at`, `updated_at`
      - CHECK: `issued_quantity between 0 and total_quantity`, `issue_ends_at > issue_starts_at`, `use_ends_at > use_starts_at`, `(discount_type = 'FIXED' and max_discount is null) or (discount_type = 'RATE' and discount_value between 1 and 90 and max_discount > 0)`
    - `member_coupon`:
      - 컬럼: `id bigserial pk`, `member_id bigint not null references member(id)`, `coupon_id bigint not null references coupon(id)`, `status varchar(20) not null`, `used_order_id bigint`, `used_at timestamptz`, `created_at`, `updated_at`
      - 제약·인덱스: `unique(member_id, coupon_id)`, `index(member_id, id desc)`
  - `ErrorCode`:
    - `COUPON_NOT_FOUND(404, "쿠폰을 찾을 수 없습니다.")`
    - `COUPON_SOLD_OUT(409, "쿠폰이 모두 소진되었습니다.")`
    - `COUPON_ALREADY_ISSUED(409, "이미 받은 쿠폰입니다.")`
    - `COUPON_NOT_ISSUABLE(400, "지금은 받을 수 없는 쿠폰입니다.")`: 발급 기간 밖이거나 등급 미달
    - `COUPON_NOT_USABLE(400, "사용할 수 없는 쿠폰입니다.")`
    - `COUPON_NOT_APPLICABLE(400, "쿠폰 최소 주문금액을 채우지 못했습니다.")`
  - `Coupon`:
    - `boolean isIssuable(Instant now, Grade grade)`
    - `boolean isUsable(Instant now)`
    - `CouponTerms terms()`: Task 5 계산기 입력이고, `record CouponTerms(DiscountType type, long value, Long maxDiscount, long minOrderAmount)`
  - `CouponService.download(Long memberId, Long couponId)` (한 트랜잭션):
    1. `getActive`
    2. 쿠폰 조회(없으면 `COUPON_NOT_FOUND`)
    3. `isIssuable(now, member.getGrade())`가 아니면 `COUPON_NOT_ISSUABLE`
    4. `insert into member_coupon ... on conflict (member_id, coupon_id) do nothing`이 0행이면 `COUPON_ALREADY_ISSUED`
    5. 조건부 증가가 0행이면 `COUPON_SOLD_OUT`(예외로 롤백되어 4의 행도 사라진다)
  - `CouponService.downloadable(Long memberId)`: 지금 발급 기간이고, 남은 수량이 있고, 이 회원이 아직 안 받은 쿠폰. 등급 미달 쿠폰도 `eligible=false`로 함께 보여준다.
  - `CouponService.myCoupons(Long memberId)`: 상태는 `AVAILABLE` / `USED` / `EXPIRED`(AVAILABLE이고 `now ≥ use_ends_at`)
  - `CouponService.getUsable(Long memberId, Long memberCouponId, Instant now)`: Task 6이 쓴다. 이 회원 것이 아니면 `COUPON_NOT_FOUND`, AVAILABLE이 아니거나 사용 기간 밖이면 `COUPON_NOT_USABLE`
  - API:
    - `POST /api/admin/coupons` → 201 `CouponResponse`
    - `GET /api/admin/coupons` → 목록
    - `GET /api/members/me/coupons/downloadable` → `List<DownloadableCouponResponse>`
    - `POST /api/members/me/coupons/{couponId}/download` → 201 `MyCouponResponse`
    - `GET /api/members/me/coupons` → `List<MyCouponResponse>`(최신순)

- [ ] **Step 1: 실패 테스트 작성**
  - `CouponTest`
    - `발급_기간_경계`: 시작 정각에 가능, 종료 정각에 불가
    - `등급_미달이면_발급_불가`
    - `사용_기간_경계`
  - `CouponIssueConcurrencyTest` (Review Focus 1)
    - `열_장을_백_명이_동시에_받으면_정확히_열_명이다`: 회원 100명, 스레드 100개 → 성공 10, `COUPON_SOLD_OUT` 90, `issued_quantity = 10`, `member_coupon` 10행
    - `같은_회원이_동시에_다섯_번_받아도_한_장이다`: 성공 1, `COUPON_ALREADY_ISSUED` 4, `issued_quantity = 1`
  - `CouponApiTest`
    - `관리자가_쿠폰을_만든다`: REST Docs `admin-coupon/create`
    - `받을_수_있는_쿠폰을_보고_받는다`: REST Docs `coupon/downloadable`, `coupon/download`, `coupon/my`
    - `등급_전용_쿠폰은_낮은_등급이_받을_수_없다`: BABY, PINK 전용 → 400 `COUPON_NOT_ISSUABLE`, downloadable에서 `eligible=false`
    - `발급_기간이_아니면_400`
    - `사용_기간이_지난_쿠폰은_EXPIRED로_보인다`
    - `잘못된_쿠폰_정의는_400`: 정률 95%, 정률인데 max_discount 없음, 종료가 시작보다 빠름
    - `USER는_쿠폰을_만들_수_없다`: 403
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 선착순 다운로드 쿠폰 추가`

---

### Task 5: PriceCalculator (순수 도메인)

**Files:**
- Create: `checkout/domain/PriceCalculator.java`, `PriceLine.java`, `PriceBreakdown.java`, `ShippingPolicy.java`
- Test: `checkout/domain/PriceCalculatorTest.java` (순수 JUnit, 스프링 없음)

**Interfaces:**
- Consumes: Task 4의 `CouponTerms`
- Produces:
  - `record PriceLine(long unitPrice, long unitSalePrice, int quantity)`
  - `record PriceBreakdown(long itemsPrice, long saleDiscount, long itemsSalePrice, long couponDiscount, long shippingFee, long pointsUsed, long paymentAmount)`
  - `ShippingPolicy` 상수: 무료 기준 `20_000`, 배송비 `2_500`, 최소 결제금액 `100`
  - `PriceCalculator.calculate(List<PriceLine> lines, CouponTerms couponOrNull, long points) → PriceBreakdown`
    - 빈 lines는 `IllegalArgumentException`이다.
    - 쿠폰 최소 주문금액 미달은 `BusinessException(COUPON_NOT_APPLICABLE)`, 적립금 한도 초과는 `BusinessException(POINTS_EXCEED_LIMIT)`, points < 0은 `IllegalArgumentException`이다.
    - 계산 순서와 식은 Global Constraints 그대로다.

- [ ] **Step 1: 실패 테스트 작성** (값은 정확히 아래와 같다)
  - `쿠폰_적립금_없으면_세일가_합에_배송비만_더한다`: [정가 20,000·세일 17,000 ×1] → items 20,000, sale 3,000, itemsSale 17,000, 배송비 2,500, 결제 19,500
  - `배송비_경계` (Review Focus 3): 쿠폰 적용 후 19,990 → 2,500. 20,000 → 0. (정액 쿠폰 1,000으로 20,990 → 19,990, 21,000 → 20,000을 만든다)
  - `정률_쿠폰은_10원_내림_후_상한을_적용한다` (Review Focus 2): itemsSale 33,333, 15%, 상한 10,000 → 4,990(33,333×15/100 = 4,999 → 4,990). 같은 금액에 상한 3,000이면 3,000
  - `정액_쿠폰은_세일가_합을_넘지_않는다`: itemsSale 5,000, 정액 10,000 → 쿠폰 5,000, 배송비 2,500, 결제 2,500
  - `최소_주문금액과_같으면_적용되고_10원_모자라면_거절` (Review Focus 2)
  - `적립금은_결제금액이_100원_남을_때까지` (Review Focus 4): 쿠폰·배송비 후 19,500 → points 19,400은 OK(결제 100), 19,410은 `POINTS_EXCEED_LIMIT`
  - `여러_줄의_합계`: 두 줄 × 수량으로 items·sale 합이 정확하다
  - `빈_목록은_IllegalArgumentException`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과**
- [ ] **Step 5: 커밋.** `feat: 주문 금액 계산기 추가`

---

### Task 6: 주문서 금액 미리보기 API

**Files:**
- Create: `checkout/service/CheckoutService.java`, `checkout/controller/CheckoutController.java`, `checkout/dto/CheckoutPreviewRequest.java`, `CheckoutPreviewResponse.java`, `CheckoutLine.java`
- Modify: `ErrorCode.java`, `index.adoc` (`== 주문서`)
- Test: `checkout/controller/CheckoutPreviewApiTest.java`

**Interfaces:**
- Consumes: Task 1 `CartLineLoader.loadSelected`, Task 3 `PointService.balance`, Task 4 `CouponService.getUsable`, Task 5 `PriceCalculator`, `Clock`
- Produces:
  - `ErrorCode.CART_ITEM_UNAVAILABLE(409, "구매할 수 없는 상품이 포함되어 있습니다.")`
  - `record CheckoutPreviewRequest(@NotEmpty @Size(max = 50) List<@NotNull Long> cartItemIds, Long memberCouponId, @PositiveOrZero long points)`. cartItemIds에 중복이 있으면 400이다.
  - `CheckoutService.preview(Long memberId, CheckoutPreviewRequest) → CheckoutPreviewResponse` (읽기 전용, 하나의 `now`):
    1. `getActive`
    2. `loadSelected`(다른 회원 줄 → `CART_ITEM_NOT_FOUND`)
    3. AVAILABLE이 아닌 줄이 있으면 `CART_ITEM_UNAVAILABLE`
    4. 쿠폰이 있으면 `getUsable`
    5. `points > balance`면 `POINTS_NOT_ENOUGH`
    6. `PriceCalculator.calculate`
  - Phase 6 주문 생성이 1~6을 재사용할 수 있게, 응답 DTO를 만들기 전 단계를 `CheckoutService.prepare(...) → Checkout(lines, memberCouponId, breakdown)`로 분리해 둔다.
  - `record CheckoutPreviewResponse(List<CheckoutLine> lines, PriceBreakdown price, long pointBalance)`
    - `record CheckoutLine(Long cartItemId, Long skuId, String productName, String skuName, String thumbnailUrl, int quantity, long unitPrice, long unitSalePrice, long lineTotal)`
  - API: `POST /api/members/me/checkout/preview` → 200

- [ ] **Step 1: 실패 테스트 작성** (`CheckoutPreviewApiTest`)
  - `선택한_장바구니_줄로_금액을_미리_본다`: 쿠폰·적립금 포함, breakdown 값 전부 확인. REST Docs `checkout/preview`
  - `장바구니_가격과_미리보기_가격이_같다`: 같은 줄의 `unitSalePrice`·`lineTotal`
  - `다른_회원의_장바구니_줄이면_404` (Review Focus 5)
  - `다른_회원의_쿠폰이면_404_COUPON_NOT_FOUND` (Review Focus 5)
  - `품절_줄이_있으면_409_CART_ITEM_UNAVAILABLE` (Review Focus 5)
  - `사용한_쿠폰이나_기간이_지난_쿠폰은_400_COUPON_NOT_USABLE`
  - `최소_주문금액_미달_쿠폰은_400_COUPON_NOT_APPLICABLE`
  - `잔액보다_많은_적립금은_409_POINTS_NOT_ENOUGH` (Review Focus 4)
  - `결제금액이_100원_미만이_되는_적립금은_400` (Review Focus 4)
  - `잘못된_요청은_400`: 빈 목록, 51개, 중복 id, 음수 points, null 원소
  - `토큰_없이_호출하면_401`
- [ ] **Step 2: 실패 확인 → Step 3: 구현.** `index.adoc`에 `== 주문서`를 추가하고, 금액 계산 순서를 표로 정리한다.
- [ ] **Step 4: 통과 확인.** `./gradlew clean :backend:build` 통과
- [ ] **Step 5: 커밋.** `feat: 주문서 금액 미리보기 API 추가`

---

## Phase 5 완료 기준
- `./gradlew clean :backend:build`가 통과한다(Phase 0~4 테스트 포함).
- REST Docs에 `== 적립금`, `== 쿠폰`, `== 관리자 - 쿠폰`, `== 주문서`가 있고, 회원 응답에 `grade`가 있다.
- Phase 2·4의 상세·장바구니 가격 응답이 바뀌지 않았다.
