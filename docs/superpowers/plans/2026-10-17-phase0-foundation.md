# Phase 0 — 기반 세팅 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 빈 Gradle 프로젝트를 모노레포 + Spring Boot 3.5 백엔드 뼈대로 바꾼다. PostgreSQL/Flyway/Testcontainers 통합 테스트, 공통 에러 응답 + REST Docs, GitHub Actions CI까지 갖춰서 Phase 1부터는 도메인 코드만 쓰면 되게 한다.

**Architecture:** 루트는 Gradle 멀티프로젝트 셸(`settings.gradle`에 `include 'backend'`)이다. 백엔드는 `backend/` 단일 Spring Boot 앱이고, 프론트(`frontend/`)는 Phase 10에서 추가한다. 로컬 DB는 `compose.yaml`을 `spring-boot-docker-compose`가 `bootRun` 때 자동으로 띄운다. 테스트 DB는 Testcontainers `@ServiceConnection` 빈으로 Spring 컨텍스트와 수명을 같이해서, 컨텍스트 캐시와 함께 재사용된다.

**Tech Stack:** Java 21 (Gradle toolchain), Gradle 9.6 wrapper, Spring Boot 3.5.16, io.spring.dependency-management 1.1.7, PostgreSQL 17, Flyway(Boot 관리 버전), Testcontainers(Boot 관리 버전), Spring REST Docs + org.asciidoctor.jvm.convert 4.0.5, JUnit 5(Boot 관리)

**Spec:** `docs/superpowers/specs/2026-10-17-cosmetics-mall-design.md` (Phase 0 항목, "테스트 전략", "백엔드 구조")

