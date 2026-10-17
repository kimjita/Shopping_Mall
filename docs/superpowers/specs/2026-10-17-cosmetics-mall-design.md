# 화장품 쇼핑몰 (올리브영 스타일) — 설계 및 구현 계획

## Context
- 현재 저장소는 빈 Gradle Java 프로젝트다 (`com.sprint.mission`, Gradle 9.6, JUnit 6, `Main.java`만 있고 git은 아직 없다).
- 목표: 올리브영 같은 화장품 쇼핑몰을 **백엔드 중심 포트폴리오**로 만든다. 기능을 완성하는 것에 더해, **재고 동시성**과 **검색 성능**을 수치로 증명하는 게 핵심 어필 포인트다.
- 진행: Claude가 Phase 단위로 TDD로 구현하고, Phase마다 리뷰와 커밋을 한다. 백엔드를 먼저 완성하고 → 프론트 → 성능 → AWS 배포 순서로 간다.

## 확정된 요구사항
| 영역 | 결정 |
|---|---|
| 스택 | Java 21, Spring Boot 3.5, JPA + QueryDSL, PostgreSQL, Flyway, Spring REST Docs |
| 인증 | 이메일 가입, Spring Security + JWT (Access는 메모리 보관, Refresh는 HttpOnly 쿠키 + DB 저장 + 회전) |
| 프론트 | React + TS + Vite + Tailwind, TanStack Query, React Router, `/admin` 화면 포함, 반응형 |
| 구조 | 모노레포: 루트 `settings.gradle`에서 `include 'backend'`, 그리고 `frontend/` (Vite) |
| 결제 | 토스페이먼츠 테스트 모드 (결제창 → 성공 리다이렉트 → 서버 승인 + 금액 검증) |
| 이미지 | S3 Presigned URL 직접 업로드 (로컬은 MinIO) |
| 테스트 | TDD, Testcontainers(PostgreSQL), GitHub Actions CI |
| 배포 | AWS: EC2(Docker) + RDS PostgreSQL + S3 이미지, 프론트는 S3 + CloudFront |
| 데이터 | 가상 브랜드/상품, 생성 이미지·무료 이미지, 성능 측정용 더미 10만~100만 건 |
| 디자인 | 올리브영의 UX 패턴만 참고하고 브랜드는 자체로 만든다 (이름·색상 후보 3개는 Phase 10에서 제안) |

## 도메인 & 비즈니스 규칙
- **회원**: USER/ADMIN 역할, 배송지 여러 개, 피부 프로필(타입·톤·고민). 비회원은 구경만 할 수 있다 (최근 본 상품은 localStorage에 저장).
- **카탈로그**: 카테고리 트리(자기참조, 최대 3단계), 브랜드, 상품, **옵션별 SKU**(추가금액, 재고, version), 상품 이미지, 기간 한정 세일(세일가 + 시작/종료 시각).
- **검색**: 상품명·브랜드명에 `pg_trgm` GIN 인덱스, QueryDSL 동적 필터(하위 카테고리 포함, 브랜드, 가격대, 세일 중 여부), 정렬(신상품·낮은/높은 가격·판매순·평점·리뷰 많은 순).
- **찜 / 최근 본 상품 / 장바구니**: 모두 회원 전용이다. 장바구니는 SKU 단위로 담는다.
- **금액 계산 순서**: 정가 → 세일가 → 쿠폰 1장(정액/정률, 최소 주문금액, 최대 할인액) → 배송비(쿠폰 적용 후 상품금액이 2만원 이상이면 무료, 미만이면 2,500원) → 적립금 차감. 계산은 순수 도메인 서비스 하나(`PriceCalculator`)에 모은다.
- **쿠폰**: 다운로드 쿠폰만 있다. 선착순 수량이 있고, 1인 1장이며, 특정 등급만 받을 수 있게 제한할 수 있다 (= 등급 혜택 쿠폰). 발급 수량 동시성은 조건부 UPDATE와 `(member, coupon)` unique 제약으로 막는다.
- **적립금**: 원장 테이블(EARN/USE/RESTORE) + 회원 잔액. 지급은 **구매확정 시점**에 등급별 적립률로 한다.
- **등급**: BABY→PINK→GREEN→BLACK→GOLD. 최근 3개월 구매확정 금액으로 매월 1일 배치가 다시 계산한다. 기준 금액과 적립률은 설정값으로 둔다.
- **주문 상태**: PENDING(결제대기) → PAID → PREPARING → SHIPPING → DELIVERED → CONFIRMED, 그리고 CANCELED.
  - 재고는 **주문 생성 시 선차감**한다. 15분 안에 결제하지 않으면 스케줄러가 취소하고 재고·쿠폰·적립금을 되돌린다.
  - 배송 상태는 관리자가 바꾼다. 배송완료 7일 뒤에는 자동으로 구매확정된다.
  - 사용자 취소는 PAID 상태에서 **전체 취소만** 가능하다 → 토스 결제 취소, 재고·쿠폰·적립금 복원.
