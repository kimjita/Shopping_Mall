# Phase 7 — 결제(토스 승인·취소, 금액 검증, 멱등성) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 토스페이먼츠 결제창에서 돌아온 `paymentKey`로 서버가 금액을 검증하고 승인해 주문을 PAID로 바꾼다. 결제 완료 주문은 사용자가 전체 취소할 수 있고, 취소하면 토스 결제 취소와 재고·쿠폰·적립금 복원이 함께 일어난다.

**Architecture:**
- **외부 연동 경계:**
  - `PaymentGateway` 인터페이스(승인·주문번호로 조회·취소)와 RestClient 구현체 `TossPaymentClient`로 나눈다.
  - 서비스 테스트는 테스트용 `FakePaymentGateway`를 쓰고, `TossPaymentClient`는 WireMock으로 실제 HTTP·타임아웃을 테스트한다.
- **승인:**
  - 회원 잠금 → 주문 확인 → 금액 검증 → 토스 승인을 하나의 트랜잭션 안에서 한다. 토스 호출 동안 회원 잠금을 잡고 있으므로 만료 스케줄러·대체 취소가 끼어들 수 없다.
  - 승인 타임아웃이면 주문번호로 결제를 조회해 상태를 맞춘다.
  - 토스 승인 뒤 우리 쪽 처리(검증·저장·커밋)가 실패하면, 트랜잭션 밖에서 즉시 토스 취소로 보상한다.
- **취소:**
  - 회원 잠금 → PAID 확인 → 토스 취소 → `Payment` 취소 기록 → `OrderCanceler`(USER)로 복원한다.
  - 토스가 "이미 취소됨"이라고 답하면 성공으로 본다. 그래서 우리 쪽 저장이 실패해도 다시 요청하면 복구된다.

**Tech Stack:** Phase 6과 같다. Flyway V16과 Spring `RestClient`를 쓰고, 테스트 의존성 `org.wiremock:wiremock-standalone:3.9.2` 하나를 추가한다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("결제", "주문 상태", Phase 7 항목). 이월 노트의 Phase 7 항목(회원 → 주문 잠금과 재확인, 장바구니 정리, `CancelReason.USER`, `cancel(null)` 금지, 잠금 규칙)을 반영한다.

## Global Constraints
- Phase 0~6 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`, DTO는 `record`
  - 스키마는 Flyway로만, `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속, setter 없음, id 컬럼으로 참조
  - 에러는 `BusinessException(ErrorCode)`
  - 문서·주석·REST Docs 설명은 평서체, 에러 메시지는 "~니다"체
  - 벌크 UPDATE·DELETE는 `@Modifying(flushAutomatically = true)`만 쓴다
  - 시각은 `Clock` 빈에서 가져온다
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 새 코드 위치: `com.sprint.mission.payment`(controller/service/domain/repository/dto/client)
- **잠금 순서:**
  - 승인과 취소는 회원 행(`MemberService.getActiveForUpdate`)을 먼저 잠근 뒤 주문을 읽는다.
  - FK가 걸린 행을 잠글 때는 `FOR NO KEY UPDATE`를 쓴다(Phase 6 전역 규칙).
- **토스 API (테스트 모드):**
  - base URL `https://api.tosspayments.com`
  - 인증 헤더는 `Authorization: Basic base64(secretKey + ":")`다.
  - 승인: `POST /v1/payments/confirm`, 본문 `{paymentKey, orderId, amount}`. `orderId`에는 우리 `orderNumber`를 넣는다.
  - 조회: `GET /v1/payments/orders/{orderId}`
  - 취소: `POST /v1/payments/{paymentKey}/cancel`, 본문 `{cancelReason}`. `Idempotency-Key` 헤더에 `cancel-{paymentKey}`를 넣는다.
  - 응답에서 쓰는 필드: `paymentKey`, `orderId`, `status`, `totalAmount`, `method`, `approvedAt`. `approvedAt`은 오프셋이 붙은 ISO 시각이다.
  - 오류 응답은 `{code, message}`다. 취소의 `ALREADY_CANCELED_PAYMENT`는 성공으로 처리한다.
- **설정 (`app.toss`):**
  - `secret-key: ${TOSS_SECRET_KEY:}`
  - `base-url: https://api.tosspayments.com`
  - `connect-timeout: 3s`, `read-timeout: 10s`
  - 비밀 키는 로그·예외 메시지·응답 어디에도 남기지 않는다.
