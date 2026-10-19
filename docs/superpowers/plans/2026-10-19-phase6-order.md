# Phase 6 — 주문(생성·만료·취소 복원) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 장바구니에서 결제대기(PENDING) 주문을 만들면서 재고·쿠폰·적립금을 먼저 차감하고, 15분 안에 결제하지 않은 주문은 스케줄러가 취소하며 모두 되돌린다. 재고 락 3종을 같은 시나리오로 비교해 채택 근거를 문서로 남긴다.

**Architecture:**
- **주문 생성 트랜잭션:**
  - 하나의 쓰기 트랜잭션에서 회원 행 잠금 → 이전 대기 주문 취소 → 배송지 스냅샷 → `CheckoutService.prepare`(Phase 5) → 주문·주문상품 저장 → SKU id 순서로 재고 조건부 차감 → 쿠폰 사용 → 적립금 사용 순서로 처리한다.
  - 어느 단계에서 실패해도 전부 롤백된다(이전 대기 주문의 취소도 함께 롤백된다).
- **재고:**
  - 운영 코드는 조건부 UPDATE(`adjustStock`) 하나만 쓴다.
  - 비관적 락과 낙관적 락+재시도는 테스트 소스에만 비교용으로 둔다.
- **취소 복원:** `OrderCanceler` 하나가 재고·쿠폰·적립금을 되돌린다. 만료 스케줄러와 "새 주문의 이전 대기 주문 대체"가 함께 쓰고, Phase 7의 결제 취소도 이것을 쓴다.
- **잠금 순서:** 회원 → 주문 → SKU(id 오름차순)다. 관리자 재고 경로(상품 → SKU 한 행)와 순환이 생기지 않는다.

**Tech Stack:** Phase 5와 같다(Spring Boot 3.5.16, Flyway V15~V16, Testcontainers, REST Docs, `@Scheduled`). 새 의존성은 없다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("주문 상태", "동시성 지점", "스케줄러", Phase 6 항목). 이월 노트(`phase-carryover-notes`)의 Phase 6 항목을 모두 반영한다.

## Global Constraints
- Phase 0~5 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, DTO는 `record`
  - 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속, setter 없음, 연관관계 대신 id 컬럼으로 참조
  - 에러는 `BusinessException(ErrorCode)`
  - 문서·주석·REST Docs 설명은 평서체, 에러 메시지는 "~니다"체
  - 트랜잭션 안에서 무결성 예외를 잡아 무시하지 않는다
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 새 코드 위치: `com.sprint.mission.order`(controller/service/domain/repository/dto)
- 회원 API는 `/api/members/me/orders...`다.
- **시각:** 모든 시각은 `Clock` 빈에서 가져온다. 한 요청과 한 만료 처리는 하나의 `now`를 쓴다.
- **벌크 UPDATE 규칙:**
  - `@Modifying`에는 `flushAutomatically = true`만 쓰고 `clearAutomatically`는 쓰지 않는다.
  - 바뀐 값은 스칼라 쿼리로 다시 읽는다.
  - 재고를 엔티티 필드로 바꾸는 코드는 만들지 않는다(`Sku`는 `@DynamicUpdate`).
- **주문 규칙:**
  - 결제 기한은 생성 시각 + 15분(`Order.PAYMENT_WINDOW = Duration.ofMinutes(15)`)이고, `expires_at`에 저장한다.
  - `expires_at <= now`이면 만료다.
  - 회원당 PENDING 주문은 최대 1개다. 새 주문은 이전 PENDING 주문을 `REPLACED`로 취소한다. DB의 부분 unique 인덱스가 마지막 방어선이다.
  - 주문 진입점은 장바구니(`cartItemIds`)뿐이다. 장바구니 줄은 Phase 6에서 지우지 않는다(Phase 7 결제 승인 때 지운다).
  - 배송지는 `addressId`로 받아 받는 사람·연락처·우편번호·주소1·주소2를 주문에 복사한다. 배송 메모는 없다.
  - 주문번호는 `yyyyMMdd`(Asia/Seoul) + `-` + `SecureRandom`으로 뽑은 `[0-9A-Z]` 8자다(예: `20261019-7K3Q9ZP2`). 토스 orderId 규칙(6~64자, 영숫자·`-`·`_`)을 만족해야 하고, 컬럼은 unique다.
