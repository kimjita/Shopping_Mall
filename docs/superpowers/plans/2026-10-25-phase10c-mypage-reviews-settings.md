# Phase 10c 마이페이지(리뷰·쿠폰·적립금·설정)와 대기 주문 보정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문서 미리보기·내 쿠폰이 결제 대기 주문이 잡은 쿠폰·적립금을 반영하게 백엔드를 고치고, 프론트에 설정(프로필·피부 프로필·비밀번호·탈퇴), 리뷰(작성 가능·내 리뷰), 내 쿠폰, 적립금 화면을 더한다.

**Architecture:** 백엔드는 미리보기에서 회원의 PENDING 주문을 "가상으로 풀어" 계산한다(주문 생성이 실제로 풀고 계산하는 것과 같은 결과). 프론트는 Phase 10a·10b 구조를 그대로 쓴다.

**Tech Stack:** 백엔드 Java 21·Spring Boot 3.5·JPA·Testcontainers·REST Docs, 프론트 Phase 10b와 같음(새 의존성 없음).

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md`(회원·리뷰·쿠폰·적립금). 백엔드 계약은 `backend/src/docs/asciidoc/index.adoc`(회원, 적립금, 쿠폰, 주문서, 리뷰 장)과 `backend/build/generated-snippets/{member,point,coupon,checkout,review}/*`.

## Global Constraints

- Phase 10a·10b의 Global Constraints가 모두 그대로 적용된다(토큰 색만, 375px 가로 스크롤 없음, 화면 "~니다"·주석/문서 평서체, `formatWon`·`formatDateTime`, 서버 `message` 그대로·필드 오류는 입력 아래, 모든 요청 `apiFetch`, MSW `onUnhandledRequest:'error'`, 데이터가 있으면 오류로 화면을 바꾸지 않음, 커밋 trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, `.idea/*`·`.claude/*` 커밋 금지).
- 백엔드: Flyway 마이그레이션은 만들지 않는다(스키마 변경 없음). 공개 API 응답 형태는 바꾸지 않는다(값의 의미만 문서에 적는다). 백엔드 Task의 확인 명령은 `./gradlew :backend:build`(`zsh -l -c`).
- 프론트 새 의존성 없음.
- 문구:
  - 피부 톤: `COOL` 쿨톤, `WARM` 웜톤, `NEUTRAL` 뉴트럴.
  - 피부 고민: `BLEMISH` 잡티, `WHITENING` 미백, `WRINKLE` 주름, `DEAD_SKIN` 각질, `TROUBLE` 트러블, `BLACKHEAD` 블랙헤드, `EXCESS_SEBUM` 피지 과다, `SENSITIVITY` 민감성, `PORE` 모공, `ELASTICITY` 탄력, `REDNESS` 홍조, `ATOPY` 아토피, `DARK_CIRCLE` 다크서클(이 순서로 표시).
  - 적립금 종류: `EARN` 구매 적립, `USE` 주문 사용, `RESTORE` 사용 취소 복원, `ADMIN_GRANT` 관리자 지급. 증가(EARN·RESTORE·ADMIN_GRANT)는 `+{formatWon}`, 감소(USE)는 `-{formatWon}`.
- 비밀번호 규칙은 회원가입과 같다(`/^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,64}$/`, 문구 `'비밀번호는 영문과 숫자를 각각 1자 이상 포함한 8~64자여야 합니다.'`). 한 곳(`src/lib/password.ts`)에 두고 회원가입도 그것을 쓴다.
- 리뷰 본문 규칙: 앞뒤 공백을 뗀 길이 10~1,000자, 원문 2,000자 이하. 별점 1~5 정수.
- 각 프론트 Task 끝 확인 명령: `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build`.

## Review Focus

1. 사진이 있는 리뷰를 수정 → PUT 본문에 기존 `imageUrls`를 그대로 보내 사진이 지워지지 않는다(Task 5 테스트).
2. 리뷰 본문이 공백뿐이거나 공백을 빼면 10자 미만, 1,000자 초과 → 요청 없이 안내, 글자 수 표시는 공백을 뺀 길이(Task 5 테스트).
3. 비밀번호 변경 성공 → 서버가 refresh token을 모두 폐기했으므로 이 탭도 로그아웃 상태가 되고 로그인 화면에서 안내를 본다(access token이 남아 반쯤 로그인된 상태가 되지 않는다)(Task 3 테스트).
4. 진행 중 주문이 있는 탈퇴 → `409` 메시지를 보이고 로그인 상태 유지, 탈퇴 성공 → 로그아웃·캐시 비움(Task 3 테스트).
5. 결제 대기 주문이 쿠폰 X와 적립금 3,000원을 잡은 상태 → 미리보기에 쿠폰 X를 다시 써도 되고 `pointBalance`는 잔액+3,000, 내 쿠폰에서 X는 `AVAILABLE`; 그 주문이 결제 완료되면 X는 `USED`(Task 1 테스트).

---

## API 계약 (프론트, Task 2에서 `src/api/types.ts`에 더한다)

```ts
export type SkinTone = 'COOL' | 'WARM' | 'NEUTRAL'
export type SkinConcern = 'BLEMISH' | 'WHITENING' | 'WRINKLE' | 'DEAD_SKIN' | 'TROUBLE' | 'BLACKHEAD' | 'EXCESS_SEBUM'
  | 'SENSITIVITY' | 'PORE' | 'ELASTICITY' | 'REDNESS' | 'ATOPY' | 'DARK_CIRCLE'
export type PointType = 'EARN' | 'USE' | 'RESTORE' | 'ADMIN_GRANT'
export interface PointHistoryItem { id: number; type: PointType; amount: number; balanceAfter: number; reason: string | null; orderId: number | null; createdAt: string }
export interface MyReview { reviewId: number; orderItemId: number; productId: number; productName: string; skuName: string; thumbnailUrl: string | null;
  rating: number; content: string; imageUrls: string[]; skinType: SkinType | null; skinTone: SkinTone | null; skinConcerns: SkinConcern[]; createdAt: string; updatedAt: string }
export interface WritableReview { orderItemId: number; orderNumber: string; productId: number; productName: string; skuName: string; thumbnailUrl: string | null; confirmedAt: string }
```

`Me.skinTone`은 `SkinTone | null`, `Me.skinConcerns`는 `SkinConcern[]`, `Review.skinTone`도 `SkinTone | null`로 좁힌다.

엔드포인트(회원 전용 `auth:'required'`): `PATCH members/me {name, phone}` → `Me`, `PUT members/me/skin-profile {skinType|null, skinTone|null, skinConcerns}` → `Me`, `PATCH members/me/password {currentPassword, newPassword}` → 204(`400 PASSWORD_MISMATCH`), `DELETE members/me {password}` → 204(`400 PASSWORD_MISMATCH`, `409 MEMBER_HAS_ACTIVE_ORDER`), `GET members/me/points` → `{balance}`, `GET members/me/points/history?cursor&size` → `CursorPage<PointHistoryItem>`, `POST members/me/reviews {orderItemId, rating, content, imageUrls}` → 201 `MyReview`(`404 ORDER_ITEM_NOT_FOUND`, `409 REVIEW_NOT_WRITABLE`, `409 REVIEW_ALREADY_EXISTS`), `PUT members/me/reviews/{reviewId} {rating, content, imageUrls}` → `MyReview`(`404 REVIEW_NOT_FOUND`), `DELETE members/me/reviews/{reviewId}` → 204, `GET members/me/reviews?cursor&size` → `CursorPage<MyReview>`, `GET members/me/reviews/writable?cursor&size` → `CursorPage<WritableReview>`.

Query key: `['points']`, `['pointHistory']`, `['myReviews']`, `['writableReviews']`. 이 넷을 `AuthProvider`의 회원 전용 key 목록에 더한다. 리뷰 쓰기·수정·삭제 성공 시 `['myReviews']`, `['writableReviews']`, `['reviews', productId]`(10a 상품 리뷰 목록 prefix), `['product', productId]`(평점)를 invalidate한다. 프로필·피부 프로필 저장 성공 시 응답으로 `['me']`를 `setQueryData`.

---

### Task 1: (백엔드) 결제 대기 주문이 잡은 쿠폰·적립금을 미리보기와 내 쿠폰에 반영

**Files:**
- Modify: `backend/src/main/java/com/sprint/mission/checkout/service/CheckoutService.java`, `backend/src/main/java/com/sprint/mission/coupon/service/CouponService.java`, `backend/src/main/java/com/sprint/mission/coupon/dto/MyCouponResponse.java`(필요하면), `backend/src/docs/asciidoc/index.adoc`(쿠폰·주문서 장 설명)
- Create: `backend/src/main/java/com/sprint/mission/checkout/service/PendingHold.java`
- Test: `backend/src/test/java/com/sprint/mission/checkout/controller/CheckoutPreviewApiTest.java`(추가), `backend/src/test/java/com/sprint/mission/coupon/service/CouponServiceTest.java` 또는 `coupon/controller/CouponApiTest.java`(추가)

**Interfaces:**
- Produces: `record PendingHold(Long orderId, Long memberCouponId, long pointsUsed)` + `static PendingHold NONE`. `CheckoutService`에서 `orderRepository.findByMemberIdAndStatus(memberId, PENDING)`로 만든다(만료 여부와 상관없이 — 주문 생성도 PENDING이면 만료 전후 모두 대체한다).
- `CheckoutService.preview`만 hold를 쓴다: `prepare(memberId, ids, couponId, points, now, hold)` 오버로드. 기존 `prepare(memberId, ids, couponId, points, now)`는 `PendingHold.NONE`으로 위임하고 주문 생성(`OrderService.create`)은 그대로 기존 것을 쓴다(이미 대기 주문을 실제로 취소한 뒤라서).
- `CouponService.getUsable(memberId, memberCouponId, now, Long releasedOrderId)` 오버로드: 회원 쿠폰이 `USED`이고 `usedOrderId == releasedOrderId`이면 `AVAILABLE`로 보고 기간을 확인한다. 기존 3인자 메서드는 `null`로 위임.
- 잔액: `pointBalance = pointService.balance(memberId) + hold.pointsUsed()`, 이 값으로 `POINTS_NOT_ENOUGH`를 판단하고 응답 `pointBalance`에도 쓴다.
- `CouponService.myCoupons`: 회원의 PENDING 주문이 쓴 회원 쿠폰(`usedOrderId == pendingOrderId`)은 `status: AVAILABLE`, `usedAt: null`로 내려 준다(EXPIRED 계산은 그대로).
- index.adoc: 쿠폰 장 "쿠폰 상태"와 주문서 장 머리말에 "결제 대기 주문이 있으면 그 주문이 쓴 쿠폰과 적립금은 새 주문에 다시 쓸 수 있으므로, 미리보기와 내 쿠폰은 그 주문을 되돌린 것처럼 계산한다. 실제로 되돌리는 것은 주문 생성 때다." 취지의 평서체 문장.

- [ ] **Step 1: 실패하는 테스트** (기존 테스트 클래스의 setUp·픽스처를 그대로 쓴다)
  - `CheckoutPreviewApiTest`: `결제_대기_주문이_쓴_쿠폰과_적립금으로_다시_미리_본다` — 쿠폰 X와 적립금 3,000원으로 주문을 만들어 PENDING 상태로 둔다(잔액 5,000 → 2,000) → 같은 줄로 `{memberCouponId: X, points: 5000}` 미리보기 → 200, `pointBalance == 5000`, `couponDiscount > 0`. `결제_대기_주문이_없으면_사용한_쿠폰은_여전히_COUPON_NOT_USABLE` — 결제 완료(PAID) 주문이 쓴 쿠폰이면 400 `COUPON_NOT_USABLE`.
  - 쿠폰 목록: `결제_대기_주문이_쓴_쿠폰은_내_쿠폰에서_AVAILABLE` — 위 상태에서 `GET /api/members/me/coupons`의 X가 `AVAILABLE`, `usedAt` null; 주문을 PAID로 바꾼 뒤(테스트 헬퍼·JdbcTemplate 또는 결제 승인 경로) X는 `USED`.
  - 미리보기 뒤 DB 상태 불변: 미리보기 후에도 주문은 PENDING, 회원 쿠폰은 USED, 잔액 2,000.
- [ ] **Step 2:** `zsh -l -c './gradlew :backend:test --tests "*CheckoutPreviewApiTest" --tests "*Coupon*"'` 실패 확인 → 구현 → PASS → `zsh -l -c './gradlew :backend:build'` 통과(REST Docs 포함) → 커밋 `fix: 결제 대기 주문이 잡은 쿠폰·적립금을 미리보기와 내 쿠폰에 반영`.

### Task 2: 타입·문구·API 훅·마이페이지 링크

**Files:**
- Modify: `frontend/src/api/types.ts`(위 계약), `frontend/src/lib/labels.ts`, `frontend/src/auth/AuthProvider.tsx`(회원 전용 key 4개), `frontend/src/pages/MyPage.tsx`, `frontend/src/pages/SignupPage.tsx`(비밀번호 규칙 import)
- Create: `frontend/src/lib/password.ts`, `frontend/src/api/members.ts`(설정용 뮤테이션), `frontend/src/api/points.ts`, `frontend/src/api/myReviews.ts`
- Test: `frontend/src/lib/labels.test.ts`(추가), `frontend/src/lib/password.test.ts`, `frontend/src/pages/MyPage.test.tsx`(수정)

**Interfaces:**
- Produces: `SKIN_TONE_LABEL`, `SKIN_CONCERN_LABEL`(Global 순서의 `SKIN_CONCERNS: SkinConcern[]`도), `POINT_TYPE_LABEL`, `signedPoints(type: PointType, amount: number): string`.
- Produces: `PASSWORD_RULE: RegExp`, `PASSWORD_RULE_MESSAGE: string`, `isValidPassword(s): boolean`.
- Produces(`members.ts`): `useUpdateProfile()`, `useUpdateSkinProfile()` — 성공 응답으로 `['me']` `setQueryData`; `useChangePassword()`, `useWithdraw()`(뮤테이션만, 로그아웃 처리는 Task 3 화면이 한다).
- Produces(`points.ts`): `usePointBalance()`(`['points']`), `usePointHistory()`(`['pointHistory']` 무한, `size=20`).
- Produces(`myReviews.ts`): `useWritableReviews()`(`['writableReviews']` 무한), `useMyReviews()`(`['myReviews']` 무한), `useWriteReview()`, `useEditReview()` — `mutate({reviewId, productId, body})`, `useDeleteReview()` — `mutate({reviewId, productId})`; 성공 시 API 계약의 invalidate 목록.
- `MyPage`: 링크에 리뷰 `/mypage/reviews`, 내 쿠폰 `/mypage/coupons`, 적립금 `/mypage/points`, 설정 `/mypage/settings`를 더한다(기존 다섯 링크 유지, 순서: 주문 내역, 리뷰, 내 쿠폰, 쿠폰 받기, 적립금, 배송지 관리, 찜, 최근 본 상품, 설정).

- [ ] **Step 1: 실패하는 테스트** — `signedPoints('USE', 500)==='-500원'`, `signedPoints('RESTORE', 500)==='+500원'`, `SKIN_CONCERNS.length===13`이고 첫 원소 `'BLEMISH'`·마지막 `'DARK_CIRCLE'`, `SKIN_TONE_LABEL.NEUTRAL==='뉴트럴'`; `isValidPassword('abcdefgh')===false`, `'password1'`→true, `'pass word1'`→false; `MyPage` 아홉 링크 href.
- [ ] **Step 2:** 실패 확인 → 구현(회원가입 기존 테스트가 그대로 통과해야 한다) → PASS → 확인 명령 → 커밋 `feat: 마이페이지 확장용 타입·문구·API 훅 추가`.

### Task 3: 설정 페이지(프로필·피부 프로필·비밀번호·탈퇴)

**Files:**
- Create: `frontend/src/pages/SettingsPage.tsx`
- Modify: `frontend/src/routes.tsx`(`/mypage/settings`, `RequireAuth`), `frontend/src/pages/LoginPage.tsx`(`location.state.notice`를 `role=status`로 보여 줌)
- Test: `frontend/src/pages/SettingsPage.test.tsx`, `frontend/src/pages/LoginPage.test.tsx`(추가)

**Interfaces:**
- Consumes: Task 2의 `members.ts` 훅, 라벨, `isValidPassword`; `useAuth().logout()`, `ConfirmDialog`.
- 섹션(각각 `<section>`+제목, 자기 "저장" 버튼과 자기 성공 `role=status`·실패 `role=alert`):
  - **프로필**: 이름·휴대폰 번호(값은 `me`로 초기화, `autoComplete` `name`/`tel`), 저장 → `'프로필을 저장했습니다.'`. 서버 필드 오류는 입력 아래.
  - **피부 프로필**: 피부 타입 라디오(선택 안 함 + 5개, `role=radiogroup` `aria-label="피부 타입"`), 피부 톤 라디오(선택 안 함 + 3개), 피부 고민 체크박스 13개(`SKIN_CONCERNS` 순서). 저장 → `PUT {skinType|null, skinTone|null, skinConcerns}` → `'피부 프로필을 저장했습니다.'`.
  - **비밀번호 변경**: 현재 비밀번호·새 비밀번호·새 비밀번호 확인(`autoComplete` `current-password`/`new-password`). 클라이언트 검사: 새 비밀번호 규칙(`PASSWORD_RULE_MESSAGE`), 확인 불일치 `'새 비밀번호가 일치하지 않습니다.'`(요청 없음). 성공 → `await logout()` → `navigate('/login', {replace:true, state:{notice:'비밀번호를 바꿨습니다. 다시 로그인해 주세요.'}})`. `PASSWORD_MISMATCH`는 현재 비밀번호 아래 서버 메시지.
  - **회원 탈퇴**: 안내 `'탈퇴하면 장바구니·찜·최근 본 상품이 지워지고 같은 이메일로 다시 가입할 수 없습니다. 작성한 리뷰는 남습니다.'` + "회원 탈퇴" 버튼 → `<dialog>`(비밀번호 입력 + "탈퇴" 버튼, `ConfirmDialog`를 쓰지 않고 입력이 있는 자체 dialog). 성공 → `await logout()` → `navigate('/login', {replace:true, state:{notice:'탈퇴가 완료되었습니다.'}})`. 실패(`PASSWORD_MISMATCH`, `MEMBER_HAS_ACTIVE_ORDER`)는 dialog 안에 서버 메시지, 로그인 유지.

- [ ] **Step 1: 실패하는 테스트**
  - `'프로필을 저장하면 PATCH하고 안내한다'`(본문 `{name, phone}`), `'피부 프로필은 선택한 값으로 PUT한다'`(타입 `DRY`, 톤 선택 안 함, 고민 `PORE`·`WRINKLE` → 본문 `{skinType:'DRY', skinTone:null, skinConcerns:['WRINKLE','PORE']}` — `SKIN_CONCERNS` 순서), `'피부 프로필은 me 값으로 미리 채운다'`.
  - `'새 비밀번호 확인이 다르면 요청하지 않는다'`, `'비밀번호 규칙을 먼저 검사한다'`, `'비밀번호를 바꾸면 로그아웃하고 로그인 화면에서 안내한다'`(PATCH 204 → logout API 호출 → `/login` 라우트에서 notice 보임, 이후 `useAuth` 상태가 guest), `'현재 비밀번호가 틀리면 그 입력 아래 메시지'`.
  - `'진행 중 주문이 있으면 탈퇴 창에 메시지를 보이고 로그인 상태를 유지한다'`(409), `'탈퇴하면 로그아웃하고 안내한다'`(DELETE 본문 `{password}` → `/login` notice).
  - `LoginPage`: `'state의 notice를 보여 준다'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 회원 설정 화면(프로필·피부·비밀번호·탈퇴) 추가`.

### Task 4: 적립금·내 쿠폰 화면

**Files:**
- Create: `frontend/src/pages/PointsPage.tsx`, `frontend/src/pages/MyCouponsPage.tsx`
- Modify: `frontend/src/routes.tsx`(`/mypage/points`, `/mypage/coupons`, 둘 다 `RequireAuth`)
- Test: `frontend/src/pages/PointsPage.test.tsx`, `frontend/src/pages/MyCouponsPage.test.tsx`

**Interfaces:**
- Consumes: `usePointBalance`, `usePointHistory`(Task 2), `useMyCoupons`(10b), `couponDiscountText`, `minOrderText`, `InfiniteSentinel`(`errorText` 지원).
- `PointsPage`: `'보유 적립금'` + `formatWon(balance)`; 내역 줄마다 `formatDateTime(createdAt)`, `POINT_TYPE_LABEL`, `reason`(있으면), `signedPoints`, `'잔액 {formatWon(balanceAfter)}'`. 무한 스크롤(다음 페이지 실패 문구 `'다음 내역을 불러오지 못했습니다.'`). 비면 `'적립금 내역이 없습니다.'`.
- `MyCouponsPage`: 네 묶음 제목과 순서 — `'사용 가능'`(`AVAILABLE`이고 `useStartsAt <= now`), `'사용 시작 전'`(`AVAILABLE`이고 `useStartsAt > now`), `'사용 완료'`(`USED`, `usedAt` 표시), `'기간 만료'`(`EXPIRED`). 빈 묶음은 제목째 숨김. 카드: 이름, `couponDiscountText`, `minOrderText`, `'사용 기간 {start} ~ {end}'`. 맨 위에 "쿠폰 받으러 가기"(`/coupons`). 모두 비면 `'받은 쿠폰이 없습니다.'`.

- [ ] **Step 1: 실패하는 테스트** — `PointsPage`: `'잔액과 내역을 부호와 함께 보여 준다'`(USE `-500원`, EARN `+300원`, 종류 문구), `'다음 내역을 이어 읽는다'`, `'내역이 없으면 안내'`. `MyCouponsPage`: `'상태별로 묶어 보여 준다'`(가짜 시계 또는 고정 날짜로 네 묶음 각각 하나), `'빈 묶음은 숨긴다'`, `'받은 쿠폰이 없으면 안내와 쿠폰 받기 링크'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 적립금 내역과 내 쿠폰 화면 추가`.

### Task 5: 리뷰 화면(작성 가능·내 리뷰)과 주문 상세 연결

**Files:**
- Create: `frontend/src/components/ReviewForm.tsx`, `frontend/src/pages/MyReviewsPage.tsx`
- Modify: `frontend/src/routes.tsx`(`/mypage/reviews`, `RequireAuth`), `frontend/src/pages/OrderDetailPage.tsx`(구매확정이면 `'리뷰 쓰기'` 링크 → `/mypage/reviews?tab=writable`)
- Test: `frontend/src/components/ReviewForm.test.tsx`, `frontend/src/pages/MyReviewsPage.test.tsx`, `frontend/src/pages/OrderDetailPage.test.tsx`(추가)

**Interfaces:**
- Consumes: `useWritableReviews`, `useMyReviews`, `useWriteReview`, `useEditReview`, `useDeleteReview`(Task 2), `ConfirmDialog`, `InfiniteSentinel`.
- Produces: `<ReviewForm initial?: {rating: number; content: string} submitLabel pending error? onSubmit({rating, content}) onCancel />` — 별점 라디오 5개(`role=radiogroup` `aria-label="별점"`, 각 `aria-label` `'별점 {n}점'`, 처음엔 미선택), 본문 `<textarea aria-label="리뷰 내용" maxLength={2000}>`, 글자 수 `'{trimmedLength}/1,000자'`. 제출 검사: 별점 미선택 `'별점을 골라 주세요.'`, 공백 뺀 길이 10 미만 `'리뷰는 10자 이상 써 주세요.'`, 1,000 초과 `'리뷰는 1,000자 이하로 써 주세요.'`(요청 없음). `onSubmit`에는 원문 그대로(서버가 strip) 넘긴다.
- `MyReviewsPage`: 탭(`role=tablist`) "작성 가능한 리뷰" / "내 리뷰", 현재 탭은 쿼리 `tab`(`writable` 기본, `mine`)에 둔다.
  - 작성 가능: 줄마다 썸네일·상품명·옵션·`'{formatDateTime(confirmedAt)} 구매확정'`·"리뷰 쓰기" → `<dialog>`에 `ReviewForm` → `POST {orderItemId, rating, content, imageUrls: []}` → 성공 시 dialog 닫고 `role=status` `'리뷰를 등록했습니다.'`. 비면 `'작성할 리뷰가 없습니다.'`.
  - 내 리뷰: 줄마다 상품명(상세 링크 `/products/{productId}`)·옵션·별점(`'★ {rating}'`)·본문·사진(있으면 썸네일, 수정 불가 안내 `'사진은 그대로 유지됩니다.'`)·작성일, "수정"(dialog `ReviewForm initial`) → `PUT {rating, content, imageUrls: 기존 그대로}` → `'리뷰를 수정했습니다.'`, "삭제"(`ConfirmDialog` `'리뷰를 삭제할까요? 삭제하면 같은 상품에 다시 쓸 수 있습니다.'`). 비면 `'작성한 리뷰가 없습니다.'`.
  - 서버 오류(`REVIEW_ALREADY_EXISTS`, `REVIEW_NOT_WRITABLE`, `REVIEW_NOT_FOUND` 등)는 dialog 안 `role=alert`.
- 두 목록 모두 무한 스크롤·다음 페이지 실패 인라인(문구 `'다음 리뷰를 불러오지 못했습니다.'`).

- [ ] **Step 1: 실패하는 테스트**
  - `ReviewForm`: `'공백을 뺀 글자 수를 보여 준다'`(`'  좋아요  '` → `'3/1,000자'`), `'별점이 없으면 제출하지 않는다'`, `'공백만이거나 10자 미만이면 제출하지 않는다'`, `'1,000자를 넘으면 제출하지 않는다'`, `'규칙을 지키면 원문으로 onSubmit을 부른다'`.
  - `MyReviewsPage`: `'작성 가능한 리뷰에 쓰면 POST하고 목록을 다시 읽는다'`(본문 `{orderItemId, rating, content, imageUrls:[]}`, writable 재요청), `'사진이 있는 리뷰를 고치면 사진 목록을 그대로 보낸다'`(기존 `imageUrls` 2개 → PUT 본문에 같은 2개), `'삭제는 확인 뒤 DELETE하고 상품 평점 캐시를 무효화한다'`(test queryClient의 `['product', productId]` `isInvalidated`), `'이미 쓴 리뷰면 서버 메시지를 dialog에 보여 준다'`(409), `'tab 쿼리로 내 리뷰 탭을 연다'`.
  - `OrderDetailPage`: `'구매확정 주문에는 리뷰 쓰기 링크가 있다'`(href `/mypage/reviews?tab=writable`), 다른 상태에는 없음.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 리뷰 작성·수정·삭제 화면 추가`.

---

## 실행 뒤 확인(컨트롤러, Task 아님)

백엔드 재시작 → 브라우저: 결제 대기 주문이 있는 상태로 주문서에 쿠폰·적립금이 다시 보이는지, 설정 각 섹션 저장, 비밀번호 변경 후 재로그인, 적립금·내 쿠폰 화면. 리뷰는 구매확정 주문이 필요하다(토스 키가 없으면 관리자 API·SQL로 테스트 주문을 CONFIRMED로 만들어 확인).
