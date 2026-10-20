# Phase 8 — 주문 운영(관리자 상태 변경·취소, 구매확정 적립, 등급 배치) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- 관리자가 주문을 조회하고 배송 상태(PAID→PREPARING→SHIPPING→DELIVERED)를 바꾸며, PAID·PREPARING 주문을 전체 취소할 수 있게 한다.
- 배송완료 주문은 사용자가 직접 구매확정하거나 7일 뒤 배치가 확정하고, 확정할 때 등급 적립률로 적립금을 준다.
- 매월 1일 배치가 직전 3개월 구매확정 금액으로 등급을 다시 매긴다.

**Architecture:**
- **잠금:** 상태를 바꾸는 모든 경로(관리자 상태 변경·취소, 사용자 구매확정, 자동 확정)는 회원 행 → 주문 순서로 잠근다. 주문번호로 회원 id를 스칼라로 읽고, 회원을 잠근 뒤 주문을 읽는다. 그래서 사용자 취소·결제 승인·만료와 직렬화된다.
- **구매확정:** `OrderConfirmer` 하나가 상태 변경과 적립을 한다. 사용자 API와 자동 확정 배치가 이것을 같이 쓴다.
- **취소:** Phase 7 `PaymentService.cancel`의 "토스 취소 → 결제 취소 기록 → `OrderCanceler`" 부분을 공용 메서드로 꺼내, 사용자 취소(USER)와 관리자 취소(ADMIN)가 같이 쓴다.
- **등급 배치:**
  - 회원 id 순서로 1,000명씩 끊어서 처리한다. 한 묶음은 쿼리 하나로 금액을 집계하고, Java의 `GradePolicy.gradeFor`로 등급을 정한 뒤, 바뀐 회원만 batch UPDATE한다.
  - 등급 규칙은 Java 한 곳에만 둔다(SQL CASE를 따로 두지 않는다).

**Tech Stack:** Phase 7과 같다. Flyway V17을 쓰고, `@Scheduled(cron, zone)`을 쓴다. 새 의존성은 없다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("주문 상태", "적립금", "등급", "스케줄러", "관리자", Phase 8 항목). 이월 노트의 Phase 8 항목(관리자 경로 잠금, PREPARING 이후 취소 규칙)을 반영한다.

## Global Constraints
- Phase 0~7 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, DTO는 `record`
  - 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속, setter 없음, id 참조
  - 에러는 `BusinessException(ErrorCode)`
  - 문서·주석은 평서체, 에러 메시지는 "~니다"체
  - 벌크 쿼리는 `@Modifying(flushAutomatically = true)`만 쓴다
  - FK가 걸린 행 잠금은 `FOR NO KEY UPDATE`
  - 시각은 `Clock` 빈
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 관리자 API는 `/api/admin/orders...`(이미 `hasRole(ADMIN)`), 회원 API는 `/api/members/me/orders...`다.
- **상태 전이:**
  - 관리자 상태 변경은 PAID→PREPARING, PREPARING→SHIPPING, SHIPPING→DELIVERED만 연다. DELIVERED로 바꿀 때 `delivered_at = now`다.
  - 대상이 이 세 상태가 아니면 400 `INVALID_INPUT`이다.
  - 현재 상태에서 그 전이가 안 되면 409 `INVALID_ORDER_STATUS_TRANSITION`이다(같은 상태로 다시 보내도 409).
  - 구매확정은 DELIVERED→CONFIRMED이고 `confirmed_at = now`다.
  - `OrderStatus.canTransitionTo`에 PREPARING→CANCELED를 추가한다. 사용자 취소는 PAID만 허용하는 기존 검사를 그대로 둔다.
- **관리자 취소:**
  - PAID·PREPARING에서만 전체 취소한다. 사유는 `CancelReason.ADMIN`, 토스 취소 사유는 고정값 `판매자 취소`다.
  - 이미 ADMIN으로 취소된 주문이면 토스를 부르지 않고 200이다.
  - 그 밖의 상태면 409 `ORDER_NOT_CANCELABLE`, 토스 실패는 502 `PAYMENT_GATEWAY_ERROR`(DB는 그대로)다.