## Global Constraints
- 패키지 루트: `com.sprint.mission`, 메인 클래스는 `com.sprint.mission.ShoppingMallApplication`
- Java 21 toolchain을 쓴다. 로컬 JDK 위치: `/Library/Java/JavaVirtualMachines/zulu-21.jdk`. 다른 환경은 foojay 리졸버가 자동으로 받는다.
- 의존성 버전은 Spring Boot BOM에 맡기고, 직접 고정하는 건 플러그인 버전뿐이다. 기존 `junit-bom:6.0.0`은 제거한다 (Boot 3.5가 관리하는 JUnit 5와 충돌한다).
- 스키마는 오직 Flyway로만 바꾼다. JPA는 `ddl-auto: validate`, `open-in-view: false`로 둔다.
- DTO와 응답 객체는 `record`로 만든다. Lombok은 첫 엔티티가 생기는 Phase 1에서 추가한다.
- 에러 메시지는 한국어로 쓰고, 500 응답에는 내부 예외 메시지를 노출하지 않는다.
- 커밋 메시지는 `type: 한국어 요약` 형식이고, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`을 붙인다.

**설계서와 다른 점 (의도적):** 설계서에는 Phase 0에 MinIO, `BaseEntity`, `PageResponse`가 있다. 쓰는 곳이 생기는 Phase(MinIO·presign → Phase 2, BaseEntity → Phase 1, PageResponse → Phase 2)로 미룬다. MinIO 공식 Docker 이미지 배포 정책이 바뀌었으니, 로컬 S3 대체재도 Phase 2에서 다시 정한다.

## Review Focus
1. 한글 에러 메시지가 깨지는 경우 (응답 charset) → 응답 본문을 UTF-8로 읽어서 한글 메시지가 그대로 나오는지 Task 3에서 검증한다.
2. 존재하지 않는 URL에 Spring 기본 에러(HTML/whitelabel)가 나가는 경우 → 공통 JSON 포맷 404인지 Task 3에서 검증한다.
3. 깨진 JSON 본문 → 500이 아니라 400 `INVALID_INPUT`인지 Task 3에서 검증한다.
4. 예상하지 못한 예외의 메시지/스택이 클라이언트로 새는 경우 → 500 응답 메시지가 고정 문구인지 Task 3에서 검증한다.
5. 지원하지 않는 HTTP 메서드 → 공통 포맷 405인지 Task 3에서 검증한다.

---

### Task 1: 모노레포 구조 + Spring Boot 뼈대

**Files:**
- Modify: `settings.gradle` (루트 셸 + foojay + `include 'backend'`)
- Delete: `build.gradle`(루트), `src/` 전체(`Main.java` 포함)
- Create: `backend/build.gradle`
- Create: `backend/src/main/java/com/sprint/mission/ShoppingMallApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/sprint/mission/ShoppingMallApplicationTest.java`
- Modify: `.gitignore` (`.env` 추가)

**Interfaces:**
- Produces: Gradle 프로젝트 경로 `:backend`. 이후 모든 명령은 루트에서 `./gradlew :backend:<task>`로 실행한다.

- [ ] **Step 1: 루트 `settings.gradle` 작성**

```groovy
plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}
rootProject.name = 'shopping_mall'
include 'backend'
```

- [ ] **Step 2: `backend/build.gradle` 작성**

plugins: `java`, `org.springframework.boot` `3.5.16`, `io.spring.dependency-management` `1.1.7`. `group = 'com.sprint.mission'`, `version = '0.0.1-SNAPSHOT'`, `java.toolchain.languageVersion = JavaLanguageVersion.of(21)`, `repositories { mavenCentral() }`, `test { useJUnitPlatform() }`. 이 Task의 의존성은 `spring-boot-starter-web`, `spring-boot-starter-validation`, `testImplementation spring-boot-starter-test`, `testRuntimeOnly junit-platform-launcher`다. 나머지 Task에서 의존성을 더 추가한다.

- [ ] **Step 3: 컨텍스트 로드 테스트 작성**

```java
@SpringBootTest
class ShoppingMallApplicationTest {
    @Test
    void contextLoads() {}
}
```

- [ ] **Step 4: 실행해서 실패 확인**

Run: `./gradlew :backend:test`
Expected: 컴파일 실패, `ShoppingMallApplication`을 찾을 수 없음 (`@SpringBootConfiguration` 없음)

- [ ] **Step 5: `ShoppingMallApplication` (`@SpringBootApplication` + `main`)과 `application.yml` 작성**

`application.yml`에는 `spring.application.name: shopping-mall`만 넣는다.

- [ ] **Step 6: 실행해서 통과 확인**

Run: `./gradlew :backend:test`
Expected: `BUILD SUCCESSFUL`.
만약 Boot 3.5.16 플러그인이 Gradle 9.6을 지원하지 않는다는 오류가 나면 `./gradlew wrapper --gradle-version 8.14.3`로 wrapper를 내리고 다시 실행한다. 이 경우 wrapper 파일도 커밋에 포함한다.

- [ ] **Step 7: 루트 `build.gradle`과 `src/` 삭제 후 커밋**

```bash
git rm -r build.gradle src
git add settings.gradle backend .gitignore gradle
git commit -m "chore: 모노레포 구조와 Spring Boot 3.5 뼈대 구성"
```

---

### Task 2: PostgreSQL + Flyway + Testcontainers 통합 테스트 기반

**Files:**
- Modify: `backend/build.gradle` (의존성 추가)
- Create: `compose.yaml` (루트)
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V1__enable_extensions.sql`
- Create: `backend/src/test/java/com/sprint/mission/support/TestcontainersConfig.java`
- Create: `backend/src/test/java/com/sprint/mission/support/IntegrationTest.java`
- Test: `backend/src/test/java/com/sprint/mission/support/DatabaseMigrationTest.java`
- Modify: `backend/src/test/java/com/sprint/mission/ShoppingMallApplicationTest.java` (`IntegrationTest`를 상속하도록, DB가 생겨서 컨텍스트가 DB를 필요로 한다)

**Interfaces:**
- Consumes: Task 1의 `:backend` 프로젝트
- Produces:
  - `com.sprint.mission.support.IntegrationTest`: `@SpringBootTest @Import(TestcontainersConfig.class)`가 붙은 abstract class. 모든 DB 통합 테스트는 이걸 상속한다.
  - `TestcontainersConfig`: `@TestConfiguration(proxyBeanMethods = false)`, `@Bean @ServiceConnection PostgreSQLContainer<?> postgres()` (`postgres:17-alpine`)
  - DB 객체(이름 고정): DB `shopping_mall`, 계정 `mall`/`mall` (로컬 compose 전용)

