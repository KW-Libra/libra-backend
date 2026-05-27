# libra-backend

Libra Spring Boot API. 멀티에이전트 의사결정 거버넌스 시스템의 표면 레이어 — 인증 + Vue↔agent 중계 + SSE relay.

## Stack
- Java 21 (Eclipse Temurin) — Spring Boot 4 는 17+ baseline, 25 도 OK지만 toolchain 자동 설치 환경 의존이라 21 로 고정
- Spring Boot 4.0 + Spring Framework 7 + Jakarta EE 11
- PostgreSQL 16 + Flyway + JPA (`ddl-auto: validate`)
- JWT (jjwt 0.12)
- Swagger UI / OpenAPI (springdoc)
- Gradle 8.14.3+ Kotlin DSL — *Spring Boot 4.0 플러그인이 Gradle 8.14+ 또는 9.x 요구* (8.10/8.13 안 됨)

## 의사결정 (왜 이렇게?)
- **Backend-owned domain DB**: backend 가 인증/사용자, 포트폴리오, 브로커 연동, 주문/audit 같은 비즈니스 영속 데이터를 소유한다. `libra-agent` 는 판단 워크플로우를 수행하고, 도메인 데이터의 system of record 가 되지 않는다.
- **B-2**: `libra-agent` 의 SSE 를 그대로 Vue 까지 흘림 (passthrough). backend 는 인증/인가 + traceId 만 보탬.
- **Flyway**: 스키마는 migration 이 단일 소스. seed migration 은 만들지 않고 `DemoDataSeeder` 컴포넌트가 profile=local/dev/demo 일 때 demo user 1명 자동 생성.

## Run (local)

```powershell
# 1. Postgres 띄우기
docker compose up -d

# 2. env
copy .env.example .env

# 3. wrapper (최초 1회) — Spring Boot 4.0 은 Gradle 8.14+ 필요
gradle wrapper --gradle-version 8.14.3

# 4. run
.\gradlew.bat bootRun
```

http://localhost:8080

Swagger UI:

- local: http://localhost:8080/api/docs
- prod: https://3-34-80-58.nip.io/api/docs

로그인 API로 받은 `accessToken`을 Swagger UI의 `Authorize` 버튼에 Bearer token으로 넣으면 보호된 API를 바로 호출할 수 있다.

## 엔드포인트 (현재 뼈대)

| Method | Path | Auth | 비고 |
|---|---|---|---|
| GET | `/health` | public | service / now |
| POST | `/api/auth/signup` | public | `{email, password, displayName?}` |
| POST | `/api/auth/login` | public | `{email, password}` → JWT |
| GET | `/api/auth/me` | bearer | 현재 사용자 |
| POST | `/api/runs` | bearer | live ingest bundle 생성 후 agent 실행 시작 + SSE relay. `approval_required=true` 이면 HITL interrupt 흐름 |
| POST | `/api/runs/{threadId}/resume` | bearer | agent 실행 재개 + SSE relay |
| GET | `/api/backtests/public-rss-3y/validation` | public | S3에 저장된 3년 public RSS 백테스트 검증 요약. 프론트에는 raw artifact를 배포하지 않음 |
| POST | `/api/admin/backtests/runs` | bearer | `libra-agent` 공식 committee replay 백테스트 시작. `startDate`, `endDate`, `decisionFrequency`, `decisionInterval` 로 기간/판단 주기 지정 |
| GET | `/api/admin/backtests/runs/{runId}` | bearer | 백테스트 raw row, 결정 분포, usage, stdout/stderr tail 상태 조회 |
| GET | `/api/market/kis/status` | bearer | KIS 연동 설정 상태. 키 값은 노출하지 않음 |
| GET | `/api/market/kis/quotes/{symbol}` | bearer | KIS 국내주식 현재가 조회. 예: `005930` |
| GET | `/api/market/kis/symbols/{symbol}` | bearer | 현재가 메타데이터 기반 종목 코드 확인 |
| GET | `/api/broker/kis/account/balance` | bearer | KIS 국내주식 계좌 잔고/보유종목 조회 + 기본 portfolio snapshot 저장. `saveSnapshot=false` 로 저장 생략 |
| GET | `/api/broker/kis/account/buyable` | bearer | KIS 종목별 매수가능 금액/수량 조회. `symbol`, `price`, `orderDivision` |
| GET | `/api/broker/kis/credentials` | bearer | 내 KIS API 키 등록 상태. 키 원문은 반환하지 않음 |
| PUT | `/api/broker/kis/credentials` | bearer | 내 KIS API 키 등록/교체. App Key/Secret은 암호화 저장 |
| DELETE | `/api/broker/kis/credentials` | bearer | 내 KIS API 키 삭제 |
| POST | `/api/broker/kis/orders/cash` | bearer | KIS 국내주식 현금주문. 모의투자는 KIS `paper` 환경을 사용하며, 주문 전송은 `KIS_TRADING_ENABLED=true` 필요. 재시도 중복 방지는 `Idempotency-Key` 헤더 사용 |
| GET | `/api/broker/kis/orders/audits` | bearer | 내 KIS 주문 audit log 최근 목록 |
| GET | `/api/broker/kis/orders/audits/{id}` | bearer | 내 KIS 주문 audit log 단건 조회 |
| GET | `/api/portfolio/snapshots` | bearer | 내 portfolio snapshot 최근 목록 |
| GET | `/api/portfolio/snapshots/latest` | bearer | 내 최신 portfolio snapshot 상세 |
| GET | `/api/portfolio/snapshots/{id}` | bearer | 내 portfolio snapshot 단건 상세 |
| GET | `/api/backtests/{experimentId}/validation` | bearer | 실제 validation artifact(JSON/CSV)를 읽어 LIBRA-v3 검증 결과 제공. 기본 root는 `LIBRA_BACKTEST_OUTPUT_ROOT` |
| GET | `/api/docs` | public | Swagger UI |
| GET | `/api/openapi` | public | OpenAPI JSON |

