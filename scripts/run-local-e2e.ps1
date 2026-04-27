param(
    [string]$BackendBaseUrl = "http://localhost:8080",
    [string]$KnowledgeDir = "",
    [string]$EventsPath = "",
    [string]$NormalizedDocumentsPath = "",
    [string]$EnrichedDocumentsPath = ""
)

$ErrorActionPreference = "Stop"

if ($KnowledgeDir) {
    if (-not $EventsPath) {
        $EventsPath = Join-Path $KnowledgeDir "events.json"
    }
    if (-not $NormalizedDocumentsPath) {
        $NormalizedDocumentsPath = Join-Path $KnowledgeDir "normalized_documents.json"
    }
    if (-not $EnrichedDocumentsPath) {
        $candidate = Join-Path $KnowledgeDir "enriched_documents.json"
        if (Test-Path -LiteralPath $candidate) {
            $EnrichedDocumentsPath = $candidate
        }
    }
}

$knowledgeSources = @{}
if ($EventsPath) {
    if (-not (Test-Path -LiteralPath $EventsPath)) {
        throw "EventsPath does not exist: $EventsPath"
    }
    $knowledgeSources.events = (Resolve-Path -LiteralPath $EventsPath).Path
}
if ($NormalizedDocumentsPath) {
    if (-not (Test-Path -LiteralPath $NormalizedDocumentsPath)) {
        throw "NormalizedDocumentsPath does not exist: $NormalizedDocumentsPath"
    }
    $knowledgeSources.normalized_documents = (Resolve-Path -LiteralPath $NormalizedDocumentsPath).Path
}
if ($EnrichedDocumentsPath) {
    if (-not (Test-Path -LiteralPath $EnrichedDocumentsPath)) {
        throw "EnrichedDocumentsPath does not exist: $EnrichedDocumentsPath"
    }
    $knowledgeSources.enriched_documents = (Resolve-Path -LiteralPath $EnrichedDocumentsPath).Path
}

$payloadObject = @{
    query = "포트폴리오 점검"
    portfolio = @{
        generated_at = "2026-04-15T09:00:00+09:00"
        holdings = @(
            @{
                ticker = "005930"
                company_name = "삼성전자"
                weight = 0.48
                aliases = @("005930.KS", "KRX:005930", "Samsung Electronics")
                shares = 12
                last_price = 60000
            },
            @{
                ticker = "000660"
                company_name = "SK하이닉스"
                weight = 0.36
                aliases = @("000660.KS", "KRX:000660", "SK hynix", "SK Hynix")
                shares = 4
                last_price = 180000
            }
        )
        total_value_krw = 1500000
        cash_weight = 0.16
        user_preferences = @(
            "장기 보유 우선",
            "규제성 이슈는 사용자 승인 필요"
        )
    }
    depth = "medium"
    trigger = "pull"
    enable_human_interrupts = $false
}

if ($knowledgeSources.Count -gt 0) {
    $payloadObject.knowledge_sources = $knowledgeSources
}

$payload = $payloadObject | ConvertTo-Json -Depth 20

$judgeUrl = "$BackendBaseUrl/api/v1/judge-runs"
$response = Invoke-WebRequest `
    -Uri $judgeUrl `
    -Method Post `
    -ContentType "application/json; charset=utf-8" `
    -Body $payload `
    -UseBasicParsing

$body = $response.Content | ConvertFrom-Json
$runId = $response.Headers["X-Libra-Decision-Run-Id"]
$threadId = $response.Headers["X-Libra-Thread-Id"]

Write-Host "Judge run completed"
Write-Host "  decision_run_id: $runId"
Write-Host "  thread_id      : $threadId"
Write-Host "  model          : $($body.model)"
Write-Host "  decision       : $($body.decision.decision)"
Write-Host "  urgency        : $($body.decision.urgency)"
Write-Host "  summary        : $($body.decision.summary)"
if ($knowledgeSources.Count -gt 0) {
    Write-Host "  knowledge      : $($knowledgeSources | ConvertTo-Json -Compress)"
}

if ($body.runtime.agent_gateway_error) {
    Write-Host "  gateway_error  : $($body.runtime.agent_gateway_error)"
}

Write-Host ""
Write-Host "Recent decision runs:"
$recent = Invoke-RestMethod -Uri "$BackendBaseUrl/api/v1/decision-runs" -Method Get
$recent | Select-Object -First 5 | Format-Table id, decision, urgency, confidence, created_at -AutoSize

if ($runId) {
    Write-Host ""
    Write-Host "Stored decision detail:"
    Invoke-RestMethod -Uri "$BackendBaseUrl/api/v1/decision-runs/$runId" -Method Get |
        ConvertTo-Json -Depth 8
}