- [ ] **Step 1: 의존성 추가**

`spring-boot-starter-data-jpa`, `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`, `runtimeOnly org.postgresql:postgresql`, `developmentOnly org.springframework.boot:spring-boot-docker-compose`, `testImplementation org.springframework.boot:spring-boot-testcontainers`, `testImplementation org.testcontainers:postgresql`, `testImplementation org.testcontainers:junit-jupiter`

- [ ] **Step 2: 마이그레이션 테스트 작성**

```java
class DatabaseMigrationTest extends IntegrationTest {
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void pg_trgm_확장이_설치된다() {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void flyway가_V1을_적용한다() {
        String version = jdbcTemplate.queryForObject(
            "select max(version) from flyway_schema_history where success", String.class);
        assertThat(version).isEqualTo("1");
    }
}
```

- [ ] **Step 3: 실행해서 실패 확인**

Run: `./gradlew :backend:test --tests '*DatabaseMigrationTest'`
Expected: 컴파일 실패 (`IntegrationTest` 없음)

- [ ] **Step 4: `TestcontainersConfig`, `IntegrationTest`, `V1__enable_extensions.sql` 작성**

SQL은 `CREATE EXTENSION IF NOT EXISTS pg_trgm;` 한 줄이다. `application.yml`에 다음을 추가한다:
- `spring.jpa.hibernate.ddl-auto: validate`
- `spring.jpa.open-in-view: false`
- `spring.jpa.properties.hibernate.default_batch_fetch_size: 100`
- `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`

datasource URL은 yml에 넣지 않는다. 로컬은 docker-compose 모듈, 테스트는 `@ServiceConnection`, 운영은 `SPRING_DATASOURCE_*` 환경변수가 채운다.

- [ ] **Step 5: 실행해서 통과 확인**

Run: `./gradlew :backend:test`
Expected: `DatabaseMigrationTest` 2개와 `contextLoads` 통과 (OrbStack이 실행 중이어야 한다)

- [ ] **Step 6: `compose.yaml` 작성 후 로컬 기동 확인**

서비스 `postgres`: 이미지 `postgres:17-alpine`, 환경변수 `POSTGRES_DB=shopping_mall`, `POSTGRES_USER=mall`, `POSTGRES_PASSWORD=mall`, 포트 `5432:5432`, named volume `pgdata:/var/lib/postgresql/data`.

Run: `./gradlew :backend:bootRun` (백그라운드), 로그에 `Successfully applied 1 migration`과 `Started ShoppingMallApplication`이 나오면 종료한다.
Expected: 컨테이너 `postgres`가 자동으로 기동되고, 마이그레이션이 적용된다.

- [ ] **Step 7: 커밋**

```bash
git add compose.yaml backend
git commit -m "chore: PostgreSQL·Flyway·Testcontainers 통합 테스트 기반 추가"
```

---

### Task 3: 공통 에러 응답 + REST Docs

**Files:**
- Create: `backend/src/main/java/com/sprint/mission/global/error/ErrorCode.java`
- Create: `backend/src/main/java/com/sprint/mission/global/error/BusinessException.java`
- Create: `backend/src/main/java/com/sprint/mission/global/error/ErrorResponse.java`
- Create: `backend/src/main/java/com/sprint/mission/global/error/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/sprint/mission/global/error/ErrorTestController.java` (테스트 전용 컨트롤러)
- Test: `backend/src/test/java/com/sprint/mission/global/error/GlobalExceptionHandlerTest.java`
- Create: `backend/src/docs/asciidoc/index.adoc`
- Modify: `backend/build.gradle` (REST Docs·asciidoctor 설정)