- **구매확정:**
  - 사용자 확정은 DELIVERED에서만 된다. 이미 CONFIRMED면 200(멱등)이고, 그 밖의 상태면 409 `ORDER_NOT_CONFIRMABLE`이다.
  - 자동 확정은 `delivered_at + 7일 <= now`인 DELIVERED 주문이다. 매일 03:00 `Asia/Seoul`(`@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")`)에 돈다.
- **적립:**
  - 적립 기준 금액은 `max(0, payment_amount − shipping_fee)`(`Order.earnBase()`)다.
  - 적립금은 `GradePolicy.earnPoints(확정 시점 회원 등급, 기준 금액)`이고, `PointService.earn(memberId, points, "구매확정 적립", orderId)`로 준다. 0원이면 원장이 생기지 않는다(기존 규칙).
- **등급:**
  - 매월 1일 00:00 `Asia/Seoul`(`cron = "0 0 0 1 * *"`)에 돈다.
  - 기간은 실행 월 기준 직전 3개 달력 월(서울 시각 `[실행월−3월 1일 00:00, 실행월 1일 00:00)`)이다. 예: 11월 1일 실행 → 8/1~10/31.
  - 그 기간에 `confirmed_at`이 있는 CONFIRMED 주문의 `earnBase` 합으로 `GradePolicy.gradeFor`를 계산한다.
  - 대상은 ACTIVE 회원 전체다. 확정 주문이 없으면 BABY다. 등급은 내려갈 수도 있다.
  - 회원 id 오름차순으로 1,000명씩 묶음마다 별도 트랜잭션이다. 같은 달을 다시 돌려도 결과가 같다.
- **새 ErrorCode:**
  - `INVALID_ORDER_STATUS_TRANSITION(409, "주문 상태를 바꿀 수 없습니다.")`
  - `ORDER_NOT_CONFIRMABLE(409, "구매확정할 수 없는 주문입니다.")`
- **스케줄러:** 새 스케줄러도 기존 `SchedulingConfig`(`app.scheduling.enabled`)를 따른다. 테스트는 배치 메서드를 직접 호출한다.

**의도적으로 하지 않는 것:**
- 송장번호·택배사, 알림, SHIPPING 이후 취소·반품·교환
- 관리자 회원 조회 → Phase 11
- 등급 변경 이력 테이블, 등급 상승 즉시 반영(월 배치로만 바꾼다)
- 구매확정 취소

## Review Focus
1. 관리자 상태 변경·취소와 사용자 취소가 같은 주문에 동시에 온다 → 토스 취소는 최대 1번이다. 결과는 (CANCELED 하나, 복원 1번)이거나 (PREPARING, 취소 409)뿐이다 → Task 3 동시성 테스트
2. 사용자 구매확정과 자동 확정이 같은 주문에 동시에 온다 → 적립은 1번(EARN 원장 1행)뿐이다 → Task 4 동시성 테스트
3. 적립금을 많이 써서 결제금액이 배송비보다 작은 주문(예: 결제 100원, 배송비 2,500원) → 적립 기준 0, 원장 없음, 예외 없음 → Task 1·4 테스트
4. 등급 기간 경계: 서울 시각 7/31 23:59와 8/1 00:00에 확정된 주문은 11월 배치에서 앞의 것은 빠지고 뒤의 것은 들어간다. UTC로 계산하면 9시간 어긋난다 → Task 5 테스트
5. 관리자가 없는 주문번호, 허용되지 않은 대상 상태(CANCELED·CONFIRMED·PAID), 같은 상태 반복으로 변경한다 → 400/404/409이고 상태는 그대로다 → Task 3 테스트

---

### Task 1: 스키마 + 주문 도메인 보강

**Files:**
- Create: `V17__order_operations.sql`
- Modify:
  - `order/domain/Order.java`, `OrderStatus.java`, `CancelReason.java`
  - `order/dto/OrderDetailResponse.java`(`deliveredAt`, `confirmedAt`)
  - `order/repository/OrderRepository.java`
  - REST Docs에서 OrderDetailResponse를 문서화하는 기존 테스트들. strict responseFields라서 새 필드를 추가해야 한다. `grep -rl "cancelReason" src/test`로 찾는다.
  - `index.adoc`의 주문 응답 필드 설명
- Test: `order/domain/OrderTest.java`, `OrderStatusTest.java`(추가), `order/repository/OrderRepositoryTest.java`(추가)

