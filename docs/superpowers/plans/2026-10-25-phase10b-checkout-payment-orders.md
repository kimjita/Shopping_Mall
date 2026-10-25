# Phase 10b 사용자 프론트(주문서·결제·주문 내역) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 듀이 프론트에 마이페이지 허브, 배송지 관리, 쿠폰 받기, 주문서, 토스 결제창 결제, 주문 목록·상세(재결제·취소·구매확정)를 더하고 데모 쿠폰을 시드에 넣는다.

**Architecture:** Phase 10a의 구조(`apiFetch` + TanStack Query + React Router, 자체 컴포넌트)를 그대로 쓴다. 토스 결제창은 `src/payments/toss.ts` 한 파일에 가두고 테스트는 이 모듈을 `vi.mock`으로 바꾼다. 결제 승인은 successUrl 페이지가 서버 승인 API를 한 번 부르고 주문 상세로 넘긴다.

**Tech Stack:** Phase 10a와 같음 + `@tosspayments/tosspayments-sdk` 2.9.0(새 의존성은 이것 하나).

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` (Phase 10, 결제·주문 규칙). 백엔드 계약은 `backend/src/docs/asciidoc/index.adoc`(배송지·쿠폰·주문서·주문·결제 장)와 `backend/build/generated-snippets/{address,coupon,checkout,order,payment,admin-coupon}/*`가 기준이다.

## Global Constraints

- Phase 10a의 Global Constraints가 모두 그대로 적용된다(토큰 색만 사용, 모바일 우선 375px 가로 스크롤 없음, 화면 문구 "~니다"체·주석 평서체, `formatWon`만으로 금액 표시, 오류는 서버 `message`를 그대로·필드 오류는 해당 입력 아래, 모든 요청은 `apiFetch`, MSW `onUnhandledRequest:'error'`, 커밋 trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, `.idea/*`·`.claude/*` 커밋 금지).
- 새 의존성은 `@tosspayments/tosspayments-sdk@2.9.0`(`--save-exact`) 하나뿐이다. 다른 패키지(주소 검색, 날짜, 타이머 라이브러리 등)를 더하지 않는다.
- SDK는 `src/payments/toss.ts`에서만 import한다. 컴포넌트·테스트는 그 모듈의 `startTossPayment`만 쓴다. 테스트에서는 `vi.mock('../payments/toss')`로 바꾸고 진짜 SDK를 불러오지 않는다.
- 클라이언트 키는 `import.meta.env.VITE_TOSS_CLIENT_KEY`(`test_ck_`로 시작하는 결제창 키), 서버 키는 백엔드 환경변수 `TOSS_SECRET_KEY`다. 키 값은 저장소에 커밋하지 않는다(`frontend/.env.example`에는 이름만).
- customerKey는 SDK의 `ANONYMOUS`다(문서: 추측 가능한 값은 안전하지 않음).
- 문구:
  - 주문 상태: `PENDING` 결제 대기, `PAID` 결제 완료, `PREPARING` 상품 준비 중, `SHIPPING` 배송 중, `DELIVERED` 배송 완료, `CONFIRMED` 구매확정, `CANCELED` 취소.
  - 취소 사유: `EXPIRED` 결제 기한 지남, `REPLACED` 새 주문으로 대체, `USER` 고객 취소, `ADMIN` 판매자 취소.
  - 등급: `BABY` 베이비, `PINK` 핑크, `GREEN` 그린, `BLACK` 블랙, `GOLD` 골드.
  - 쿠폰 할인: 정액 `'{formatWon(v)} 할인'`, 정률 `'{v}% 할인 (최대 {formatWon(max)})'`; 최소 주문금액 `'{formatWon(min)} 이상 구매 시'`(0이면 생략).
- 날짜·시각은 `formatDateTime(iso)` 하나로 표시한다: `Intl.DateTimeFormat('ko-KR', {timeZone:'Asia/Seoul', year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', hour12:false})` → 예 `2026. 10. 25. 12:10`. 파싱할 수 없는 값은 `''`(던지지 않는다).
- 주문번호는 `/^[A-Za-z0-9_=-]{6,64}$/`(토스 orderId 규칙)에 맞을 때만 요청·이동에 쓴다.
- 결제 가능 여부는 서버 상태가 아니라 `status === 'PENDING' && Date.now() < Date.parse(expiresAt)`로 판단한다(서버 만료 스케줄러는 최대 1분 늦다).
- 각 Task 끝의 확인 명령: `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build` 전부 통과(Task 7은 `node --test scripts/demo/*.test.mjs`도).

## Review Focus

1. successUrl 페이지가 StrictMode 이중 실행·새로고침으로 다시 열림 → 한 마운트에서 승인 요청은 한 번, 새로고침의 두 번째 요청은 서버 멱등 200으로 주문 상세에 도착(Task 4 테스트).
2. successUrl 쿼리가 비었거나 조작됨(`paymentKey` 없음, `amount=abc`·`0`·`1.5`, `orderId=../x`) → 승인 API를 부르지 않고 `'결제 정보가 올바르지 않습니다.'`(Task 4 테스트).
3. 결제 기한이 막 지난 `PENDING` 주문(서버는 아직 PENDING) → "결제하기"가 사라지고 `'결제 기한이 지났습니다.'`(Task 6 테스트, 가짜 시계).
4. 손으로 고친 주문서 주소(`items=`, `items=abc`, `items=1,1`, 51개, `items=-3`) → 미리보기 요청 없이 `'주문할 상품을 다시 선택해 주세요.'` + 장바구니 링크(Task 5 테스트).
5. 적립금 입력에 음수·소수·잔액 초과·결제금액 100원 미만이 되는 값 → 입력은 0 이상 정수로 고쳐지고, "모두 사용"은 결제금액이 100원 이상 남는 최댓값, 서버 오류(`POINTS_EXCEED_LIMIT` 등)는 메시지로 보이고 "결제하기"가 막힌다(Task 5 테스트).

---

## File Structure

```
frontend/
  .env.example                       VITE_TOSS_CLIENT_KEY= (Task 7)
  src/vite-env.d.ts                  ImportMetaEnv에 VITE_TOSS_CLIENT_KEY (Task 4)
  src/api/types.ts                   주문·쿠폰·배송지 타입 추가 (Task 1)
  src/api/addresses.ts  coupons.ts  orders.ts  checkout.ts
  src/lib/labels.ts                  ORDER_STATUS_LABEL, CANCEL_REASON_LABEL, GRADE_LABEL, couponDiscountText, minOrderText (Task 1)
  src/lib/format.ts                  formatDateTime (Task 1)
  src/lib/checkoutParams.ts          parseCheckoutItems (Task 5)
  src/lib/orderName.ts               orderNameOf, isOrderNumber, isPayable (Task 4)
  src/payments/toss.ts               startTossPayment, TossCancelError (Task 4)
  src/components/ConfirmDialog.tsx   확인 창 (Task 1)
  src/components/AddressForm.tsx  AddressPicker.tsx (Task 2, Task 5)
  src/hooks/useNow.ts                1초마다 갱신되는 현재 시각 (Task 6)
  src/pages/MyPage.tsx  AddressesPage.tsx  CouponsPage.tsx  CheckoutPage.tsx
  src/pages/PaymentSuccessPage.tsx  PaymentFailPage.tsx  OrdersPage.tsx  OrderDetailPage.tsx
scripts/demo/data.mjs  seed.mjs  data.test.mjs   데모 쿠폰 (Task 7)
README.md                                           토스 테스트 키 절 (Task 7)
```

## API 계약 (Task 1에서 `src/api/types.ts`에 더한다)

```ts
export type Grade = 'BABY' | 'PINK' | 'GREEN' | 'BLACK' | 'GOLD'
export type OrderStatus = 'PENDING' | 'PAID' | 'PREPARING' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED'
export type CancelReason = 'EXPIRED' | 'REPLACED' | 'USER' | 'ADMIN'
export type DiscountType = 'FIXED' | 'RATE'
export interface Address { id: number; recipient: string; phone: string; zipCode: string; address1: string; address2: string | null; isDefault: boolean }
export interface AddressInput { recipient: string; phone: string; zipCode: string; address1: string; address2: string; isDefault: boolean }
export interface DownloadableCoupon { couponId: number; name: string; discountType: DiscountType; discountValue: number; maxDiscount: number | null;
  minOrderAmount: number; requiredGrade: Grade | null; eligible: boolean; remainingQuantity: number; issueEndsAt: string; useStartsAt: string; useEndsAt: string }
export interface MyCoupon { id: number; couponId: number; name: string; discountType: DiscountType; discountValue: number; maxDiscount: number | null;
  minOrderAmount: number; status: 'AVAILABLE' | 'USED' | 'EXPIRED'; useStartsAt: string; useEndsAt: string; usedAt: string | null; issuedAt: string }
export interface PriceBreakdown { itemsPrice: number; saleDiscount: number; itemsSalePrice: number; couponDiscount: number; shippingFee: number; pointsUsed: number; paymentAmount: number }
export interface CheckoutPreview { lines: { cartItemId: number; skuId: number; productName: string; skuName: string; thumbnailUrl: string | null;
  quantity: number; unitPrice: number; unitSalePrice: number; lineTotal: number }[]; price: PriceBreakdown; pointBalance: number }
export interface OrderItem { productId: number; skuId: number; productName: string; skuName: string; thumbnailUrl: string | null; unitPrice: number; unitSalePrice: number; quantity: number; lineTotal: number }
export interface Order { orderNumber: string; status: OrderStatus; orderedAt: string; expiresAt: string; items: OrderItem[]; price: PriceBreakdown; couponName: string | null;
  shipping: { recipient: string; phone: string; zipCode: string; address1: string; address2: string | null }; canceledAt: string | null; cancelReason: CancelReason | null;
  deliveredAt: string | null; confirmedAt: string | null }
export interface OrderSummary { orderNumber: string; status: OrderStatus; orderedAt: string; firstProductName: string; thumbnailUrl: string | null; itemCount: number; paymentAmount: number }
```

`Me.grade`는 `Grade`로 바꾼다(`src/test/render.tsx`의 `testMe.grade`도 `'BABY'`로).

엔드포인트(모두 회원 전용 `auth:'required'`, `/api` 접두사): `GET|POST members/me/addresses`, `PUT|DELETE members/me/addresses/{id}`(PUT은 전체 교체), `GET members/me/coupons/downloadable` → `DownloadableCoupon[]`, `POST members/me/coupons/{couponId}/download` → 201 `MyCoupon`, `GET members/me/coupons` → `MyCoupon[]`, `POST members/me/checkout/preview {cartItemIds, memberCouponId|null, points}` → `CheckoutPreview`, `POST members/me/orders {cartItemIds, memberCouponId|null, points, addressId}` → 201 `Order`, `GET members/me/orders?cursor&size` → `CursorPage<OrderSummary>`, `GET members/me/orders/{orderNumber}` → `Order`, `POST members/me/orders/{orderNumber}/payment {paymentKey, amount}` → `Order`, `POST members/me/orders/{orderNumber}/cancel` → `Order`, `POST members/me/orders/{orderNumber}/confirm` → `Order`. 관리자(시드용): `POST admin/coupons {name, discountType, discountValue, maxDiscount, minOrderAmount, totalQuantity, requiredGrade, issueStartsAt, issueEndsAt, useStartsAt, useEndsAt}`, `GET admin/coupons`.

Query key 규칙: `['addresses']`, `['downloadableCoupons']`, `['myCoupons']`, `['checkoutPreview', ids.join(','), couponId, points]`, `['orders']`(무한 목록), `['order', orderNumber]`. 로그아웃·세션 만료 때 지우는 회원 전용 목록(`AuthProvider`의 `removeQueries` 대상)에 `['addresses']`, `['myCoupons']`, `['downloadableCoupons']`, `['orders']`, `['order']`, `['checkoutPreview']`를 더한다.

---

### Task 1: 공통 타입·문구·확인 창·마이페이지 허브

**Files:**
- Modify: `src/api/types.ts`(위 계약), `src/lib/labels.ts`, `src/lib/format.ts`, `src/components/Header.tsx`, `src/routes.tsx`, `src/auth/AuthProvider.tsx`(회원 전용 key 추가), `src/test/render.tsx`(`grade:'BABY'`)
- Create: `src/components/ConfirmDialog.tsx`, `src/pages/MyPage.tsx`
- Test: `src/lib/labels.test.ts`, `src/lib/format.test.ts`(추가), `src/components/ConfirmDialog.test.tsx`, `src/pages/MyPage.test.tsx`, `src/components/Header.test.tsx`(추가)

**Interfaces:**
- Produces: `ORDER_STATUS_LABEL: Record<OrderStatus,string>`, `CANCEL_REASON_LABEL: Record<CancelReason,string>`, `GRADE_LABEL: Record<Grade,string>`, `couponDiscountText(c: {discountType; discountValue; maxDiscount}): string`, `minOrderText(min: number): string`(0이면 `''`).
- Produces: `formatDateTime(iso: string): string`.
- Produces: `<ConfirmDialog open title message confirmLabel onConfirm onCancel pending? />` — `<dialog>` 모달, 확인 버튼(`confirmLabel`)과 "취소" 버튼, `pending`이면 확인 버튼 비활성.
- Produces: 라우트 `/mypage`(`RequireAuth`). `MyPage`: `'{me.name}님'`, `'{GRADE_LABEL[me.grade]} 등급'`, 링크 목록 — 주문 내역 `/mypage/orders`, 배송지 관리 `/mypage/addresses`, 쿠폰 받기 `/coupons`, 찜 `/wishlist`, 최근 본 상품 `/recent`.
- Header: 회원이면 "찜" 다음에 "마이"(`/mypage`) 링크.

- [ ] **Step 1: 실패하는 테스트** — `labels`: `couponDiscountText({discountType:'FIXED',discountValue:3000,maxDiscount:null})==='3,000원 할인'`, `couponDiscountText({discountType:'RATE',discountValue:10,maxDiscount:5000})==='10% 할인 (최대 5,000원)'`, `minOrderText(15000)==='15,000원 이상 구매 시'`, `minOrderText(0)===''`, `ORDER_STATUS_LABEL.PREPARING==='상품 준비 중'`, `CANCEL_REASON_LABEL.REPLACED==='새 주문으로 대체'`. `format`: `formatDateTime('2026-10-25T03:10:00Z')==='2026. 10. 25. 12:10'`, `formatDateTime('nope')===''`. `ConfirmDialog`: 확인 클릭 → `onConfirm` 1번, "취소" → `onCancel`. `MyPage`: 회원(`member:{name:'김듀이', grade:'PINK'}`)이면 `'김듀이님'`, `'핑크 등급'`, 다섯 링크의 href. `Header`: 회원이면 `'마이'` 링크 href `/mypage`, 비회원이면 없음.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 주문 문구·확인 창·마이페이지 허브 추가`.

### Task 2: 배송지 관리

**Files:**
- Create: `src/api/addresses.ts`, `src/components/AddressForm.tsx`, `src/pages/AddressesPage.tsx`
- Modify: `src/routes.tsx`(`/mypage/addresses`, `RequireAuth`)
- Test: `src/components/AddressForm.test.tsx`, `src/pages/AddressesPage.test.tsx`

**Interfaces:**
- Produces: `useAddresses()`(`['addresses']`), `useSaveAddress()` — `mutate({id?: number, input: AddressInput})`(id 있으면 PUT, 없으면 POST), `useDeleteAddress()`; 셋 다 성공 시 `['addresses']` invalidate.
- Produces: `<AddressForm initial?: Address onSaved(address: Address) onCancel />` — 받는 사람·휴대폰 번호·우편번호·기본 주소·상세 주소 입력(라벨·`autoComplete`: `name`, `tel`, `postal-code`, `address-line1`, `address-line2`)과 "기본 배송지로 지정" 체크박스, "저장" 버튼. 클라이언트 검사는 필수값과 우편번호 `/^\d{5}$/`(`'우편번호는 숫자 5자리입니다.'`)만. 서버 필드 오류는 입력 아래, 서버 `message`는 `role=alert`.
- `AddressesPage`: 목록(기본 배송지는 `'기본'` 배지), 줄마다 "수정"(폼 `<dialog>`), "삭제"(`ConfirmDialog` `'배송지를 삭제할까요?'`), 기본이 아니면 "기본으로 지정"(같은 값 + `isDefault:true`로 PUT). "배송지 추가"는 10개면 비활성 + `'배송지는 최대 10개까지 등록할 수 있습니다.'`. 비면 `'등록된 배송지가 없습니다.'`.

- [ ] **Step 1: 실패하는 테스트** — `AddressForm`: `'우편번호가 5자리가 아니면 요청하지 않는다'`(`'0623'` → 메시지, 요청 0), `'저장하면 POST 본문을 보내고 onSaved를 부른다'`(본문 `{recipient,phone,zipCode,address1,address2,isDefault}`), `'서버 필드 오류를 입력 아래에 보여 준다'`. `AddressesPage`: `'기본 배송지에 기본 배지를 단다'`, `'기본으로 지정하면 isDefault true로 PUT한다'`, `'삭제는 확인 뒤 DELETE한다'`, `'10개면 추가 버튼이 막힌다'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 배송지 관리 화면 추가`.

### Task 3: 쿠폰 받기

**Files:**
- Create: `src/api/coupons.ts`, `src/pages/CouponsPage.tsx`
- Modify: `src/routes.tsx`(`/coupons`, `RequireAuth`)
- Test: `src/pages/CouponsPage.test.tsx`

**Interfaces:**
- Produces: `useDownloadableCoupons()`(`['downloadableCoupons']`), `useMyCoupons()`(`['myCoupons']`), `useDownloadCoupon()` — `mutate(couponId)`, 성공 시 두 key invalidate.
- `CouponsPage`: 카드마다 이름, `couponDiscountText`, `minOrderText`, `'남은 수량 {n}장'`, `'{formatDateTime(issueEndsAt)}까지 받을 수 있습니다.'`, `'사용 기간 {formatDateTime(useStartsAt)} ~ {formatDateTime(useEndsAt)}'`. 버튼 "받기": `eligible:false`면 비활성 + `'{GRADE_LABEL[requiredGrade]} 등급 이상 받을 수 있습니다.'`. 성공하면 `role=status` `'쿠폰을 받았습니다.'`(목록에서 사라지는 것은 서버 응답대로). 실패는 서버 `message` `role=alert`. 비면 `'지금 받을 수 있는 쿠폰이 없습니다.'`.

- [ ] **Step 1: 실패하는 테스트** — `'받기를 누르면 다운로드 API를 부르고 안내를 보여 준다'`(POST `/coupons/1/download` → `'쿠폰을 받았습니다.'`, 목록 다시 요청), `'등급이 안 되면 버튼이 막히고 안내한다'`(`requiredGrade:'PINK', eligible:false` → `'핑크 등급 이상 받을 수 있습니다.'`), `'소진되면 서버 메시지를 보여 준다'`(409 `COUPON_SOLD_OUT`), `'받을 쿠폰이 없으면 안내 문구'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 쿠폰 받기 화면 추가`.

### Task 4: 토스 결제 모듈과 결제 결과 페이지

**Files:**
- Create: `src/payments/toss.ts`, `src/lib/orderName.ts`, `src/api/orders.ts`, `src/pages/PaymentSuccessPage.tsx`, `src/pages/PaymentFailPage.tsx`
- Modify: `package.json`(SDK), `src/vite-env.d.ts`, `src/routes.tsx`(`/payments/success`, `/payments/fail` 둘 다 `RequireAuth`)
- Test: `src/lib/orderName.test.ts`, `src/payments/toss.test.ts`, `src/pages/PaymentSuccessPage.test.tsx`, `src/pages/PaymentFailPage.test.tsx`

**Interfaces:**
- Produces: `orderNameOf(items: {productName: string}[]): string` — 1개면 이름, 아니면 `'{첫 이름} 외 {n-1}건'`, 100자 초과는 100자로 자름. `isOrderNumber(s: string): boolean`(Global 정규식). `isPayable(order: Pick<Order,'status'|'expiresAt'>, now: number): boolean`.
- Produces: `class TossCancelError extends Error {}`, `startTossPayment(order: Order, customer: {email: string; name: string}): Promise<void>` — 키가 없으면 `Error('결제 설정이 없습니다. 관리자에게 문의해 주세요.')`. `loadTossPayments(key)` → `.payment({customerKey: ANONYMOUS})` → `.requestPayment({method:'CARD', amount:{currency:'KRW', value: order.price.paymentAmount}, orderId: order.orderNumber, orderName: orderNameOf(order.items), successUrl: location.origin + '/payments/success', failUrl: location.origin + '/payments/fail', customerEmail, customerName})`. SDK 오류의 `code`가 `'USER_CANCEL'`이면 `TossCancelError`로 바꿔 던지고, 그 밖의 오류는 메시지를 유지해 다시 던진다.
- Produces: `useOrder(orderNumber)`(`['order', n]`, `isOrderNumber`가 아니면 요청하지 않음), `useCreateOrder()`, `useConfirmPayment()` — `mutate({orderNumber, paymentKey, amount})`, `useCancelOrder()`, `useConfirmOrder()`; 성공한 뮤테이션은 응답으로 `['order', n]`을 `setQueryData`하고 `['orders']`를 invalidate(결제 승인은 `['cart']`도).
- `PaymentSuccessPage`: 쿼리 `paymentKey`(1~200자), `orderId`(`isOrderNumber`), `amount`(양의 정수 문자열 `/^[1-9]\d*$/`)를 검사해 하나라도 틀리면 요청 없이 `'결제 정보가 올바르지 않습니다.'` + 홈 링크. 맞으면 마운트당 한 번(ref 가드) 승인 요청, 진행 중 `'결제를 확인하고 있습니다.'`.
  - 200 → `navigate('/mypage/orders/{orderId}', {replace:true, state:{notice:'결제가 완료되었습니다.'}})`.
  - 502 `PAYMENT_GATEWAY_ERROR` → 서버 메시지 + `'결제 확인이 지연되고 있습니다. 잠시 후 다시 확인해 주세요.'` + "다시 확인" 버튼(같은 값으로 재요청).
  - 409 `ORDER_NOT_PAYABLE` → `navigate('/mypage/orders/{orderId}', {replace:true, state:{notice: 서버 message}})`(상세가 현재 상태를 다시 읽는다).
  - 그 밖의 오류 → 서버 메시지 `role=alert` + "주문 상세로" 링크.
- `PaymentFailPage`: 쿼리 `message`(없으면 `'결제가 완료되지 않았습니다.'`)를 글자로 보여 주고, `orderId`가 `isOrderNumber`면 "주문 상세에서 다시 결제하기"(`/mypage/orders/{orderId}`), 아니면 "주문 내역 보기"(`/mypage/orders`).

- [ ] **Step 1: 실패하는 테스트**
  - `orderName`: `orderNameOf([{productName:'토너'}])==='토너'`, 3개 → `'토너 외 2건'`; `isOrderNumber('20261025-Y1GAX7TR')===true`, `'../x'`·`'abc'`→false; `isPayable({status:'PENDING',expiresAt:'2026-10-25T03:25:00Z'}, Date.parse('2026-10-25T03:24:59Z'))===true`, 03:25:00 → false, `PAID` → false.
  - `toss`(`vi.mock('@tosspayments/tosspayments-sdk')`): `'requestPayment에 주문 값을 넘긴다'`(amount·orderId·orderName·successUrl 확인, customerKey가 `ANONYMOUS`), `'USER_CANCEL은 TossCancelError로 바꾼다'`, `'키가 없으면 결제 설정 오류'`(`vi.stubEnv('VITE_TOSS_CLIENT_KEY','')`).
  - `PaymentSuccessPage`: `'StrictMode에서도 승인 요청은 한 번이다'`(`<StrictMode>`로 렌더, 요청 수 1, 상세 경로 도착), `'쿼리가 잘못되면 요청하지 않는다'`(`amount=1.5`, `paymentKey` 없음, `orderId=../x` 각각), `'502면 다시 확인 버튼으로 같은 요청을 다시 보낸다'`(두 번째 요청 본문이 첫 번째와 같음), `'409면 주문 상세로 간다'`.
  - `PaymentFailPage`: `'메시지와 다시 결제하기 링크를 보여 준다'`, `'orderId가 없으면 주문 내역 링크'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 토스 결제 모듈과 결제 결과 화면 추가`.

### Task 5: 주문서와 장바구니 연결

**Files:**
- Create: `src/lib/checkoutParams.ts`, `src/api/checkout.ts`, `src/components/AddressPicker.tsx`, `src/pages/CheckoutPage.tsx`
- Modify: `src/pages/CartPage.tsx`(주문하기 활성화), `src/routes.tsx`(`/checkout`, `RequireAuth`)
- Test: `src/lib/checkoutParams.test.ts`, `src/pages/CheckoutPage.test.tsx`, `src/pages/CartPage.test.tsx`(수정)

**Interfaces:**
- Consumes: `useAddresses`, `AddressForm`(Task 2), `useMyCoupons`(Task 3), `useCreateOrder`, `startTossPayment`, `TossCancelError`(Task 4).
- Produces: `parseCheckoutItems(raw: string | null): number[] | null` — 콤마로 나눈 값이 모두 양의 정수, 중복 없음, 1~50개일 때만 배열, 아니면 `null`.
- Produces: `useCheckoutPreview(ids, couponId: number | null, points: number)` — `useQuery`(POST), `placeholderData: keepPreviousData`.
- Produces: `maxUsablePoints(price: PriceBreakdown, balance: number): number` = `max(0, min(balance, itemsSalePrice - couponDiscount + shippingFee - 100))`(`checkoutParams.ts`).
- Produces: `<AddressPicker selectedId onSelect(id) />` — "변경" 버튼이 여는 `<dialog>`: 배송지 라디오 목록 + "선택", 목록이 비었으면 `AddressForm`을 바로 보여 주고 저장하면 그 배송지를 고른다.
- `CartPage`: "주문하기"는 선택한 AVAILABLE 줄이 1개 이상이면 활성, 누르면 `/checkout?items={장바구니 순서대로 id 콤마}`. `'주문 기능은 준비 중입니다.'` 문구는 지운다.
- `CheckoutPage`:
  - `parseCheckoutItems`가 `null`이면 요청 없이 `'주문할 상품을 다시 선택해 주세요.'` + 장바구니 링크.
  - 배송지: 기본 배송지(목록 첫 줄)를 미리 고르고 받는 사람·연락처·주소를 보여 줌, `AddressPicker`로 변경. 배송지가 없으면 `'배송지를 등록해 주세요.'`와 피커.
  - 상품 줄: 미리보기 `lines`(이미지·이름·옵션·수량·줄 금액).
  - 쿠폰 `<select aria-label="쿠폰">`: "쿠폰 사용 안 함" + `status==='AVAILABLE'`이고 `useStartsAt <= now < useEndsAt`인 내 쿠폰(`'{name} · {couponDiscountText}'`). 현재 미리보기의 `itemsSalePrice < minOrderAmount`인 쿠폰은 `disabled` + `' (최소 주문금액 미달)'`.
  - 적립금: `'보유 {formatWon(pointBalance)}'`, 숫자 입력(`aria-label="사용할 적립금"`, 음수·소수는 0 이상 정수로 고침), "모두 사용"(`maxUsablePoints`), "적용"(적용한 값만 미리보기에 반영).
  - 금액: 상품 금액(`itemsPrice`), 세일 할인(`-saleDiscount`), 쿠폰 할인(`-couponDiscount`), 배송비, 적립금 사용(`-pointsUsed`), 결제금액(`paymentAmount`, 강조).
  - 미리보기 오류는 서버 `message` `role=alert`(줄 문제 `CART_ITEM_UNAVAILABLE`·`CART_ITEM_NOT_FOUND`면 장바구니 링크도), 오류 중에는 "결제하기" 비활성.
  - "{formatWon(paymentAmount)} 결제하기": 배송지·미리보기 성공이 있어야 활성. 주문 생성 → `startTossPayment(order, {email: me.email, name: me.name})`. 진행 중 버튼 비활성. 주문 생성 실패는 서버 메시지. `TossCancelError`면 `'결제가 취소되었습니다. 주문 상세에서 15분 안에 다시 결제할 수 있습니다.'` + 주문 상세 링크. 그 밖의 결제창 오류는 그 메시지 + 주문 상세 링크.

- [ ] **Step 1: 실패하는 테스트**
  - `checkoutParams`: `parseCheckoutItems('1,2')` → `[1,2]`; `''`, `null`, `'abc'`, `'1,1'`, `'-3'`, `'1.5'`, 51개 → `null`; `maxUsablePoints({itemsSalePrice:20000,couponDiscount:0,shippingFee:0,…}, 50000)===19900`, 잔액 3000 → 3000, `itemsSalePrice:100,shippingFee:0` → 0.
  - `CheckoutPage`(`vi.mock('../payments/toss')`): `'잘못된 items면 요청 없이 안내한다'`, `'기본 배송지와 미리보기 금액을 보여 준다'`, `'쿠폰을 고르면 memberCouponId로 다시 미리보기한다'`, `'최소 주문금액 미달 쿠폰은 고를 수 없다'`, `'적립금 모두 사용은 결제금액 100원을 남긴다'`(요청 본문 `points`), `'적립금 입력의 음수·소수는 0 이상 정수가 된다'`, `'미리보기 오류면 메시지를 보여 주고 결제하기를 막는다'`(400 `POINTS_EXCEED_LIMIT`), `'결제하기는 주문을 만들고 결제창을 연다'`(POST 본문 `{cartItemIds,memberCouponId,points,addressId}`, `startTossPayment`가 응답 주문과 `{email,name}`으로 불림), `'결제창을 닫으면 다시 결제 안내와 주문 상세 링크'`, `'배송지가 없으면 피커에서 추가한 배송지를 고른다'`.
  - `CartPage`: `'주문하기는 선택한 구매 가능 줄로 주문서에 간다'`(location `/checkout?items=1,2`), `'선택이 없으면 주문하기가 막힌다'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 주문서 화면과 장바구니 주문 연결 추가`.

### Task 6: 주문 목록·상세

**Files:**
- Create: `src/hooks/useNow.ts`, `src/pages/OrdersPage.tsx`, `src/pages/OrderDetailPage.tsx`
- Modify: `src/api/orders.ts`(`useOrders`), `src/routes.tsx`(`/mypage/orders`, `/mypage/orders/:orderNumber`, 둘 다 `RequireAuth`)
- Test: `src/pages/OrdersPage.test.tsx`, `src/pages/OrderDetailPage.test.tsx`

**Interfaces:**
- Consumes: `useOrder`, `useCancelOrder`, `useConfirmOrder`, `isPayable`, `isOrderNumber`, `startTossPayment`, `TossCancelError`(Task 4), `ConfirmDialog`(Task 1), `InfiniteSentinel`(10a, `error` prop).
- Produces: `useNow(intervalMs = 1000): number` — `Date.now()`를 주기적으로 갱신.
- Produces: `useOrders()` — `useInfiniteQuery`(`['orders']`, `size=20`).
- `OrdersPage`: 줄마다 `formatDateTime(orderedAt)`, `ORDER_STATUS_LABEL`, 썸네일, `itemCount>1`이면 `'{firstProductName} 외 {itemCount-1}건'`, `formatWon(paymentAmount)`, 상세 링크. 무한 스크롤·다음 페이지 실패 처리는 10a 목록과 같다(데이터가 있으면 목록 유지 + 인라인 오류). 비면 `'주문 내역이 없습니다.'`.
- `OrderDetailPage`:
  - `isOrderNumber`가 아니거나 404면 `'주문을 찾을 수 없습니다.'` + 주문 내역 링크.
  - `location.state.notice`가 있으면 `role=status`로 맨 위에 보여 줌.
  - 주문번호·주문 시각·상태 문구, 상품 줄(상품 상세 링크), 금액 내역(Task 5와 같은 항목), 사용 쿠폰 이름, 배송지, 시각(`canceledAt`+`CANCEL_REASON_LABEL`, `deliveredAt`, `confirmedAt` 있는 것만).
  - `isPayable(order, useNow())`면 `'남은 시간 {mm}:{ss}'` + "결제하기"(`startTossPayment`, 취소·오류 처리는 Task 5와 같은 문구). `PENDING`이지만 결제 불가면 `'결제 기한이 지났습니다.'`.
  - `PAID`면 "주문 취소"(`ConfirmDialog` `'주문을 취소할까요? 결제가 취소되고 쿠폰·적립금이 돌아옵니다.'`). `DELIVERED`면 "구매확정"(`ConfirmDialog` `'구매를 확정할까요? 확정 후에는 취소할 수 없습니다.'`). 실패는 서버 메시지 `role=alert`.

- [ ] **Step 1: 실패하는 테스트**
  - `OrdersPage`: `'상태 문구와 첫 상품 외 N건을 보여 준다'`, `'다음 페이지를 이어 읽는다'`, `'주문이 없으면 안내 문구'`.
  - `OrderDetailPage`: `'결제 대기 주문은 남은 시간과 결제하기를 보여 주고 결제창을 연다'`(`vi.useFakeTimers({shouldAdvanceTime:true})` + `vi.setSystemTime`, `'남은 시간 14:59'` 근처, 클릭 → `startTossPayment`), `'기한이 지나면 결제하기가 사라진다'`(시계를 `expiresAt` 뒤로 → `'결제 기한이 지났습니다.'`, 버튼 없음), `'결제 완료 주문은 확인 뒤 취소한다'`(POST `/cancel` → 상태 `'취소'`, `'고객 취소'`), `'배송 완료 주문은 확인 뒤 구매확정한다'`, `'취소 실패는 서버 메시지를 보여 준다'`(502), `'notice가 있으면 맨 위에 보여 준다'`, `'잘못된 주문번호는 요청 없이 안내한다'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 주문 목록·상세와 재결제·취소·구매확정 추가`.

### Task 7: 데모 쿠폰과 토스 키 안내

**Files:**
- Modify: `scripts/demo/data.mjs`, `scripts/demo/seed.mjs`, `scripts/demo/data.test.mjs`, `README.md`
- Create: `frontend/.env.example`

**Interfaces:**
- Produces: `buildCoupons(now: Date)` — 쿠폰 3개: `'웰컴 3천원 쿠폰'` FIXED 3000·최소 15000·수량 1000·등급 제한 없음, `'전 상품 10% 쿠폰'` RATE 10·최대 5000·최소 20000·수량 1000, `'핑크 등급 5천원 쿠폰'` FIXED 5000·최소 30000·수량 500·`requiredGrade:'PINK'`. 발급 `now−1h ~ now+30d`, 사용 `now−1h ~ now+60d`(ISO 문자열).
- `seed.mjs`: 로그인 직후 쿠폰 단계 — `GET /api/admin/coupons`에 같은 이름이 있으면 그 쿠폰은 건너뛰고 없는 것만 `POST /api/admin/coupons`. 그다음 기존 브랜드 검사(있으면 `'이미 데모 상품이 있습니다.'` 출력 후 종료 코드 0). 이미 상품만 있는 DB에 다시 돌리면 쿠폰만 추가된다.
- `frontend/.env.example`: `VITE_TOSS_CLIENT_KEY=` 한 줄(`frontend/.gitignore`가 `*.local`을 무시하는지 확인하고, 안 하면 `.env.local`을 추가).
- README "토스 테스트 결제" 절: 토스 개발자센터나 문서에서 받은 같은 세트의 테스트 키를 `frontend/.env.local`의 `VITE_TOSS_CLIENT_KEY`(`test_ck_…`)와 백엔드 `TOSS_SECRET_KEY`(`test_sk_…`)에 넣는다, 테스트 키는 실제 돈이 나가지 않는다, 결제창(구버전)용 키여야 하고 결제위젯 키(`test_gck_`)는 쓰지 않는다.

- [ ] **Step 1: 실패하는 테스트** `data.test.mjs` 추가: 쿠폰 3개, 이름 중복 없음, FIXED는 `discountValue` 100~1,000,000·`maxDiscount:null`, RATE는 1~90·`maxDiscount` 있음, `requiredGrade`가 있는 쿠폰 1개, 발급 종료 > 발급 시작, 사용 종료 > 사용 시작.
- [ ] **Step 2:** `node --test scripts/demo/*.test.mjs` 실패 확인 → 구현 → PASS → `node --check scripts/demo/seed.mjs` + 프론트 확인 명령 → 커밋 `feat: 데모 쿠폰 시드와 토스 테스트 키 안내 추가`.

---

## 실행 뒤 확인(컨트롤러, Task 아님)

사용자가 테스트 키를 넣거나 공개 테스트 키 사용을 확인한 뒤: 시드(쿠폰 추가) → 테스트 회원으로 쿠폰 받기 → 배송지 등록 → 장바구니 → 주문서(쿠폰·적립금) → 토스 테스트 결제 → successUrl → 주문 상세(결제 완료) → 주문 취소, 그리고 결제창 닫기 → 주문 상세에서 다시 결제, 관리자 API로 배송 완료로 바꾼 주문의 구매확정.