- **잠금 순서:**
  - 주문을 만들거나 취소·만료하는 모든 경로는 먼저 회원 행을 잠근다(`MemberService.getActiveForUpdate` 또는 `MemberRepository.findWithLockById`).
  - 그다음 SKU를 id 오름차순으로 차감하거나 복원한다.
  - 같은 회원의 쿠폰 상태 변경은 회원 잠금 아래에서만 일어나므로, `MemberCoupon`은 엔티티 메서드(변경 감지)로 바꾼다.
- **스케줄러:**
  - `@EnableScheduling`은 `app.scheduling.enabled`(기본 true)로 켜고 끈다. 테스트 프로필은 false로 두고, 테스트는 만료 처리 메서드를 직접 호출한다.
  - 만료는 1분마다(`fixedDelay` 60초) 돈다.
- **탈퇴 정책 (이월 결정, 사용자 확정):**
  - PENDING·PAID·PREPARING·SHIPPING·DELIVERED 주문이 있으면 탈퇴를 거부한다(409).
  - 탈퇴할 때 장바구니·찜·최근 본 상품을 지운다. 주문·적립금 기록은 남긴다. 개인정보는 마스킹하지 않는다.
- **새 ErrorCode:**
  - `ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다.")`
  - `MEMBER_HAS_ACTIVE_ORDER(409, "진행 중인 주문이 있어 탈퇴할 수 없습니다.")`
  - 재고 부족은 기존 `STOCK_NOT_ENOUGH`를 쓴다.

**의도적으로 하지 않는 것:**
- 사용자 취소 API와 토스 결제 취소 → Phase 7(이번에는 `OrderCanceler`만 만든다)
- 장바구니 줄 삭제 → Phase 7 결제 승인
- 관리자 주문 조회·상태 변경, 자동 구매확정 → Phase 8
- 바로구매, 배송 메모, 부분 취소, 주문 상태 필터
- 운영 코드의 비관적·낙관적 재고 구현과 운영 스키마의 `sku.version` 컬럼(Task 2 Ruling 참고)

## Review Focus
1. 대기 주문이 있는 회원이 같은 쿠폰·적립금으로 다시 주문한다 → 이전 주문이 취소되고 새 주문이 같은 쿠폰을 쓴다. 부분 unique 인덱스 위반(500)이 나지 않는다. 새 주문이 실패하면 이전 주문은 그대로 PENDING이다 → Task 4 테스트
2. 재고 50인 SKU를 100명이 동시에 1개씩 주문한다 → 정확히 50건 성공, 재고 0, 음수 0건이다. 두 회원이 A·B 두 SKU를 동시에 주문해도 데드락(40P01) 없이 끝난다 → Task 4 동시성 테스트
3. 만료 경계: 14분 59초에는 그대로, 15분에는 CANCELED이고 재고·쿠폰·적립금이 복원된다. 재고가 99,999일 때 2개 복원 → 100,001이 된다(상한 없음) → Task 5 테스트
4. 만료 처리와 같은 회원의 새 주문이 동시에 일어난다 → 복원이 두 번 되거나 빠지지 않는다 → Task 5 동시성 테스트
5. 다른 회원의 주문번호·배송지로 조회하거나 주문한다 → 존재를 드러내지 않는 404다 → Task 4·6 테스트

---

### Task 1: 기반 정리 (auditing 시계, 재고 쿼리)

**Files:**
- Modify: `global/config/JpaConfig.java`, `product/repository/SkuRepository.java`, `product/service/AdminSkuService.java`
- Test:
  - `support/BaseEntityAuditingTest.java`(추가)
  - `product/repository/SkuRepositoryTest.java`(새로, `IntegrationTest`)

**Interfaces:**
- Produces:
  - `@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")`와 `DateTimeProvider auditingDateTimeProvider(Clock clock)` 빈. `Optional.of(clock.instant())`를 돌려준다.
  - `SkuRepository.adjustStock(Long id, int delta, Instant now)`: `clearAutomatically`를 없애고 `@Modifying(flushAutomatically = true)`만 둔다. 동작은 그대로다.
  - `SkuRepository.findStockById(Long id) → Optional<Integer>`(스칼라 JPQL). `AdminSkuService.adjustStock`의 재조회를 이것으로 바꾼다.
  - `SkuRepository.restoreStock(Long id, int quantity, Instant now) → int`:
    - `update Sku s set s.stock = s.stock + :quantity, s.updatedAt = :now where s.id = :id`
    - 상한이 없다. `quantity <= 0`이면 `IllegalArgumentException`이다(기본 메서드로 검사하거나 서비스가 검사한다).