**Interfaces:**
- Produces:
  - **V17:**
    - `alter table orders add column delivered_at timestamptz, add column confirmed_at timestamptz`
    - 인덱스:
      - `(delivered_at) where status = 'DELIVERED'`
      - `(member_id, confirmed_at) where status = 'CONFIRMED'`
      - `(status, id desc)`(관리자 상태 필터 목록)
  - `OrderStatus.canTransitionTo`에 PREPARING→CANCELED를 추가한다.
  - `CancelReason.ADMIN`(주석: 판매자가 결제 완료·상품 준비 중 주문을 취소했다)
  - `Order`:
    - `void advance(OrderStatus next, Instant now)`
      - `next`가 PREPARING·SHIPPING·DELIVERED가 아니면 `IllegalArgumentException`이다.
      - 전이가 안 되면 `IllegalStateException`이다.
      - DELIVERED면 `deliveredAt = now`다.
    - `void confirm(Instant now)`: DELIVERED가 아니면 `IllegalStateException`이고, 성공하면 `confirmedAt = now`다.
    - `long earnBase()` = `Math.max(0, paymentAmount − shippingFee)`
    - getter `getDeliveredAt()`, `getConfirmedAt()`
  - `OrderRepository`:
    - `Optional<Long> findMemberIdByOrderNumber(String orderNumber)`(스칼라)
    - `Optional<Order> findByOrderNumber(String orderNumber)`
    - `List<Long> findConfirmDueIds(Instant deliveredBefore, Long afterId, Limit limit)`: DELIVERED이고 `deliveredAt <= :deliveredBefore`이고 `id > afterId`인 주문을 id 오름차순으로
  - `OrderDetailResponse`에 `Instant deliveredAt, Instant confirmedAt`을 `cancelReason` 뒤에 추가한다.

- [ ] **Step 1: 실패 테스트 작성**
  - `OrderStatusTest`: 전이 표에서 PREPARING→CANCELED가 true다(기존 7×7 표 갱신).
  - `OrderTest`
    - `관리자는_결제_완료부터_배송_완료까지_한_단계씩_바꾼다`: 배송완료 시각 기록
    - `건너뛰거나_되돌리는_변경은_IllegalStateException`: PAID→SHIPPING, SHIPPING→PREPARING, 같은 상태
    - `허용하지_않는_대상_상태는_IllegalArgumentException`: CANCELED·CONFIRMED·PAID
    - `배송_완료만_구매확정된다`
    - `적립_기준은_결제금액에서_배송비를_뺀_값이고_음수면_0이다`: 22,500/2,500 → 20,000, 100/2,500 → 0 (Review Focus 3)
  - `OrderRepositoryTest`: `findConfirmDueIds`의 경계(정확히 7일 전은 포함, 1ms 늦으면 제외), DELIVERED가 아닌 주문 제외, `findMemberIdByOrderNumber`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**(REST Docs 필드 갱신 포함)
- [ ] **Step 5: 커밋.** `feat: 주문 배송·구매확정 상태와 시각 추가`

---

### Task 2: 관리자 주문 목록·상세 API

**Files:**
- Create:
  - `order/service/AdminOrderService.java`, `order/controller/AdminOrderController.java`
  - `order/dto/AdminOrderSummary.java`, `AdminOrderPage.java`, `AdminOrderDetailResponse.java`
- Modify: `order/repository/OrderRepository.java`(관리자 목록 쿼리), `index.adoc`(`== 관리자 - 주문`, `=== 주문 목록(관리자)`, `=== 주문 상세(관리자)`)
- Test: `order/controller/AdminOrderQueryApiTest.java`

**Interfaces:**
- Consumes: Task 1, Phase 6 `OrderItemRepository.findByOrderIdInOrderByIdAsc`·`OrderDetailResponse.of`, Phase 7 `PaymentRepository.findByOrderId`
- Produces:
  - `GET /api/admin/orders?status=&cursor=&size=`
    - 최신순(id 내림차순)이고, `status`는 선택(`OrderStatus` 이름, 틀리면 400)이다.
    - 커서와 size 규칙은 회원 주문 목록(`OrderService.list`)과 같다(기본 20, 1~50, 커서 최대 20자).
    - 응답 `record AdminOrderPage(List<AdminOrderSummary> items, String nextCursor)`
    - `record AdminOrderSummary(String orderNumber, OrderStatus status, Long memberId, String memberEmail, Instant orderedAt, String firstProductName, int itemCount, long paymentAmount)`
    - 회원 이메일과 주문상품은 페이지 단위로 한 번에 읽는다(N+1 없음).
  - `GET /api/admin/orders/{orderNumber}` → `record AdminOrderDetailResponse(OrderDetailResponse order, Long memberId, String memberEmail, AdminPayment payment)`
    - `record AdminPayment(String paymentKey, String method, long amount, Instant approvedAt, Instant canceledAt)`이고, 결제 전이면 null이다.
    - 없는 주문번호면 404 `ORDER_NOT_FOUND`다.