- **결제**: `PaymentGateway` 인터페이스와 `TossPaymentClient` 구현체로 나눈다. 승인 전에 주문 금액과 요청 금액이 같은지 확인하고, `paymentKey` unique로 멱등성을 보장한다. 승인 호출이 타임아웃되면 결제 조회로 상태를 맞춘다.
- **리뷰**: 구매확정된 주문상품 하나당 리뷰 하나, 별점 1~5, 사진 최대 5장. 작성 시점의 피부 프로필을 스냅샷으로 저장한다. 상품의 리뷰 수와 평균 평점은 원자적 UPDATE로 비정규화한다. "나와 같은 피부타입" 필터를 제공한다.
- **추천**: 피부타입별로 같은 타입 고객의 평점이 높은 상품(리뷰 5건 이상)을 일 배치로 집계 테이블에 저장한다.
- **랭킹**: 최근 7일 판매량(취소 제외)을 매시간 집계한다. 메인 섹션과 "판매순" 정렬에 쓴다.
- **메인**: 관리자가 등록하는 배너, 오늘의 특가(진행 중인 세일), 신상품, 랭킹, 피부타입 추천.
- **관리자**: 상품·SKU·재고·세일, 카테고리·브랜드, 쿠폰, 배너, 주문 상태 변경, 회원 조회.

## 백엔드 구조
```
backend/src/main/java/com/sprint/mission/
  global/   config, security(jwt), error(ErrorCode, GlobalExceptionHandler), storage(S3 presign), common(BaseEntity, PageResponse)
  member/ auth/ product/(category, brand, sku, sale) search/ cart/ wishlist/ order/ payment/
  coupon/ point/ grade/ review/ recommendation/ ranking/ banner/ admin/
  각 도메인 내부: controller / service / domain / repository / dto
```
- 스케줄러(`@Scheduled`, EC2 한 대 기준): 주문 만료(1분마다), 자동 구매확정(매일), 등급(매월 1일), 랭킹(매시간), 추천(매일).
- 동시성 지점:
  - SKU 재고: 비관적 락, 낙관적 락+재시도, 조건부 UPDATE 세 가지를 구현해 비교한 뒤 하나를 채택한다. 데드락을 막기 위해 여러 SKU는 id 순서로 잠근다.
  - 쿠폰 발급 수량, 적립금 잔액.
- 에러: `ErrorCode` enum과 공통 에러 응답 형식, `@RestControllerAdvice`.

## 테스트 전략
- 도메인 단위 테스트(순수 Java): PriceCalculator, 주문 상태 전이, 쿠폰 검증, 등급 계산.
- 통합 테스트: Testcontainers PostgreSQL(pg_trgm 포함). 컨테이너 하나를 재사용하는 공통 베이스 클래스를 둔다.
- 동시성 테스트: `ExecutorService` + `CountDownLatch`로 100~1000개 동시 요청을 보내 재고·쿠폰 수량이 정확한지 검증한다.
- API 테스트: MockMvc + REST Docs → asciidoc 문서를 생성하고 `/docs/index.html`로 제공한다.
- 외부 연동: 테스트에서는 `PaymentGateway`에 Fake를 쓰고, `TossPaymentClient`는 WireMock으로 테스트한다.
- CI(GitHub Actions): 백엔드 `./gradlew test`, 프론트 `lint + typecheck + build`.