- [ ] **Step 1: 실패 테스트 작성**
  - `BaseEntityAuditingTest`: `생성_수정_시각은_Clock_빈을_따른다`. `@TestConfiguration`의 `@Primary MutableClock`을 2030-01-01T00:00:00Z로 맞춘다. `MutableClock`에 `set(Instant)`가 없으면 추가한다. 저장한 `createdAt`이 그 시각과 같아야 한다.
  - `SkuRepositoryTest`
    - `재고_조정_뒤에도_같은_트랜잭션의_엔티티가_분리되지_않는다`: `TransactionTemplate` 안에서 Sku를 읽고 `adjustStock` → `entityManager.contains(sku)`가 true다.
    - `복원은_상한_없이_더한다`: 재고 99,999 + `restoreStock(2)` → 100,001
    - `adjustStock은_여전히_100000을_넘지_못한다`
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*BaseEntityAuditingTest' --tests '*SkuRepositoryTest'` → 실패(시각 불일치, `contains` false, 메서드 없음)
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인.** 전체 `./gradlew :backend:test` 통과(Phase 2 재고 API 테스트 포함)
- [ ] **Step 5: 커밋.** `refactor: auditing을 Clock에 연결하고 재고 쿼리에서 영속성 컨텍스트 비우기를 제거`

---

### Task 2: 재고 락 3종 비교 (테스트 전용 구현 + 결과 문서)

**Ruling (계획 단계, 사용자 확정):**
- 운영 코드는 조건부 UPDATE만 쓴다.
- 낙관적 락용 `version` 컬럼은 테스트 마이그레이션에만 둔다. 설계서의 "SKU(…, version)"는 비교 실험용 컬럼으로 해석한다.
- 틀렸을 때 비용: 낙관적 락을 채택하게 되면 운영 마이그레이션 하나를 추가하면 된다.

**Files:**
- Create (test 소스만):
  - `src/test/resources/db/testmigration/V901__sku_version.sql`: `alter table sku add column version bigint not null default 0`
  - `src/test/java/com/sprint/mission/product/stock/StockDeductor.java`
  - `PessimisticStockDeductor.java`, `OptimisticStockDeductor.java`, `ConditionalUpdateStockDeductor.java`
  - `StockLockComparisonTest.java`
- Create: `docs/performance/stock-lock-comparison.md`

**Interfaces:**
- Consumes: Task 1 `SkuRepository.adjustStock`
- Produces (테스트 전용):
  - `interface StockDeductor { boolean deduct(Long skuId, int quantity); }`: 성공하면 true, 재고가 부족하면 false다.
  - 비관적: `TransactionTemplate` 안에서 `select stock from sku where id = ? for update` → 부족하면 false → `update sku set stock = stock - ? where id = ?`
  - 낙관적:
    - 트랜잭션 없이 `select stock, version` → 부족하면 false
    - `update sku set stock = ?, version = version + 1 where id = ? and version = ?`
    - 0행이면 1~5ms 무작위로 쉬고 다시 시도한다. 최대 100회다.
    - 시도 횟수 합계를 `AtomicLong retries`로 센다. 100회를 넘기면 `IllegalStateException`이다.
  - 조건부 UPDATE: `TransactionTemplate` 안에서 `skuRepository.adjustStock(id, -quantity, now) == 1`

- [ ] **Step 1: 비교 테스트 작성** (`StockLockComparisonTest`, `IntegrationTest`)
  - `@ParameterizedTest`로 세 구현을 돌린다. 세 구현 모두 `재고_100에_200명이_1개씩_빼면_정확히_100명만_성공한다`를 통과해야 한다.
  - 결과 검증: 성공 100, 실패 100, 최종 재고 0, 차감 중 예외 0
  - 측정: 구현별 소요 시간(ms), 낙관적 재시도 합계. `System.out`에 `| 방식 | 소요(ms) | 재시도 |` 표 한 줄로 출력한다. 시간은 검증하지 않는다.
  - 스레드는 200개, 시작은 `CountDownLatch` 하나로 맞춘다(기존 `StockAdjustConcurrencyTest` 패턴).
