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
- **A-2**: backend 는 Postgres 직접 두드리지 않음 — 오직 `users` 테이블만 소유. 도메인 데이터(포트폴리오·결정 이력·보고서 메타)는 전부 `libra-agent` REST 경유.
- **B-2**: `libra-agent` 의 SSE 를 그대로 Vue 까지 흘림 (passthrough). backend 는 인증/인가 + traceId 만 보탬.
- **Flyway**: 시연 destroy → 재배포 시 V1 만 다시 돌면 같은 스키마. `V2__seed` 안 만들고 `DemoDataSeeder` 컴포넌트가 profile=local/dev/demo 일 때 demo user 1명 자동 생성.

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
| POST | `/api/runs` | bearer | agent 실행 시작 + SSE relay. `approval_required=true` 이면 HITL interrupt 흐름 |
| POST | `/api/runs/{threadId}/resume` | bearer | agent 실행 재개 + SSE relay |
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
| correlation_id | `common/correlation/CorrelationIdFilter` — `X-Trace-Id` 헤더 + MDC `traceId` |
| 에러 응답 | `common/error/GlobalExceptionHandler` — RFC 7807 ProblemDetail |
| 로깅 | `logback-spring.xml` — local 은 텍스트, 그 외 JSON (logstash encoder) |

## 마이그레이션
- `src/main/resources/db/migration/V*.sql`
- `jpa.hibernate.ddl-auto: validate` (스키마는 Flyway 단일 소스)

## 다음 작업
- 회원가입 보강 — 이메일 verify / 비번 reset / 탈퇴
- agent RunEvent 스키마 확정 후 OpenAPI 예시 보강
