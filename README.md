# libra-backend

LIBRA Spring Boot 백엔드. 사용자 인증, 포트폴리오, KIS 연동, Judge run 분배, 결정 이력을 담당합니다.

## 책임 분담

이 레포가 책임지는 것:

- 사용자 인증 (Spring Security + JWT access/refresh + OAuth: Google/Kakao/Naver)
- 사용자별 포트폴리오 스냅샷 (KIS 동기화 결과 포함) — multi-tenant
- 사용자별 결정 이력 (Decision run + agent signals + rebalance plan + 평가)
- libra-agent와의 HTTP 경계 (계약은 `contracts/`에 버저닝)
- frontend ([libra-frontend](../libra-frontend)) 대상 REST API
- MySQL 영속성 + Flyway 마이그레이션

이 레포가 책임지지 않는 것:

- LangGraph Judge 오케스트레이션 → [libra-agent](../libra-agent)
- LLM 호출 (Claude/Gemini/Ollama) — agent가 담당, 키도 agent 측 env
- 뉴스/공시/리포트 수집 → [libra-ingest](../libra-ingest)
- frontend UI

## 스택

- Java 21, Spring Boot 3.5
- Spring Web / Validation / Actuator / Data JPA / Security / OAuth2 Client
- jjwt 0.12 (HS256 JWT 발급·검증)
- Flyway (MySQL) — V1 ~ V5
- MySQL 8 (테스트는 H2 + MySQL 호환 모드)
- BCrypt 비밀번호 해시

## 실행

```powershell
cd D:\libra-backend
.\gradlew.bat bootRun
```

기본 포트 `8080`. 테스트는 `gradlew test` (H2 인메모리, Flyway 마이그레이션도 같이 적용).

로컬 backend-agent-MySQL 통합 실행은 [docs/local-e2e.md](docs/local-e2e.md), KIS 매핑/경계는 [docs/kis-portfolio-sync.md](docs/kis-portfolio-sync.md) 참조.

## 인증

### 정책

- 공개 엔드포인트: `/api/v1/auth/**`, `/oauth2/**`, `/login/oauth2/**`, `/actuator/health`, `/actuator/info`
- 그 외 모든 엔드포인트: `Authorization: Bearer <access_token>` 필수
- 토큰: access JWT (HS256, 1h 기본) + opaque random refresh (DB 저장, 14d 기본, 회전·취소 가능)
- 비밀번호: BCrypt 해시
- Multi-tenant: 모든 portfolio·decision-run이 `user_id`로 scope. 다른 사용자 소유 row 접근 시 404 (정보 누출 방지)

### Email / Password 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/auth/signup` | `email` + `password` (8자+) + `name` → 가입 + 토큰 발급 |
| POST | `/api/v1/auth/login` | email + password → 토큰 발급 |
| POST | `/api/v1/auth/refresh` | `refresh_token` → 회전 (기존 무효, 새 발급) |
| POST | `/api/v1/auth/logout` | `refresh_token` revoke |
| GET | `/api/v1/auth/me` | Bearer 헤더로 현재 사용자 정보 |

응답 모양:

```json
{
  "access_token": "eyJ…",
  "access_token_expires_at": "2026-05-03T15:32:00Z",
  "refresh_token": "Ux9-…",
  "refresh_token_expires_at": "2026-05-17T14:32:00Z",
  "token_type": "Bearer",
  "user": { "id": "uuid", "email": "...", "name": "...", "role": "USER", "oauth_provider": null }
}
```

### OAuth (Google / Kakao / Naver)

Frontend가 사용자를 다음 URL로 redirect시켜 진입:

- `GET /oauth2/authorization/google`
- `GET /oauth2/authorization/kakao`
- `GET /oauth2/authorization/naver`

Spring Security가 provider로 redirect → 사용자 동의 → 콜백 `/login/oauth2/code/{provider}` → user upsert (provider+id로 먼저, 없으면 email로 link, 없으면 신규) → 토큰 발급 → frontend redirect:

```
${LIBRA_OAUTH_FRONTEND_SUCCESS_URI}?provider=kakao&access_token=…&refresh_token=…&access_token_expires_at=…&refresh_token_expires_at=…
```