- **결제수단:** 승인 응답의 `status`가 `DONE`인 것만 받는다. 그 밖의 상태(예: 가상계좌 `WAITING_FOR_DEPOSIT`)는 즉시 토스 취소를 하고 `PAYMENT_METHOD_NOT_SUPPORTED`로 거절한다. 웹훅은 없다.
- **멱등성:**
  - `payment.payment_key`는 unique이고, `payment.order_id`도 unique다(주문당 결제 1건).
  - 이미 PAID인 주문에 같은 `paymentKey`로 승인 요청이 오면 토스를 부르지 않고 200으로 주문 상세를 돌려준다. 다른 `paymentKey`면 409 `ORDER_NOT_PAYABLE`이다.
  - 이미 USER로 취소된 주문에 취소 요청이 다시 오면 토스를 부르지 않고 200으로 주문 상세를 돌려준다.
- **사용자 취소:** PAID에서만 전체 취소한다. 토스 취소 사유는 고정값 `고객 요청`이다.
- **결제 승인 후 장바구니:** 주문한 SKU의 장바구니 줄을 같은 트랜잭션(회원 잠금 아래)에서 지운다.
- **새 ErrorCode:**
  - `ORDER_NOT_PAYABLE(409, "결제할 수 없는 주문입니다.")`
  - `PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 주문 금액과 다릅니다.")`
  - `PAYMENT_REJECTED(400, "결제가 승인되지 않았습니다.")`
  - `PAYMENT_METHOD_NOT_SUPPORTED(400, "지원하지 않는 결제수단입니다.")`
  - `PAYMENT_GATEWAY_ERROR(502, "결제사와 통신하지 못했습니다. 잠시 후 다시 시도해 주세요.")`
  - `ORDER_NOT_CANCELABLE(409, "취소할 수 없는 주문입니다.")`

**의도적으로 하지 않는 것:**
- 가상계좌·웹훅, 부분 취소, 결제 실패(failUrl) 기록 → 프론트가 처리한다
- 주문 상세 응답의 결제수단·승인 시각 필드 → Phase 10에서 화면이 필요하면 추가한다
- 관리자 결제 조회와 상태 변경 → Phase 8
- 실제 토스 테스트 키로 하는 E2E → Phase 10(브라우저 결제창이 필요하다)

## Review Focus
1. 클라이언트가 보낸 `amount`가 주문 결제금액과 다르다 → 토스를 부르지 않고 400 `PAYMENT_AMOUNT_MISMATCH`, 주문은 PENDING 그대로다 → Task 3 테스트
2. 결제 기한 직후(`expiresAt` 정각)이거나 이미 만료·대체로 취소된 주문의 승인 요청 → 토스를 부르지 않고 409 `ORDER_NOT_PAYABLE`이다. 승인과 만료 처리가 동시에 와도 "PAID인데 재고 복원" 같은 상태가 생기지 않는다 → Task 3 테스트
3. 같은 승인 요청이 동시에 두 번 온다 → 토스 승인은 1번만 호출되고, 두 요청 모두 200이다 → Task 3 동시성 테스트
4. 토스 승인은 성공했는데 우리 저장이 실패한다(예: `payment_key` unique 위반) → 토스 취소가 호출되고 주문은 PENDING 그대로다 → Task 3 테스트
5. 승인 응답이 10초를 넘는다 → 주문번호로 조회해서 DONE이면 PAID, 결제가 없으면 502이고 PENDING 그대로다. 취소 응답이 타임아웃이면 502이고 주문은 PAID 그대로이며, 다시 요청하면 끝까지 처리된다 → Task 1(WireMock)·3·4 테스트

---

### Task 1: 토스 결제 클라이언트

**Files:**
- Modify:
  - `backend/build.gradle`(`testImplementation 'org.wiremock:wiremock-standalone:3.9.2'`)
  - `application.yml`(`app.toss`)
  - `src/test/resources/application-test.yml`: `app.toss.secret-key: test_sk_dummy`. 테스트용 base-url은 WireMock 테스트가 동적으로 넣는다.
- Create:
  - `payment/client/PaymentGateway.java`, `TossPaymentClient.java`, `TossProperties.java`
  - `payment/client/PaymentResult.java`, `PaymentGatewayException.java`, `PaymentTimeoutException.java`
- Test: `payment/client/TossPaymentClientTest.java`(Spring 컨텍스트 없이 WireMock + 직접 생성)