## 구현 Phase (각 Phase = 하나의 spec→plan→TDD 구현→리뷰→커밋 사이클)
0. **기반**: git init, 모노레포 구성, Boot 3.5 설정, docker-compose(postgres, minio), Flyway, Testcontainers 베이스, REST Docs, 에러 처리, CI
1. **회원/인증**: 가입·로그인·JWT·리프레시 회전·로그아웃, 역할, 프로필/피부, 배송지
2. **카탈로그 + 관리자 상품 API**: 카테고리(시드 고정)·브랜드·상품·SKU·세일(상품 단위 할인율, 10원 내림), 이미지는 URL 문자열
3. **검색**: QueryDSL 필터/정렬/페이징, pg_trgm, 더미 데이터 생성 스크립트(`generate_series`), 인덱스 적용 전후 EXPLAIN 기록
4. **찜 / 최근 본 상품 / 장바구니**
5. **쿠폰 / 적립금 / 등급 정책**, PriceCalculator
6. **주문**: 생성(재고 선차감, 락 3종 비교 테스트), 만료 스케줄러, 취소
7. **결제**: 토스 승인/취소, 금액 검증, 멱등성
8. **주문 운영**: 관리자 상태 변경, 자동 구매확정 → 적립, 등급 배치
9. **리뷰 / 추천 / 랭킹 / 배너**
10. **프론트(사용자)**: 브랜드 후보 3개 제안 → 선택 → 메인·목록·검색·상세·장바구니·주문/결제·마이페이지
11. **프론트(관리자)**: `/admin` 대시보드와 각 관리 화면, 이미지 S3 presigned 업로드(상품·리뷰 사진)
12. **성능**: k6로 검색·주문 부하 테스트, 개선 전후 TPS·p95를 `docs/performance/`에 기록
13. **AWS 배포**: ECR + EC2(Docker), RDS, S3/CloudFront, GitHub Actions CD, README(아키텍처 다이어그램, 기술 의사결정, 트러블슈팅)

## 기본값으로 정한 항목
- 패키지는 기존 `com.sprint.mission`을 그대로 쓴다.
- 적립금은 1원 단위로 쓸 수 있다. 최종 결제금액이 최소 100원은 남도록 사용을 제한해서, 0원 결제 분기는 만들지 않는다.
- 등급 기준(3개월 금액)과 적립률: BABY 0원/0.5%, PINK 10만/1%, GREEN 20만/1.5%, BLACK 40만/2%, GOLD 80만/3% (설정값)
- 실제 배송 연동과 송장번호는 없다. 알림(메일/SMS)도 없다.
- 적립금 소멸, 리뷰 "도움돼요", 상품 Q&A는 이번 범위에서 뺀다.
- 다중 서버 스케줄러 락(ShedLock)은 EC2 한 대 기준이라 넣지 않는다.

## 검증 방법
- Phase마다 `./gradlew test` 전체 통과, REST Docs 생성 확인, 동시성 테스트 통과(재고/쿠폰 음수·초과 0건)
- 로컬에서 `docker compose up` 뒤 백엔드와 프론트를 실행하고, 브라우저 패널에서 가입 → 검색 → 장바구니 → 쿠폰 다운로드 → 주문 → 토스 테스트 결제 → 관리자 배송 처리 → 구매확정 → 리뷰까지 E2E로 확인한다.
- Phase 12: k6 결과 리포트, Phase 13: 배포된 URL에서 같은 E2E 시나리오를 확인한다.

## 승인 후 첫 작업
1. 이 설계를 `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md`로 저장하고 git init + 커밋한다.
2. writing-plans로 **Phase 0** 상세 구현 계획을 쓰고, 검토를 받은 뒤 TDD로 구현한다.