**Interfaces:**
- Produces (Phase 1 이후 모든 도메인이 사용):
  - `enum ErrorCode { INVALID_INPUT(BAD_REQUEST, "입력값이 올바르지 않습니다."), RESOURCE_NOT_FOUND(NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."), METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."), INTERNAL_ERROR(INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."); HttpStatus status(); String message(); }`. 응답의 `code` 값은 enum 이름이다. 도메인별 코드는 각 Phase에서 이 enum에 추가한다.
  - `class BusinessException extends RuntimeException { BusinessException(ErrorCode errorCode); BusinessException(ErrorCode errorCode, String detailMessage); ErrorCode getErrorCode(); }`. detailMessage가 있으면 응답 message로 쓴다.
  - `record ErrorResponse(String code, String message, List<FieldError> errors)`, `record FieldError(String field, String reason)` (ErrorResponse 안의 nested record). `errors`는 검증 오류가 없으면 빈 리스트다 (null 아님).
  - 응답 예: `{"code":"INVALID_INPUT","message":"입력값이 올바르지 않습니다.","errors":[{"field":"name","reason":"공백일 수 없습니다"}]}`
  - REST Docs 스니펫 경로: `common/error-response`

- [ ] **Step 1: REST Docs 설정**

`backend/build.gradle`에 plugin `org.asciidoctor.jvm.convert` `4.0.5`를 추가한다. configuration `asciidoctorExt`를 만들고, 의존성 `asciidoctorExt 'org.springframework.restdocs:spring-restdocs-asciidoctor'`, `testImplementation 'org.springframework.restdocs:spring-restdocs-mockmvc'`를 추가한다. `ext.snippetsDir = file('build/generated-snippets')`로 두고, `test { outputs.dir snippetsDir }`를 설정한다. `asciidoctor { inputs.dir snippetsDir; configurations 'asciidoctorExt'; dependsOn test; baseDirFollowsSourceFile() }`, `bootJar { dependsOn asciidoctor; from("${asciidoctor.outputDir}") { into 'static/docs' } }`다.

- [ ] **Step 2: 테스트 전용 컨트롤러 작성**

`ErrorTestController` (`@RestController @RequestMapping("/test/errors")`)를 만든다:
- `GET /business` → `throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND)`
- `POST /validation` → `@Valid @RequestBody Req(@NotBlank String name, @Min(1) int quantity)`
- `GET /unexpected` → `throw new IllegalStateException("db password=secret")`

- [ ] **Step 3: 핸들러 테스트 작성**

`@WebMvcTest(controllers = ErrorTestController.class) @AutoConfigureRestDocs`를 쓰고, 응답은 `andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)`로 검증한다:
- `비즈니스_예외는_ErrorCode의_상태와_메시지로_응답한다`: 404, `code=RESOURCE_NOT_FOUND`, `message="요청한 리소스를 찾을 수 없습니다."`, `errors=[]`. 여기에 `document("common/error-response", responseFields(code, message, errors[], errors[].field, errors[].reason))`를 붙인다.
- `검증_실패는_필드별_오류를_담는다`: 본문 `{"name":"","quantity":0}` → 400, `code=INVALID_INPUT`, `errors`에 field `name`과 `quantity`가 모두 있다.
- `깨진_JSON은_400으로_응답한다`: 본문 `{"name":` → 400 `INVALID_INPUT` (Review Focus 3)
- `한글_메시지가_깨지지_않는다`: 응답 문자열에 `"입력값이 올바르지 않습니다."`가 포함되고, `Content-Type`이 `application/json`이다 (Review Focus 1)
- `없는_URL은_공통_포맷_404로_응답한다`: `GET /test/errors/nope` → 404 `RESOURCE_NOT_FOUND` (`NoResourceFoundException` 처리, Review Focus 2)
- `지원하지_않는_메서드는_405로_응답한다`: `DELETE /test/errors/business` → 405 `METHOD_NOT_ALLOWED` (Review Focus 5)
- `예상치_못한_예외는_내부_메시지를_숨긴다`: 500 `INTERNAL_ERROR`, `message="서버 오류가 발생했습니다."`, 본문에 `secret`이 없어야 한다 (Review Focus 4)