- [ ] **Step 1: 실패 테스트 작성**
  - `상태로_걸러_최신순으로_읽는다`: PAID 2건, PENDING 1건, `status=PAID`, size 1 → 커서로 2페이지. REST Docs `admin-order/list`
  - `목록_쿼리_수는_주문_수와_상관없다`: 2건과 10건
  - `상세에_주문자와_결제_정보가_나온다`: REST Docs `admin-order/detail`. 결제 전 주문은 payment가 null이다.
  - `없는_주문번호면_404`, `잘못된_상태_필터는_400`
  - `일반_회원은_403`, `토큰_없이_호출하면_401`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인**
- [ ] **Step 5: 커밋.** `feat: 관리자 주문 목록과 상세 API 추가`

---

### Task 3: 관리자 상태 변경·취소 API

**Files:**
- Modify:
  - `order/service/AdminOrderService.java`, `order/controller/AdminOrderController.java`
  - `payment/service/PaymentService.java`: 취소 공용부를 `cancelPaid(Order order, CancelReason reason, String tossReason, Instant now)`로 꺼내고 `adminCancel`을 추가한다.
  - `ErrorCode.java`(`INVALID_ORDER_STATUS_TRANSITION`)
  - `index.adoc`(`=== 주문 상태 변경(관리자)`, `=== 주문 취소(관리자)`)
- Create: `order/dto/AdminOrderStatusRequest.java`
- Test: `order/controller/AdminOrderCommandApiTest.java`, `order/service/AdminOrderConcurrencyTest.java`

**Interfaces:**
- Consumes: Task 1 `Order.advance`·`findMemberIdByOrderNumber`·`findByOrderNumber`·`CancelReason.ADMIN`, Phase 7 `PaymentGateway.cancel`·`Payment.cancel`·`FakePaymentGateway`, Phase 6 `OrderCanceler`, `MemberRepository.findWithLockById`
- Produces:
  - 잠금 헬퍼(AdminOrderService 안): `findMemberIdByOrderNumber` → 없으면 `ORDER_NOT_FOUND` → `memberRepository.findWithLockById` → `findByOrderNumber`
  - `PATCH /api/admin/orders/{orderNumber}/status`, 본문 `record AdminOrderStatusRequest(@NotNull OrderStatus status)` → 200 `AdminOrderDetailResponse`
    - `@Transactional`이고 하나의 `now`다.
    - `IllegalArgumentException` → `INVALID_INPUT`, `IllegalStateException` → `INVALID_ORDER_STATUS_TRANSITION`으로 바꾼다. 서비스에서 먼저 검사해서 BusinessException으로 던져도 된다.
  - `POST /api/admin/orders/{orderNumber}/cancel` → 200 `AdminOrderDetailResponse`
    - `PaymentService.adminCancel(String orderNumber)`: 잠금 헬퍼 → ADMIN 멱등 → PAID·PREPARING이 아니면 `ORDER_NOT_CANCELABLE` → `cancelPaid(order, ADMIN, "판매자 취소", now)`
    - 사용자 `cancel`은 `cancelPaid(order, USER, "고객 요청", now)`를 쓰고, 동작과 문서는 바뀌지 않는다.

