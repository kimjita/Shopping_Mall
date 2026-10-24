# Phase 10a 사용자 프론트(구경하기~장바구니) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `frontend/`에 듀이(Dewy) 사용자 화면 전반부(골격·인증·메인·목록/검색·상세·찜·최근 본 상품·장바구니)와 데모 시드 스크립트를 만든다.

**Architecture:** Vite SPA가 Vite 프록시로 같은 출처의 `/api`(Spring, 8080)를 부른다. access token은 메모리에만 두고, refresh는 탭 안에서는 Promise 하나, 탭 사이에서는 `navigator.locks`로 한 번에 하나만 보낸다. 서버 상태는 TanStack Query, 목록 필터는 URL 쿼리가 진실의 원천이다.

**Tech Stack:** Node 22+(CI 22, 로컬 26), Vite, React 19, TypeScript, Tailwind CSS v4(`@tailwindcss/vite`), React Router v7(`react-router`), TanStack Query v5, Vitest + jsdom + React Testing Library + MSW v2, ESLint(typescript-eslint, react-hooks).

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` (Phase 10). 백엔드 API 계약은 `backend/src/docs/asciidoc/index.adoc`과 `backend/build/generated-snippets/*`가 기준이다.

## Global Constraints

- 브랜드: 이름 `dewy`(로고 표기 소문자), 한글 `듀이`. 대표 색 `#14B8A6`, 글자 색 짙은 남색 `#0F172A`. Tailwind `@theme`에 `--color-brand: #14B8A6`, `--color-brand-dark: #0F766E`, `--color-ink: #0F172A`, `--color-sale: #E11D48`로 둔다. 색은 이 토큰으로만 쓴다.
- 모바일 우선 반응형. 기준 폭 375px에서 가로 스크롤이 생기지 않는다. 상품 그리드는 2열(기본) → 3열(`md`) → 4열(`lg`).
- 의존성은 Tech Stack에 적힌 것만 쓴다. UI 라이브러리·상태 관리 라이브러리·캐러셀·아이콘 패키지를 추가하지 않는다. 모달은 `<dialog>`, 아이콘은 인라인 SVG다. 버전은 `npm install --save-exact`로 고정하고 `package-lock.json`을 커밋한다.
- 화면 문구는 한국어이고 안내·오류 문장은 "~니다"체다. 코드 주석·README는 평서체(~다)다.
- 금액은 `formatWon(n)` = `n.toLocaleString('ko-KR') + '원'` 하나로만 표시한다.
- 서버 오류 응답은 `{code, message, errors:[{field, reason}]}`다. 사용자에게는 `message`를 그대로 보여 준다. 필드 오류는 `field`에 맞는 입력 아래에 `reason`을 보여 준다.
- 피부 타입 문구: `DRY` 건성, `OILY` 지성, `COMBINATION` 복합성, `SENSITIVE` 민감성, `NORMAL` 중성.
- 정렬 문구: `NEWEST` 신상품순, `SALES` 판매순, `PRICE_ASC` 낮은 가격순, `PRICE_DESC` 높은 가격순, `RATING` 평점순, `REVIEW_COUNT` 리뷰 많은순. 리뷰 정렬: `LATEST` 최신순, `RATING_DESC` 별점 높은순, `RATING_ASC` 별점 낮은순.
- 장바구니 상태 문구: `NOT_ENOUGH_STOCK` 재고 부족, `SOLD_OUT` 품절, `UNAVAILABLE` 판매 종료(`AVAILABLE`은 표시하지 않음).
- 모든 `fetch`는 `src/api/client.ts`의 `apiFetch`만 거친다. jsdom의 fetch는 상대 경로를 못 받으므로 `apiFetch`가 `new URL(path, window.location.origin)`으로 바꾼다.
- 테스트는 MSW `setupServer`로 API를 흉내 낸다. 핸들러 응답 JSON은 이 계획의 `src/api/types.ts` 타입을 따른다. `onUnhandledRequest: 'error'`.
- 커밋 메시지는 한국어 `feat:`/`fix:`/`test:`/`chore:`/`docs:` 접두사, 마지막 줄은 정확히 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `.idea/*`는 커밋하지 않는다.
- 각 Task 끝의 확인 명령: `cd frontend && npm run lint && npm run typecheck && npm test -- --run && npm run build` 전부 통과.

## Review Focus

1. 로그인 화면의 `?redirect=//evil.com`, `?redirect=/\evil.com`, `?redirect=https://evil.com` → 외부로 가지 않고 `/`로 간다(Task 3 테스트).
2. 만료된 access token을 들고 메인을 열고 refresh 쿠키도 만료됨 → 공개 섹션은 토큰 없이 다시 불러 정상 표시, 로그인 상태는 비회원으로 바뀐다(Task 2 테스트).
3. 같은 탭에서 401이 동시에 여러 개(StrictMode 이중 부트스트랩 포함) → refresh 요청은 정확히 한 번(Task 2 테스트).
4. 손으로 고친 URL 쿼리(`sort=FOO`, `minPrice=abc`, `maxPrice`<`minPrice`, `brandIds` 21개 이상, `categoryId=-1`) → 400 화면이 아니라 잘못된 값을 버리고 목록을 보여 준다(Task 6 테스트).
5. localStorage의 최근 본 상품이 깨졌거나(JSON 아님, 배열 아님, 숫자 아닌 원소) 그 사이 삭제된 상품(상세 404) → 오류 없이 빈 목록 또는 남은 상품만 보이고 삭제된 id는 저장소에서 빠진다(Task 8 테스트).

---

## File Structure

```
frontend/
  package.json, package-lock.json, vite.config.ts, tsconfig*.json, eslint.config.js, index.html
  public/demo/*.svg                  데모 상품·배너 이미지 (Task 10)
  src/
    main.tsx, App.tsx (Providers + RouterProvider), routes.tsx, index.css(@theme 토큰)
    test/setup.ts, test/server.ts (MSW), test/render.tsx (renderWithProviders)
    api/types.ts      서버 응답 타입(아래 계약)
    api/client.ts     apiFetch, ApiError
    api/*.ts          도메인별 호출 함수 + query hook (catalog, member, cart, wishlist, recentViews, reviews, home)
    auth/tokenStore.ts, auth/refresh.ts, auth/AuthProvider.tsx, auth/RequireAuth.tsx, auth/safeRedirect.ts
    lib/format.ts, lib/labels.ts, lib/listParams.ts, lib/guestRecentViews.ts, lib/cartTotals.ts, lib/links.ts
    components/       Header, CategoryDrawer, ProductCard, ProductGrid, Price, ProductImage, WishButton,
                      SectionError, InfiniteSentinel, QuantityStepper, Spinner
    pages/            HomePage, ProductListPage, ProductDetailPage, LoginPage, SignupPage,
                      WishlistPage, RecentViewsPage, CartPage, NotFoundPage
scripts/demo/seed.mjs, scripts/demo/data.mjs, scripts/demo/data.test.mjs
.github/workflows/ci.yml (frontend job 추가)
README.md (루트, 프론트 실행·데모 시드 절)
```

## API 계약 (`src/api/types.ts`, Task 2에서 만든다)

```ts
export type SkinType = 'DRY' | 'OILY' | 'COMBINATION' | 'SENSITIVE' | 'NORMAL';
export type ProductSort = 'NEWEST' | 'SALES' | 'PRICE_ASC' | 'PRICE_DESC' | 'RATING' | 'REVIEW_COUNT';
export type ReviewSort = 'LATEST' | 'RATING_DESC' | 'RATING_ASC';
export type CartItemStatus = 'AVAILABLE' | 'NOT_ENOUGH_STOCK' | 'SOLD_OUT' | 'UNAVAILABLE';
export interface ErrorBody { code: string; message: string; errors: { field: string; reason: string }[] }
export interface TokenResponse { accessToken: string; tokenType: 'Bearer'; expiresIn: number }
export interface Me { id: number; email: string; name: string; phone: string; role: 'USER' | 'ADMIN';
  grade: string; skinType: SkinType | null; skinTone: string | null; skinConcerns: string[] }
export interface ProductSummary { id: number; name: string; brandName: string; thumbnailUrl: string | null;
  price: number; salePrice: number; saleRate: number | null; soldOut: boolean; ratingAvg: number | null; reviewCount: number }
export interface CursorPage<T> { items: T[]; nextCursor: string | null }
export interface Category { id: number; name: string; children: Category[] }
export interface Brand { id: number; name: string; description: string | null }
export interface ProductDetail { id: number; name: string; brand: { id: number; name: string };
  categoryPath: { id: number; name: string }[]; summary: string | null; description: string; ingredients: string | null;
  imageUrls: string[]; basePrice: number; saleRate: number | null; soldOut: boolean;
  skus: { id: number; name: string; price: number; salePrice: number; soldOut: boolean }[];
  ratingAvg: number | null; reviewCount: number }
export interface RankingItem { rank: number; product: ProductSummary }
export interface Recommendations { skinType: SkinType; items: { rank: number; product: ProductSummary;
  sameSkinReviewCount: number; sameSkinRatingAvg: number }[] }
export interface Banner { id: number; title: string; imageUrl: string; linkUrl: string; startsAt: string; endsAt: string; sortOrder: number }
export interface Review { reviewId: number; authorName: string; rating: number; content: string; imageUrls: string[];
  skuName: string; skinType: SkinType | null; skinTone: string | null; skinConcerns: string[]; createdAt: string; updatedAt: string }
export interface WishItem { product: ProductSummary; available: boolean; wishedAt: string }
export interface RecentView { product: ProductSummary; available: boolean; viewedAt: string }
export interface CartItem { id: number; skuId: number; skuName: string; productId: number; productName: string;
  brandName: string; thumbnailUrl: string | null; quantity: number; unitPrice: number; unitSalePrice: number;
  lineTotal: number; status: CartItemStatus }
```

엔드포인트(모두 `/api` 접두사): `POST auth/signup {email,password,name,phone}` → 201, `POST auth/login {email,password}` → `TokenResponse`, `POST auth/refresh` → `TokenResponse`, `POST auth/logout` → 204, `GET members/me` → `Me`, `GET categories` → `Category[]`, `GET brands` → `Brand[]`, `GET products?keyword&categoryId&brandIds&minPrice&maxPrice&onSale&sort&cursor&size` → `CursorPage<ProductSummary>`, `GET products/{id}` → `ProductDetail`, `GET products/{id}/reviews?sort&skinType&cursor&size` → `CursorPage<Review>`, `GET rankings?categoryId` → `{items: RankingItem[]}`, `GET recommendations?skinType` → `Recommendations`, `GET banners` → `Banner[]`, `PUT|DELETE members/me/wishlist/{productId}` → 204, `GET members/me/wishlist?cursor&size` → `CursorPage<WishItem>`, `GET members/me/wishlist/ids?productIds=1,2` → `{productIds: number[]}`(1~100개), `POST members/me/recent-views {productId}` → 204, `GET members/me/recent-views` → `RecentView[]`, `DELETE members/me/recent-views[/{productId}]` → 204, `GET members/me/cart` → `{items: CartItem[]}`, `POST members/me/cart/items {skuId,quantity}` → 201 `CartItem`, `PATCH members/me/cart/items/{id} {quantity}` → `CartItem`, `DELETE members/me/cart/items/{id}` → 204.

---

### Task 1: 프론트 골격·디자인 토큰·CI

**Files:**
- Create: `frontend/` 전체 골격(`package.json`, `vite.config.ts`, `tsconfig.json`/`tsconfig.app.json`/`tsconfig.node.json`, `eslint.config.js`, `index.html`, `src/main.tsx`, `src/App.tsx`, `src/routes.tsx`, `src/index.css`, `src/test/setup.ts`, `src/test/server.ts`, `src/test/render.tsx`, `src/pages/HomePage.tsx`(빈 자리), `src/pages/NotFoundPage.tsx`, `frontend/.gitignore`)
- Modify: `.github/workflows/ci.yml`, `README.md`(없으면 생성)
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Produces: `renderWithProviders(ui: ReactElement, opts?: { route?: string }): RenderResult & { queryClient: QueryClient }` — 새 `QueryClient`(`retry: false`)와 `MemoryRouter`(initialEntries=[route ?? '/'])로 감싼다. Task 3 이후에는 `AuthProvider`도 감싼다(Task 3이 이 함수를 고친다).
- Produces: `server`(MSW `setupServer()`), `setup.ts`에서 `beforeAll(listen({onUnhandledRequest:'error'}))`·`afterEach(resetHandlers + localStorage.clear())`·`afterAll(close)`.
- Produces: `routes: RouteObject[]`(Task 4~9가 항목을 더한다), `npm` 스크립트 `dev`, `build`(`tsc -b && vite build`), `lint`(`eslint .`), `typecheck`(`tsc -b --noEmit` 또는 `tsc -p tsconfig.app.json --noEmit`), `test`(`vitest`).

- [ ] **Step 1:** `npm create vite@latest frontend -- --template react-ts`로 만들고, Tech Stack 의존성을 `--save-exact`로 설치한다. `package.json`에 `"engines": {"node": ">=22"}`.
- [ ] **Step 2:** `vite.config.ts` — 플러그인 `react()`, `tailwindcss()`; `server.proxy['/api'] = { target: 'http://localhost:8080', changeOrigin: false }`; `test: { environment: 'jsdom', setupFiles: './src/test/setup.ts', globals: false }`. `setup.ts`에서 `@testing-library/jest-dom/vitest`를 import한다.
- [ ] **Step 3:** `src/index.css` — `@import "tailwindcss";`와 Global Constraints의 `@theme` 토큰, `body`에 `bg-white text-ink`, 기본 글꼴은 시스템 글꼴(`Pretendard`를 쓰지 않는다 — 의존성 금지).
- [ ] **Step 4: 실패하는 테스트** `App.test.tsx`:

```tsx
it('첫 화면에 dewy 로고 링크가 있다', async () => {
  renderWithProviders(<AppRoutes />, { route: '/' });
  expect(await screen.findByRole('link', { name: 'dewy 홈' })).toHaveAttribute('href', '/');
});
it('없는 주소는 안내 화면을 보여 준다', async () => {
  renderWithProviders(<AppRoutes />, { route: '/nope' });
  expect(await screen.findByText('페이지를 찾을 수 없습니다.')).toBeInTheDocument();
});
```

`AppRoutes`는 `routes.tsx`가 내보내는 `useRoutes(routes)` 컴포넌트다(테스트는 MemoryRouter, 앱은 `createBrowserRouter(routes)`). 루트 레이아웃(`Layout`)은 이 Task에서 로고 링크(`aria-label="dewy 홈"`)만 있는 헤더와 `<Outlet/>`이다(Task 4가 채운다).
- [ ] **Step 5:** `npm test -- --run` → 실패 확인 후 구현, 다시 실행해 PASS.
- [ ] **Step 6:** CI — `.github/workflows/ci.yml`에 `frontend` job 추가: `actions/checkout@v4`, `actions/setup-node@v4`(`node-version: '22'`, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`), `working-directory: frontend`에서 `npm ci`, `npm run lint`, `npm run typecheck`, `npm test -- --run`, `npm run build`. 기존 `backend` job은 건드리지 않는다.
- [ ] **Step 7:** README에 "프론트 실행" 절: `./gradlew :backend:bootRun`(8080) + `cd frontend && npm install && npm run dev`(5173). Safari는 `http://localhost`에서 `Secure` 쿠키를 저장하지 않으므로 백엔드를 `APP_AUTH_REFRESH_COOKIESECURE=false`로 띄운다는 문장.
- [ ] **Step 8:** 확인 명령 전부 통과 후 커밋 `chore: 프론트 골격과 듀이 디자인 토큰, 프론트 CI 추가`.

### Task 2: API 클라이언트와 토큰 갱신

**Files:**
- Create: `src/api/types.ts`(위 계약 그대로), `src/api/client.ts`, `src/auth/tokenStore.ts`, `src/auth/refresh.ts`
- Test: `src/api/client.test.ts`, `src/auth/refresh.test.ts`

**Interfaces:**
- Produces: `class ApiError extends Error { status: number; code: string; fieldErrors: Record<string, string> }` — 본문이 `ErrorBody`가 아니면 `code='UNKNOWN'`, `message='잠시 후 다시 시도해 주세요.'`.
- Produces: `apiFetch<T>(path: string, init?: RequestInit & { auth?: 'none' | 'optional' | 'required'; json?: unknown }): Promise<T>` — 기본 `auth='optional'`. `json`이 있으면 `Content-Type: application/json` + `JSON.stringify`. 204면 `undefined as T`. `credentials: 'same-origin'`.
- Produces: `tokenStore`: `getToken(): string | null`, `setToken(t: string | null): void`, `subscribe(fn: (t: string | null) => void): () => void`.
- Produces: `refreshAccessToken(): Promise<string | null>` — 성공하면 새 토큰을 `tokenStore`에 넣고 돌려준다. 401이면 `setToken(null)` 후 `null`. 네트워크/5xx면 던진다(로그아웃시키지 않는다).
- Produces: `authChannel`: `broadcast(msg: { type: 'login' | 'logout' }): void`, `onMessage(fn): () => void` — `BroadcastChannel('dewy-auth')`가 없으면 아무것도 하지 않는다.

`apiFetch`의 401 처리(이 순서가 결정이다):
1. `auth='none'`이면 그대로 `ApiError`.
2. 아니면 `refreshAccessToken()`을 한 번 부른다. 새 토큰이 있으면 같은 요청을 새 토큰으로 **한 번만** 다시 보낸다(재시도의 401은 그대로 던진다).
3. refresh가 `null`이면 `'optional'`은 토큰 없이 한 번 다시 보내고, `'required'`는 `ApiError(401)`을 던진다.
- 토큰이 없는 `'required'` 요청도 먼저 refresh를 시도한다(새로고침 직후 부트스트랩과 겹칠 때).

`refreshAccessToken` 단일 요청:

```ts
let inFlight: Promise<string | null> | null = null;
export function refreshAccessToken() {
  // 같은 탭의 동시 호출(StrictMode 포함)은 진행 중인 Promise 하나를 같이 기다린다.
  inFlight ??= withCrossTabLock(doRefresh).finally(() => { inFlight = null; });
  return inFlight;
}
// 다른 탭과 같은 refresh 쿠키를 동시에 보내면 서버가 재사용으로 보고 패밀리를 폐기하므로 탭 사이에서도 줄을 세운다.
const withCrossTabLock = <T,>(fn: () => Promise<T>) =>
  navigator.locks ? navigator.locks.request('dewy-refresh', fn) : fn();
```

`doRefresh`는 `fetch('/api/auth/refresh', {method:'POST'})`를 `apiFetch(..., {auth:'none'})`로 부른다.

- [ ] **Step 1: 실패하는 테스트** `client.test.ts` (MSW):
  - `'401이 동시에 세 번 나도 refresh는 한 번만 보낸다'`: 토큰 `old`, `/api/members/me`는 `Bearer new`일 때만 200·아니면 401, refresh 핸들러 호출 수를 센다. `Promise.all`로 `apiFetch('/api/members/me',{auth:'required'})` 3번 → 모두 성공, refresh 호출 수 `1`, `getToken()==='new'`.
  - `'refresh가 401이면 optional 요청은 토큰 없이 다시 보낸다'`: `/api/banners`는 Authorization 헤더가 있으면 401, 없으면 `[]`; refresh 401 → 결과 `[]`, 마지막 요청에 Authorization 헤더 없음, `getToken()===null`.
  - `'refresh가 401이면 required 요청은 401 ApiError'`: `rejects.toMatchObject({status:401})`.
  - `'재시도도 401이면 다시 refresh하지 않는다'`: refresh는 항상 200 `new`, 대상은 항상 401 → `ApiError(401)`, refresh 호출 수 `1`.
  - `'auth none은 401에 refresh하지 않는다'`: `/api/auth/login` 401 `INVALID_CREDENTIALS` → `ApiError{code:'INVALID_CREDENTIALS'}`, refresh 호출 수 `0`.
  - `'오류 본문의 errors를 fieldErrors로 바꾼다'`: 400 `{code:'INVALID_INPUT',message:'입력값이 올바르지 않습니다.',errors:[{field:'email',reason:'이메일 형식이 아닙니다.'}]}` → `fieldErrors.email === '이메일 형식이 아닙니다.'`.
  - `'JSON이 아닌 오류 본문은 UNKNOWN'`: 502 text → `code==='UNKNOWN'`.
- [ ] **Step 2:** `refresh.test.ts`:
  - `'navigator.locks가 있으면 dewy-refresh 잠금 안에서 보낸다'`: `vi.stubGlobal('navigator', {...navigator, locks: {request: vi.fn((name, fn) => fn())}})` → `request`가 `'dewy-refresh'`로 1번 불림.
  - `'refresh 5xx는 토큰을 지우지 않고 던진다'`: 토큰 `old`, refresh 503 → reject, `getToken()==='old'`.
- [ ] **Step 3:** 실패 확인 → 구현 → PASS.
- [ ] **Step 4:** 확인 명령 통과 후 커밋 `feat: API 클라이언트와 탭 간 단일 토큰 갱신 추가`.

### Task 3: 인증 상태·로그인·회원가입

**Files:**
- Create: `src/auth/AuthProvider.tsx`, `src/auth/RequireAuth.tsx`, `src/auth/safeRedirect.ts`, `src/api/member.ts`, `src/pages/LoginPage.tsx`, `src/pages/SignupPage.tsx`
- Modify: `src/test/render.tsx`(AuthProvider 감싸기), `src/routes.tsx`(`/login`, `/signup`), `src/App.tsx`
- Test: `src/auth/safeRedirect.test.ts`, `src/auth/AuthProvider.test.tsx`, `src/pages/LoginPage.test.tsx`, `src/pages/SignupPage.test.tsx`

**Interfaces:**
- Consumes: Task 2의 `apiFetch`, `tokenStore`, `refreshAccessToken`, `authChannel`.
- Produces: `useAuth(): { status: 'loading' | 'guest' | 'member'; me: Me | undefined; login(email: string, password: string): Promise<void>; logout(): Promise<void> }`.
  - 마운트 시 `refreshAccessToken()` 한 번(StrictMode 이중 실행은 Task 2의 inFlight가 막는다). 토큰이 있으면 `useQuery(['me'], GET /api/members/me, {auth:'required'})`; 성공 → `member`, 토큰 없음 → `guest`.
  - `tokenStore.subscribe`로 토큰이 `null`이 되면 `guest`.
  - `login`: `POST /api/auth/login`(`auth:'none'`) → `setToken` → `queryClient.clear()` → `authChannel.broadcast({type:'login'})`.
  - `logout`: `POST /api/auth/logout`(`auth:'none'`, 실패해도 계속) → `setToken(null)` → `queryClient.clear()` → `broadcast({type:'logout'})`.
  - 다른 탭의 `logout` 메시지 → `setToken(null)` + `queryClient.clear()`. `login` 메시지 → `refreshAccessToken()`.
- Produces: `safeRedirect(raw: string | null): string` — `/`로 시작하고 `//`·`/\`로 시작하지 않으며 제어문자·공백이 없을 때만 그대로, 아니면 `'/'`.
- Produces: `<RequireAuth>{children}</RequireAuth>` — `loading`이면 `<Spinner/>`, `guest`면 `<Navigate to={'/login?redirect=' + encodeURIComponent(pathname + search)} replace/>`.
- Produces: `loginPath(currentPathWithSearch: string): string` — 위와 같은 URL을 만든다(Task 4·7의 비회원 버튼이 쓴다).
- Produces(테스트): `renderWithProviders(ui, { route?, member?: Partial<Me> })` — `member`가 있으면 MSW에 refresh 200(`accessToken:'test-token'`)과 `GET /api/members/me` 200(기본 `Me`에 `member`를 덮은 값) 핸들러를, 없으면 refresh 401 핸들러를 먼저 등록한다. 이후 Task의 테스트는 회원/비회원을 이 옵션으로만 만든다. `setup.ts`의 `afterEach`에 `tokenStore.setToken(null)`과 `refresh.ts`의 테스트용 `resetRefreshForTest()`(inFlight 비움)를 더한다.

- [ ] **Step 1: 실패하는 테스트**
  - `safeRedirect`: `'/cart'`→`'/cart'`, `'/products?keyword=토너'`→그대로, `null`→`'/'`, `'//evil.com'`→`'/'`, `'/\\evil.com'`→`'/'`, `'https://evil.com'`→`'/'`, `'/\tx'`→`'/'`.
  - `AuthProvider`: `'refresh 쿠키가 유효하면 member가 되고 me를 읽는다'`(refresh 200 → me 200 `name:'홍길동'` → `status==='member'`), `'refresh 401이면 guest'`, `'logout 뒤 guest가 되고 logout API를 부른다'`.
  - `LoginPage`: `'로그인 성공 시 redirect 경로로 간다'`(route `/login?redirect=%2Fcart`, `/cart`에 `<p>cart page</p>` 테스트 라우트 → 이메일·비밀번호 입력, "로그인" 클릭 → `cart page` 보임), `'redirect가 외부 주소면 홈으로 간다'`(`?redirect=%2F%2Fevil.com` → `/`), `'401이면 서버 메시지를 보여 준다'`(`message:'이메일 또는 비밀번호가 올바르지 않습니다.'` 그대로 `role=alert`).
  - `SignupPage`: `'가입 성공 시 자동 로그인 후 홈으로 간다'`(signup 201 → login 200 → `/`), `'서버 필드 오류를 입력 아래에 보여 준다'`(409 `DUPLICATE_EMAIL` message는 이메일 아래, 400 errors `[{field:'phone',reason:'휴대폰 번호 형식이 아닙니다.'}]`는 휴대폰 아래), `'비밀번호 규칙을 먼저 검사한다'`(`'abcdefgh'` → `'비밀번호는 영문과 숫자를 각각 1자 이상 포함한 8~64자여야 합니다.'`, 요청 0번).
- [ ] **Step 2:** 실패 확인 → 구현. 클라이언트 검사는 비밀번호(`/^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]{8,64}$/`)와 필수값만 한다. 나머지는 서버 오류를 보여 준다. 입력에는 `<label>`을 붙이고 `autoComplete`(`email`, `current-password`/`new-password`, `name`, `tel`)을 단다.
- [ ] **Step 3:** PASS → 확인 명령 → 커밋 `feat: 로그인·회원가입과 인증 상태 관리 추가`.

### Task 4: 레이아웃·헤더·상품 카드·찜 버튼

**Files:**
- Create: `src/components/{Header,CategoryDrawer,ProductCard,ProductGrid,Price,ProductImage,WishButton,SectionError,Spinner}.tsx`, `src/lib/format.ts`, `src/lib/labels.ts`, `src/api/catalog.ts`, `src/api/wishlist.ts`, `src/api/cart.ts`(이 Task는 `useCart` 조회만)
- Modify: `src/routes.tsx`의 `Layout`
- Test: `src/lib/format.test.ts`, `src/components/ProductCard.test.tsx`, `src/components/Header.test.tsx`, `src/components/WishButton.test.tsx`

**Interfaces:**
- Produces: `formatWon(n: number): string`, `formatRating(avg: number | null): string`(`null`→`''`, 아니면 소수 첫째 자리 `'4.6'`).
- Produces: `labels.ts`의 `SKIN_TYPE_LABEL`, `PRODUCT_SORT_LABEL`, `REVIEW_SORT_LABEL`, `CART_STATUS_LABEL`(Global Constraints 문구).
- Produces: `useCategories()`, `useBrands()`(staleTime 10분), `useCart()`(member일 때만 enabled), `useWishIds(productIds: number[]): Set<number>`(member이고 ids가 있을 때만, 100개씩 나눠 부른 결과를 합친다, queryKey `['wishIds', ...ids]`), `useToggleWish()` — `mutate({productId, wished})`: PUT/DELETE 후 `['wishIds']`·`['wishlist']` invalidate.
- Produces: `<ProductCard product={ProductSummary} wished?={boolean} rank?={number} />` — 링크 `/products/{id}`, 썸네일, 브랜드명, 상품명, `<Price/>`, 평점(`★ 4.6 (12)`, reviewCount 0이면 숨김), 품절이면 이미지 위 `품절` 표시.
- Produces: `<Price price salePrice saleRate />` — `saleRate`가 있으면 `{saleRate}%`(sale 색) + `formatWon(salePrice)` + 취소선 `formatWon(price)`, 없으면 `formatWon(price)`만.
- Produces: `<ProductImage src={string | null} alt />` — `src`가 `null`이거나 `onError`면 `brand` 색 배경의 자리 표시.
- Produces: `<ProductGrid products={ProductSummary[]} />` — `useWishIds`로 하트를 한 번에 채운다.
- Produces: `<WishButton productId wished />` — 비회원이면 `navigate(loginPath(location))`, 회원이면 토글. `aria-pressed`, `aria-label` `'찜하기'`/`'찜 취소'`.
- Produces: `<SectionError onRetry message? />` — 기본 문구 `'불러오지 못했습니다.'` + "다시 시도" 버튼.
- Header: 로고(`aria-label="dewy 홈"`), 검색 폼(`role=search`, 입력 `aria-label="상품 검색"`, 제출하면 trim한 값이 1~50자일 때만 `/products?keyword=…`), "카테고리" 버튼 → `CategoryDrawer`(`<dialog>`, 트리를 펼쳐 보여 주고 이름 클릭 시 `/products?categoryId={id}`로 이동하며 닫힘), 장바구니 링크(`aria-label="장바구니 {n}개"`, n = `useCart` items 길이, 비회원·로딩이면 `장바구니`), 회원이면 "최근 본"·"찜"·"로그아웃", 비회원이면 "최근 본"·"로그인".

- [ ] **Step 1: 실패하는 테스트**
  - `format`: `formatWon(12900)==='12,900원'`, `formatWon(0)==='0원'`, `formatRating(4.55)==='4.6'`, `formatRating(null)===''`.
  - `ProductCard`: `'세일 상품은 할인율·세일가·정가를 보여 준다'`(`price 18000, salePrice 16200, saleRate 10` → `'10%'`, `'16,200원'`, `'18,000원'`), `'썸네일이 없으면 자리 표시를 보여 준다'`(`thumbnailUrl:null` → `img` 없음, `role="img"` `name=상품명`), `'품절이면 품절 표시'`.
  - `Header`: `'공백만 입력한 검색은 이동하지 않는다'`, `'검색하면 trim한 키워드로 목록에 간다'`(`'  토너 '` → location `/products?keyword=%ED%86%A0%EB%84%88`), `'회원이면 장바구니 개수를 보여 준다'`(cart items 2개 → `'장바구니 2개'`).
  - `WishButton`: `'비회원이 누르면 로그인으로 보낸다'`(route `/products/3` → `/login?redirect=%2Fproducts%2F3`), `'회원이 누르면 PUT을 보내고 눌림 상태가 된다'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 헤더·카테고리·상품 카드와 찜 버튼 추가`.

### Task 5: 메인

**Files:**
- Create: `src/pages/HomePage.tsx`(교체), `src/api/home.ts`, `src/lib/links.ts`
- Test: `src/lib/links.test.ts`, `src/pages/HomePage.test.tsx`

**Interfaces:**
- Consumes: `ProductGrid`, `ProductCard`, `SectionError`, `useAuth`, `apiFetch`.
- Produces: `isExternalLink(url: string): boolean` — `http://`·`https://`로 시작하고 `new URL(url).origin !== window.location.origin`이면 `true`.
- Produces: `useBanners()`, `useTodaySale()`(`GET /api/products?onSale=true&size=8`), `useNewArrivals()`(`sort=NEWEST&size=8`), `useRankings()`(`GET /api/rankings`, 화면에는 상위 10개), `useRecommendations(skinType: SkinType | null)`(null이면 disabled).

섹션 순서: 배너 → 오늘의 특가 → 랭킹 → 피부타입 추천 → 신상품. 섹션마다 자기 query의 로딩·오류·빈 상태를 따로 보인다(한 섹션 실패가 다른 섹션을 막지 않는다). 데이터가 빈 섹션은 제목째 숨긴다(배너·특가·랭킹·추천). 신상품이 비면 `'상품이 없습니다.'`.
- 배너: 가로 `scroll-snap` 줄. 외부 링크는 `<a target="_blank" rel="noopener noreferrer">`, 내부는 `<Link>`. 이미지 `alt`=title.
- 랭킹 제목 옆 안내 `'결제 기준 최근 7일 판매량 · 매시 정각 갱신'`. 카드에 `rank` 배지.
- 추천: 회원이고 `me.skinType`이 있으면 그 값, 아니면 피부 타입 칩 5개(`role="radio"` 그룹, `aria-label="피부 타입"`) 중 고른 값(처음엔 선택 없음, 안내 `'피부 타입을 고르면 같은 피부 고객이 높게 평가한 상품을 보여 드립니다.'`). 카드 아래 `'같은 피부 {sameSkinReviewCount}명 평균 ★{sameSkinRatingAvg}'`.

- [ ] **Step 1: 실패하는 테스트**
  - `links`: `isExternalLink('https://example.com/a')===true`, `isExternalLink('/events/autumn')===false`, `isExternalLink(window.location.origin + '/x')===false`.
  - `HomePage`: `'랭킹이 실패해도 다른 섹션은 보인다'`(rankings 500 → 랭킹 자리에 `'불러오지 못했습니다.'`, 특가 상품명은 보임), `'외부 배너는 새 탭으로 연다'`(`linkUrl:'https://example.com/e'` → `target="_blank"`, `rel`에 `noopener`), `'비회원이 피부 타입을 고르면 추천을 부른다'`(칩 `지성` 클릭 → `/api/recommendations?skinType=OILY` 요청 → 상품명 표시), `'회원 피부 타입이 있으면 칩 없이 바로 추천을 부른다'`(me `skinType:'DRY'`).
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 메인 섹션(배너·특가·랭킹·추천·신상품) 추가`.

### Task 6: 상품 목록·검색

**Files:**
- Create: `src/lib/listParams.ts`, `src/components/InfiniteSentinel.tsx`, `src/pages/ProductListPage.tsx`
- Modify: `src/api/catalog.ts`(`useProductSearch`), `src/routes.tsx`(`/products`)
- Test: `src/lib/listParams.test.ts`, `src/pages/ProductListPage.test.tsx`

**Interfaces:**
- Produces: `interface ListParams { keyword?: string; categoryId?: number; brandIds: number[]; minPrice?: number; maxPrice?: number; onSale: boolean; sort: ProductSort }`.
- Produces: `parseListParams(sp: URLSearchParams): ListParams` — 잘못된 값은 버린다: `keyword`는 trim 뒤 1~50자이고 제어문자가 없을 때만, `categoryId`·가격은 0 이상 정수(`categoryId`는 1 이상)만, `maxPrice < minPrice`면 `maxPrice`를 버린다, `brandIds`는 콤마/반복 모두 받아 양의 정수만, 중복 제거, 앞에서 20개까지, `onSale`은 `'true'`일 때만 `true`, `sort`는 6개 중 하나가 아니면 `'NEWEST'`.
- Produces: `toSearchParams(p: ListParams): URLSearchParams` — 기본값(`onSale=false`, `sort=NEWEST`, 빈 배열)은 쓰지 않는다. `brandIds`는 `1,2` 콤마 하나.
- Produces: `useProductSearch(p: ListParams)` — `useInfiniteQuery`, `size=20`, `getNextPageParam: last => last.nextCursor ?? undefined`, queryKey `['products', toSearchParams(p).toString()]`.
- Produces: `<InfiniteSentinel onReach hasNext loading />` — `IntersectionObserver`(없는 환경에서는 버튼만)로 화면에 들어오면 `onReach`; 같은 자리에 "더보기" `<button>`(키보드용, `hasNext`일 때만).

화면: 제목(키워드면 `'"{keyword}" 검색 결과'`, 카테고리면 카테고리 이름 — `useCategories` 트리에서 찾음, 아니면 `'전체 상품'`), 정렬 `<select aria-label="정렬">`, 필터 버튼 → `<dialog>`(브랜드 체크박스·최소/최대 가격·세일만 보기, "적용" 시 URL 갱신·커서 처음부터). 필터·정렬 변경은 `setSearchParams(toSearchParams(next))`. 결과 없음 `'조건에 맞는 상품이 없습니다.'`.

- [ ] **Step 1: 실패하는 테스트**
  - `listParams`: `'잘못된 값은 버린다'` — `parseListParams(new URLSearchParams('sort=FOO&minPrice=abc&maxPrice=-5&categoryId=-1&onSale=yes'))`가 `{brandIds:[], onSale:false, sort:'NEWEST'}`와 같다(나머지 키 없음); `'maxPrice가 minPrice보다 작으면 maxPrice를 버린다'`(`minPrice=5000&maxPrice=1000` → `{minPrice:5000}`); `'brandIds는 콤마와 반복을 합치고 20개로 자른다'`(`brandIds=1,2&brandIds=2&brandIds=3,x` → `[1,2,3]`, 25개 → 길이 20); `'키워드는 trim하고 51자는 버린다'`; `'toSearchParams는 기본값을 생략한다'`(`{brandIds:[],onSale:false,sort:'NEWEST'}` → `''`); 왕복(`parse(toSearchParams(p))`가 `p`와 같다).
  - `ProductListPage`: `'URL의 조건으로 검색하고 다음 페이지를 이어 붙인다'`(route `/products?keyword=토너&sort=PRICE_ASC`, 첫 응답 `nextCursor:'c1'` → "더보기" 클릭 → 요청에 `cursor=c1`·`sort=PRICE_ASC`·`keyword=토너`, 두 페이지 상품명 모두 보임), `'정렬을 바꾸면 URL이 바뀌고 처음부터 읽는다'`, `'잘못된 쿼리여도 목록을 보여 준다'`(route `/products?sort=FOO&minPrice=abc` → 요청 쿼리에 `sort`·`minPrice` 없음, 상품 보임), `'결과가 없으면 안내 문구'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 상품 목록·검색과 URL 필터, 무한 스크롤 추가`.

### Task 7: 상품 상세·리뷰 목록·장바구니 담기

**Files:**
- Create: `src/pages/ProductDetailPage.tsx`, `src/components/QuantityStepper.tsx`, `src/api/reviews.ts`, `src/api/recentViews.ts`(기록 부분), `src/lib/guestRecentViews.ts`
- Modify: `src/api/catalog.ts`(`useProduct`), `src/api/cart.ts`(`useAddToCart`), `src/routes.tsx`(`/products/:productId`)
- Test: `src/lib/guestRecentViews.test.ts`, `src/pages/ProductDetailPage.test.tsx`

**Interfaces:**
- Produces: `useProduct(id: number)` — 404면 `ApiError(404)`를 그대로 둔다(화면이 처리).
- Produces: `useAddToCart()` — `mutate({skuId, quantity})`, 성공 시 `['cart']` invalidate.
- Produces: `useProductReviews(productId, sort: ReviewSort, skinType: SkinType | null)` — `useInfiniteQuery`, `size=10`.
- Produces: `guestRecentViews`: `read(): number[]`, `record(productId: number): void`(맨 앞으로, 중복 제거, 최대 50), `remove(productId: number): void`, `clear(): void`. 키 `'dewy.recentViews'`. 깨진 값(JSON 아님, 배열 아님, 양의 정수 아닌 원소)은 빈 배열 또는 그 원소를 버린다. `localStorage` 접근이 던지면 빈 배열·무시.
- Produces: `useRecordRecentView(productId)` — 상세 로드 성공 후 한 번: 회원이면 `POST /api/members/me/recent-views`(실패 무시), 비회원이면 `guestRecentViews.record`. `loading` 동안은 기록하지 않는다.
- Produces: `<QuantityStepper value min=1 max=99 onChange />` — `−`/`+` 버튼(`aria-label` `'수량 줄이기'`/`'수량 늘리기'`)과 숫자 입력(`aria-label="수량"`), 범위 밖 입력은 경계값으로.

화면: 이미지 갤러리(대표 + 썸네일 버튼), 브랜드·이름·요약, 평점·리뷰 수, `<Price>`(옵션을 고르면 그 SKU의 price/salePrice, 아니면 최저 SKU 값), 옵션 `<select aria-label="옵션">`(품절 옵션은 `disabled`, 문구 `{name} (품절)`), 옵션이 하나면 미리 선택, 수량, "장바구니 담기"(비회원은 `loginPath`로 이동, 옵션 미선택이면 `'옵션을 선택해 주세요.'`, 상품 전체 품절이면 버튼 비활성 `'품절'`), 성공하면 `<dialog>` `'장바구니에 담았습니다.'` + "장바구니로 가기"/"계속 쇼핑하기", 실패하면 서버 `message`를 `role=alert`. `<WishButton>`. 탭(`role=tablist`): 상세 설명 / 전성분(`ingredients`가 null이면 `'등록된 전성분 정보가 없습니다.'`) / 리뷰({reviewCount}). 리뷰 탭: 정렬 select, 회원이고 `me.skinType`이 있으면 체크박스 `'나와 같은 피부타입({라벨})만 보기'`, 각 리뷰는 별점·작성자·옵션·피부 타입 라벨·본문·사진·작성일(`ko-KR`, `Asia/Seoul`, `2026. 10. 21.`), "리뷰 더보기". 리뷰가 없으면 `'아직 리뷰가 없습니다.'`. 상품 404면 `'판매하지 않는 상품입니다.'` + 홈 링크.

- [ ] **Step 1: 실패하는 테스트**
  - `guestRecentViews`: `'최근 것을 맨 앞에 두고 중복을 없앤다'`(record 1,2,1 → `[1,2]`), `'50개를 넘으면 오래된 것부터 버린다'`(1~51 → 길이 50, 첫 원소 51), `'깨진 값은 빈 목록으로 읽는다'`(`'{oops'`, `'{"a":1}'` → `[]`), `'숫자가 아닌 원소는 버린다'`(`'[3,"x",-1,2.5,4]'` → `[3,4]`).
  - `ProductDetailPage`: `'옵션을 고르고 담으면 장바구니 API를 부르고 안내를 띄운다'`(SKU 2개, 하나 품절 → 품절 옵션 disabled, 다른 옵션 선택·수량 2 → POST `{skuId:1,quantity:2}` → `'장바구니에 담았습니다.'`), `'비회원이 담기를 누르면 로그인으로 보낸다'`, `'담기 실패 시 서버 메시지를 보여 준다'`(409 message 그대로), `'없는 상품은 안내를 보여 준다'`(404 → `'판매하지 않는 상품입니다.'`), `'비회원이 보면 localStorage에 기록한다'`(`guestRecentViews.read()`에 id), `'회원이 보면 최근 본 상품 API를 부른다'`, `'리뷰 정렬을 바꾸면 sort로 다시 부른다'`(`RATING_DESC`), `'같은 피부타입만 보기를 켜면 skinType으로 부른다'`(me `skinType:'OILY'` → 요청 `skinType=OILY`).
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 상품 상세·리뷰 목록·장바구니 담기 추가`.

### Task 8: 찜 목록·최근 본 상품

**Files:**
- Create: `src/pages/WishlistPage.tsx`, `src/pages/RecentViewsPage.tsx`
- Modify: `src/api/wishlist.ts`(`useWishlist`), `src/api/recentViews.ts`(`useRecentViews`, `useRemoveRecentView`, `useClearRecentViews`, `useGuestRecentProducts`), `src/routes.tsx`(`/wishlist`는 `RequireAuth`, `/recent`는 공개)
- Test: `src/pages/WishlistPage.test.tsx`, `src/pages/RecentViewsPage.test.tsx`

**Interfaces:**
- Consumes: `guestRecentViews`(Task 7), `ProductCard`, `InfiniteSentinel`.
- Produces: `useWishlist()` — `useInfiniteQuery`(`size=20`). 화면은 모든 페이지의 items를 합쳐 보여 준다. 받은 페이지 items가 비었는데 `nextCursor`가 있으면 사용자 조작 없이 다음 페이지를 바로 읽는다(삭제된 상품만 있던 페이지).
- Produces: `toSummaryFromDetail(d: ProductDetail): ProductSummary` — `price`=SKU `price` 최솟값, `salePrice`=SKU `salePrice` 최솟값, `thumbnailUrl`=`imageUrls[0] ?? null`, 나머지는 같은 이름 필드.
- Produces: `useGuestRecentProducts(ids: number[])` — `useQueries`로 `GET /api/products/{id}`(queryKey `['product', id]`, `useProduct`와 공유). 404인 id는 `guestRecentViews.remove(id)`로 지우고 목록에서 뺀다. 다른 오류는 그 카드만 뺀다. 순서는 `ids` 순서.

화면: 찜 — `available:false`면 카드를 흐리게 하고 `'판매 중지'` 표시, 하트로 바로 취소, 비면 `'찜한 상품이 없습니다.'`. 최근 본 — 회원은 서버 목록(+ 카드별 "삭제", "전체 삭제"), 비회원은 localStorage 목록(같은 버튼이 localStorage를 지운다) + 안내 `'로그인하지 않으면 이 기기에만 저장됩니다.'`. 비면 `'최근 본 상품이 없습니다.'`.

- [ ] **Step 1: 실패하는 테스트**
  - `WishlistPage`: `'빈 페이지라도 nextCursor가 있으면 이어 읽는다'`(첫 응답 `{items:[],nextCursor:'c1'}`, 두 번째 `{items:[상품A],nextCursor:null}` → 클릭 없이 상품A 보임), `'판매 중지 상품은 표시를 단다'`, `'비회원은 로그인으로 보낸다'`.
  - `RecentViewsPage`: `'비회원은 localStorage 순서대로 상세를 불러 보여 준다'`(`[2,1]` → 2의 이름이 먼저), `'삭제된 상품은 목록과 저장소에서 뺀다'`(id 9 → 404 → 화면에 없음, `guestRecentViews.read()`에 9 없음), `'깨진 저장값이면 빈 안내'`(`localStorage['dewy.recentViews']='oops'` → `'최근 본 상품이 없습니다.'`), `'회원은 서버 목록을 보여 주고 전체 삭제를 부른다'`(DELETE `/api/members/me/recent-views`).
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 찜 목록과 최근 본 상품 화면 추가`.

### Task 9: 장바구니

**Files:**
- Create: `src/pages/CartPage.tsx`, `src/lib/cartTotals.ts`
- Modify: `src/api/cart.ts`(`useChangeQuantity`, `useRemoveCartItem`), `src/routes.tsx`(`/cart`는 `RequireAuth`)
- Test: `src/lib/cartTotals.test.ts`, `src/pages/CartPage.test.tsx`

**Interfaces:**
- Consumes: `useCart`(Task 4), `QuantityStepper`(Task 7), `CART_STATUS_LABEL`.
- Produces: `selectedTotal(items: CartItem[], selected: ReadonlySet<number>): { count: number; amount: number }` — `status==='AVAILABLE'`이고 `selected`에 있는 줄만 `lineTotal`을 더한다. `count`는 그 줄 수.
- Produces: `useChangeQuantity()` — `mutate({id, quantity})`, 성공 시 응답 `CartItem`으로 `['cart']` 캐시의 그 줄을 바꾼다. `useRemoveCartItem()` — 성공 시 `['cart']` invalidate.

화면: 전체 선택 체크박스(AVAILABLE 줄만 대상), 줄마다 체크박스(AVAILABLE이 아니면 `disabled`, 상태 문구 표시), 이미지·브랜드·상품명(상세 링크)·옵션, 단가(`unitSalePrice`, 세일이면 취소선 `unitPrice`), `QuantityStepper`(변경 즉시 PATCH, 실패하면 서버 message `role=alert`, 값은 서버 값으로 되돌림), 줄 금액 `lineTotal`, "삭제". 처음 선택 상태는 AVAILABLE 줄 전부이고, 목록이 바뀌어 사라진 id는 선택에서 뺀다. 하단 요약 `'선택 상품 {count}개'`, `'상품 금액 {formatWon(amount)}'`, 안내 `'배송비와 쿠폰 할인은 주문서에서 계산됩니다.'`, "주문하기" 버튼은 `disabled`이고 옆에 `'주문 기능은 준비 중입니다.'`(10b가 연결한다). 비면 `'장바구니가 비어 있습니다.'` + 홈 링크.

- [ ] **Step 1: 실패하는 테스트**
  - `cartTotals`: `'AVAILABLE이고 선택한 줄만 더한다'`(AVAILABLE 10000 선택, AVAILABLE 5000 미선택, SOLD_OUT 3000 선택 → `{count:1, amount:10000}`), `'빈 선택이면 0'`.
  - `CartPage`: `'처음에는 구매 가능한 줄만 선택되고 합계를 보여 준다'`(AVAILABLE 2줄 + NOT_ENOUGH_STOCK 1줄 → 품절 줄 체크박스 disabled, `'재고 부족'`, 합계 두 줄 합), `'선택을 풀면 합계가 줄어든다'`, `'수량을 바꾸면 PATCH하고 줄 금액이 바뀐다'`(+ 클릭 → `{quantity:3}` → 응답 lineTotal 표시), `'수량 변경 실패 시 메시지를 보여 준다'`, `'삭제하면 목록에서 사라진다'`, `'주문하기는 비활성'`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 확인 명령 → 커밋 `feat: 장바구니 화면 추가`.

### Task 10: 데모 시드와 데모 이미지

**Files:**
- Create: `scripts/demo/data.mjs`, `scripts/demo/seed.mjs`, `scripts/demo/data.test.mjs`, `frontend/public/demo/*.svg`
- Modify: `README.md`(데모 시드 절)

**Interfaces:**
- Produces: `data.mjs` — `export const brands: {name, description}[]`(가상 브랜드 8개, 실제 브랜드 이름 금지), `export function buildProducts(imageBaseUrl: string): {brandName, categoryId, name, summary, description, ingredients, basePrice, imageUrls, skus:[{name, additionalPrice, stock}], saleRate?: number}[]`(약 40개), `export function buildBanners(imageBaseUrl: string, now: Date): {title, imageUrl, linkUrl, startsAt, endsAt, sortOrder}[]`(3개, 하나는 `https://` 외부 링크, 나머지는 `/products?onSale=true`·`/products?categoryId=1` 같은 내부 경로).
- 상품 `categoryId`는 아래 말단 id만 쓴다: 8~22, 26~40(`GET /api/categories` 트리의 children이 빈 노드). 각 상위 카테고리(1~7)에 최소 3개. 정가 3,000~60,000원 100원 단위. 상품 4개 이상은 모든 SKU `stock: 0`(품절 보이기), 1개 이상은 SKU 하나만 품절. 세일은 10개 상품에 10~40%.
- 이미지: `frontend/public/demo/`에 SVG 10개(상품 8: 카테고리별 색 병·튜브 일러스트, 배너 2: 1200×400). 손으로 쓴 단순 도형만 쓴다. URL은 `${imageBaseUrl}/demo/{file}.svg`.
- `seed.mjs` — 환경변수 `API_BASE_URL`(기본 `http://localhost:8080`), `IMAGE_BASE_URL`(기본 `http://localhost:5173`), `ADMIN_EMAIL`, `ADMIN_PASSWORD`(없으면 사용법을 출력하고 종료 코드 1). 순서: 로그인 → `GET /api/brands`에 데모 브랜드 이름이 하나라도 있으면 `'이미 데모 데이터가 있습니다.'` 출력 후 종료 코드 0 → 브랜드 생성 → 상품 생성(`POST /api/admin/products`) → 세일 대상은 `PUT /api/admin/products/{id}/sale {rate, startsAt: now−1h, endsAt: now+14d}` → 배너 생성(`startsAt: now−1h`, `endsAt: now+30d`). 응답이 2xx가 아니면 상태·본문을 출력하고 종료 코드 1. Node 기본 `fetch`만 쓴다.

- [ ] **Step 1: 실패하는 테스트** `data.test.mjs`(`node:test` + `node:assert`):
  - 상품 수 36~44, 모든 `categoryId`가 말단 목록 안, 상위 카테고리 1~7 각각 3개 이상(말단→상위 매핑을 테스트에 상수로 둔다: 8~12→1, 13~15→2, 16~19→3, 20~22→4, 32~40→5, 26~28→6, 29~31→7).
  - 브랜드별 상품명 중복 없음, 상품마다 SKU 1~3개·SKU 이름 중복 없음, `basePrice % 100 === 0`.
  - 모든 SKU 품절인 상품 4개 이상, `saleRate`가 있는 상품 10개이고 값 10~40.
  - 모든 `imageUrls`가 `buildProducts('http://x')` 기준 `http://x/demo/`로 시작하고 해당 파일이 `frontend/public/demo/`에 있다.
  - 배너 3개, 외부 링크 1개, `linkUrl`이 백엔드 규칙 `^(https?://\S+|/(?![/\\])\S*)$`을 만족.
- [ ] **Step 2:** `node --test scripts/demo/` → 실패 확인 → 데이터·SVG·스크립트 작성 → PASS.
- [ ] **Step 3:** README "데모 데이터" 절: 로컬 DB를 비우려면 `docker compose down -v`(성능용 더미 10만 건도 지워진다. 다시 만들려면 `scripts/perf`), 백엔드를 `ADMIN_EMAIL`·`ADMIN_PASSWORD`로 띄우고 `ADMIN_EMAIL=… ADMIN_PASSWORD=… node scripts/demo/seed.mjs`. 리뷰·랭킹·추천은 주문·구매확정 흐름(10b)으로 쌓인다는 문장.
- [ ] **Step 4:** 확인 명령(프론트) + `node --test scripts/demo/` 통과 후 커밋 `feat: 데모 시드 스크립트와 데모 이미지 추가`.

---

## 실행 뒤 확인(컨트롤러, Task 아님)

사용자 확인을 받은 뒤 `docker compose down -v` → 백엔드 bootRun(ADMIN 환경변수) → `npm run dev` → 시드 → 브라우저 패널에서 375px·1280px로: 메인 다섯 섹션, 카테고리 이동, 검색, 필터·정렬·무한 스크롤, 상세(옵션·품절·리뷰 탭), 비회원 담기 → 로그인 → 원래 상세로 복귀, 장바구니 담기·수량·삭제·합계, 찜·최근 본, 두 탭에서 새로고침 후 둘 다 로그인 유지, 한 탭 로그아웃이 다른 탭에 반영.