- [ ] **Step 2: 실패 확인 → Step 3: 구현.** 테스트 전용 구현 3개
- [ ] **Step 4: 통과 확인.** 같은 테스트를 3번 실행해 결과 표를 모은다.
- [ ] **Step 5: 문서 작성** (`docs/performance/stock-lock-comparison.md`, 평서체)
  - 실험 환경: 로컬 Testcontainers PostgreSQL 17, Hikari 기본 풀 10, 스레드 200
  - 3회 결과 표
  - 방식별 장단점: 트랜잭션 길이, 재시도 비용, 다중 SKU 데드락 위험, 코드량
  - 채택 이유: 단일 행 차감은 조건부 UPDATE 한 문장이 행 잠금을 가장 짧게 잡는다. 여러 SKU는 id 순서로 차감해 데드락을 막는다.
- [ ] **Step 6: 커밋.** `test: 재고 락 3종 비교 실험과 결과 문서 추가`

---

### Task 3: 주문 도메인 + 스키마

**Files:**
- Create:
  - `V15__create_orders.sql`
  - `order/domain/Order.java`, `OrderItem.java`, `OrderStatus.java`, `CancelReason.java`, `ShippingInfo.java`(`@Embeddable`), `OrderNumberGenerator.java`
  - `order/repository/OrderRepository.java`, `OrderItemRepository.java`
- Modify: `coupon/domain/MemberCoupon.java`(`use`, `restore`)
- Test: `order/domain/OrderStatusTest.java`, `OrderTest.java`, `OrderNumberGeneratorTest.java`, `coupon/domain/MemberCouponTest.java`, 그리고 `DatabaseMigrationTest`(있으면 테이블 목록 갱신)

**Interfaces:**
- Produces:
  - **V15 `orders` 테이블:**
    - `id`, `order_number varchar(20) not null unique`, `member_id → member`, `status varchar(20)`
    - 금액 7개 `bigint not null`: `items_price`, `sale_discount`, `items_sale_price`, `coupon_discount`, `shipping_fee`, `points_used`, `payment_amount`. CHECK(`payment_amount >= 100`, `points_used >= 0`)
    - 쿠폰: `member_coupon_id → member_coupon`(null 가능), `coupon_name varchar(50)`(null 가능)
    - 배송지: `recipient varchar(30)`, `phone varchar(11)`, `zip_code char(5)`, `address1 varchar(200)`, `address2 varchar(200)` null 가능
    - 시각·사유: `expires_at timestamptz not null`, `canceled_at timestamptz`, `cancel_reason varchar(20)`, 그리고 `created_at`, `updated_at`
    - 인덱스:
      - `(member_id, id desc)`
      - `(expires_at) where status = 'PENDING'`
      - `unique (member_id) where status = 'PENDING'`
  - **V15 `order_item` 테이블:**
    - `id`, `order_id → orders`, `product_id → product`, `sku_id → sku`
    - 스냅샷: `product_name varchar(100)`, `sku_name varchar(50)`, `thumbnail_url varchar(500)`
    - `unit_price`, `unit_sale_price`, `quantity int check 1~99`, `line_total`, `created_at`, `updated_at`
    - 인덱스 `(order_id)`
  - **V15 외래 키:** `point_history.order_id → orders`, `member_coupon.used_order_id → orders`. 기존 V13·V14 주석이 예고한 것이다.
  - **`enum OrderStatus { PENDING, PAID, PREPARING, SHIPPING, DELIVERED, CONFIRMED, CANCELED }`**
    - `boolean canTransitionTo(OrderStatus next)`
    - 허용 전이: PENDING→PAID|CANCELED, PAID→PREPARING|CANCELED, PREPARING→SHIPPING, SHIPPING→DELIVERED, DELIVERED→CONFIRMED. 나머지는 false다.
    - `static final Set<OrderStatus> IN_PROGRESS` = PENDING, PAID, PREPARING, SHIPPING, DELIVERED
  - **`enum CancelReason { EXPIRED, REPLACED }`**: Phase 7이 `USER`를 추가한다.
  - **`ShippingInfo(recipient, phone, zipCode, address1, address2)`**: `static ShippingInfo from(Address)`
  - **`Order`** (`@Table(name = "orders")`):
    - `static Order place(Long memberId, String orderNumber, PriceBreakdown price, Long memberCouponId, String couponName, ShippingInfo shipping, Instant now)`: PENDING, `expiresAt = now + PAYMENT_WINDOW`
    - `PriceBreakdown price()`: 저장된 7개 금액으로 다시 만든다.
    - `boolean isExpired(Instant now)`
    - `void cancel(CancelReason reason, Instant now)`: `canTransitionTo(CANCELED)`가 아니면 `IllegalStateException`이다.
  - **`OrderItem.create(Long orderId, Long productId, Long skuId, String productName, String skuName, String thumbnailUrl, long unitPrice, long unitSalePrice, int quantity)`**: `lineTotal = unitSalePrice × quantity`
  - **`OrderNumberGenerator`**(`@Component`, `Clock` 주입): `String next()`
  - **`MemberCoupon`:**
    - `void use(Long orderId, Instant now)`: AVAILABLE이 아니면 `IllegalStateException`
    - `void restore()`: USED → AVAILABLE, `usedOrderId`·`usedAt`을 null로. USED가 아니면 `IllegalStateException`
  - **`OrderRepository`:**
    - `Optional<Order> findByOrderNumberAndMemberId(String, Long)`
    - `Optional<Order> findByMemberIdAndStatus(Long, OrderStatus)`
    - `boolean existsByMemberIdAndStatusIn(Long, Collection<OrderStatus>)`
    - 커서용 `findByMemberIdOrderByIdDesc(Long, Limit)`, `findByMemberIdAndIdLessThanOrderByIdDesc(Long, Long, Limit)`
    - `List<Long> findDueIds(Instant now, Long afterId, Limit limit)`: PENDING이고 `expiresAt <= now`이고 `id > afterId`인 주문을 id 오름차순으로
    - `Optional<Long> findMemberIdById(Long)`
  - **`OrderItemRepository`:** `List<OrderItem> findByOrderIdOrderByIdAsc(Long)`, `List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long>)`