**Interfaces:**
- Produces:
  - `record PaymentResult(String paymentKey, String orderId, String status, long totalAmount, String method, Instant approvedAt)`
    - `boolean isDone()` = `"DONE".equals(status)`
    - `approvedAt`은 null일 수 있다(승인 전 상태).
  - `interface PaymentGateway`:
    - `PaymentResult confirm(String paymentKey, String orderId, long amount)`
    - `Optional<PaymentResult> findByOrderId(String orderId)`: 404(`NOT_FOUND_PAYMENT` 등)면 empty다.
    - `void cancel(String paymentKey, String cancelReason)`: `ALREADY_CANCELED_PAYMENT`면 조용히 성공한다.
  - `PaymentGatewayException(String code, String message)`(RuntimeException): 토스가 4xx/5xx 오류 본문을 준 경우다. `code`는 토스 코드다.
  - `PaymentTimeoutException`(RuntimeException): 연결·응답 시간 초과나 I/O 오류다(`ResourceAccessException`을 감싼다).
  - `record TossProperties(String secretKey, URI baseUrl, Duration connectTimeout, Duration readTimeout)` (`@ConfigurationProperties("app.toss")`)
  - `TossPaymentClient(TossProperties)`(`@Component`):
    - `RestClient`를 `SimpleClientHttpRequestFactory`(연결·응답 타임아웃 설정)로 만든다.
    - 비밀 키가 비어 있어도 시작은 된다(로컬 개발). 호출할 때 비어 있으면 `IllegalStateException`이다.

- [ ] **Step 1: 실패 테스트 작성** (`TossPaymentClientTest`, WireMock 동적 포트)
  - `승인은_Basic_인증과_본문을_보내고_결과를_읽는다`: 헤더 `Basic dGVzdF9za19kdW1teTo=`(=`test_sk_dummy:`), 본문 3필드, 응답 → `PaymentResult` 전 필드(approvedAt `2026-10-20T10:00:00+09:00` → `2026-10-20T01:00:00Z`)
  - `승인_거절은_토스_코드를_담은_PaymentGatewayException이다`: 400 `{code: "REJECT_CARD_PAYMENT", message: ...}`
  - `응답이_읽기_타임아웃보다_늦으면_PaymentTimeoutException이다`: `withFixedDelay`가 read-timeout보다 길다. 테스트에서는 read-timeout을 500ms로 만든다.
  - `주문번호_조회는_없으면_empty다`: 404 `NOT_FOUND_PAYMENT`
  - `취소는_멱등_키를_보내고_이미_취소됐으면_성공이다`: 헤더 `Idempotency-Key: cancel-{paymentKey}`. 400 `ALREADY_CANCELED_PAYMENT` → 예외 없음
  - `5xx는_PaymentGatewayException이다`
  - `비밀_키를_예외_메시지에_넣지_않는다`: 위 오류들의 `getMessage()`에 `test_sk_dummy`가 없다.
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인.** 전체 `./gradlew :backend:test`
- [ ] **Step 5: 커밋.** `feat: 토스페이먼츠 결제 클라이언트 추가`

---

### Task 2: 결제 도메인 + 스키마 + 주문 상태 보강

**Files:**
- Create: `V16__create_payment.sql`, `payment/domain/Payment.java`, `PaymentStatus.java`, `payment/repository/PaymentRepository.java`
- Modify:
  - `order/domain/Order.java`(`markPaid`, `cancel` null 금지)
  - `order/domain/CancelReason.java`(`USER`)
  - `cart/repository/CartItemRepository.java`(`deleteByMemberIdAndSkuIdIn`)
  - `docs/performance/stock-lock-comparison.md`: 머리말의 "3번"을 본문과 같은 "5번"으로 고친다(이월 노트).
  - `order/service/OrderService.java`: `create` Javadoc에 "비대체 경로는 order_item INSERT(FK KEY SHARE)가 SKU 차감보다 먼저다. 복원과 차감을 함께 하는 흐름은 `lockSkus`를 쓴다"는 불변식을 적는다(이월 노트, 코드 변경 없음).
- Test: `payment/domain/PaymentTest.java`, `order/domain/OrderTest.java`(추가), `payment/repository/PaymentRepositoryTest.java`

