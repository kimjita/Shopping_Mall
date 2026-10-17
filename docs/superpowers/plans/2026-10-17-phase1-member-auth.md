# Phase 1 — 회원/인증 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이메일 회원가입·로그인, JWT access + 회전형 refresh token(재사용 감지 시 패밀리 폐기), USER/ADMIN 권한, 내 정보·피부 프로필·비밀번호 변경·탈퇴, 배송지 CRUD, 환경변수 기반 초기 관리자 생성을 만든다.

**Architecture:**
- Access token은 Spring Security OAuth2 Resource Server(Nimbus, HS256)로 발급하고 검증한다. 그래서 토큰 검증 필터를 직접 만들지 않는다.
- Refresh token은 JWT가 아닌 무작위 문자열(opaque)이다. DB에는 SHA-256 해시만 저장하고, `family_id`로 한 번의 로그인에서 이어진 토큰들을 묶는다. 회전은 조건부 UPDATE로 처리해서, 동시에 들어온 요청 중 하나만 성공하게 한다.
- 인증·인가 실패(401/403)와 필터 예외는 모두 Phase 0의 `ErrorResponse` 포맷으로 응답한다.

**Tech Stack:** Spring Boot 3.5.16 (spring-boot-starter-security, spring-boot-starter-oauth2-resource-server), Lombok(Boot 관리), JPA auditing, Flyway V2~V4, Testcontainers, REST Docs

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` (확정된 요구사항 "인증", 도메인 "회원", Phase 1 항목)

## Global Constraints
- Phase 0 규칙을 그대로 따른다:
  - 패키지 루트 `com.sprint.mission`
  - DTO는 `record`
  - 스키마는 Flyway로만 바꾸고, JPA는 `ddl-auto: validate`
  - 에러 메시지는 한국어, 500 응답에는 내부 정보를 노출하지 않음
  - 커밋은 `type: 한국어 요약` + `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- API는 모두 `/api`로 시작한다. 관리자 API는 `/api/admin/**`(ADMIN만 접근)이다.
- 엔티티는 Lombok `@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)`를 쓰고, setter는 만들지 않는다. 상태는 의미가 드러나는 이름의 메서드로만 바꾼다.
- 시간은 `Instant`, DB 컬럼은 `timestamptz`다. 현재 시각은 주입받은 `Clock` 빈에서 가져온다.
- 정책값:
  - access token 30분, refresh token 14일
  - refresh 쿠키 이름 `refresh_token`, `HttpOnly`, `Path=/api/auth`, `Secure`와 `SameSite`는 설정값(기본 `true` / `Lax`)
  - JWT 서명 키는 32바이트 이상이다. 없거나 짧으면 앱이 시작하지 않는다.
- 회원 입력 규칙:
  - 이메일: 앞뒤 공백을 지우고 소문자로 정규화한 뒤 저장한다. 255자 이하, 형식 검증.
  - 비밀번호: 8~64자, 영문과 숫자를 각각 1자 이상 포함.
  - 이름: 1~30자.
  - 휴대폰: `^01[016789]-?\d{3,4}-?\d{4}$`. 저장할 때는 숫자만 남긴다.
- 탈퇴는 soft delete다. 상태를 `WITHDRAWN`으로 바꾸고 모든 refresh token을 폐기한다. 같은 이메일로 다시 가입할 수는 없다.
- 관리자는 `ADMIN_EMAIL`·`ADMIN_PASSWORD` 환경변수가 둘 다 있고 그 이메일이 아직 없을 때만, 앱 시작 시 생성한다.

## Review Focus
1. 대소문자·공백만 다른 이메일로 중복 가입(`" A@Mall.com "` vs `a@mall.com`) → 같은 이메일로 보고 409 → Task 3 테스트
2. 두 탭에서 같은 refresh token으로 동시에 재발급 → 정확히 하나만 성공하고, 나머지는 401 + 패밀리 폐기 → Task 5 테스트
3. 만료되거나 위조된 access token → 500이 아니라 공통 포맷 401 → Task 2 테스트
4. 탈퇴 직후 아직 유효한 access token으로 내 정보 요청, 그리고 탈퇴 회원의 로그인 → 각각 404 / 401 → Task 6 테스트
5. 다른 회원의 배송지 id로 수정·삭제 → 존재를 드러내지 않는 404 → Task 7 테스트

---

### Task 1: 테스트 인프라 정비 + BaseEntity + Lombok

**Files:**
- Modify: `backend/build.gradle`
  - `compileOnly`/`annotationProcessor` lombok, `testCompileOnly`/`testAnnotationProcessor` lombok 추가
  - `bootRun`에 `environment 'JWT_SECRET', System.getenv('JWT_SECRET') ?: 'local-dev-only-jwt-secret-key-0123456789'` 추가