- [ ] **Step 4: 실행해서 실패 확인**

Run: `./gradlew :backend:test --tests '*GlobalExceptionHandlerTest'`
Expected: 컴파일 실패 (`BusinessException`, `ErrorCode` 없음)

- [ ] **Step 5: `ErrorCode`, `BusinessException`, `ErrorResponse`, `GlobalExceptionHandler` 구현**

`@RestControllerAdvice`의 핸들러 매핑은 다음과 같다:
- `BusinessException` → 해당 코드
- `MethodArgumentNotValidException` → INVALID_INPUT + 필드 오류
- `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` → INVALID_INPUT
- `NoResourceFoundException` → RESOURCE_NOT_FOUND
- `HttpRequestMethodNotSupportedException` → METHOD_NOT_ALLOWED
- `Exception` → `log.error` 후 INTERNAL_ERROR

- [ ] **Step 6: 실행해서 통과 확인**

Run: `./gradlew :backend:test --tests '*GlobalExceptionHandlerTest'`
Expected: 7개 통과, `backend/build/generated-snippets/common/error-response/response-fields.adoc` 생성

- [ ] **Step 7: `index.adoc` 작성 후 문서 빌드 확인**

`= Shopping Mall API`, `:toc: left`, 그리고 `== 공통 에러 응답` 섹션에 `include::{snippets}/common/error-response/response-fields.adoc[]`과 `http-response.adoc`을 넣는다.

Run: `./gradlew :backend:asciidoctor`
Expected: `backend/build/docs/asciidoc/index.html` 생성, 안에 `response-fields` 표가 있다.

- [ ] **Step 8: 커밋**

```bash
git add backend
git commit -m "feat: 공통 에러 응답과 REST Docs 설정 추가"
```

---

### Task 4: GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: 루트에서 `./gradlew :backend:build`가 테스트와 asciidoctor를 포함해서 통과하는 상태
- Produces: `main` push와 PR마다 백엔드 빌드·테스트가 돈다. 프론트 job은 Phase 10에서 추가한다.

- [ ] **Step 1: 워크플로 작성**

`on: push(branches: main), pull_request`. job `backend`는 `runs-on: ubuntu-latest`이고 steps는 다음과 같다:
- `actions/checkout@v4`
- `actions/setup-java@v4` (distribution `zulu`, java-version `21`)
- `gradle/actions/setup-gradle@v4`
- `./gradlew :backend:build`

실패하면 `actions/upload-artifact@v4`로 `backend/build/reports/tests`를 올린다 (`if: failure()`).

- [ ] **Step 2: 로컬에서 같은 명령 통과 확인**

Run: `./gradlew clean :backend:build`
Expected: `BUILD SUCCESSFUL`, 테스트 10개 통과, `bootJar` 안에 `BOOT-INF/classes/static/docs/index.html`이 있다 (`unzip -l backend/build/libs/*.jar | grep static/docs`로 확인).

- [ ] **Step 3: YAML 문법 확인**

Run: `ruby -ryaml -e 'YAML.load_file(".github/workflows/ci.yml"); puts "ok"'`
Expected: `ok`

- [ ] **Step 4: 커밋**

```bash
git add .github
git commit -m "ci: 백엔드 빌드·테스트 GitHub Actions 추가"
```

- [ ] **Step 5: 실제 CI 실행 (사용자 확인 필요)**

GitHub 원격 저장소가 아직 없다. 사용자가 저장소 생성과 push를 승인하면 push하고, Actions의 `backend` job이 초록색인지 확인한다. 승인이 없으면 이 Step은 보류로 남긴다.

---

## Phase 0 완료 기준
- `./gradlew clean :backend:build` 통과 (테스트 10개: contextLoads 1 + 마이그레이션 2 + 에러 핸들러 7)
- `./gradlew :backend:bootRun`으로 compose PostgreSQL이 자동 기동되고 V1 마이그레이션이 적용된다
- REST Docs `index.html`이 jar의 `static/docs`에 포함된다
- 커밋 4개(Task별), 원격 CI는 저장소를 연결한 뒤 확인한다