**Interfaces:**
- Produces:
  - **V16 `payment`:**
    - `id`, `order_id bigint not null unique → orders`, `payment_key varchar(200) not null unique`
    - `amount bigint not null check (amount >= 100)`, `method varchar(30) not null`, `status varchar(20) not null`
    - `approved_at timestamptz not null`, `canceled_at timestamptz`, `created_at`, `updated_at`
  - `enum PaymentStatus { DONE, CANCELED }`
  - `Payment`:
    - `static Payment approve(Long orderId, PaymentResult result, Instant now)`: `approvedAt`이 null이면 `now`를 쓴다.
    - `void cancel(Instant now)`: DONE이 아니면 `IllegalStateException`이다.
  - `PaymentRepository`: `Optional<Payment> findByOrderId(Long)`
  - `Order.markPaid()`: `canTransitionTo(PAID)`가 아니면 `IllegalStateException`이다.
  - `Order.cancel(reason, now)`: `reason`이 null이면 `NullPointerException`(`Objects.requireNonNull`)이다.
  - `CancelReason.USER`(주석: 고객이 결제 완료 주문을 취소했다)
  - `CartItemRepository.deleteByMemberIdAndSkuIdIn(Long memberId, Collection<Long> skuIds) → int`: `@Modifying(flushAutomatically = true)` JPQL delete

- [ ] **Step 1: 실패 테스트 작성**
  - `PaymentTest`: 승인 기록이 결과 필드를 복사하고, approvedAt이 null이면 now다. 취소하면 CANCELED와 canceledAt이 남는다. 두 번 취소하면 `IllegalStateException`이다.
  - `OrderTest`: `대기_주문은_결제_완료가_된다`, `취소된_주문은_결제_완료가_될_수_없다`, `취소_사유_없이_취소할_수_없다`
  - `PaymentRepositoryTest`: 같은 `payment_key`나 같은 `order_id` 두 번째 insert는 unique 위반이다. `deleteByMemberIdAndSkuIdIn`은 그 회원의 해당 SKU 줄만 지운다(다른 회원·다른 SKU는 남는다).
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 전체 통과**
- [ ] **Step 5: 커밋.** `feat: 결제 기록 테이블과 주문 결제 완료 상태 추가`

---

### Task 3: 결제 승인 API

**Files:**
- Create: `payment/service/PaymentService.java`, `payment/controller/PaymentController.java`, `payment/dto/PaymentConfirmRequest.java`
- Modify: `ErrorCode.java`(위 6개 중 `ORDER_NOT_PAYABLE`, `PAYMENT_*` 5개), `index.adoc`(`== 결제`, `=== 결제 승인`)
- Test:
  - `src/test/java/com/sprint/mission/payment/FakePaymentGateway.java`: 테스트 설정에서 `@Primary` 빈으로 등록한다. 호출 기록, 응답 지정, 예외 지정, 지연 지정을 할 수 있다.
  - `payment/controller/PaymentConfirmApiTest.java`, `payment/service/PaymentConcurrencyTest.java`