- [ ] **Step 1: 실패 테스트 작성**
  - `AdminOrderCommandApiTest`
    - `결제_완료부터_배송_완료까지_바꾼다`: 각 응답의 status, 마지막에 deliveredAt. REST Docs `admin-order/status`
    - `허용하지_않는_대상_상태는_400이고_그대로다`: CANCELED·CONFIRMED·PAID (Review Focus 5)
    - `건너뛰거나_같은_상태로_바꾸면_409` (Review Focus 5)
    - `상품_준비_중_주문을_관리자가_취소하면_토스_취소_후_모두_되돌린다`
      - CANCELED/ADMIN, payment CANCELED
      - 재고·쿠폰·적립금 복원
      - 토스 취소 1회(사유 `판매자 취소`)
      - REST Docs `admin-order/cancel`
    - `다시_취소하면_토스를_부르지_않고_200`
    - `배송_중_주문은_취소_409`
    - `토스_취소가_실패하면_502이고_그대로다`
    - `없는_주문번호면_404` (Review Focus 5)
    - `일반_회원은_403`
    - 사용자 취소 회귀 테스트(`OrderCancelApiTest`)가 그대로 통과해야 한다.
  - `AdminOrderConcurrencyTest` (Review Focus 1)
    - `관리자_취소와_사용자_취소가_동시에_와도_토스_취소와_복원은_한_번이다`
    - `관리자_상품_준비와_사용자_취소가_동시에_와도_상태가_일관된다`: (CANCELED/USER, 복원 1회)이거나 (PREPARING, 사용자 409)
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인**
- [ ] **Step 5: 커밋.** `feat: 관리자 주문 상태 변경과 취소 API 추가`

---

### Task 4: 구매확정(사용자 + 7일 자동)과 적립

**Files:**
- Create: `order/service/OrderConfirmer.java`, `order/service/OrderConfirmService.java`, `order/service/OrderAutoConfirmScheduler.java`
- Modify: `order/controller/OrderController.java`, `ErrorCode.java`(`ORDER_NOT_CONFIRMABLE`), `index.adoc`(`=== 구매확정`, 적립 규칙, 자동 확정)
- Test: `order/controller/OrderConfirmApiTest.java`, `order/service/OrderAutoConfirmTest.java`

**Interfaces:**
- Consumes: Task 1 `Order.confirm`·`earnBase`·`findConfirmDueIds`, Phase 5 `GradePolicy.earnPoints`·`PointService.earn`, `MemberRepository.findWithLockById`
- Produces:
  - `OrderConfirmer`(`@Component`): `void confirm(Order order, Member member, Instant now)`
    - 호출자가 회원 잠금과 쓰기 트랜잭션을 가지고 있어야 한다.
    - `order.confirm(now)` → `pointService.earn(memberId, gradePolicy.earnPoints(member.getGrade(), order.earnBase()), "구매확정 적립", orderId)`
  - `OrderConfirmService`:
    - `OrderDetailResponse confirmByMember(Long memberId, String orderNumber)` (`@Transactional`)
      - `getActiveForUpdate` → `findByOrderNumberAndMemberId`(없으면 404)
      - CONFIRMED면 그대로 반환하고, DELIVERED가 아니면 `ORDER_NOT_CONFIRMABLE`이다.
    - `boolean autoConfirm(Long orderId, Instant now)` (`@Transactional`)
      - 순서는 만료와 같다: `findMemberIdById` → `findWithLockById` → 주문 로드
      - 아직 DELIVERED이고 `deliveredAt + 7일 <= now`면 확정하고 true다.
  - `OrderAutoConfirmScheduler.confirmDue() → int`
    - `@Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")`
    - `now` 하나로 `findConfirmDueIds(now − 7일, afterId, Limit.of(100))`를 keyset으로 반복한다.
    - 주문별 실패는 `warn` 로그(주문 id, 원인)를 남기고 계속한다.
  - API: `POST /api/members/me/orders/{orderNumber}/confirm` → 200 `OrderDetailResponse`

- [ ] **Step 1: 실패 테스트 작성**
  - `OrderConfirmApiTest`
    - `배송_완료_주문을_구매확정하면_등급_적립률로_적립한다`: BABY(0.5%), 기준 20,000 → 100원 EARN(order_id), confirmedAt. REST Docs `order/confirm`
    - `GOLD_회원은_3퍼센트다`: SQL로 등급 변경
    - `결제금액이_배송비보다_작으면_적립하지_않는다` (Review Focus 3)
    - `다시_확정하면_200이고_적립은_한_번이다`
    - `배송_완료가_아니면_409_ORDER_NOT_CONFIRMABLE`, `다른_회원의_주문이면_404`, `토큰_없이_호출하면_401`
  - `OrderAutoConfirmTest`(`MutableClock`)
    - `배송_완료_7일_뒤에_자동으로_확정하고_적립한다`
    - `7일이_안_됐으면_그대로다`
    - `100건을_넘어도_모두_확정한다`
    - `한_주문이_실패해도_나머지는_확정한다`
    - `사용자_확정과_자동_확정이_동시에_와도_적립은_한_번이다` (Review Focus 2)
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인**
- [ ] **Step 5: 커밋.** `feat: 구매확정과 7일 자동 확정 적립 추가`

