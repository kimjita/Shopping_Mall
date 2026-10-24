# dewy

화장품 쇼핑몰 포트폴리오 프로젝트다. 백엔드는 Spring(`backend/`), 프론트는 Vite React(`frontend/`)다.

## 프론트 실행

백엔드와 프론트를 각각 띄운다.

```bash
./gradlew :backend:bootRun            # 8080
cd frontend && npm install && npm run dev   # 5173
```

개발 서버는 `/api` 요청을 `http://localhost:8080`으로 프록시한다. Safari는 `http://localhost`에서 `Secure` 쿠키를 저장하지 않으므로 백엔드를 `APP_AUTH_REFRESH_COOKIESECURE=false`로 띄운다.