- [ ] **Step 1: 실패 테스트 작성**
  - `OrderStatusTest`
    - `허용된_전이만_가능하다`: 7×7 표 전체를 `@ParameterizedTest`나 표로 검증
    - `진행_중_상태는_다섯_개다`
  - `OrderTest`
    - `생성하면_PENDING이고_15분_뒤_만료된다`: 14분 59초 → false, 15분 → true
    - `취소된_주문을_다시_취소하면_IllegalStateException`
    - `가격_스냅샷을_그대로_돌려준다`
  - `OrderNumberGeneratorTest`: Clock 2026-10-18T15:30:00Z(서울 10-19 00:30) → `20261019-`로 시작하고, `^\d{8}-[0-9A-Z]{8}$`에 맞고, 1,000번 뽑아도 중복이 없다.
  - `MemberCouponTest`: 사용 → USED·orderId·usedAt, 복원 → AVAILABLE·null, 사용된 쿠폰 재사용은 `IllegalStateException`
  - 마이그레이션 테스트: `orders`와 `order_item`이 생기고, 같은 회원의 PENDING 두 건 insert는 unique 위반이다(JdbcTemplate).
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 주문 도메인과 주문 테이블 추가`

---

### Task 4: 주문 생성 API

**Files:**
- Create:
  - `order/service/OrderService.java`, `order/service/OrderCanceler.java`
  - `order/controller/OrderController.java`
  - `order/dto/OrderCreateRequest.java`, `OrderDetailResponse.java`, `OrderItemResponse.java`
- Modify:
  - `checkout/service/Checkout.java`, `CheckoutService.java`: `memberCouponId` 대신 `UsableCoupon coupon`(null 가능)
  - `ErrorCode.java`(`ORDER_NOT_FOUND`)
  - `index.adoc`(`== 주문`, `=== 주문 생성`)
- Test: `order/controller/OrderCreateApiTest.java`(`ApiTest`), `order/service/OrderConcurrencyTest.java`(`IntegrationTest`)

**Interfaces:**
- Consumes:
  - Task 1 `adjustStock`, `restoreStock`
  - Task 3 도메인·리포지토리
  - Phase 5 `CheckoutService.prepare`, `PointService.use/restore`, `MemberService.getActiveForUpdate`, `AddressRepository.findByIdAndMemberId`
- Produces:
  - `record Checkout(List<CartLine> lines, UsableCoupon coupon, PriceBreakdown breakdown, long pointBalance)`. 미리보기 응답은 바뀌지 않는다.
  - `record OrderCreateRequest(@NotEmpty @Size(max = 50) List<@NotNull Long> cartItemIds, Long memberCouponId, @PositiveOrZero long points, @NotNull Long addressId)`
  - `OrderCanceler`(`@Component`): `void cancel(Order order, CancelReason reason, Instant now)`
    - 호출자가 회원 잠금과 쓰기 트랜잭션을 가지고 있어야 한다.
    - `order.cancel` → 주문상품을 sku id 오름차순으로 `restoreStock` → 쿠폰이 있으면 `MemberCoupon.restore()` → `pointService.restore(memberId, pointsUsed, "주문 취소", orderId)`
  - `OrderService.create(Long memberId, OrderCreateRequest) → OrderDetailResponse` (`@Transactional`, 하나의 `now`):
    1. `memberService.getActiveForUpdate(memberId)`
    2. 이 회원의 PENDING 주문이 있으면 `orderCanceler.cancel(old, REPLACED, now)` 뒤 `orderRepository.flush()`. 새 주문 insert 전에 부분 unique 인덱스를 비워야 한다(IDENTITY insert는 대기 중인 UPDATE를 먼저 flush하지 않는다).
    3. 배송지: `findByIdAndMemberId` → 없으면 `ADDRESS_NOT_FOUND`
    4. `checkoutService.prepare(memberId, cartItemIds, memberCouponId, points, now)`. 여기서 나는 오류는 Phase 5 순서 그대로다.
    5. `Order.place(...)` 저장. 쿠폰 이름은 `checkout.coupon().name()`이다.
    6. 줄마다 `OrderItem` 저장(상품명·썸네일은 `line.card().summary()`, 옵션명은 `line.sku().getName()`)
    7. 줄을 sku id 오름차순으로 정렬해 `adjustStock(skuId, -quantity, now)`. 0행이면 `STOCK_NOT_ENOUGH`(전체 롤백)
    8. 쿠폰이 있으면 `memberCouponRepository.findById(...).use(orderId, now)`
    9. `pointService.use(memberId, pointsUsed, "주문 사용", orderId)`
  - 응답 DTO:
    - `record OrderDetailResponse(String orderNumber, OrderStatus status, Instant orderedAt, Instant expiresAt, List<OrderItemResponse> items, PriceBreakdown price, String couponName, ShippingInfo shipping, Instant canceledAt, CancelReason cancelReason)`
    - `record OrderItemResponse(Long productId, Long skuId, String productName, String skuName, String thumbnailUrl, long unitPrice, long unitSalePrice, int quantity, long lineTotal)`
  - API: `POST /api/members/me/orders` → 201, `Location: /api/members/me/orders/{orderNumber}`

- [ ] **Step 1: 실패 테스트 작성**
  - `OrderCreateApiTest`
    - `장바구니_줄로_결제대기_주문을_만든다`
      - 쿠폰·적립금 포함
      - 응답 금액이 같은 요청의 미리보기와 같다
      - DB 확인: 재고 감소, 쿠폰 USED·used_order_id, 적립금 잔액 감소와 `USE` 내역(order_id)
      - 장바구니 줄은 남아 있다
      - REST Docs `order/create`
    - `대기_주문이_있으면_이전_주문을_취소하고_같은_쿠폰으로_새로_만든다` (Review Focus 1): 이전 주문 CANCELED/REPLACED, 재고·적립금이 한 번만 차감된 상태
    - `새_주문이_실패하면_이전_대기_주문은_그대로다` (Review Focus 1): 두 번째 요청이 `POINTS_NOT_ENOUGH` → 이전 주문 PENDING, 쿠폰 USED 유지
    - `다른_회원의_배송지면_404_ADDRESS_NOT_FOUND` (Review Focus 5)
    - `다른_회원의_장바구니_줄이면_404`
    - `구매할_수_없는_줄이_있으면_409_CART_ITEM_UNAVAILABLE`
    - `판매_중지_상품이면_409_CART_ITEM_UNAVAILABLE`
    - `잘못된_요청은_400`: addressId null, 빈 목록, 51개, 중복 id, 음수 points
    - `토큰_없이_호출하면_401`
  - `OrderConcurrencyTest`
    - `재고_50에_100명이_동시에_주문하면_50건만_성공한다` (Review Focus 2): 성공 50, `STOCK_NOT_ENOUGH` 50, 재고 0, 실패한 회원의 쿠폰·적립금은 그대로
    - `두_SKU를_엇갈려_담은_주문이_동시에_와도_데드락_없이_끝난다` (Review Focus 2): 회원 20명이 A·B를 담는다(담은 순서를 반씩 다르게). 동시에 주문하고, 예외는 `BusinessException`뿐이며, 재고 = 시작 − 성공 수다.
    - `같은_회원이_동시에_두_번_주문해도_대기_주문은_하나다`
- [ ] **Step 2: 실패 확인 → Step 3: 구현.** `index.adoc`에 `== 주문`과 `=== 주문 생성`을 추가한다. 처리 순서와 오류 우선순위를 코드 순서대로 적고, "결제 기한 15분, 이전 대기 주문 대체, 장바구니는 결제 후 정리"를 설명한다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과(미리보기 테스트 포함)
- [ ] **Step 5: 커밋.** `feat: 재고·쿠폰·적립금을 선차감하는 주문 생성 API 추가`

---

### Task 5: 결제 기한 만료 스케줄러

**Files:**
- Create: `order/service/OrderExpiryService.java`, `order/service/OrderExpiryScheduler.java`, `global/config/SchedulingConfig.java`
- Modify: `application.yml`(`app.scheduling.enabled: true`), `src/test/resources/application-test.yml`(`false`)
- Test: `order/service/OrderExpiryTest.java`(`IntegrationTest` + `@Primary MutableClock`)

**Interfaces:**
- Consumes: Task 4 `OrderCanceler.cancel`, `OrderService.create`
- Produces:
  - `SchedulingConfig`: `@EnableScheduling` + `@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)`
  - `OrderExpiryService.expire(Long orderId, Instant now) → boolean` (`@Transactional`):
    1. `findMemberIdById`
    2. `memberRepository.findWithLockById`(탈퇴 여부와 상관없이 잠근다)
    3. 그다음에 주문을 읽는다. 잠금 전에 주문 엔티티를 읽지 않는다(낡은 상태를 보지 않게).
    4. 아직 PENDING이고 만료면 `orderCanceler.cancel(order, EXPIRED, now)` 뒤 true, 아니면 false
  - `OrderExpiryScheduler.expireDue() → int` (`@Scheduled(fixedDelay = 60_000)`):
    - `now` 하나로 `findDueIds(now, afterId, Limit.of(100))`를 `afterId`를 올려 가며 빈 목록까지 반복한다.
    - id마다 `expire`를 부르고, 예외는 로그(`warn`, 주문 id)만 남기고 다음 주문으로 넘어간다.
    - 취소한 건수를 돌려준다.

- [ ] **Step 1: 실패 테스트 작성** (`OrderExpiryTest`)
  - `15분이_지난_대기_주문을_취소하고_모두_되돌린다` (Review Focus 3): 재고·쿠폰 AVAILABLE·적립금 잔액, `RESTORE` 내역(order_id), status CANCELED, cancelReason EXPIRED, canceledAt = now
  - `14분_59초에는_취소하지_않는다` (Review Focus 3)
  - `한_번에_100건을_넘는_만료도_모두_처리한다`: 회원 150명(픽스처는 JdbcTemplate로 빠르게 넣어도 된다) → 150
  - `한_주문이_실패해도_나머지는_처리한다`: 주문 하나의 회원을 JdbcTemplate로 WITHDRAWN으로 바꿔 복원(`PointService.restore`의 `getActive`)이 실패하게 한다 → 나머지는 취소되고, 반환값은 성공 수다. 실패한 주문은 PENDING으로 남는다(전체 롤백).
  - `만료_처리와_새_주문이_동시에_와도_복원은_한_번이다` (Review Focus 4): 같은 회원에서 `expireDue`와 `create`를 동시에 실행 → 이전 주문은 EXPIRED나 REPLACED 중 하나로 한 번만 취소되고, 재고·적립금 합이 맞다.
  - `스케줄러는_테스트_프로필에서_꺼져_있다`: `OrderExpiryScheduler` 빈은 있지만 `ScheduledAnnotationBeanPostProcessor` 빈이 없다.
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 결제 기한이 지난 주문을 취소하는 스케줄러 추가`

