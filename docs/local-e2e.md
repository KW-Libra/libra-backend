# Local E2E Run

This flow checks the full local path:

```text
libra-backend -> libra-agent -> local LLM -> libra-backend Decision DB
```

## 1. Start MySQL

```powershell
cd D:\libra-backend
.\scripts\start-local-mysql.ps1
```

Default data path:

```text
D:/libra-data/mysql
```

If port `3306` is already in use:

```powershell
.\scripts\start-local-mysql.ps1 -Port 3308 -DataDir D:/libra-data/mysql-backend-e2e
```

Then start backend with the same port:

```powershell
.\scripts\start-backend-local.ps1 -ServerPort 18080 -DbPort 3308
```

## 2. Build a local knowledge cache

In a separate terminal:

```powershell
cd D:\libra-ingest
.\scripts\build-local-cache.ps1 -Mode sample -OutDir D:\libra-data\knowledge\sample
```

This writes:

```text
D:\libra-data\knowledge\sample\events.json
D:\libra-data\knowledge\sample\normalized_documents.json
```

For a small live probe:

```powershell
cd D:\libra-ingest
.\scripts\build-local-cache.ps1 `
  -Mode live `
  -OutDir D:\libra-data\knowledge\live_probe `
  -RssLimit 3 `
  -DartLimit 5 `
  -ReportLimit 0 `
  -SkipArticleBody
```

## 3. Start llama.cpp

In a separate terminal:

```powershell
cd D:\libra-agent
.\scripts\start-supergemma-llama.ps1
```

Expected llama.cpp URL:

```text
http://localhost:8081
```

## 4. Start libra-agent API

In a separate terminal:

```powershell
cd D:\libra-agent
$env:LIBRA_LLM_PROVIDER = "llama_cpp"
$env:LIBRA_LLAMA_CPP_BASE_URL = "http://localhost:8081"
$env:LIBRA_AGENT_STATE_DIR = "D:/libra-data/agent-state"
D:\Libra\.venv\Scripts\python.exe -m libra_agent.libra_api --host 0.0.0.0 --port 8010
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8010/health
```

## 5. Start backend

In a separate terminal:

```powershell
cd D:\libra-backend
.\scripts\start-backend-local.ps1 `
  -AgentReadTimeoutMs 180000 `
  -KnowledgeLocalDir D:\libra-data\knowledge\sample
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 6. Run E2E request

In a separate terminal:

```powershell
cd D:\libra-backend
.\scripts\run-local-e2e.ps1
```

To connect ingest output into the run:

```powershell
.\scripts\run-local-e2e.ps1 `
  -BackendBaseUrl http://127.0.0.1:18080 `
  -KnowledgeDir D:\libra-data\knowledge\sample
```

If backend was started with `-KnowledgeLocalDir`, the request can omit `-KnowledgeDir`; backend will attach that cache automatically while `manifest.json` is fresh.

If you want the Judge request to use the stored portfolio instead of sending a portfolio inline, save or sync the current portfolio first:

```powershell
$portfolio = @{
  generated_at = "2026-04-27T09:00:00+09:00"
  holdings = @(
    @{ ticker = "005930"; company_name = "삼성전자"; weight = 0.45; aliases = @("005930.KS", "KRX:005930"); shares = 10; last_price = 65000 },
    @{ ticker = "000660"; company_name = "SK하이닉스"; weight = 0.35; aliases = @("000660.KS", "KRX:000660"); shares = 4; last_price = 180000 }
  )
  total_value_krw = 1500000
  cash_weight = 0.20
  user_preferences = @("국내 대형주 중심", "규제성 이슈는 사용자 승인 필요")
} | ConvertTo-Json -Depth 8

Invoke-RestMethod `
  -Uri http://127.0.0.1:18080/api/v1/portfolios/current `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $portfolio
```

For KIS, configure `LIBRA_KIS_*` environment variables and call:

```powershell
Invoke-RestMethod `
  -Uri http://127.0.0.1:18080/api/v1/portfolios/current/sync/kis `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body '{"environment":"real"}'
```

Both paths persist `portfolio_snapshots` in MySQL. The latest snapshot becomes `GET /api/v1/portfolios/current` and the default portfolio for `POST /api/v1/judge-runs` when `portfolio` is omitted.

The script prints:

- `X-Libra-Decision-Run-Id`
- `X-Libra-Thread-Id`
- final decision
- recent persisted decision runs
- stored decision detail

If `runtime.agent_gateway_error` appears, backend could not reach `libra-agent` and used the fallback stub. That still proves MySQL persistence works, but not the full agent path.

## Useful Commands

Stop MySQL:

```powershell
cd D:\libra-backend
docker compose -f docker-compose.local.yml down
```

View MySQL logs:

```powershell
docker compose -f docker-compose.local.yml logs -f mysql
```

Open MySQL shell:

```powershell
docker exec -it libra-local-mysql mysql -ulibra -plibra_dev_password libra
```