실패 시: `${LIBRA_OAUTH_FRONTEND_FAILURE_URI}?error=...`

각 콘솔에 등록할 Redirect URI:

- Google ([credentials](https://console.cloud.google.com/apis/credentials)): `http://localhost:8080/login/oauth2/code/google`
- Kakao ([apps](https://developers.kakao.com/console/app)): `http://localhost:8080/login/oauth2/code/kakao` + 동의 항목 `account_email`, `profile_nickname`
- Naver ([apps](https://developers.naver.com/apps)): `http://localhost:8080/login/oauth2/code/naver` + 권한 `email`, `name`

env에 client-id/secret이 비어있는 provider는 자동으로 비활성화 (애플리케이션은 정상 시작).

## 도메인 엔드포인트

### 포트폴리오 (사용자 scoped)

```http
GET /api/v1/portfolios/current
Authorization: Bearer <access>
```

```http
POST /api/v1/portfolios/current
Authorization: Bearer <access>
Content-Type: application/json

{ "generated_at": "...", "holdings": [...], ... }
```

```http
POST /api/v1/portfolios/current/sync/kis
Authorization: Bearer <access>
Content-Type: application/json

{ "environment": "real", "user_preferences": [...] }
```

KIS sync는 먼저 사용자별 저장 credential을 사용하고, 없으면 backend env credential로 fallback. 저장 credential이 있으면 그 credential에 저장된 `environment`가 KIS URL을 결정하고, 요청의 `environment`는 env fallback을 고를 때만 의미가 있다. manual `POST /current`와 sync 결과는 모두 MySQL `portfolio_snapshots`에 사용자별로 저장. `current`는 그 사용자의 가장 최근 스냅샷.

### KIS credential (사용자 scoped)

```http
GET /api/v1/kis/credential
Authorization: Bearer <access>
```

```http
PUT /api/v1/kis/credential
Authorization: Bearer <access>
Content-Type: application/json

{
  "environment": "real",
  "app_key": "...",
  "app_secret": "...",
  "account_no": "12345678-01",
  "product_code": "01",
  "user_agent": "LIBRA-Frontend/1.0"
}
```

```http
DELETE /api/v1/kis/credential
Authorization: Bearer <access>
```

저장된 KIS app key, app secret, account number는 `LIBRA_KIS_CREDENTIAL_SECRET`에서 파생한 AES-GCM key로 암호화해 `kis_credentials`에 저장한다. 조회 응답은 설정 여부, 환경, 마스킹된 app key/account, product code, updated_at만 반환한다.

### Judge run

```http
POST /api/v1/judge-runs
Authorization: Bearer <access>
Content-Type: application/json

{
  "query": "포트폴리오 점검",
  "portfolio": { ... },
  "knowledge_sources": { "events": "s3://...", "normalized_documents": "s3://..." },
  "depth": "medium",
  "trigger": "pull"
}
```

`portfolio`가 비면 backend가 그 사용자의 latest snapshot을 자동 사용. 응답에는 `X-Libra-Decision-Run-Id`, `X-Libra-Thread-Id` 헤더가 포함됨. 결정 결과는 `decision_runs` 테이블에 사용자별로 저장.

agent가 응답하지 않으면 `502 Bad Gateway`로 실패합니다. Judge run이 성공으로 저장되려면 `libra-agent`가 실제로 응답해야 합니다.

`knowledge_sources`가 비고 `LIBRA_KNOWLEDGE_LOCAL_DIR`에 fresh 캐시가 있으면 자동 attach. freshness는 `manifest.json`을 `LIBRA_KNOWLEDGE_MAX_AGE_MINUTES` 기준으로 검증.

### 결정 이력

```http
GET /api/v1/decision-runs               # 최근 20개 (사용자별)
GET /api/v1/decision-runs/{id}          # 본인 소유만 조회
GET /api/v1/decision-runs/{id}/evaluations
POST /api/v1/decision-runs/{id}/evaluations
```

다른 사용자 소유 run 접근 시 404 (`DecisionRunNotFoundException`).

## DB 마이그레이션

| 버전 | 내용 |
|---|---|
| V1 | decision_runs + agent_signals + rebalance_plan_items + user_feedback + executions + evaluations |
| V2 | portfolio_snapshots |
| V3 | users + refresh_tokens (이메일/패스워드 + OAuth provider/id 저장 가능) |
| V4 | portfolio_snapshots, decision_runs에 user_id 컬럼 + FK + 인덱스 (multi-tenant) |
| V5 | kis_credentials (사용자별 KIS credential 암호화 저장) |

## CORS

```yaml
libra:
  cors:
    allowed-origins: ${LIBRA_CORS_ALLOWED_ORIGINS:http://localhost:5173}
```

여러 origin은 콤마로 구분. credentials 허용, 응답 헤더 `X-Libra-Decision-Run-Id`, `X-Libra-Thread-Id` 노출.

## 환경 변수

`.env.example` 참고.

### 인증

- `LIBRA_JWT_SECRET` (32바이트+, base64 권장)
- `LIBRA_JWT_ACCESS_TTL_MINUTES` (default 60)
- `LIBRA_JWT_REFRESH_TTL_DAYS` (default 14)
- `LIBRA_JWT_ISSUER` (default `libra-backend`)

### OAuth (각 provider별, 비워두면 비활성화)

- `LIBRA_OAUTH_GOOGLE_CLIENT_ID`, `LIBRA_OAUTH_GOOGLE_CLIENT_SECRET`
- `LIBRA_OAUTH_KAKAO_CLIENT_ID`, `LIBRA_OAUTH_KAKAO_CLIENT_SECRET`
- `LIBRA_OAUTH_NAVER_CLIENT_ID`, `LIBRA_OAUTH_NAVER_CLIENT_SECRET`
- `LIBRA_OAUTH_FRONTEND_SUCCESS_URI` (default `http://localhost:5173/auth/callback`)
- `LIBRA_OAUTH_FRONTEND_FAILURE_URI` (default `http://localhost:5173/auth/callback`)

### CORS

- `LIBRA_CORS_ALLOWED_ORIGINS` (default `http://localhost:5173,http://127.0.0.1:5173`)

### KIS

- `LIBRA_KIS_CREDENTIAL_SECRET` (사용자별 KIS credential AES-GCM 암호화 key seed. 운영에서는 32바이트+ random secret 권장)
- `LIBRA_KIS_REAL_APP_KEY`, `LIBRA_KIS_REAL_APP_SECRET`, `LIBRA_KIS_REAL_ACCOUNT_NO`, `LIBRA_KIS_REAL_PRODUCT_CODE`
- `LIBRA_KIS_DEMO_*` (모의투자)
- 사용자별 credential이 저장되어 있으면 `/sync/kis`가 그것을 우선 사용. 없으면 위 backend env credential을 fallback으로 사용

### Agent boundary

- `LIBRA_AGENT_BASE_URL`, `LIBRA_AGENT_CONNECT_TIMEOUT_MS`, `LIBRA_AGENT_READ_TIMEOUT_MS`

### Knowledge cache

- `LIBRA_KNOWLEDGE_LOCAL_DIR`, `LIBRA_KNOWLEDGE_MAX_AGE_MINUTES`

### AWS / DB

- `LIBRA_DB_HOST`, `LIBRA_DB_PORT`, `LIBRA_DB_NAME`, `LIBRA_DB_USER`, `LIBRA_DB_PASSWORD`
- `LIBRA_S3_REGION`, `LIBRA_S3_BUCKET`

## 패키지 구조

- `com.libra.api.LibraApiApplication` — 진입점
- `com.libra.api.auth/` — 인증/인가 (JWT, OAuth, SecurityConfig, CorsConfig, Auth controller, User entity, Refresh token entity)
- `com.libra.api.portfolio/` — 포트폴리오 스냅샷 (user-scoped)
- `com.libra.api.decision/` — 결정 이력 (user-scoped) + 평가
- `com.libra.api.judge/` — Judge run 분배 controller
- `com.libra.api.integration.agent/` — libra-agent HTTP 게이트웨이
- `com.libra.api.integration.kis/` — KIS OpenAPI 클라이언트 + 매핑
- `com.libra.api.knowledge/` — 로컬 ingest cache 자동 attach
- `com.libra.api.common/` — 공통 예외 처리