---

### Task 6: 내 주문 목록·상세 API

**Files:**
- Modify: `order/service/OrderService.java`, `order/controller/OrderController.java`, `index.adoc`(`=== 내 주문 목록`, `=== 주문 상세`)
- Create: `order/dto/OrderSummary.java`, `OrderPage.java`
- Test: `order/controller/OrderQueryApiTest.java`

**Interfaces:**
- Consumes: Task 3 리포지토리, Task 4 `OrderDetailResponse`
- Produces:
  - `OrderService.list(Long memberId, String cursor, int size) → OrderPage`
    - 최신순이고, 커서는 마지막 주문 id의 10진수 문자열이다. 검증 규칙은 `PointService.parseCursor`와 같다.
    - 주문상품은 페이지 단위로 `findByOrderIdInOrderByIdAsc` 한 번에 읽는다.
  - `OrderService.get(Long memberId, String orderNumber) → OrderDetailResponse`: 남의 주문이나 없는 번호면 `ORDER_NOT_FOUND`
  - DTO:
    - `record OrderSummary(String orderNumber, OrderStatus status, Instant orderedAt, String firstProductName, String thumbnailUrl, int itemCount, long paymentAmount)`
    - `record OrderPage(List<OrderSummary> items, String nextCursor)`
  - API:
    - `GET /api/members/me/orders?cursor=&size=`: size 기본 20, 1~50, 커서 최대 20자. 제약 메시지는 `PointController`와 같다.
    - `GET /api/members/me/orders/{orderNumber}`