## Demo 계정 (auto-seeded)
- `demo@libra.local` / `demo1234`

`local`/`dev`/`demo` 프로파일에서만 자동 생성. prod 에선 생성 X.

## 횡단

| 항목 | 위치 |
|---|---|
| CORS | `config/CorsConfig` (allowed-origins 는 `libra.cors.allowed-origins` 설정) |
| JWT 필터 | `auth/security/JwtAuthFilter` — `Authorization: Bearer` *또는* `?token=` (SSE 호환) |
| Agent relay | `agent/AgentSseClient` — backend JWT 인증 후 agent SSE 를 Vue 로 중계 |
| Backtest reports | `backtest` — private S3의 검증 리포트를 읽어 public API로 제공. 기본 URI는 `LIBRA_BACKTEST_PUBLIC_RSS_3Y_VALIDATION_URI` |
| Backtest runner | `backtest` — backend가 `libra-agent/scripts/replay_full_committee_backtest.py`를 직접 실행하고 raw/usage/trace 산출물 상태를 읽음. `LIBRA_AGENT_REPO_ROOT`, `LIBRA_BACKTEST_RUNNER_OUTPUT_DIR`, `LIBRA_BACKTEST_RUNNER_FIXTURE_FILE`, `LIBRA_BACKTEST_RUNNER_ENV_FILE`, `LIBRA_BACKTEST_RUNNER_PYTHON` 로 경로 조정. 기본 fixture는 volume 포함 strict fixture(`comparison-fixture.pykrx-volume.strict.json`) |
| Live ingest | `ingest/LiveIngestService` — run 시작 전 `D:\libra-ingest` live pipeline을 `--require-article-body`로 실행하고, 결과를 백테스트 replay와 같은 `ingest_bundle` 계약으로 agent에 전달 |
| KIS credentials | `broker/kis/domain/KisCredential` — 사용자별 KIS App Key/Secret 암호화 저장. `KIS_CREDENTIAL_ENCRYPTION_KEY` 필요 |
| KIS broker | `broker/kis` — 한국투자증권 시세/계좌/주문 경계. agent 에 broker key 를 주지 않음 |
| KIS order guard | `broker/kis/service/KisOrderRiskGuard` — 최대 수량/금액, 주문구분, 거래소, 허용종목 가드. `KIS_MAX_ORDER_QUANTITY`, `KIS_MAX_ORDER_AMOUNT`, `KIS_ALLOWED_SYMBOLS` 로 조정 |
| Portfolio snapshots | `portfolio` — KIS 잔고 조회 시점의 holdings/summary 를 backend DB에 저장. agent 입력/감사 근거로 사용 |
| correlation_id | `common/correlation/CorrelationIdFilter` — `X-Trace-Id` 헤더 + MDC `traceId` |
| 에러 응답 | `common/error/GlobalExceptionHandler` — RFC 7807 ProblemDetail |
| 로깅 | `logback-spring.xml` — local 은 텍스트, 그 외 JSON (logstash encoder) |

## 마이그레이션
- `src/main/resources/db/migration/V*.sql`
- `jpa.hibernate.ddl-auto: validate` (스키마는 Flyway 단일 소스)

## KIS 주문 안전장치
- 앱 레벨 dry-run은 없다. 모의투자는 KIS `paper` 환경으로만 처리한다.
- KIS App Key/Secret은 Libra 계정별로 등록한다. 서버 env 키는 내부 fallback 용도이고 사용자 credential이 우선이다.
- credential 암호화 키는 운영에서 반드시 `KIS_CREDENTIAL_ENCRYPTION_KEY`로 지정한다.
- 기본 한도: `KIS_MAX_ORDER_QUANTITY=1000`, `KIS_MAX_ORDER_AMOUNT=10000000`
- `KIS_ALLOWED_SYMBOLS=005930,000660` 처럼 지정하면 해당 종목만 주문 가능하다. 비워두면 종목 제한은 없다.
- 지원 주문구분은 우선 `00` 지정가, `01` 시장가만 허용한다.
- 주문 재시도 클라이언트는 `Idempotency-Key` 헤더를 넣어야 같은 요청이 중복 전송되지 않는다.

## 다음 작업
- KIS 주문내역/미체결/취소 조회
- 회원가입 보강 — 이메일 verify / 비번 reset / 탈퇴
- agent RunEvent 스키마 확정 후 OpenAPI 예시 보강
