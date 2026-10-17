# Phase 2 — 카탈로그 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카테고리 트리·브랜드·상품·옵션(SKU)·기간 한정 세일을 만든다. 비회원도 볼 수 있는 공개 조회 API와 관리자 등록·수정 API를 둔다.

**Architecture:**
- 카테고리는 Flyway 시드로 고정한 트리이고, 관리자 CRUD는 없다.
- 상품은 말단 카테고리 하나에 속한다. 옵션별 재고는 SKU에 있고, 세일은 상품에 하나만 건다(할인율 + 기간).
- 가격은 `long`(원)으로 계산하고, 세일가는 10원 단위로 내림한다.
- 재고 조정은 조건부 UPDATE 증감 방식이라 음수가 되지 않는다. Phase 6 주문 차감과 값이 서로 덮어쓰지 않는다.
- 공개 조회는 `SecurityConfig`에서 GET만 permitAll로 연다.

**Tech Stack:** Phase 1과 같다(Spring Boot 3.5.16, JPA, Flyway V5~V7, Testcontainers, REST Docs). 새 의존성은 없다.

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` ("카탈로그", "관리자", Phase 2 항목)

## Global Constraints
- Phase 0·1 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`
  - DTO는 `record`
  - 스키마는 Flyway로만 바꾸고, JPA는 `ddl-auto: validate`
  - 엔티티는 `BaseEntity` 상속 + Lombok `@Getter @NoArgsConstructor(access = PROTECTED)`, setter 없음
  - 에러는 `BusinessException(ErrorCode)`
  - 커밋 trailer는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- 문서·주석·REST Docs 설명은 평서체(~다)로 쓴다. 사용자에게 보이는 에러 메시지는 기존 `ErrorCode`처럼 "~니다"체로 쓴다.
- 새 코드는 `com.sprint.mission.product` 아래 `controller / service / domain / repository / dto`에 둔다.
- 금액은 모두 `long`(원)이다. 세일가는 `(정가 × (100 − 할인율)) / 100`을 정수로 계산한 뒤 10원 단위로 내림한다. 예: 12,900원, 15% → 10,965 → 10,960원.
- 세일 기간은 `startsAt <= now < endsAt`이다. `now`는 `Clock` 빈에서 가져온다.
- 상품 상태:
  - `ON_SALE`: 공개 조회 가능
  - `STOPPED`: 공개 조회 404, 관리자 조회 가능
  - `DELETED`: 관리자 조회도 404, 되돌릴 수 없음
- 품절은 상태가 아니라 계산값이다: SKU 재고가 0이면 그 SKU가 품절이고, 모든 SKU가 품절이면 상품이 품절이다.
- 공개 응답에는 재고 수량을 내보내지 않고 `soldOut`만 내보낸다. 관리자 응답에는 `stock`을 내보낸다.
- 이미지는 URL 문자열 목록이다. 첫 번째가 대표 이미지이고, 1~10개, 각 500자 이하, `http(s)://`로 시작한다. presign 업로드는 Phase 11로 미룬다.
- 입력 한도:
  - 상품명 1~100자, 요약 200자 이하, 상세 설명·전성분은 TEXT
  - 정가 100~10,000,000원, SKU 추가금액 0~10,000,000원
  - 재고 0~100,000, 재고 증감 delta는 0이 아니고 |delta| ≤ 100,000
  - 상품당 SKU 1~20개, SKU 이름 1~50자이고 상품 안에서 중복될 수 없다
  - 할인율 1~90, 브랜드명 1~50자이고 중복될 수 없다

**의도적으로 하지 않는 것:**
- presign 업로드와 로컬 S3 에뮬레이터 → Phase 11
- 상품 목록·검색·페이징 → Phase 3
- 카테고리 관리자 CRUD
- SKU 삭제(재고 0으로 대신한다)
- 낙관적 락용 `version` 컬럼 → Phase 6에서 락 비교와 함께 추가
- 이월 사항 중 nullable `@CurrentMemberId`, `AccessDeniedException` 재전달, auditing `Clock` 연결 → 쓰는 Phase에서 처리

