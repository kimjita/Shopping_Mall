# Phase 11a 관리자 백엔드(이미지 업로드·상품/회원 조회·대시보드·검증 보강) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 관리자 화면(11b·11c)과 리뷰 사진이 쓸 백엔드를 만든다: S3 presigned PUT 업로드(로컬 LocalStack), 관리자 상품 목록, 관리자 회원 목록·상세와 주문 목록 회원 필터, 대시보드 요약, 이월 검증 보강.

**Architecture:** 업로드는 서버가 Content-Type·Content-Length를 서명한 PUT URL을 내주고 브라우저가 S3로 직접 올린다(서버는 파일을 받지 않는다). 조회 API는 기존 관리자 API 패턴(컨트롤러 → 서비스 → QueryDSL/JPA 리포지토리, 키셋 커서)을 따른다.

**Tech Stack:** Java 21, Spring Boot 3.5.16, JPA + QueryDSL 6.12, PostgreSQL 17, Testcontainers(+ LocalStack 모듈), REST Docs, AWS SDK for Java v2(`software.amazon.awssdk:s3`).

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md`(이미지: S3 Presigned URL 직접 업로드, 관리자: 상품·SKU·재고·세일, 카테고리·브랜드, 쿠폰, 배너, 주문 상태 변경, 회원 조회). API 문서는 `backend/src/docs/asciidoc/index.adoc`.

## Global Constraints

- 새 의존성: `software.amazon.awssdk:s3`(AWS SDK BOM `software.amazon.awssdk:bom`으로 버전 관리, 2주 이상 된 버전), 테스트 `org.testcontainers:localstack`. 다른 것은 더하지 않는다.
- Flyway 마이그레이션은 인덱스가 꼭 필요할 때만 `V20__...`로 하나 만든다(필요 없으면 만들지 않는다). JPA `ddl-auto: validate` 유지.
- 프로젝트 규칙: 시간은 `Clock` 빈, 테스트는 `MutableClock`; 잠금 순서 회원 → 주문 → SKU; 오류는 `ErrorCode` + `BusinessException`, 사용자 메시지는 "~니다"; 주석·문서는 평서체(~다); 새 API는 `ApiTest` 기반 통합 테스트 + REST Docs(`responseFields` 엄격) + index.adoc 장.
- 관리자 API는 `/api/admin/**`(기존 보안 규칙으로 ADMIN 전용). 업로드 API는 `/api/uploads`(로그인 필요, 목적별 권한은 서비스에서 판단).
- 서울 시각 기준 날짜는 `ZoneId.of("Asia/Seoul")`.
- 업로드 규칙: `purpose`는 `PRODUCT`·`BANNER`(ADMIN만)·`REVIEW`(로그인 회원 누구나); `contentType`은 `image/jpeg`·`image/png`·`image/webp`만(확장자 `jpg`·`png`·`webp`); `size`는 1~5,242,880바이트; 유효 시간 5분; 객체 키 `{purpose 소문자}/{yyyy/MM/dd(서울)}/{UUID}.{확장자}`; 응답 `imageUrl` = `{publicBaseUrl}/{key}`.
- 커밋 trailer `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. `.idea/*`·`.claude/*`·`.env`·`frontend/.env.local` 커밋 금지.
- 각 Task 끝 확인 명령: `zsh -l -c './gradlew :backend:build'` 통과.

## Review Focus

1. 일반 회원이 `PRODUCT`·`BANNER` 업로드 URL을 요청 → `403 FORBIDDEN`, 비로그인 → `401`(Task 1 테스트).
2. 서명과 다른 Content-Type이나 크기로 올림 → S3(LocalStack)가 거절, 서명대로면 200이고 `imageUrl`로 GET하면 같은 바이트(Task 1 테스트).
3. `image/svg+xml`·`text/html`·`image/jpeg; charset=...` 같은 형식, 0바이트·5MB 초과 → `400 INVALID_INPUT`(Task 1 테스트).
4. 관리자 상품 목록은 판매 중지·삭제 상품도 보이고, `lowStock=true`는 판매 중 상품만(재고 5 이하 옵션이 있는 판매 중지 상품은 제외)(Task 2 테스트).
5. 대시보드 "오늘"은 서울 자정 기준: 서울 시각 어제 23:59 승인은 빠지고 오늘 00:00 승인은 들어간다(Task 4 테스트, `MutableClock`).

---

### Task 1: 이미지 업로드 presigned URL과 LocalStack

**Files:**
- Modify: `backend/build.gradle`(AWS SDK BOM + s3, test localstack), `compose.yaml`(localstack 서비스), `backend/src/main/resources/application.yml`(`app.storage.*`), `backend/src/main/java/com/sprint/mission/global/error/ErrorCode.java`(필요하면), `backend/src/docs/asciidoc/index.adoc`(`== 이미지 업로드` 장), `README.md`(로컬 업로드 절)
- Create: `backend/src/main/java/com/sprint/mission/upload/{controller/UploadController.java, service/UploadService.java, dto/UploadRequest.java, dto/UploadResponse.java, domain/UploadPurpose.java}`, `backend/src/main/java/com/sprint/mission/global/storage/{StorageProperties.java, S3Config.java}`, `scripts/localstack/init-s3.sh`
- Test: `backend/src/test/java/com/sprint/mission/upload/UploadApiTest.java`, `backend/src/test/java/com/sprint/mission/support/TestcontainersConfig.java`(LocalStack 컨테이너 + 속성 연결)

**Interfaces:**
- Produces: `POST /api/uploads {purpose, contentType, size}` → `200 {uploadUrl, imageUrl, expiresAt}`. 클라이언트는 `PUT uploadUrl`에 헤더 `Content-Type: {contentType}`, 본문 `size` 바이트로 올린다.
- `StorageProperties`(`app.storage`): `bucket`, `region`, `endpoint`(비우면 AWS 기본), `publicBaseUrl`, `pathStyle`(boolean), `accessKey`/`secretKey`(비우면 AWS 기본 자격 증명 체인). 기본값(로컬): bucket `dewy-images`, region `ap-northeast-2`, endpoint `http://localhost:4566`, publicBaseUrl `http://localhost:4566/dewy-images`, pathStyle true, 키 `test`/`test`. 운영은 환경변수로 덮는다(Phase 13).
- `S3Config`: `S3Presigner` 빈(엔드포인트·path-style·자격 증명 반영). 테스트용 업로드 확인에는 `S3Client`도 빈으로 둔다.
- 권한: `PRODUCT`·`BANNER`인데 ADMIN이 아니면 `403 FORBIDDEN`(기존 ErrorCode가 있으면 재사용). 검증 실패는 `400 INVALID_INPUT`.
- compose: `localstack/localstack`(2주 이상 된 고정 태그, 인증 토큰 없이 S3가 동작하는 버전인지 확인), `SERVICES=s3`, 포트 4566, `./scripts/localstack:/etc/localstack/init/ready.d` 마운트. `init-s3.sh`는 `awslocal s3 mb s3://dewy-images`와 공개 읽기 버킷 정책, 브라우저 PUT용 CORS(`http://localhost:5173`, `PUT`·`GET`, `Content-Type` 헤더 허용)를 건다.
- 테스트: `TestcontainersConfig`에 `LocalStackContainer`(같은 이미지 태그) 빈을 더하고 `@DynamicPropertySource` 또는 `DynamicPropertyRegistrar`로 `app.storage.endpoint`·`publicBaseUrl`을 연결, 테스트 시작 시 버킷을 만든다.

- [ ] **Step 1: 실패하는 테스트** `UploadApiTest`
  - `관리자는_상품_이미지_업로드_URL을_받고_그대로_올릴_수_있다`: ADMIN 토큰, `{PRODUCT, image/png, 68}` → 200, `imageUrl`이 `{publicBaseUrl}/product/`로 시작하고 `.png`로 끝남, `expiresAt` ≈ now+5분; `java.net.http.HttpClient`로 `uploadUrl`에 PNG 68바이트 PUT(`Content-Type: image/png`) → 200; `imageUrl` GET → 같은 바이트. REST Docs `upload/presign`.
  - `회원은_리뷰_사진_URL을_받는다`(REVIEW, 200), `회원이_상품_이미지_URL을_요청하면_403`(PRODUCT·BANNER), `비로그인이면_401`.
  - `허용하지_않는_형식이면_400`(`image/svg+xml`, `text/html`, `image/jpeg; charset=utf-8`, `IMAGE/PNG` — 형식은 소문자 세 값과 정확히 같을 때만 허용), `크기가_0이거나_5MB를_넘으면_400`(0, 5,242,881).
  - `서명과_다른_형식이나_크기로_올리면_S3가_거절한다`: PNG로 서명한 URL에 `Content-Type: image/jpeg`로 PUT → 2xx가 아님; 크기 100으로 서명한 URL에 99바이트 PUT → 2xx가 아님.
- [ ] **Step 2:** `zsh -l -c './gradlew :backend:test --tests "*UploadApiTest"'` 실패 확인 → 구현 → PASS → index.adoc `== 이미지 업로드`(흐름·규칙·오류·로컬은 LocalStack) → README(로컬: `docker compose up`이 LocalStack도 띄움, 업로드 이미지는 `http://localhost:4566/dewy-images/...`) → `./gradlew :backend:build` → 커밋 `feat: 이미지 presigned 업로드 API와 로컬 LocalStack 추가`.

### Task 2: 관리자 상품 목록

**Files:**
- Create: `backend/src/main/java/com/sprint/mission/product/repository/AdminProductSearchRepository.java`, `backend/src/main/java/com/sprint/mission/product/dto/{AdminProductSummary.java, AdminProductPage.java, AdminProductSearchCondition.java}`
- Modify: `backend/src/main/java/com/sprint/mission/product/controller/AdminProductController.java`(`GET /api/admin/products`), `backend/src/main/java/com/sprint/mission/product/service/AdminProductService.java`, `backend/src/docs/asciidoc/index.adoc`
- Test: `backend/src/test/java/com/sprint/mission/product/controller/AdminProductListApiTest.java`

**Interfaces:**
- Produces: `GET /api/admin/products?keyword&status&brandId&lowStock&cursor&size` → `{items: AdminProductSummary[], nextCursor}`. 정렬 id 내림차순(최근 등록 순), 커서는 마지막 id 문자열, `size` 1~50 기본 20.
- `AdminProductSummary(id, name, brandName, thumbnailUrl, status, basePrice, saleRate(설정된 세일률, 없으면 null), saleActive(지금 진행 중인지), skuCount, minStock, createdAt)`.
- 필터: `keyword` — 기존 `search_text` ilike(공개 검색과 같은 이스케이프·1~50자·제어문자 검사), `status` — `ON_SALE|STOPPED|DELETED`(없으면 전부), `brandId`, `lowStock=true` — `status = ON_SALE`이고 재고 5 이하 SKU가 하나라도 있는 상품(상수 `LOW_STOCK_THRESHOLD = 5`, Task 4가 같은 상수를 쓴다).
- 쿼리 수는 페이지 크기와 무관(상품+브랜드+대표 이미지 조인, SKU 집계는 서브쿼리 또는 group by 한 번).

- [ ] **Step 1: 실패하는 테스트** — `판매_중지와_삭제_상품도_최근_순으로_보인다`(REST Docs `admin-product/list`), `상태와_브랜드와_키워드로_거른다`, `재고_부족_필터는_판매_중_상품만`(ON_SALE+재고 3 포함, STOPPED+재고 3 제외, ON_SALE+재고 6 제외), `다음_페이지를_이어_읽는다`, `잘못된_커서나_size면_400`, `일반_회원이면_403`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → index.adoc `=== 상품 목록(관리자)` → build → 커밋 `feat: 관리자 상품 목록 API 추가`.

### Task 3: 관리자 회원 목록·상세와 주문 목록 회원 필터

**Files:**
- Create: `backend/src/main/java/com/sprint/mission/member/controller/AdminMemberController.java`, `backend/src/main/java/com/sprint/mission/member/service/AdminMemberService.java`, `backend/src/main/java/com/sprint/mission/member/dto/{AdminMemberSummary.java, AdminMemberPage.java, AdminMemberDetail.java}`, 필요하면 `member/repository/AdminMemberSearchRepository.java`
- Modify: `backend/src/main/java/com/sprint/mission/order/controller/AdminOrderController.java`·`order/service/AdminOrderService.java`·관련 리포지토리(`memberId` 필터), `backend/src/docs/asciidoc/index.adoc`(`== 관리자 - 회원`, 주문 목록 파라미터)
- Test: `backend/src/test/java/com/sprint/mission/member/controller/AdminMemberApiTest.java`, 기존 관리자 주문 목록 테스트에 `memberId` 케이스 추가

**Interfaces:**
- Produces: `GET /api/admin/members?keyword&cursor&size` → `{items: AdminMemberSummary[], nextCursor}`, id 내림차순(최근 가입 순), 탈퇴 회원 포함. `keyword`는 이메일 또는 이름 부분 일치(대소문자 무시, 1~50자, LIKE 이스케이프, 제어문자 거부). `AdminMemberSummary(id, email, name, role, grade, status, createdAt, withdrawnAt)`.
- Produces: `GET /api/admin/members/{memberId}` → `AdminMemberDetail(id, email, name, phone, role, grade, status, skinType, skinTone, skinConcerns, pointBalance, orderCount(모든 상태), createdAt, withdrawnAt)`. 없으면 `404 MEMBER_NOT_FOUND`(탈퇴 회원은 404가 아니라 그대로 보인다).
- Modify: `GET /api/admin/orders?status&memberId&cursor&size` — `memberId`가 있으면 그 회원 주문만(두 필터는 AND).

- [ ] **Step 1: 실패하는 테스트** — `회원을_최근_가입_순으로_보고_탈퇴_회원도_포함한다`(REST Docs `admin-member/list`), `이메일이나_이름으로_찾는다`(대소문자 무시, `%`·`_` 글자 그대로), `회원_상세에_적립금과_주문_수가_있다`(REST Docs `admin-member/detail`), `없는_회원이면_404`, `일반_회원이면_403`; 주문 목록 `회원으로_거른다`(REST Docs 파라미터 갱신).
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → index.adoc → build → 커밋 `feat: 관리자 회원 조회와 주문 목록 회원 필터 추가`.

### Task 4: 대시보드 요약

**Files:**
- Create: `backend/src/main/java/com/sprint/mission/admin/{controller/AdminDashboardController.java, service/AdminDashboardService.java, dto/AdminDashboardResponse.java}`(또는 기존 패키지 관례에 맞는 위치)
- Modify: `backend/src/docs/asciidoc/index.adoc`(`== 관리자 - 대시보드`)
- Test: `backend/src/test/java/com/sprint/mission/admin/AdminDashboardApiTest.java`

**Interfaces:**
- Produces: `GET /api/admin/dashboard` → `AdminDashboardResponse(date(서울 오늘, yyyy-MM-dd), todayPaidCount, todayPaidAmount, paidCount, preparingCount, shippingCount, lowStockSkuCount, todaySignupCount)`.
  - `todayPaid*`: 결제(`payment`)의 승인 시각이 서울 오늘 `[00:00, 내일 00:00)`이고 주문 상태가 `PAID~CONFIRMED`(취소 제외)인 주문의 수와 `paymentAmount` 합.
  - `paid/preparing/shippingCount`: 현재 그 상태인 주문 수.
  - `lowStockSkuCount`: 판매 중 상품의 SKU 중 재고 ≤ `LOW_STOCK_THRESHOLD`(Task 2 상수) 개수.
  - `todaySignupCount`: 회원 가입 시각(`createdAt`)이 서울 오늘인 수(탈퇴 포함).
- 쿼리는 지표당 한 번 이하.

- [ ] **Step 1: 실패하는 테스트** — `오늘_지표를_서울_기준으로_센다`(`MutableClock`을 서울 2026-10-26 10:00으로, 어제 23:59(서울) 승인 결제는 제외·오늘 00:00 승인은 포함, 취소된 오늘 결제는 제외; REST Docs `admin-dashboard/get`), `상태별_처리_대기_수를_센다`, `재고_부족_옵션은_판매_중_상품만_센다`, `일반_회원이면_403`.
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → index.adoc → build → 커밋 `feat: 관리자 대시보드 요약 API 추가`.

### Task 5: 이월 검증 보강(먼 연도·제어 문자·소수 금액)

**Files:**
- Create: `backend/src/main/java/com/sprint/mission/global/validation/{InstantRange.java, InstantRangeValidator.java}`
- Modify: `product/dto/SaleRequest.java`, `coupon/dto/CouponCreateRequest.java`, `banner/dto/BannerRequest.java`(Instant 필드에 `@InstantRange`), `point/dto/AdminPointGrantRequest.java`(사유 제어 문자 거부), `backend/src/main/resources/application.yml`(`spring.jackson.deserialization.accept-float-as-int: false`), 필요하면 `global/error/GlobalExceptionHandler.java`, `backend/src/docs/asciidoc/index.adoc`(공통 에러 응답 장에 "정수 필드에 소수를 보내면 400" 한 줄, 해당 장 시각 범위 한 줄)
- Test: 각 기능의 기존 ApiTest에 케이스 추가(`AdminProduct*`의 세일, `AdminCoupon*`, `AdminBanner*`, `AdminPoint*`), 공통 소수 금액 테스트 1개

**Interfaces:**
- `@InstantRange`: `2000-01-01T00:00:00Z` 이상 `9999-12-31T23:59:59Z` 이하만 허용, null은 통과(필수 여부는 기존 `@NotNull`이 판단). 메시지 `'시각은 2000년부터 9999년 사이여야 합니다.'`. 
- 적립금 지급 사유: 제어 문자(`\p{Cntrl}`, U+001C~U+001F 포함)가 있으면 `'지급 사유에 쓸 수 없는 문자가 있습니다.'`.
- 정수 필드에 소수(`1.9`)를 보내면 `400 INVALID_INPUT`(현재는 1로 잘려 저장된다).

- [ ] **Step 1: 실패하는 테스트** — `세일_종료_시각이_9999년을_넘으면_400`(`+300000-01-01T00:00:00Z`), `쿠폰_발급_시각이_2000년_전이면_400`, `배너_시각이_범위_밖이면_400`, `지급_사유에_제어_문자가_있으면_400`(`"사유\u001C"`), `금액에_소수를_보내면_400`(관리자 적립금 지급 `amount: 1.9`, 장바구니 담기 `quantity: 1.5`).
- [ ] **Step 2:** 실패 확인 → 구현 → PASS → 전체 build(소수 금액 전역 변경이 기존 테스트를 깨지 않는지 확인, 깨지면 그 테스트가 소수를 의도한 것인지 보고) → index.adoc → 커밋 `fix: 먼 연도·제어 문자·소수 금액 입력을 400으로 막음`.

---

## 실행 뒤 확인(컨트롤러, Task 아님)

`docker compose up -d`로 LocalStack이 뜨고 버킷이 생기는지, 백엔드 실행 후 관리자 토큰으로 업로드 URL을 받아 curl로 PUT하고 `imageUrl`을 GET해 보는지, 새 관리자 API 세 개를 curl로 호출해 보는지 확인한다.
