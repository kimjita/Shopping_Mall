# dewy

화장품 쇼핑몰 포트폴리오 프로젝트다. 백엔드는 Spring(`backend/`), 프론트는 Vite React(`frontend/`)다.

## 프론트 실행

백엔드와 프론트를 각각 띄운다.

```bash
./gradlew :backend:bootRun            # 8080
cd frontend && npm install && npm run dev   # 5173
```

개발 서버는 `/api` 요청을 `http://localhost:8080`으로 프록시한다. Safari는 `http://localhost`에서 `Secure` 쿠키를 저장하지 않으므로 백엔드를 `APP_AUTH_REFRESH_COOKIESECURE=false`로 띄운다.

## 로컬 이미지 업로드

이미지는 S3 presigned URL로 올린다. 로컬에서는 `docker compose up`(또는 `bootRun`)이 PostgreSQL과 함께 LocalStack S3도 띄우고, `scripts/localstack/init-s3.sh`가 `dewy-images` 버킷과 공개 읽기 정책, 프론트(`http://localhost:5173`)용 CORS를 건다. 올린 이미지는 `http://localhost:4566/dewy-images/...`로 열린다. LocalStack은 데이터를 저장하지 않으므로 컨테이너를 다시 띄우면 올린 이미지가 사라진다.

## 데모 데이터

화면 확인용 가상 브랜드 8개, 상품 41개, 배너 3개, 쿠폰 3개를 관리자 API로 채우는 스크립트다. 이미지는 `frontend/public/demo/`의 SVG이며 프론트 개발 서버가 `IMAGE_BASE_URL`(기본 `http://localhost:5173`)로 제공한다.

로컬 DB를 비우려면 `docker compose down -v`를 실행한다. 성능용 더미 10만 건도 함께 지워지며, 다시 만들려면 `scripts/perf/seed-catalog.sql`을 쓴다.

백엔드를 `ADMIN_EMAIL`·`ADMIN_PASSWORD`로 띄운 뒤 같은 값으로 시드를 실행한다. 데모 브랜드가 이미 있으면 상품·배너는 만들지 않고 끝나며, 같은 이름의 쿠폰이 없을 때만 쿠폰을 추가한다.

```bash
ADMIN_EMAIL=… ADMIN_PASSWORD=… node scripts/demo/seed.mjs
node --test scripts/demo/   # 데이터 규칙 검사
```

`API_BASE_URL`(기본 `http://localhost:8080`)로 백엔드 주소를 바꿀 수 있다. 리뷰·랭킹·추천 데이터는 시드로 만들지 않고, 주문·구매확정 흐름(10b)을 거치며 쌓인다.

## 토스 테스트 결제

주문 결제는 토스페이먼츠 결제창을 쓴다. 토스 개발자센터나 문서에서 받은 같은 세트의 테스트 키를 아래 두 곳에 넣는다. 테스트 키로는 실제 돈이 나가지 않는다.

- 프론트: `frontend/.env.local`의 `VITE_TOSS_CLIENT_KEY`(`test_ck_…`). 이름은 `frontend/.env.example`에 있다. `*.local`은 git이 무시한다.
- 백엔드: 환경변수 `TOSS_SECRET_KEY`(`test_sk_…`).

결제창(구버전)용 키여야 하며 결제위젯 키(`test_gck_…`)는 쓰지 않는다. 키 값은 저장소에 커밋하지 않는다.