## Review Focus
1. 세일 시작 시각 정각에는 세일가이고, 종료 시각 정각에는 정가다 → Task 6 도메인 테스트
2. 재고보다 큰 감소 요청, 또는 동시에 들어온 감소 요청 → 음수가 되지 않고 `STOCK_NOT_ENOUGH`, 재고는 그대로다 → Task 5 테스트(동시성 포함)
3. 판매중지·삭제 상품의 공개 상세 → 존재를 드러내지 않는 404 → Task 4 테스트
4. 말단이 아닌 카테고리(예: "스킨케어")에 상품 등록 → 400 `CATEGORY_NOT_LEAF` → Task 3 테스트
5. 토큰 없는 비회원의 공개 GET → 200, USER 토큰으로 관리자 API → 403 → Task 1·3 테스트

---

### Task 1: 카테고리 시드 + 공개 카테고리 트리 + 공개 GET 허용

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_category.sql`
- Create: `product/domain/Category.java`, `product/repository/CategoryRepository.java`, `product/service/CategoryService.java`, `product/controller/CategoryController.java`, `product/dto/CategoryResponse.java`
- Modify: `global/security/SecurityConfig.java`
- Modify: `backend/src/test/java/com/sprint/mission/support/DatabaseCleaner.java`
- Modify: `index.adoc` (`== 카탈로그`)
- Test: `product/controller/CategoryApiTest.java` (`ApiTest`)

**Interfaces:**
- Produces:
  - 테이블 `category`:
    - 컬럼: `id bigint pk`(시드는 명시 id), `parent_id bigint references category(id)`, `name varchar(30) not null`, `depth smallint not null`(1~3), `sort_order int not null`
    - 시드가 끝나면 `setval`로 시퀀스를 맞춘다. auditing 컬럼은 두지 않는다(시드 전용, `BaseEntity`를 상속하지 않음).
  - 시드 트리 (이름 그대로, 각 단계 안에서 아래 순서가 `sort_order`):
    - 스킨케어: 토너, 에센스·세럼, 크림, 로션, 미스트·오일
    - 마스크팩: 시트팩, 패드, 워시오프팩
    - 클렌징: 폼·젤, 오일·밤, 워터·밀크, 필링·스크럽
    - 선케어: 선크림, 선스틱, 선쿠션
    - 메이크업:
      - 립: 립스틱, 립틴트, 립글로스
      - 베이스: 쿠션, 파운데이션, 프라이머
      - 아이: 아이섀도, 아이라이너, 마스카라
    - 바디케어: 바디로션, 바디워시, 핸드크림
    - 헤어케어: 샴푸, 트리트먼트, 헤어에센스
  - `Category`: `Long id`, `Long parentId`, `String name`, `int depth`, `int sortOrder`, `@Immutable` 엔티티(읽기 전용)
  - `CategoryService`:
    - `List<CategoryResponse> tree()`: 전체를 한 번 조회해서 메모리에서 트리로 만든다. 카테고리는 약 40행이다.
    - `Category getLeaf(Long categoryId)`: 없으면 `CATEGORY_NOT_FOUND`, 자식이 있으면 `CATEGORY_NOT_LEAF`
    - `List<Category> path(Long categoryId)`: 루트부터 해당 카테고리까지
  - `record CategoryResponse(Long id, String name, List<CategoryResponse> children)`: 형제끼리는 `sort_order` 순서로 정렬하고, 말단은 `children=[]`
  - `ErrorCode`: `CATEGORY_NOT_FOUND(404, "카테고리를 찾을 수 없습니다.")`, `CATEGORY_NOT_LEAF(400, "가장 아래 단계 카테고리를 선택해 주세요.")`
  - `GET /api/categories` → 200 `List<CategoryResponse>` (루트 7개)
  - `SecurityConfig`: `/api/**` authenticated 규칙보다 먼저 `requestMatchers(HttpMethod.GET, "/api/categories", "/api/brands", "/api/products/**").permitAll()`를 둔다.
  - `DatabaseCleaner`: 시드 테이블 `category`는 TRUNCATE에서 뺀다(`flyway_schema_history`와 같은 제외 목록). 이후 Task의 테이블은 지금처럼 자동으로 비운다.

- [ ] **Step 1: 실패 테스트 작성** (`CategoryApiTest`)
  - `카테고리_트리를_비회원도_조회한다`: 토큰 없이 200, 루트 이름이 순서대로 `["스킨케어","마스크팩","클렌징","선케어","메이크업","바디케어","헤어케어"]`, `메이크업`의 첫 자식이 `립`이고 그 첫 자식이 `립스틱`. REST Docs `category/tree`.
  - `테스트가_끝나도_시드_카테고리는_남는다`: 같은 클래스의 두 번째 테스트에서도 루트가 7개다(cleaner 제외가 동작하는지 확인).
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*CategoryApiTest'` → 컴파일 실패
- [ ] **Step 3: 구현.** Files 목록대로 구현하고, `index.adoc`에 `== 카탈로그` / `=== 카테고리 트리`를 추가한다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과
- [ ] **Step 5: 커밋.** `feat: 카테고리 시드와 공개 카테고리 트리 API 추가`

---

### Task 2: 브랜드 (관리자 등록·수정 + 공개 목록)

**Files:**
- Create: `V6__create_brand.sql`, `product/domain/Brand.java`, `product/repository/BrandRepository.java`, `product/service/BrandService.java`, `product/controller/BrandController.java`, `product/controller/AdminBrandController.java`, `product/dto/BrandRequest.java`, `product/dto/BrandResponse.java`
- Modify: `ErrorCode.java`, `index.adoc`
- Test: `product/controller/BrandApiTest.java`

**Interfaces:**
- Consumes: `ApiTest.bearer(id, Role.ADMIN)`, Task 1의 GET permitAll
- Produces:
  - 테이블 `brand`: `id bigserial pk`, `name varchar(50) not null unique`, `description varchar(500)`, `created_at`, `updated_at`
  - `ErrorCode`: `BRAND_NOT_FOUND(404, "브랜드를 찾을 수 없습니다.")`, `DUPLICATE_BRAND(409, "이미 있는 브랜드 이름입니다.")`
  - `record BrandRequest(String name, String description)`: name은 앞뒤 공백을 지운 뒤 1~50자이고, description은 500자 이하
  - `record BrandResponse(Long id, String name, String description)`
  - `BrandService`:
    - `BrandResponse create(BrandRequest)`
    - `BrandResponse update(Long id, BrandRequest)`
    - `List<BrandResponse> list()`: 이름 오름차순
    - `Brand get(Long id)`: 없으면 `BRAND_NOT_FOUND`
    - 이름 중복은 `existsByName` 검사 + unique 위반 catch로 `DUPLICATE_BRAND`(가입과 같은 방식). 수정할 때 자기 이름 그대로면 중복이 아니다.
  - API:
    - `GET /api/brands` (공개)
    - `POST /api/admin/brands` → 201
    - `PUT /api/admin/brands/{brandId}` → 200

- [ ] **Step 1: 실패 테스트 작성** (`BrandApiTest`)
  - `관리자가_브랜드를_등록한다`: REST Docs `brand/create`
  - `관리자가_브랜드를_수정한다`: REST Docs `brand/update`
  - `비회원도_브랜드_목록을_본다`: 이름 오름차순. REST Docs `brand/list`
  - `같은_이름은_409`
  - `자기_이름_그대로_수정하면_200`
  - `USER는_브랜드를_등록할_수_없다`: 403 `FORBIDDEN`
  - `없는_브랜드_수정은_404`
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*BrandApiTest'`
- [ ] **Step 3: 구현.** `index.adoc`에 `=== 브랜드`를 추가한다.
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `feat: 브랜드 등록·수정과 공개 목록 API 추가`

---

### Task 3: 상품·SKU·이미지 등록 + 관리자 상세

**Files:**
- Create:
  - `V7__create_product.sql`
  - `product/domain/Product.java`, `ProductStatus.java`, `Sku.java`
  - `product/repository/ProductRepository.java`, `SkuRepository.java`
  - `product/service/ProductService.java`, `product/service/AdminProductService.java`
  - `product/controller/AdminProductController.java`
  - `product/dto/ProductCreateRequest.java`, `SkuRequest.java`, `AdminProductResponse.java`, `IdResponse.java`
- Modify: `ErrorCode.java`, `index.adoc` (`== 관리자 - 상품`)
- Test: `product/domain/ProductTest.java`, `product/controller/AdminProductApiTest.java`

**Interfaces:**
- Consumes: `CategoryService.getLeaf`, `CategoryService.path`, `BrandService.get`
- Produces:
  - 테이블 (V7):
    - `product`:
      - `id bigserial pk`, `brand_id bigint not null references brand(id)`, `category_id bigint not null references category(id)`
      - `name varchar(100) not null`, `summary varchar(200)`, `description text not null`, `ingredients text`
      - `base_price bigint not null`, `status varchar(20) not null`
      - `sale_rate int`, `sale_starts_at timestamptz`, `sale_ends_at timestamptz` (Task 6에서 쓴다)
      - `created_at`, `updated_at`
      - 인덱스: `(category_id)`, `(brand_id)`
    - `product_image`: `product_id bigint not null references product(id)`, `sort_order int not null`, `url varchar(500) not null`, `primary key(product_id, sort_order)`
    - `sku`:
      - `id bigserial pk`, `product_id bigint not null references product(id)`, `name varchar(50) not null`
      - `additional_price bigint not null default 0`, `stock int not null check (stock >= 0)`
      - `created_at`, `updated_at`, `unique(product_id, name)`
  - `ProductStatus { ON_SALE, STOPPED, DELETED }`
  - `Product`:
    - `static create(Long brandId, Long categoryId, String name, String summary, String description, String ingredients, long basePrice, List<String> imageUrls)` → `ON_SALE`
    - 이미지는 `@ElementCollection @OrderColumn(name="sort_order") List<String> imageUrls`(테이블 `product_image`, 컬럼 `url`)
    - `boolean isVisibleToPublic()`(== ON_SALE), `boolean isDeleted()`
    - brand·category는 id 컬럼으로만 들고 연관관계를 맺지 않는다(Phase 1의 `memberId` 방식)
  - `Sku`:
    - `static create(Long productId, String name, long additionalPrice, int stock)`
    - `long price(long basePrice)` = basePrice + additionalPrice
    - `boolean isSoldOut()` = stock == 0
  - `ErrorCode`: `PRODUCT_NOT_FOUND(404, "상품을 찾을 수 없습니다.")`, `DUPLICATE_SKU_NAME(409, "같은 이름의 옵션이 이미 있습니다.")`
  - `record SkuRequest(String name, long additionalPrice, int stock)`
  - `record ProductCreateRequest(Long brandId, Long categoryId, String name, String summary, String description, String ingredients, long basePrice, List<String> imageUrls, List<SkuRequest> skus)`: 검증은 Global Constraints의 입력 한도 그대로. 요청 안 SKU 이름 중복은 서비스에서 `DUPLICATE_SKU_NAME`으로 처리한다.
  - `record AdminProductResponse(Long id, ProductStatus status, Long brandId, String brandName, List<String> categoryPath, String name, String summary, String description, String ingredients, long basePrice, List<String> imageUrls, List<AdminSku> skus)`
    - `record AdminSku(Long id, String name, long additionalPrice, long price, int stock)`
    - Task 6에서 `sale` 필드를 추가한다.
  - `AdminProductService`:
    - `Long create(ProductCreateRequest)`
    - `AdminProductResponse get(Long productId)`: DELETED면 `PRODUCT_NOT_FOUND`
  - `ProductService.getProduct(Long id)`: 없거나 DELETED면 `PRODUCT_NOT_FOUND`. 관리자·공개 서비스가 함께 쓰는 조회다.
  - API (ADMIN):
    - `POST /api/admin/products` → 201 `IdResponse(Long id)` + `Location: /api/admin/products/{id}`
    - `GET /api/admin/products/{productId}` → 200 `AdminProductResponse`

- [ ] **Step 1: 실패 테스트 작성**
  - `ProductTest`
    - `등록하면_ON_SALE이다`
    - `SKU_가격은_정가에_추가금액을_더한다`: base 12,900 + 2,000 → 14,900
    - `재고_0이면_품절이다`
  - `AdminProductApiTest` (브랜드는 `BrandService`로, 카테고리는 시드 "토너" id를 이름으로 조회해서 준비한다)
    - `관리자가_상품을_등록하고_상세를_본다`: SKU 2개(`"150ml"` +0 / 재고 10, `"300ml"` +8,000 / 재고 0), 이미지 2개 → 201. 상세의 `categoryPath = ["스킨케어","토너"]`, `skus[1].price = basePrice + 8000`, 이미지 순서가 그대로다. REST Docs `admin-product/create`, `admin-product/get`
    - `말단이_아닌_카테고리는_400_CATEGORY_NOT_LEAF` (Review Focus 4): "스킨케어" id
    - `없는_브랜드는_404_BRAND_NOT_FOUND`
    - `요청_안_SKU_이름이_겹치면_409`
    - `이미지가_없거나_http가_아니면_400`: `imageUrls=[]` 또는 `["ftp://x"]` → 400, field `imageUrls`
    - `USER_토큰이면_403` (Review Focus 5)
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*ProductTest' --tests '*AdminProductApiTest'`
- [ ] **Step 3: 구현.** `index.adoc`에 `== 관리자 - 상품` / `=== 상품 등록`, `=== 상품 상세(관리자)`를 추가한다.
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `feat: 상품·옵션 등록과 관리자 상품 상세 API 추가`

---

### Task 4: 공개 상품 상세 + 상품 수정·상태 변경

**Files:**
- Create: `product/controller/ProductController.java`, `product/dto/ProductResponse.java`, `ProductUpdateRequest.java`, `ProductStatusRequest.java`
- Modify: `Product.java`, `ProductService.java`, `AdminProductService.java`, `AdminProductController.java`, `index.adoc`
- Test: `product/controller/ProductApiTest.java`, `AdminProductApiTest.java`(추가)

**Interfaces:**
- Consumes: Task 3 전부
- Produces:
  - `Product.update(Long brandId, Long categoryId, String name, String summary, String description, String ingredients, long basePrice, List<String> imageUrls)`
  - `Product.changeStatus(ProductStatus)`: DELETED에서는 바꿀 수 없다(서비스가 먼저 `PRODUCT_NOT_FOUND`로 막는다)
  - `record ProductResponse(Long id, String name, BrandSummary brand, List<CategorySummary> categoryPath, String summary, String description, String ingredients, List<String> imageUrls, long basePrice, boolean soldOut, List<PublicSku> skus)`
    - `record BrandSummary(Long id, String name)`, `record CategorySummary(Long id, String name)`
    - `record PublicSku(Long id, String name, long price, boolean soldOut)`
    - Task 6에서 가격 필드를 세일 반영 형태로 넓힌다.
  - `ProductService.getPublic(Long id)`: ON_SALE이 아니면 `PRODUCT_NOT_FOUND`
  - `record ProductUpdateRequest(...)`: `ProductCreateRequest`에서 `skus`만 뺀 필드와 같은 검증
  - `record ProductStatusRequest(ProductStatus status)`: `@NotNull`
  - API:
    - `GET /api/products/{productId}` (공개) → 200 `ProductResponse`
    - `PUT /api/admin/products/{productId}` → 200 `AdminProductResponse`
    - `PATCH /api/admin/products/{productId}/status` → 200 `AdminProductResponse`

- [ ] **Step 1: 실패 테스트 작성**
  - `ProductApiTest`
    - `비회원이_상품_상세를_본다`: 토큰 없이 200. `stock` 필드가 없고 `soldOut`이 있다. `brand.name`, `categoryPath[*].name = ["스킨케어","토너"]`. REST Docs `product/get`.
    - `모든_SKU가_품절이면_상품도_품절이다`
    - `판매중지_상품은_404` (Review Focus 3)
    - `삭제된_상품은_404` (Review Focus 3)
  - `AdminProductApiTest`
    - `관리자가_상품을_수정한다`: REST Docs `admin-product/update`
    - `판매중지한_상품도_관리자는_본다`
    - `삭제한_상품은_관리자도_404이고_상태를_되돌릴_수_없다`: DELETED 후 `GET` 404, `PATCH status ON_SALE` 404. REST Docs `admin-product/change-status`.
- [ ] **Step 2: 실패 확인.** focused 테스트 실행 → 컴파일 실패
- [ ] **Step 3: 구현.** `index.adoc`에 `=== 상품 상세`(카탈로그), `=== 상품 수정`, `=== 상품 상태 변경`을 추가한다.
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `feat: 공개 상품 상세와 상품 수정·상태 변경 API 추가`

---

### Task 5: SKU 관리 + 재고 증감

**Files:**
- Create: `product/controller/AdminSkuController.java`, `product/dto/SkuUpdateRequest.java`, `StockAdjustRequest.java`, `StockResponse.java`
- Modify: `Sku.java`, `SkuRepository.java`, `AdminProductService.java`(또는 `AdminSkuService.java`로 분리), `ErrorCode.java`, `index.adoc`
- Test: `product/controller/AdminSkuApiTest.java`, `product/service/StockAdjustConcurrencyTest.java` (`IntegrationTest`, 트랜잭션 없음)

**Interfaces:**
- Consumes: Task 3의 `Sku`, `ProductService.getProduct`
- Produces:
  - `ErrorCode`: `SKU_NOT_FOUND(404, "옵션을 찾을 수 없습니다.")`, `STOCK_NOT_ENOUGH(409, "재고가 부족합니다.")`, `SKU_LIMIT_EXCEEDED(400, "옵션은 최대 20개까지 등록할 수 있습니다.")`
  - `SkuRepository`:
    - `@Modifying int adjustStock(Long skuId, int delta)`: `update sku set stock = stock + :delta, updated_at = now() where id = :id and stock + :delta >= 0`. JPQL에서 `updatedAt`은 파라미터로 넘긴다(Phase 1 bulk update와 같은 방식).
    - `Optional<Sku> findByIdAndProductId(...)`
  - 재고 조정 서비스: 상품이 DELETED면 404. `adjustStock == 0`이면, SKU가 없으면 `SKU_NOT_FOUND`이고 있으면 `STOCK_NOT_ENOUGH`.
  - `record SkuUpdateRequest(String name, long additionalPrice)`, `record StockAdjustRequest(int delta)`, `record StockResponse(Long skuId, int stock)`
  - API (ADMIN):
    - `POST /api/admin/products/{productId}/skus` (`SkuRequest`) → 201 `AdminProductResponse`. 20개가 넘으면 `SKU_LIMIT_EXCEEDED`, 이름이 겹치면 `DUPLICATE_SKU_NAME`.
    - `PUT /api/admin/products/{productId}/skus/{skuId}` → 200 `AdminProductResponse`
    - `POST /api/admin/products/{productId}/skus/{skuId}/stock-adjustments` → 200 `StockResponse`

- [ ] **Step 1: 실패 테스트 작성**
  - `AdminSkuApiTest`
    - `옵션을_추가한다`: REST Docs `admin-sku/create`
    - `옵션을_수정한다`: REST Docs `admin-sku/update`
    - `입고하면_재고가_늘어난다`: 10 + 5 → 15. REST Docs `admin-sku/adjust-stock`
    - `재고보다_많이_빼면_409이고_재고는_그대로다` (Review Focus 2): 10 − 11 → 409 `STOCK_NOT_ENOUGH`, 재고는 10
    - `delta가_0이면_400`
    - `다른_상품의_SKU_id면_404`
    - `21번째_옵션은_400`
  - `StockAdjustConcurrencyTest`
    - `동시에_빼도_음수가_되지_않는다` (Review Focus 2): 재고 5에서 10개 스레드가 동시에 −1 → 성공 5, `STOCK_NOT_ENOUGH` 5, 최종 재고 0
- [ ] **Step 2: 실패 확인.** focused 테스트 실행
- [ ] **Step 3: 구현.** `index.adoc`에 `=== 옵션 추가`, `=== 옵션 수정`, `=== 재고 조정`을 추가한다.
- [ ] **Step 4: 통과 확인.** 전체 통과
- [ ] **Step 5: 커밋.** `feat: 옵션 관리와 재고 증감 API 추가`

---

### Task 6: 기간 한정 세일

**Files:**
- Create: `product/domain/Sale.java`(`@Embeddable`), `product/dto/SaleRequest.java`, `SaleResponse.java`
- Modify: `Product.java`, `ProductResponse.java`, `AdminProductResponse.java`, `ProductService.java`, `AdminProductService.java`, `AdminProductController.java`, `ErrorCode.java`, `index.adoc`
- Test: `product/domain/SaleTest.java`, `product/controller/SaleApiTest.java`

**Interfaces:**
- Consumes: V7의 `sale_rate`, `sale_starts_at`, `sale_ends_at` 컬럼, `Clock` 빈
- Produces:
  - `@Embeddable Sale(int rate, Instant startsAt, Instant endsAt)`:
    - `boolean isActive(Instant now)` = `!now.isBefore(startsAt) && now.isBefore(endsAt)`
    - `long apply(long price)` = `(price * (100 - rate) / 100) / 10 * 10`
  - `Product`: `@Embedded Sale sale`(nullable), `void startSale(Sale)`, `void endSale()`, `Optional<Sale> activeSale(Instant now)`
    - 모든 컬럼이 NULL이면 Hibernate가 임베디드를 null로 읽는다(Phase 1 `SkinProfile`과 같다). null을 안전하게 다룬다.
  - `ErrorCode.INVALID_SALE_PERIOD(400, "세일 종료 시각은 시작 시각보다 늦어야 합니다.")`
  - `record SaleRequest(int rate, Instant startsAt, Instant endsAt)`: rate는 1~90, 둘 다 `@NotNull`, `endsAt <= startsAt`이면 `INVALID_SALE_PERIOD`
  - `record SaleResponse(int rate, Instant startsAt, Instant endsAt, boolean active)`
  - 응답 변경:
    - `ProductResponse`에 `Integer saleRate`(진행 중인 세일이 없으면 null)를 추가한다.
    - `PublicSku`는 `long price`(정가), `long salePrice`(진행 중이면 세일가, 아니면 정가와 같다)
    - `AdminProductResponse`에 `SaleResponse sale`(nullable)을 추가한다.
  - API (ADMIN):
    - `PUT /api/admin/products/{productId}/sale` (`SaleRequest`) → 200 `AdminProductResponse`. 기존 세일은 교체된다.
    - `DELETE /api/admin/products/{productId}/sale` → 204

- [ ] **Step 1: 실패 테스트 작성**
  - `SaleTest`
    - `세일가는_10원_단위로_내림한다`: 12,900원, 15% → 10,960원. 14,900원, 15% → 12,660원 (12,665 → 12,660).
    - `시작_시각_정각에는_세일이다` (Review Focus 1)
    - `종료_시각_정각에는_세일이_아니다` (Review Focus 1)
    - `시작_전에는_세일이_아니다`
  - `SaleApiTest`
    - `세일을_걸면_공개_상세에_세일가가_나온다`: startsAt = now − 1시간, endsAt = now + 1시간, 15% → `saleRate=15`, `skus[0].salePrice=10960`. REST Docs `admin-product/start-sale`.
    - `기간이_지난_세일은_정가로_나온다`: 과거 기간 → `saleRate=null`, `salePrice == price`
    - `세일을_끝낸다`: DELETE 204 → `sale=null`. REST Docs `admin-product/end-sale`.
    - `종료가_시작보다_빠르면_400_INVALID_SALE_PERIOD`
    - `할인율_91은_400`
- [ ] **Step 2: 실패 확인.** focused 테스트 실행
- [ ] **Step 3: 구현.** `index.adoc`에 `=== 세일 설정`, `=== 세일 종료`를 추가하고, `=== 상품 상세`의 가격 필드 설명을 갱신한다.
- [ ] **Step 4: 통과 확인.** `./gradlew clean :backend:build` 통과
- [ ] **Step 5: 커밋.** `feat: 상품 기간 한정 세일 추가`

---

## Phase 2 완료 기준
- `./gradlew clean :backend:build`가 통과한다 (Phase 0·1 테스트 포함 전부).
- REST Docs에 카탈로그(카테고리·브랜드·상품 상세)와 관리자(브랜드·상품·옵션·재고·세일) 섹션이 있다.
- 로컬 `bootRun`에서 관리자 계정으로 브랜드 → 상품 등록 → 세일 설정을 하고, 토큰 없이 `GET /api/products/{id}`로 세일가를 확인한다.