**Interfaces:**
- Consumes: Task 1 `PaymentGateway`·`PaymentResult`·예외, Task 2 `Payment`·`Order.markPaid`·`deleteByMemberIdAndSkuIdIn`, Phase 6 `OrderRepository.findByOrderNumberAndMemberId`·`OrderItemRepository`·`OrderDetailResponse.of`
- Produces:
  - `record PaymentConfirmRequest(@NotBlank @Size(max = 200) String paymentKey, @Positive long amount)`
  - `PaymentService.confirm(Long memberId, String orderNumber, PaymentConfirmRequest) → OrderDetailResponse`
    - 메서드 자체에는 `@Transactional`이 없다. 본문은 `TransactionTemplate`으로 감싸고, 보상 취소는 그 밖에서 한다.
    - 트랜잭션 안(하나의 `now`):
      1. `getActiveForUpdate(memberId)` → `findByOrderNumberAndMemberId` 없으면 `ORDER_NOT_FOUND`
      2. 멱등: 주문이 PAID이고 `Payment.paymentKey`가 같으면 그대로 상세를 반환한다(토스 호출 없음). 다르면 `ORDER_NOT_PAYABLE`이다.
      3. PENDING이 아니거나 `order.isExpired(now)`면 `ORDER_NOT_PAYABLE`
      4. `request.amount() != order.getPaymentAmount()`이면 `PAYMENT_AMOUNT_MISMATCH`
      5. `gateway.confirm(paymentKey, orderNumber, amount)`:
         - `PaymentGatewayException`이고 코드가 `ALREADY_PROCESSED_PAYMENT`면 조회 분기로 간다. 이전 시도가 타임아웃 뒤에 승인된 경우를 다시 시도로 복구한다.
         - 그 밖의 `PaymentGatewayException`이면 `PAYMENT_REJECTED`
         - `PaymentTimeoutException`이면 `findByOrderId(orderNumber)`로 조회한다.
           - 결과가 있고 paymentKey가 같으면 그 결과로 계속한다.
           - 결과가 없거나 조회도 실패하면 `gateway.cancel(paymentKey, "승인 응답 시간 초과")`를 한 번 시도하고(실패는 `warn` 로그만), `PAYMENT_GATEWAY_ERROR`를 던진다. 늦게 도착한 승인 때문에 돈만 빠져나가는 일을 줄이기 위해서다.
           - 남는 위험: 취소 시도 뒤에 승인이 도착하는 경우다. 코드에 `shortcut:` 주석으로 남기고, 정산 대사 배치가 생기면 고친다.
      6. 여기부터 "승인됨"으로 표시한다(보상 대상).
         - `!result.isDone()`이면 `PAYMENT_METHOD_NOT_SUPPORTED`
         - `result.totalAmount() != amount`이면 `PAYMENT_AMOUNT_MISMATCH`
      7. `Payment.approve` 저장 → `order.markPaid()` → `deleteByMemberIdAndSkuIdIn(memberId, 주문상품 skuId들)` → `orderRepository.flush()` → 상세 반환
    - 트랜잭션 밖:
      - "승인됨" 이후 예외가 나면(6·7단계와 커밋 실패 포함) `gateway.cancel(paymentKey, "주문 처리 실패")`로 보상한다.
      - 보상이 실패하면 `error` 로그(주문번호·paymentKey, 비밀 키 없음)를 남긴다.
      - 원래 예외를 다시 던진다. 6단계의 BusinessException은 그대로 던지고, 그 밖의 예외는 `INTERNAL_ERROR`가 된다.
  - API: `POST /api/members/me/orders/{orderNumber}/payment` → 200 `OrderDetailResponse`

- [ ] **Step 1: 실패 테스트 작성**
  - `PaymentConfirmApiTest`
    - `금액이_맞으면_승인하고_결제_완료가_된다`
      - status PAID, `payment` 행(DONE, method, paymentKey)
      - 주문한 SKU의 장바구니 줄은 삭제되고, 주문하지 않은 줄은 남는다
      - 토스 승인 1회(orderId = orderNumber, amount)
      - REST Docs `payment/confirm`
    - `금액이_다르면_토스를_부르지_않고_400` (Review Focus 1)
    - `기한이_지난_주문은_토스를_부르지_않고_409`: `MutableClock`으로 정확히 15분 (Review Focus 2)
    - `대체되거나_만료된_주문은_409` (Review Focus 2)
    - `같은_paymentKey로_다시_승인하면_토스를_부르지_않고_200`, `다른_paymentKey면_409`
    - `토스가_거절하면_400_PAYMENT_REJECTED이고_주문은_대기_그대로다`
    - `입금_대기_결제는_토스_취소_후_400_PAYMENT_METHOD_NOT_SUPPORTED`: 취소 1회, PENDING 그대로
    - `토스_승인_후_저장이_실패하면_토스_취소로_보상한다` (Review Focus 4): 다른 주문의 `payment` 행에 같은 `payment_key`를 미리 넣어 unique 위반을 낸다. 토스 취소 1회, 주문은 PENDING, 재고·쿠폰·적립금 그대로다.
    - `승인_타임아웃이면_조회해서_완료면_결제_완료다` (Review Focus 5)
    - `승인_타임아웃이고_결제가_없으면_취소를_시도하고_502이고_대기_그대로다` (Review Focus 5)
    - `이미_처리된_결제_응답이면_조회해서_결제_완료다`: Fake 승인이 `ALREADY_PROCESSED_PAYMENT`를 던지고, 조회는 같은 paymentKey의 DONE을 준다.
    - `다른_회원의_주문번호면_404_ORDER_NOT_FOUND`
    - `잘못된_요청은_400`: paymentKey 빈 값·201자, amount 0
    - `토큰_없이_호출하면_401`
  - `PaymentConcurrencyTest`
    - `같은_승인_요청이_동시에_두_번_와도_토스는_한_번만_호출된다` (Review Focus 3): Fake 승인에 200ms 지연을 둔다. 둘 다 200, `payment` 1행.
    - `기한_직전_승인과_만료_처리가_동시에_와도_상태가_일관된다` (Review Focus 2): 결과가 (PAID, 재고 차감 유지)이거나 (CANCELED/EXPIRED, 재고 복원, 토스 미호출이나 보상 취소)뿐이다.
