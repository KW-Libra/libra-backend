# libra-backend

Spring Boot backend repository for LIBRA.

This repository owns:

- frontend-facing REST APIs
- users, portfolios, approvals, schedules, and run history
- Korea Investment portfolio sync
- RDS MySQL persistence
- S3 object access for documents and collected artifacts
- HTTP boundary to `libra-agent`

This repository does not own:

- LangGraph Judge orchestration
- LLM provider adapters
- news/disclosure/report collection pipelines
- frontend UI

## Current Scope

- in-memory portfolio state for local development
- Korea Investment domestic-stock portfolio sync endpoint
- contract-shaped Judge run stub endpoint
- shared `contracts/` copied from the current agent boundary

## Run

```powershell
cd D:\libra-backend
.\gradlew.bat bootRun
```

## Example Endpoints

### Sync portfolio from KIS

```http
POST /api/v1/portfolios/current/sync/kis
Content-Type: application/json

{
  "environment": "real",
  "account_no": "12345678",
  "product_code": "01",
  "user_preferences": [
    "국내 대형주 중심",
    "실적 시즌 변동성 감내 가능"
  ]
}
```

### Read current portfolio

```http
GET /api/v1/portfolios/current
```

### Dispatch Judge run

```http
POST /api/v1/judge-runs
Content-Type: application/json

{
  "query": "포트폴리오 점검",
  "portfolio": {
    "generated_at": "2026-04-15T09:00:00+09:00",
    "holdings": [
      {
        "ticker": "005930",
        "company_name": "삼성전자",
        "weight": 0.48,
        "aliases": ["005930.KS", "KRX:005930"],
        "shares": 12,
        "last_price": 60000
      }
    ],
    "total_value_krw": 1500000,
    "cash_weight": 0.16,
    "user_preferences": ["실적 악화 시 빠른 점검"]
  },
  "knowledge_sources": {
    "events": "s3://libra-bucket/events/latest.json",
    "normalized_documents": "s3://libra-bucket/documents/latest.json"
  },
  "depth": "medium",
  "trigger": "pull"
}
```

The current Judge endpoint returns a contract-shaped stub response so frontend and backend work can proceed before the real `libra-agent` bridge is wired.

## Environment Variables

KIS:

- `LIBRA_KIS_REAL_APP_KEY`
- `LIBRA_KIS_REAL_APP_SECRET`
- `LIBRA_KIS_REAL_ACCOUNT_NO`
- `LIBRA_KIS_REAL_PRODUCT_CODE`
- `LIBRA_KIS_DEMO_APP_KEY`
- `LIBRA_KIS_DEMO_APP_SECRET`
- `LIBRA_KIS_DEMO_ACCOUNT_NO`
- `LIBRA_KIS_DEMO_PRODUCT_CODE`

Agent boundary:

- `LIBRA_AGENT_BASE_URL`

AWS:

- `LIBRA_DB_HOST`
- `LIBRA_DB_PORT`
- `LIBRA_DB_NAME`
- `LIBRA_DB_USER`
- `LIBRA_DB_PASSWORD`
- `LIBRA_S3_REGION`
- `LIBRA_S3_BUCKET`
