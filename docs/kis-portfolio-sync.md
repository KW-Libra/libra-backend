# KIS Portfolio Sync Contract

`libra-backend` owns broker integration. The agent repository receives only a normalized portfolio snapshot and must never receive KIS app keys, app secrets, account numbers, or raw broker responses.

## Endpoint

Users save their own KIS credential first. App keys, app secrets, and account numbers stay in `libra-backend`; responses expose only masked metadata.

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

Credential status:

```http
GET /api/v1/kis/credential
Authorization: Bearer <access>
```

```json
{
  "configured": true,
  "environment": "real",
  "app_key_masked": "test*********1234",
  "account_no_masked": "1234*****01",
  "product_code": "01",
  "updated_at": "2026-05-04T10:15:30Z"
}
```

After a credential is stored, portfolio sync can omit broker secrets and account identifiers:

```http
POST /api/v1/portfolios/current/sync/kis
Authorization: Bearer <access>
Content-Type: application/json

{
  "environment": "real",
  "user_preferences": [
    "국내 대형주 중심",
    "실적 시즌 변동성 감내 가능"
  ]
}
```

The endpoint calls the KIS domestic stock balance API, normalizes the response into `PortfolioSnapshot`, and stores it as the current portfolio. It uses the user's encrypted credential first; that stored credential's `environment` selects the KIS base URL. If no user credential exists, it falls back to backend env credentials and the sync request's `environment` selects real/demo. A later `POST /api/v1/judge-runs` request may omit `portfolio`; in that case the backend dispatches the latest stored snapshot to `libra-agent`.

## Field Mapping

| KIS source | Backend field | Agent JSON field | Notes |
| --- | --- | --- | --- |
| `output1[].pdno` | `PortfolioHolding.ticker` | `ticker` | Korean domestic stock code, for example `005930`. |
| `output1[].prdt_name` | `PortfolioHolding.companyName` | `company_name` | Display name from KIS. |
| `output1[].hldg_qty` | `PortfolioHolding.shares` | `shares` | Rows with zero or missing quantity are skipped. |
| `output1[].prpr` | `PortfolioHolding.lastPrice` | `last_price` | Current price. |
| `output1[].pchs_avg_pric` | `PortfolioHolding.averagePrice` | `average_price` | Average purchase price. |
| `output1[].evlu_amt` | `PortfolioHolding.marketValueKrw` | `market_value_krw` | Position market value. |
| `output1[].evlu_pfls_amt` | `PortfolioHolding.unrealizedPnlKrw` | `unrealized_pnl_krw` | Unrealized profit/loss. `0` is preserved when KIS returns it; `null` means the field was absent or invalid. |
| `output2[0].dnca_tot_amt` | `PortfolioSnapshot.cashWeight` numerator | `cash_weight` | Cash value divided by total portfolio value. |
| `output2[0].tot_evlu_amt` | `PortfolioSnapshot.totalValueKrw` | `total_value_krw` | Preferred total portfolio value. |
| `output2[0].evlu_amt_smtl_amt` | stock-value fallback | internal only | Used when total value is missing. |

Fallback rules:

- If `output1[].evlu_amt` is missing, backend derives `market_value_krw` from `hldg_qty * prpr`.
- If `output2[0].tot_evlu_amt` is missing, backend derives `total_value_krw` from stock value plus cash.
- If both summary stock value and row evaluation amounts are missing, backend derives stock value from each row's `hldg_qty * prpr`.

## Invariants

- Rows without `pdno`, `prdt_name`, or a positive `hldg_qty` are skipped.
- Holdings are sorted by descending `weight`, then by `ticker`.
- Aliases include `<ticker>.KS` and `KRX:<ticker>`. Space-collapsed Korean company names are also added when applicable.
- KIS-originated snapshots are persisted with source `KIS_DOMESTIC_BALANCE`.
- Broker credentials and account identifiers remain inside `libra-backend`; user credentials are AES-GCM encrypted in `kis_credentials`.
- `libra-agent` receives only the normalized `judge-run-request` contract.

## Reference Scope

`D:\Libra\references\JYlibra-sample_v1` was used as a reference for KIS field names and portfolio synchronization behavior. Its fixed multi-agent execution order, Node/Supabase/Kafka architecture, and frontend-specific assumptions are not adopted here because they conflict with LIBRA's Judge-first dynamic LangGraph orchestration and Spring Boot backend split.