- [ ] **Step 2: 실패 확인 → Step 3: 구현.** `index.adoc`에 `== 결제`를 추가한다. 내용은 결제 흐름(토스 결제창 → successUrl의 paymentKey·orderId·amount → 이 API), 처리 순서와 오류 우선순위, 멱등 규칙, 보상 취소, 타임아웃 처리다.
- [ ] **Step 4: 통과 확인.** 전체 `./gradlew :backend:test`
- [ ] **Step 5: 커밋.** `feat: 금액 검증과 보상 취소를 갖춘 결제 승인 API 추가`

---

### Task 4: 결제 완료 주문 취소 API

**Files:**
- Modify:
  - `payment/service/PaymentService.java`(`cancel`), `payment/controller/PaymentController.java`
  - `ErrorCode.java`(`ORDER_NOT_CANCELABLE`)
  - `index.adoc`(`=== 주문 취소`). 주문 섹션의 "사용자 취소는 Phase 7" 같은 문구가 있으면 고친다.
- Test: `payment/controller/OrderCancelApiTest.java`

**Interfaces:**
- Consumes: Task 1 `PaymentGateway.cancel`, Task 2 `Payment.cancel`·`CancelReason.USER`, Phase 6 `OrderCanceler.cancel`
- Produces:
  - `PaymentService.cancel(Long memberId, String orderNumber) → OrderDetailResponse` (`@Transactional`, 하나의 `now`):
    1. `getActiveForUpdate` → 주문 없으면 `ORDER_NOT_FOUND`
    2. 이미 CANCELED이고 사유가 USER면 그대로 상세를 반환한다(멱등).
    3. PAID가 아니면 `ORDER_NOT_CANCELABLE`
    4. `gateway.cancel(payment.paymentKey, "고객 요청")`: `PaymentGatewayException`·`PaymentTimeoutException`이면 `PAYMENT_GATEWAY_ERROR`(DB는 그대로)
    5. `payment.cancel(now)` → `orderCanceler.cancel(order, USER, now)` → 상세 반환
  - API: `POST /api/members/me/orders/{orderNumber}/cancel` → 200 `OrderDetailResponse`

- [ ] **Step 1: 실패 테스트 작성** (`OrderCancelApiTest`)
  - `결제_완료_주문을_취소하면_토스_취소_후_모두_되돌린다`
    - status CANCELED, cancelReason USER
    - `payment` CANCELED
    - 재고·쿠폰 AVAILABLE·적립금 `RESTORE` 내역 복원
    - 토스 취소 1회(사유 "고객 요청")
    - REST Docs `payment/cancel`
  - `다시_취소하면_토스를_부르지_않고_200`
  - `결제_대기_주문은_409_ORDER_NOT_CANCELABLE`
  - `상품_준비_중_주문은_409`: SQL로 PREPARING으로 바꾼다.
  - `토스_취소가_타임아웃이면_502이고_주문은_결제_완료_그대로다` (Review Focus 5). 이어서 Fake가 `ALREADY_CANCELED`처럼 성공하면 다시 요청해서 CANCELED가 된다.
  - `다른_회원의_주문번호면_404`, `토큰_없이_호출하면_401`
- [ ] **Step 2: 실패 확인 → Step 3: 구현 → Step 4: 통과 확인.** `./gradlew clean :backend:build`
- [ ] **Step 5: 커밋.** `feat: 결제 완료 주문의 전체 취소 API 추가`

---

## Phase 7 완료 기준
- `./gradlew clean :backend:build`가 통과한다(Phase 0~6 테스트 포함).
- REST Docs에 `== 결제`(승인)와 `=== 주문 취소`가 있다.
- 이월 노트에 Phase 8 항목을 남긴다:
  - 관리자 상태 변경도 회원 → 주문 잠금 순서를 따른다.
  - PREPARING 이후 취소는 관리자 경로에서 정한다.
  - 실제 토스 테스트 키 E2E는 Phase 10이다.