- Create: `backend/src/main/java/com/sprint/mission/global/common/BaseEntity.java`
- Create: `backend/src/main/java/com/sprint/mission/global/config/JpaConfig.java` (`@EnableJpaAuditing`)
- Create: `backend/src/main/java/com/sprint/mission/global/config/ClockConfig.java` (`@Bean Clock clock()` → `Clock.systemUTC()`)
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/java/com/sprint/mission/support/DatabaseCleaner.java`
- Create: `backend/src/test/java/com/sprint/mission/support/ApiTest.java`
- Modify: `backend/src/test/java/com/sprint/mission/support/IntegrationTest.java`
- Modify: `backend/src/test/java/com/sprint/mission/support/DatabaseMigrationTest.java`
- Modify: `backend/src/test/java/com/sprint/mission/global/error/GlobalExceptionHandlerTest.java` (`@WebMvcTest` 대신 `ApiTest` 상속)
- Modify: `backend/src/test/java/com/sprint/mission/global/error/ErrorContractIntegrationTest.java` (`@ActiveProfiles("test")` 추가)

**Interfaces:**
- Produces:
  - `abstract class BaseEntity`: `@MappedSuperclass @EntityListeners(AuditingEntityListener.class)`, 필드 `Instant createdAt`(`@CreatedDate`, updatable=false), `Instant updatedAt`(`@LastModifiedDate`), getter만 있다.
  - `IntegrationTest`: 기존 어노테이션에 `@ActiveProfiles("test")`를 더하고, `@AfterEach`에서 `DatabaseCleaner.clean()`을 호출한다.
  - `DatabaseCleaner` (`@Component`, 테스트 소스): `void clean()`은 `public` 스키마에서 `flyway_schema_history`를 뺀 모든 테이블을 한 문장으로 `TRUNCATE ... RESTART IDENTITY CASCADE`한다. 비울 테이블이 없으면 아무것도 하지 않는다. `IntegrationTest`가 `@Import`로 등록한다.
  - `ApiTest extends IntegrationTest`: `@AutoConfigureMockMvc @AutoConfigureRestDocs`, `protected MockMvc mockMvc`, `protected ObjectMapper objectMapper`. 이후 모든 API 테스트는 전체 컨텍스트 + 실제 Security 설정으로 돈다.
  - `application-test.yml`:
    - `app.jwt.secret: test-only-jwt-secret-key-0123456789abcdef`
    - `app.auth.refresh.cookie-secure: false`

- [ ] **Step 1: 깨질 마이그레이션 단언 교체.** `flyway가_V1을_적용한다`를 `flyway가_V1을_성공적으로_적용했다`로 바꾼다. 단언은 `select count(*) from flyway_schema_history where version = '1' and success`가 `1`이다. 이후 V2가 추가돼도 깨지지 않는다.
- [ ] **Step 2: 실패 테스트 작성.** `support/BaseEntityAuditingTest extends IntegrationTest`를 만든다.
  - 테스트 전용 엔티티 `AuditedSample`(테스트 소스, `@Entity` 테이블 `audited_sample`, `BaseEntity` 상속)을 쓰고, 테스트 마이그레이션 `src/test/resources/db/testmigration/V900__audited_sample.sql`을 둔다.
  - `application-test.yml`에 `spring.flyway.locations: classpath:db/migration,classpath:db/testmigration`을 추가한다.
  - 테스트 `저장하면_생성_수정_시각이_채워진다`: 저장 후 `createdAt`, `updatedAt`이 not null이다.
  - 테스트 `테스트가_끝나면_테이블이_비워진다`: `DatabaseCleaner.clean()`을 부른 뒤 `count()`가 0이다.
- [ ] **Step 3: 실패 확인.** `./gradlew :backend:test --tests '*BaseEntityAuditingTest'` → 컴파일 실패 (`BaseEntity` 없음)
- [ ] **Step 4: 구현.** `BaseEntity`, `JpaConfig`, `ClockConfig`, `DatabaseCleaner`, `ApiTest`, `application-test.yml`, Lombok 의존성, `IntegrationTest`·`GlobalExceptionHandlerTest`·`ErrorContractIntegrationTest` 수정을 한다. `GlobalExceptionHandlerTest`는 이제 전체 컨텍스트를 쓴다. `/test/**` 경로는 Task 2의 Security 설정에서 permitAll이다.
- [ ] **Step 5: 전체 통과 확인.** `./gradlew :backend:test` → 16개 통과 (기존 14 + 2)
- [ ] **Step 6: 커밋.** `chore: 테스트 인프라 정비와 BaseEntity·JPA auditing 추가`

---

### Task 2: Spring Security + JWT 뼈대와 401/403 공통 응답

**Files:**
- Modify: `backend/build.gradle`
  - `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` 추가
  - 테스트용 `spring-security-test` 추가
- Modify: `backend/src/main/resources/application.yml`
  - `app.jwt.secret: ${JWT_SECRET:}`
  - `app.jwt.access-token-ttl: 30m`
  - `app.cors.allowed-origins: http://localhost:5173`
- Modify: `ErrorCode.java`
  - `UNAUTHORIZED(401, "로그인이 필요합니다.")`, `FORBIDDEN(403, "접근 권한이 없습니다.")` 추가
  - `from()`은 공통 코드에서만 고르도록 바꾼다
- Create: `global/error/ErrorResponseWriter.java`
- Modify: `global/error/GlobalErrorController.java` (`ErrorResponseWriter` 사용)
- Create:
  - `global/security/JwtProperties.java`
  - `global/security/CorsProperties.java`
  - `global/security/JwtConfig.java`
  - `global/security/JwtTokenProvider.java`
  - `global/security/SecurityConfig.java`
  - `global/security/RestAuthenticationEntryPoint.java`
  - `global/security/RestAccessDeniedHandler.java`
  - `global/security/CurrentMemberId.java`
- Create: `member/domain/Role.java` (`USER`, `ADMIN`)
- Test:
  - `global/security/JwtPropertiesTest.java`
  - `global/error/ErrorCodeTest.java`
  - `global/security/SecurityIntegrationTest.java` (`ApiTest` 상속)
  - 테스트 전용 `global/security/SecurityTestController.java`

**Interfaces:**
- Consumes: `ApiTest`, `ErrorResponse`, `ErrorCode`
- Produces:
  - `ErrorCode.from(HttpStatusCode)`: 후보는 `INVALID_INPUT, RESOURCE_NOT_FOUND, METHOD_NOT_ALLOWED, UNSUPPORTED_MEDIA_TYPE, UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR`뿐이다. 같은 상태가 없으면 4xx는 `INVALID_INPUT`, 나머지는 `INTERNAL_ERROR`다. 이후 도메인 코드(예: 409 `DUPLICATE_EMAIL`)는 `from()`이 절대 고르지 않는다.
  - `ErrorResponseWriter` (`@Component`): `void write(HttpServletResponse response, ErrorCode errorCode)`. 상태, `application/json`, UTF-8을 설정하고 `ErrorResponse.of(errorCode)`를 쓴다. `GlobalErrorController`, entry point, access denied handler가 함께 쓴다.
  - `record JwtProperties(String secret, Duration accessTokenTtl)` (`@ConfigurationProperties("app.jwt")`): compact constructor에서 secret이 null이거나 UTF-8 32바이트 미만이면 `IllegalArgumentException("JWT_SECRET은 32바이트 이상이어야 합니다.")`을 던진다.
  - `record CorsProperties(List<String> allowedOrigins)` (`@ConfigurationProperties("app.cors")`)
  - `JwtConfig`: `JwtEncoder`(`NimbusJwtEncoder` + `ImmutableSecret`), `JwtDecoder`(`NimbusJwtDecoder.withSecretKey(...).macAlgorithm(HS256)`) 빈
  - `JwtTokenProvider`: `String createAccessToken(Long memberId, Role role)`. claim은 `sub=memberId`, `role=role.name()`, `iat`, `exp=iat+accessTokenTtl`이고 시각은 `Clock` 기준이다. `long accessTokenTtlSeconds()`도 제공한다.
  - `SecurityConfig`:
    - stateless, csrf/formLogin/httpBasic 비활성
    - CORS: `CorsProperties` 출처, credentials 허용, 메서드 GET/POST/PUT/PATCH/DELETE
    - `oauth2ResourceServer(jwt)`: `JwtGrantedAuthoritiesConverter`(claim `role`, prefix `ROLE_`), entry point는 `RestAuthenticationEntryPoint`
    - `exceptionHandling`: entry point + `RestAccessDeniedHandler`
    - 인가 규칙:
      - `POST /api/auth/signup`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` permitAll
      - `/api/admin/**` hasRole ADMIN
      - `/api/**` authenticated
      - 그 밖(`/docs/**`, `/error`, `/test/**`, 없는 경로)은 permitAll
    - `BearerTokenResolver`: 요청 경로가 `/api/auth/`로 시작하면 null을 반환하고, 나머지는 `DefaultBearerTokenResolver`에 위임한다. 만료된 access token 헤더를 붙인 채 refresh/login을 호출해도 401이 나지 않게 하려는 것이다.
  - `PasswordEncoder` 빈: `PasswordEncoderFactories.createDelegatingPasswordEncoder()`
  - `@CurrentMemberId`: 파라미터 어노테이션 `@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")`. 컨트롤러에서 `@CurrentMemberId Long memberId`로 쓴다.
  - `ApiTest`에 헬퍼를 추가한다: `protected String bearer(Long memberId, Role role)` → `"Bearer " + jwtTokenProvider.createAccessToken(...)`

- [ ] **Step 1: 실패 테스트 작성**
  - `JwtPropertiesTest`
    - `32바이트_미만_secret은_거부한다`: `"short"` → `IllegalArgumentException`
    - `null_secret은_거부한다`
  - `ErrorCodeTest`
    - `도메인_코드가_아닌_공통_코드만_고른다`: `from(HttpStatus.CONFLICT)` == `INVALID_INPUT`
    - `401은_UNAUTHORIZED`: `from(UNAUTHORIZED)` == `UNAUTHORIZED`
  - `SecurityTestController`(테스트 소스): `GET /api/test/me` → `@CurrentMemberId Long memberId`를 그대로 반환, `GET /api/admin/test` → `"ok"`
  - `SecurityIntegrationTest`
    - `토큰_없이_API를_호출하면_401_공통_포맷`: `GET /api/test/me` → 401, `code=UNAUTHORIZED`, `message="로그인이 필요합니다."`
    - `위조된_토큰은_401`: `Authorization: Bearer abc.def.ghi` → 401 `UNAUTHORIZED` (Review Focus 3)
    - `만료된_토큰은_401`: `JwtEncoder`로 `exp = now - 1분`인 토큰을 직접 만들어 호출 → 401 `UNAUTHORIZED` (Review Focus 3)
    - `유효한_토큰이면_회원_id를_꺼낸다`: `bearer(7L, USER)` → 200, 본문 `7`
    - `USER가_관리자_API를_호출하면_403`: `GET /api/admin/test` + USER 토큰 → 403 `FORBIDDEN`
    - `ADMIN은_관리자_API에_접근한다`: ADMIN 토큰 → 200 `ok`
    - `허용된_출처의_preflight는_통과한다`: `OPTIONS /api/test/me`, `Origin: http://localhost:5173`, `Access-Control-Request-Method: GET` → 200, `Access-Control-Allow-Origin` 헤더가 그 출처
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test` → 컴파일 실패 (`JwtProperties`, `Role` 없음)
- [ ] **Step 3: 구현.** Files 목록대로 구현한다. `@ConfigurationPropertiesScan`은 `ShoppingMallApplication`에 붙인다.
- [ ] **Step 4: 전체 통과 확인.** `./gradlew :backend:test` → 27개 통과. 기존 `ErrorContractIntegrationTest`, `GlobalExceptionHandlerTest`도 계속 통과해야 한다 (`/test/**`, `/nope`은 permitAll이다).
- [ ] **Step 5: 커밋.** `feat: Spring Security JWT 인증과 401·403 공통 에러 응답 추가`

---

### Task 3: 회원가입

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_member.sql`
- Create:
  - `member/domain/Member.java`, `MemberStatus.java`, `SkinType.java`, `SkinTone.java`, `SkinConcern.java`, `SkinProfile.java`
  - `member/repository/MemberRepository.java`
  - `member/service/MemberService.java`
  - `auth/controller/AuthController.java`
  - `auth/dto/SignupRequest.java`, `SignupResponse.java`
  - `member/domain/EmailNormalizer.java`
- Modify: `ErrorCode.java` (`DUPLICATE_EMAIL(409, "이미 가입된 이메일입니다.")`, `MEMBER_NOT_FOUND(404, "회원을 찾을 수 없습니다.")`)
- Modify: `backend/src/docs/asciidoc/index.adoc` (`== 인증` 섹션)
- Test: `member/domain/MemberTest.java`, `auth/controller/SignupApiTest.java` (`ApiTest`)

**Interfaces:**
- Consumes: `BaseEntity`, `Role`, `PasswordEncoder`, `ApiTest`
- Produces:
  - 테이블 (V2):
    - `member`: `id bigserial pk`, `email varchar(255) not null unique`, `password varchar(255) not null`, `name varchar(30) not null`, `phone varchar(11) not null`, `role varchar(20) not null`, `status varchar(20) not null`, `skin_type varchar(20)`, `skin_tone varchar(20)`, `withdrawn_at timestamptz`, `created_at timestamptz not null`, `updated_at timestamptz not null`
    - `member_skin_concern`: `member_id bigint not null references member(id)`, `concern varchar(30) not null`, `primary key(member_id, concern)`
  - enum 값:
    - `MemberStatus { ACTIVE, WITHDRAWN }`
    - `SkinType { DRY, OILY, COMBINATION, SENSITIVE, NORMAL }` (건성/지성/복합성/민감성/중성)
    - `SkinTone { COOL, WARM, NEUTRAL }`
    - `SkinConcern { BLEMISH, WHITENING, WRINKLE, DEAD_SKIN, TROUBLE, BLACKHEAD, EXCESS_SEBUM, SENSITIVITY, PORE, ELASTICITY, REDNESS, ATOPY, DARK_CIRCLE }`
  - `@Embeddable SkinProfile`: `SkinType skinType`, `SkinTone skinTone`(둘 다 nullable)만 가진다.
  - 고민 목록은 `Member`의 필드다: `@ElementCollection @Enumerated(STRING) Set<SkinConcern> skinConcerns`(테이블 `member_skin_concern`, 컬럼 `concern`). embeddable 안에 컬렉션을 두지 않는다.
  - `MemberRepository`: `Optional<Member> findByEmail(String normalizedEmail)`, `boolean existsByEmail(String normalizedEmail)`
  - `Member`:
    - `static Member create(String email, String encodedPassword, String name, String phone, Role role)` → 상태 `ACTIVE`
    - `boolean isActive()`
    - 이후 Task에서 `updateProfile`, `updateSkinProfile`, `changePassword`, `withdraw`를 추가한다
  - `EmailNormalizer.normalize(String raw)` → `raw.strip().toLowerCase(Locale.ROOT)`
  - `MemberService`:
    - `Long signup(String email, String rawPassword, String name, String phone)`: 정규화 → 중복이면 `BusinessException(DUPLICATE_EMAIL)` → 비밀번호 인코딩 → 전화번호 숫자만 → `USER`로 저장. 동시 가입으로 unique 위반(`DataIntegrityViolationException`)이 나도 `DUPLICATE_EMAIL`로 바꾼다.
    - `Member getActive(Long memberId)`: 없거나 `WITHDRAWN`이면 `MEMBER_NOT_FOUND`
  - `POST /api/auth/signup`
    - 요청 `SignupRequest(email, password, name, phone)`: Bean Validation으로 Global Constraints 규칙을 검사한다. 비밀번호 regex는 `^(?=.*[A-Za-z])(?=.*\d).{8,64}$`다.
    - 응답 201, 본문 `SignupResponse(Long memberId)`, `Location: /api/members/me`

- [ ] **Step 1: 실패 테스트 작성**
  - `MemberTest`: `생성하면_ACTIVE_USER다`
  - `SignupApiTest`
    - `회원가입에_성공한다`: 201, `memberId` 숫자. REST Docs `auth/signup`(request/response fields).
    - `비밀번호는_해시로_저장된다`: 저장된 `password`가 원문과 다르고 `passwordEncoder.matches(raw, stored)`가 true다.
    - `이메일은_정규화되어_저장된다`: `" A@Mall.com "`로 가입 → DB에 `a@mall.com`
    - `대소문자만_다른_이메일은_중복이다`: `a@mall.com` 가입 후 `" A@Mall.COM "` → 409 `DUPLICATE_EMAIL` (Review Focus 1)
    - `휴대폰_번호는_숫자만_저장된다`: `010-1234-5678` → `01012345678`
    - `규칙에_맞지_않는_입력은_필드_오류`: `{"email":"bad","password":"abcdefgh","name":"","phone":"02-123"}` → 400, `errors[*].field`에 `email`, `password`, `name`, `phone`이 모두 있다
    - `토큰_없이_호출할_수_있다`: `Authorization` 헤더 없이 201
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*Signup*' --tests '*MemberTest'` → 컴파일 실패
- [ ] **Step 3: 구현.** Files 목록대로 구현하고, `index.adoc`에 `=== 회원가입`(request-fields, response-fields, http-response)을 추가한다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` → 전체 통과, `./gradlew :backend:asciidoctor` 성공
- [ ] **Step 5: 커밋.** `feat: 이메일 회원가입 API 추가`

---

### Task 4: 로그인 (access token + refresh token 쿠키 발급)

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_refresh_token.sql`
- Create:
  - `auth/domain/RefreshToken.java`
  - `auth/repository/RefreshTokenRepository.java`
  - `auth/service/AuthService.java`
  - `auth/service/RefreshTokenGenerator.java`
  - `auth/RefreshTokenProperties.java`
  - `auth/RefreshTokenCookies.java`
  - `auth/dto/LoginRequest.java`, `TokenResponse.java`, `IssuedTokens.java`
- Modify:
  - `application.yml`: `app.auth.refresh.ttl: 14d`, `cookie-name: refresh_token`, `cookie-secure: true`, `cookie-same-site: Lax`
  - `ErrorCode.java`: `INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 올바르지 않습니다.")`
  - `AuthController.java`, `index.adoc`
- Test: `auth/domain/RefreshTokenTest.java`, `auth/controller/LoginApiTest.java`

**Interfaces:**
- Consumes: `MemberRepository`, `EmailNormalizer`, `PasswordEncoder`, `JwtTokenProvider`, `Clock`
- Produces:
  - 테이블 (V3) `refresh_token`:
    - 컬럼: `id bigserial pk`, `member_id bigint not null references member(id)`, `family_id uuid not null`, `token_hash char(64) not null unique`, `expires_at timestamptz not null`, `used_at timestamptz`, `revoked_at timestamptz`, `created_at timestamptz not null`, `updated_at timestamptz not null`
    - 인덱스: `(member_id)`, `(family_id)`
  - `RefreshToken`:
    - `static RefreshToken issue(Long memberId, UUID familyId, String tokenHash, Instant expiresAt)`
    - `boolean isExpired(Instant now)`, `boolean isRevoked()`, `boolean isUsed()`
  - `RefreshTokenGenerator`:
    - `String generate()`: 32바이트 `SecureRandom`, Base64url, 패딩 없음
    - `static String hash(String token)`: SHA-256 hex 소문자 64자
  - `record RefreshTokenProperties(Duration ttl, String cookieName, boolean cookieSecure, String cookieSameSite)` (`@ConfigurationProperties("app.auth.refresh")`)
  - `RefreshTokenCookies`:
    - `ResponseCookie create(String token)`: HttpOnly, `Path=/api/auth`, `Max-Age=ttl`, Secure/SameSite는 설정값
    - `ResponseCookie expire()`: 같은 속성에 `Max-Age=0`
  - `record IssuedTokens(String accessToken, long expiresIn, String refreshToken)` (서비스 → 컨트롤러)
  - `record TokenResponse(String accessToken, String tokenType, long expiresIn)` (`tokenType`은 항상 `"Bearer"`)
  - `AuthService.login(String email, String rawPassword) → IssuedTokens`
    - 정규화된 이메일로 찾는다. 회원이 없거나, 탈퇴했거나, 비밀번호가 틀리면 모두 같은 `INVALID_CREDENTIALS`다.
    - 새 `familyId`로 refresh token을 저장한다.
  - `POST /api/auth/login` → 200 `TokenResponse` + `Set-Cookie: refresh_token=...`

- [ ] **Step 1: 실패 테스트 작성**
  - `RefreshTokenTest`
    - `만료_시각이_지나면_만료다`: `expiresAt = now`이면 `isExpired(now)` true, `now - 1초`에는 false
    - `해시는_64자_hex이고_결정적이다`
  - `LoginApiTest`
    - `로그인하면_access_token과_refresh_쿠키를_준다`:
      - 200, `tokenType=Bearer`, `expiresIn=1800`
      - 쿠키 `refresh_token`이 `HttpOnly`, `Path=/api/auth`, `Max-Age=1209600`
      - REST Docs `auth/login`(request/response fields, response-headers `Set-Cookie`)
    - `발급된_access_token으로_인증된다`: 받은 토큰으로 `GET /api/test/me` → 200, 본문이 가입한 `memberId`
    - `DB에는_refresh_token_해시만_저장된다`: 저장된 `token_hash`가 쿠키 값과 다르고 `hash(쿠키값)`과 같다
    - `비밀번호가_틀리면_401`: `INVALID_CREDENTIALS`
    - `없는_이메일도_같은_401`: 메시지가 위와 같다 (계정 존재 여부를 노출하지 않음)
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*Login*' --tests '*RefreshTokenTest'` → 컴파일 실패
- [ ] **Step 3: 구현.** Files 목록대로 구현하고 `index.adoc`에 `=== 로그인`을 추가한다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과
- [ ] **Step 5: 커밋.** `feat: 로그인과 refresh token 쿠키 발급 추가`

---

### Task 5: 토큰 재발급(회전 + 재사용 감지) + 로그아웃

**Files:**
- Modify: `auth/service/AuthService.java`, `auth/repository/RefreshTokenRepository.java`, `auth/controller/AuthController.java`, `ErrorCode.java`, `index.adoc`
- Test: `auth/controller/TokenRefreshApiTest.java`, `auth/service/RefreshTokenConcurrencyTest.java` (`IntegrationTest`)

**Interfaces:**
- Consumes: Task 4의 `RefreshToken`, `RefreshTokenGenerator`, `RefreshTokenCookies`, `IssuedTokens`
- Produces:
  - `ErrorCode.INVALID_REFRESH_TOKEN(401, "다시 로그인해 주세요.")`
  - `RefreshTokenRepository`:
    - `Optional<RefreshToken> findByTokenHash(String hash)`
    - `@Modifying int markUsed(Long id, Instant now)`: `update ... set used_at = :now where id = :id and used_at is null and revoked_at is null`
    - `@Modifying int revokeFamily(UUID familyId, Instant now)`: 해당 패밀리의 `revoked_at is null`인 행에 `revoked_at = now`
    - `@Modifying int revokeAllByMemberId(Long memberId, Instant now)`
  - `AuthService.refresh(String refreshToken) → IssuedTokens`: 한 트랜잭션에서 아래 순서로 처리한다.
    1. 해시로 조회한다. 없거나, 만료됐거나, 폐기됐으면 `INVALID_REFRESH_TOKEN`이다.
    2. `markUsed == 0`이면(이미 사용됨 = 재사용 또는 동시 요청에서 졌음) `revokeFamily` 후 `INVALID_REFRESH_TOKEN`이다.
    3. 회원이 `ACTIVE`가 아니면 `INVALID_REFRESH_TOKEN`이다.
    4. 같은 `familyId`로 새 토큰을 저장하고, 새 access token과 함께 반환한다.
    - 2단계의 폐기는 예외와 함께 롤백되면 안 된다. 그래서 `refresh`에 `@Transactional(noRollbackFor = BusinessException.class)`를 붙인다. `BusinessException`은 쓰기 전에 나거나(1단계) 의도한 쓰기 뒤에만 난다(2·3단계).
  - `AuthService.logout(String refreshTokenOrNull)`: 토큰이 있고 찾아지면 그 패밀리를 폐기한다. 없거나 잘못된 토큰이어도 예외를 던지지 않는다.
  - `POST /api/auth/refresh`: `@CookieValue(name="refresh_token", required=false)`. 쿠키가 없으면 401 `INVALID_REFRESH_TOKEN`이다. 성공하면 200 `TokenResponse` + 회전된 `Set-Cookie`, 실패한 401 응답에는 `RefreshTokenCookies.expire()` 쿠키도 같이 내린다.
  - `POST /api/auth/logout`: 항상 204 + 만료 쿠키

- [ ] **Step 1: 실패 테스트 작성**
  - `TokenRefreshApiTest`
    - `refresh_쿠키로_새_토큰을_받는다`: 로그인 쿠키로 refresh → 200, 새 `accessToken`, 쿠키 값이 이전과 다르다. REST Docs `auth/refresh`.
    - `회전된_이전_토큰을_다시_쓰면_401이고_패밀리가_폐기된다`:
      1. 로그인 → refresh(쿠키 A → B)
      2. 다시 A로 refresh → 401 `INVALID_REFRESH_TOKEN`
      3. 이어서 B로 refresh해도 401
    - `다른_기기_로그인은_재사용_감지에_영향이_없다`: 같은 회원이 두 번 로그인(패밀리 X, Y) → X에서 재사용을 감지해도 Y의 refresh는 200
    - `쿠키가_없으면_401`
    - `만료된_refresh_token은_401`: 저장된 토큰의 `expires_at`을 과거로 UPDATE한 뒤 호출
    - `로그아웃하면_refresh가_막히고_쿠키가_만료된다`: logout → 204, `Set-Cookie`의 `Max-Age=0`, 이후 그 쿠키로 refresh → 401. REST Docs `auth/logout`.
    - `쿠키_없이_로그아웃해도_204`
    - `만료된_access_token_헤더를_붙여도_refresh는_동작한다`: `Authorization: Bearer <만료 토큰>`과 유효한 쿠키를 함께 보내면 200이다 (Task 2의 `BearerTokenResolver`)
  - `RefreshTokenConcurrencyTest`
    - `같은_토큰으로_동시에_재발급하면_하나만_성공한다` (Review Focus 2):
      - 로그인으로 토큰 1개를 만들고, 스레드 2개가 `CountDownLatch`로 동시에 `authService.refresh(token)`을 호출한다.
      - 성공 1, `INVALID_REFRESH_TOKEN` 1
      - 이후 패밀리의 모든 행이 `revoked_at is not null`이다
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*TokenRefresh*' --tests '*RefreshTokenConcurrency*'` → 컴파일 실패 (`refresh`, `INVALID_REFRESH_TOKEN` 없음)
- [ ] **Step 3: 구현.** Interfaces대로 구현하고 `index.adoc`에 `=== 토큰 재발급`, `=== 로그아웃`을 추가한다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과
- [ ] **Step 5: 커밋.** `feat: refresh token 회전·재사용 감지와 로그아웃 추가`

---

### Task 6: 내 정보 · 피부 프로필 · 비밀번호 변경 · 탈퇴

**Files:**
- Create:
  - `member/controller/MemberController.java`
  - `member/dto/MemberResponse.java`, `UpdateProfileRequest.java`, `UpdateSkinProfileRequest.java`, `ChangePasswordRequest.java`, `WithdrawRequest.java`
- Modify:
  - `member/domain/Member.java`, `member/service/MemberService.java`
  - `ErrorCode.java` (`PASSWORD_MISMATCH(400, "현재 비밀번호가 일치하지 않습니다.")`)
  - `index.adoc` (`== 회원` 섹션)
- Test: `member/domain/MemberTest.java`(추가), `member/controller/MemberApiTest.java`

**Interfaces:**
- Consumes: `MemberService.getActive`, `RefreshTokenRepository.revokeAllByMemberId`, `@CurrentMemberId`, `ApiTest.bearer`
- Produces:
  - `Member`:
    - `void updateProfile(String name, String phone)`
    - `void updateSkinProfile(SkinType type, SkinTone tone, Set<SkinConcern> concerns)`: 기존 고민은 전부 교체한다. null이면 그 항목을 비운다.
    - `void changePassword(String encodedPassword)`
    - `void withdraw(Instant now)`: `WITHDRAWN`, `withdrawnAt`. 이미 탈퇴한 회원이면 `IllegalStateException`이다.
  - `record MemberResponse(Long id, String email, String name, String phone, Role role, SkinType skinType, SkinTone skinTone, List<SkinConcern> skinConcerns)`: `skinConcerns`는 enum 선언 순서로 정렬하고, 없으면 빈 리스트다.
  - API (모두 인증 필요):
    - `GET /api/members/me` → 200 `MemberResponse`
    - `PATCH /api/members/me` body `{name, phone}` → 200 `MemberResponse`
    - `PUT /api/members/me/skin-profile` body `{skinType, skinTone, skinConcerns[]}` → 200 `MemberResponse`
    - `PATCH /api/members/me/password` body `{currentPassword, newPassword}` → 204
      - 현재 비밀번호가 틀리면 `PASSWORD_MISMATCH`
      - newPassword는 가입과 같은 규칙이다
      - 성공하면 `revokeAllByMemberId`
    - `DELETE /api/members/me` body `{password}` → 204
      - 비밀번호가 틀리면 `PASSWORD_MISMATCH`
      - 성공하면 `withdraw` + `revokeAllByMemberId`

- [ ] **Step 1: 실패 테스트 작성**
  - `MemberTest`
    - `피부_프로필을_바꾸면_고민이_교체된다`
    - `탈퇴하면_WITHDRAWN이고_다시_탈퇴할_수_없다`
  - `MemberApiTest` (가입은 `MemberService.signup`으로 준비하고, 토큰은 `bearer`로 만든다)
    - `내_정보를_조회한다`: 200, `email`, `role=USER`, `skinConcerns=[]`. REST Docs `member/me`.
    - `프로필을_수정한다`: `{"name":"새이름","phone":"010-9999-8888"}` → `phone=01099998888`. REST Docs `member/update-profile`.
    - `피부_프로필을_저장한다`: `{"skinType":"DRY","skinTone":"WARM","skinConcerns":["PORE","WRINKLE"]}` → `skinConcerns=["WRINKLE","PORE"]`(enum 순서). REST Docs `member/update-skin-profile`.
    - `없는_피부타입_값은_400`: `"skinType":"OILYY"` → 400 `INVALID_INPUT`
    - `비밀번호를_바꾸면_기존_refresh_token이_폐기된다`: 로그인 쿠키 확보 → 변경 204 → 그 쿠키로 refresh 401 → 새 비밀번호로 로그인 200. REST Docs `member/change-password`.
    - `현재_비밀번호가_틀리면_400_PASSWORD_MISMATCH`
    - `탈퇴하면_로그인할_수_없다` (Review Focus 4): 탈퇴 204 → 같은 이메일/비밀번호로 로그인 → 401 `INVALID_CREDENTIALS`. REST Docs `member/withdraw`.
    - `탈퇴_후_남은_access_token으로_내_정보를_요청하면_404` (Review Focus 4): 탈퇴 전에 발급한 토큰으로 `GET /api/members/me` → 404 `MEMBER_NOT_FOUND`
    - `탈퇴한_이메일로는_다시_가입할_수_없다`: 409 `DUPLICATE_EMAIL`
    - `토큰_없이_내_정보를_요청하면_401`
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*MemberApiTest' --tests '*MemberTest'` → 컴파일 실패
- [ ] **Step 3: 구현.** Interfaces대로 구현하고 `index.adoc`에 `== 회원` 섹션을 추가한다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과
- [ ] **Step 5: 커밋.** `feat: 내 정보·피부 프로필·비밀번호 변경·탈퇴 API 추가`

---

### Task 7: 배송지 CRUD

**Files:**
- Create:
  - `backend/src/main/resources/db/migration/V4__create_address.sql`
  - `member/domain/Address.java`
  - `member/repository/AddressRepository.java`
  - `member/service/AddressService.java`
  - `member/controller/AddressController.java`
  - `member/dto/AddressRequest.java`, `AddressResponse.java`
- Modify: `ErrorCode.java`, `index.adoc` (`== 배송지`)
- Test: `member/service/AddressServiceTest.java` (`IntegrationTest`), `member/controller/AddressApiTest.java`

**Interfaces:**
- Consumes: `MemberService.getActive`, `@CurrentMemberId`, `ApiTest.bearer`
- Produces:
  - 테이블 (V4) `address`:
    - 컬럼: `id bigserial pk`, `member_id bigint not null references member(id)`, `recipient varchar(30) not null`, `phone varchar(11) not null`, `zip_code char(5) not null`, `address1 varchar(200) not null`, `address2 varchar(200)`, `is_default boolean not null`, `created_at`, `updated_at`
    - 인덱스: `(member_id)`
  - `ErrorCode`: `ADDRESS_NOT_FOUND(404, "배송지를 찾을 수 없습니다.")`, `ADDRESS_LIMIT_EXCEEDED(400, "배송지는 최대 10개까지 등록할 수 있습니다.")`
  - `record AddressRequest(String recipient, String phone, String zipCode, String address1, String address2, boolean isDefault)`: `zipCode`는 `^\d{5}$`, 휴대폰은 회원과 같은 규칙이다.
  - `record AddressResponse(Long id, String recipient, String phone, String zipCode, String address1, String address2, boolean isDefault)`
  - 기본 배송지 규칙 (`AddressService`):
    - 회원의 첫 배송지는 요청과 상관없이 기본이다.
    - `isDefault=true`로 생성·수정하면 나머지는 기본이 해제된다.
    - 기본 배송지를 삭제하면 남은 것 중 가장 먼저 만든 배송지가 기본이 된다.
    - 회원당 최대 10개다.
  - 다른 회원의 배송지 id는 `ADDRESS_NOT_FOUND`다. 조회는 `findByIdAndMemberId`로 한다.
  - API (인증 필요):
    - `GET /api/members/me/addresses` → 200 `List<AddressResponse>` (기본 배송지 먼저, 그다음 `createdAt` 오름차순)
    - `POST /api/members/me/addresses` → 201 `AddressResponse`
    - `PUT /api/members/me/addresses/{addressId}` → 200 `AddressResponse`
    - `DELETE /api/members/me/addresses/{addressId}` → 204

- [ ] **Step 1: 실패 테스트 작성**
  - `AddressServiceTest`
    - `첫_배송지는_자동으로_기본이다`: `isDefault=false`로 생성해도 true
    - `새_기본_배송지를_지정하면_기존_기본이_해제된다`
    - `기본_배송지를_삭제하면_가장_오래된_배송지가_기본이_된다`
    - `11번째_배송지는_ADDRESS_LIMIT_EXCEEDED`
  - `AddressApiTest`
    - `배송지를_등록하고_목록을_조회한다`: REST Docs `address/create`, `address/list`
    - `배송지를_수정한다`: REST Docs `address/update`
    - `배송지를_삭제한다`: REST Docs `address/delete`
    - `다른_회원의_배송지는_수정할_수_없다` (Review Focus 5): 회원 A의 배송지 id로 회원 B가 PUT → 404 `ADDRESS_NOT_FOUND`, A의 배송지는 그대로다
    - `다른_회원의_배송지는_삭제할_수_없다` (Review Focus 5): DELETE → 404
    - `우편번호_형식이_틀리면_400`: `"zipCode":"1234"` → `errors[*].field`에 `zipCode`
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*Address*'` → 컴파일 실패
- [ ] **Step 3: 구현.** Interfaces대로 구현하고 `index.adoc`에 `== 배송지`를 추가한다.
- [ ] **Step 4: 통과 확인.** `./gradlew :backend:test` 전체 통과
- [ ] **Step 5: 커밋.** `feat: 배송지 CRUD API 추가`

---

### Task 8: 환경변수 기반 초기 관리자 계정

**Files:**
- Create: `member/service/AdminAccountInitializer.java`, `member/AdminProperties.java`
- Modify: `application.yml` (`app.admin.email: ${ADMIN_EMAIL:}`, `app.admin.password: ${ADMIN_PASSWORD:}`)
- Test: `member/service/AdminAccountInitializerTest.java` (`IntegrationTest`)

**Interfaces:**
- Consumes: `MemberRepository`, `PasswordEncoder`, `EmailNormalizer`, `Member.create`, `Role.ADMIN`
- Produces:
  - `record AdminProperties(String email, String password)` (`@ConfigurationProperties("app.admin")`)
  - `AdminAccountInitializer implements ApplicationRunner`
    - `void run(ApplicationArguments args)`는 `createIfAbsent()`에 위임한다.
    - `boolean createIfAbsent()`: 이메일·비밀번호 중 하나라도 비어 있으면 false. 정규화한 이메일이 이미 있으면 false(기존 계정의 역할은 바꾸지 않는다). 없으면 `Member.create(email, encode(password), "관리자", "01000000000", ADMIN)`을 저장하고 true를 반환한다.
    - 생성했을 때만 `log.info("초기 관리자 계정을 생성했습니다: {}", email)`을 남기고, 비밀번호는 로그에 남기지 않는다.
  - 테스트는 `new AdminAccountInitializer(new AdminProperties(...), memberRepository, passwordEncoder)`로 직접 만들어서 호출한다. 애플리케이션 시작 시점의 실행은 설정이 비어 있어서 아무것도 하지 않는다.

- [ ] **Step 1: 실패 테스트 작성**
  - `설정이_있으면_ADMIN_계정을_만든다`: true, 저장된 role `ADMIN`, 비밀번호가 `matches`
  - `이미_있으면_만들지_않는다`: 두 번째 호출이 false, `count()`가 1
  - `설정이_비어_있으면_아무것도_하지_않는다`: `("", "")` → false, `count()` 0
  - `생성된_관리자로_로그인하면_관리자_API에_접근한다`: `ApiTest` 기반의 별도 클래스 `AdminLoginApiTest`에서 생성 → 로그인 → 받은 토큰으로 `GET /api/admin/test` → 200
- [ ] **Step 2: 실패 확인.** `./gradlew :backend:test --tests '*Admin*'` → 컴파일 실패
- [ ] **Step 3: 구현.** Interfaces대로 구현한다.
- [ ] **Step 4: 통과 확인.** `./gradlew clean :backend:build` → 전체 테스트 통과 + asciidoctor + bootJar 성공
- [ ] **Step 5: 커밋.** `feat: 환경변수 기반 초기 관리자 계정 생성 추가`

---

## Phase 1 완료 기준
- `./gradlew clean :backend:build`가 통과한다 (Phase 0 테스트 포함 전부).
- REST Docs `index.html`에 인증(회원가입·로그인·재발급·로그아웃), 회원, 배송지 섹션이 있다.
- 로컬 `bootRun`(compose DB)에서 curl로 가입 → 로그인 → `/api/members/me` → refresh → logout 흐름이 동작한다.
- GitHub Actions CI가 통과한다 (push는 사용자 확인 후).

## 범위 밖 (의도적으로 제외)
- 로그인 시도 횟수 제한(brute-force 방어)
- 이메일 인증, 비밀번호 찾기
- 소셜 로그인
- 비밀번호 변경·탈퇴 후 이미 발급된 access token 즉시 무효화: 최대 30분 동안 유효하고, 탈퇴 회원은 `getActive`에서 막힌다.
- 프론트의 refresh 단일 요청 처리(single-flight): Phase 10에서 한다. 동시 refresh를 보내면 서버는 패밀리를 폐기한다.