- [ ] **Step 1: 실패 테스트 작성**
  - `내_주문을_최신순으로_페이지_나눠_읽는다`: 3건, size 2 → 2건 + nextCursor → 1건 + null. REST Docs `order/list`
  - `목록_쿼리_수는_주문_수와_상관없다`: 2건과 10건(Hibernate statistics)
  - `주문_상세를_읽는다`: 생성 응답과 같은 본문. REST Docs `order/detail`
  - `취소된_주문은_취소_시각과_사유가_나온다`
  - `다른_회원의_주문번호면_404_ORDER_NOT_FOUND` (Review Focus 5)
  - `잘못된_커서나_size면_400`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인**
- [ ] **Step 5: 커밋.** `feat: 내 주문 목록과 상세 API 추가`

---

### Task 7: 탈퇴 정책

**Files:**
- Modify:
  - `member/service/MemberService.java`(`withdraw`)
  - `cart/repository/CartItemRepository.java`, `wishlist/repository/WishlistRepository.java`(회원 전체 삭제)
  - `ErrorCode.java`(`MEMBER_HAS_ACTIVE_ORDER`)
  - `index.adoc`(탈퇴 설명과 409)
- Test: `member/controller/MemberApiTest.java`(추가) 또는 `member/service/WithdrawTest.java`