---

### Task 5: 월간 등급 배치

**Files:**
- Create: `member/service/GradeRecalculationService.java`, `member/service/GradeRecalculationScheduler.java`, `member/repository/GradeBatchRepository.java`(JdbcTemplate)
- Modify: `index.adoc`(회원 등급 설명: 산정 기간·금액·배치 시각)
- Test: `member/service/GradeRecalculationTest.java`

**Interfaces:**
- Consumes: Task 1 `confirmed_at`·`earnBase` 규칙, Phase 5 `GradePolicy.gradeFor`
- Produces:
  - `GradeBatchRepository`:
    - `List<Long> findActiveMemberIds(Long afterId, int limit)`: ACTIVE, id 오름차순
    - `Map<Long, Long> sumConfirmedAmounts(Collection<Long> memberIds, Instant from, Instant to)`
      - `sum(greatest(payment_amount − shipping_fee, 0))`
      - 조건은 status = CONFIRMED이고 `from <= confirmed_at < to`
      - 결과에 없는 회원은 0이다.
    - `int updateGrades(Map<Long, Grade> grades, Instant now)`: `jdbcTemplate.batchUpdate`로 `update member set grade = ?, updated_at = ? where id = ? and grade <> ?`를 id 오름차순으로 실행한다.
  - `GradeRecalculationService.recalculate(YearMonth runMonth) → int`(바뀐 회원 수)
    - 기간: `runMonth.minusMonths(3).atDay(1)`~`runMonth.atDay(1)`, 서울 자정을 `Instant`로 바꾼다.
    - 1,000명 묶음마다 `TransactionTemplate`으로 별도 트랜잭션이다.
  - `GradeRecalculationScheduler.run()`: `@Scheduled(cron = "0 0 0 1 * *", zone = "Asia/Seoul")`이고, `YearMonth.now(clock.withZone(서울))`로 `recalculate`를 부른다.

- [ ] **Step 1: 실패 테스트 작성** (`GradeRecalculationTest`)
  - `직전_3개월_확정_금액으로_등급을_다시_매긴다`: 회원 A 20만 → GREEN, B 확정 없음 → BABY(PINK에서 내려감), C 80만 → GOLD
  - `기간은_서울_시각_달력_월이다` (Review Focus 4): 11월 실행
    - 서울 7/31 23:59 확정(UTC 7/31 14:59)은 제외된다.
    - 서울 8/1 00:00 확정(UTC 7/31 15:00)은 포함된다.
    - 서울 10/31 23:59는 포함, 11/1 00:00은 제외된다.
  - `취소_주문과_확정_전_주문은_세지_않는다`
  - `1000명을_넘어도_모두_처리한다`: 회원 2,500명은 JdbcTemplate로 빠르게 넣는다.
  - `같은_달을_다시_돌려도_결과가_같고_바뀐_회원은_0이다`
  - `탈퇴_회원은_건드리지_않는다`
  - `스케줄러는_서울_기준_실행_월로_부른다`: MutableClock 서울 11/1 00:00(UTC 10/31 15:00) → 11월로 계산한다.
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인.** `./gradlew clean :backend:build`
- [ ] **Step 5: 커밋.** `feat: 매월 직전 3개월 구매확정 금액으로 등급을 다시 매기는 배치 추가`

---

## Phase 8 완료 기준
- `./gradlew clean :backend:build`가 통과한다(Phase 0~7 포함).
- REST Docs에 `== 관리자 - 주문`(목록·상세·상태 변경·취소)과 `=== 구매확정`이 있고, 회원 등급 산정 규칙이 문서에 있다.
- 이월 노트에 Phase 9 항목을 남긴다: 리뷰는 CONFIRMED 주문상품만 쓸 수 있다(`confirmed_at` 존재). 랭킹 판매량은 취소 제외 기준이다.