**Interfaces:**
- Consumes: Task 3 `OrderRepository.existsByMemberIdAndStatusIn`, `OrderStatus.IN_PROGRESS`, `RecentViewRepository.deleteAll`
- Produces:
  - `withdraw`는 `getActiveForUpdate`로 회원을 잠근 뒤 비밀번호를 확인한다. 같은 회원의 주문 생성과 줄을 세운다.
  - 순서: 진행 중 주문이 있으면 `MEMBER_HAS_ACTIVE_ORDER` → 장바구니·찜·최근 본 상품 삭제 → 기존 탈퇴 처리(refresh token 폐기)
  - `CartItemRepository.deleteByMemberId(Long)`, `WishlistRepository.deleteAllByMemberId(Long)`: `@Modifying(flushAutomatically = true)` JPQL delete
  - 오류 순서: `PASSWORD_MISMATCH`가 `MEMBER_HAS_ACTIVE_ORDER`보다 먼저다.

- [ ] **Step 1: 실패 테스트 작성**
  - `대기_주문이_있으면_409_MEMBER_HAS_ACTIVE_ORDER`: 회원은 여전히 활성이다.
  - `취소된_주문만_있으면_탈퇴하고_장바구니_찜_최근_본_상품을_지운다`: 주문·적립금 내역 행은 남는다.
  - `비밀번호가_틀리면_주문이_있어도_400_PASSWORD_MISMATCH`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인.** `./gradlew clean :backend:build` 통과
- [ ] **Step 5: 커밋.** `feat: 진행 중 주문이 있으면 탈퇴를 막고 회원 데이터를 정리`

---

## Phase 6 완료 기준
- `./gradlew clean :backend:build`가 통과한다(Phase 0~5 테스트 포함).
- REST Docs에 `== 주문`(생성·목록·상세)이 있고, 탈퇴 설명에 409가 있다.
- `docs/performance/stock-lock-comparison.md`에 3종 결과 표와 채택 이유가 있다.
- 동시성 테스트에서 재고 음수·초과 판매가 0건이다.
- 이월 노트에 Phase 7 항목을 남긴다:
  - 결제 승인도 회원 → 주문 순서로 잠그고 PENDING·미만료를 다시 확인한다.
  - 승인 뒤 주문한 SKU의 장바구니 줄을 회원 잠금 아래에서 지운다.
  - 사용자 취소는 `CancelReason.USER`를 추가하고 `OrderCanceler`를 쓴다.
