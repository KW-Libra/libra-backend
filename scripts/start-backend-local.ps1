param(
    [int]$ServerPort = 8080,
    [int]$DbPort = 3306,
    [string]$DbName = "libra",
    [string]$DbUser = "libra",
    [string]$DbPassword = "libra_dev_password",
    [string]$AgentBaseUrl = "http://localhost:8010",
    [int]$AgentReadTimeoutMs = 180000,
    [string]$KnowledgeLocalDir = "",
    [int]$KnowledgeMaxAgeMinutes = 240,
    [bool]$FallbackToStub = $true
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")

$env:LIBRA_DB_HOST = "localhost"
$env:LIBRA_DB_PORT = [string]$DbPort
$env:LIBRA_DB_NAME = $DbName
$env:LIBRA_DB_USER = $DbUser
$env:LIBRA_DB_PASSWORD = $DbPassword
$env:LIBRA_AGENT_BASE_URL = $AgentBaseUrl
$env:LIBRA_AGENT_READ_TIMEOUT_MS = [string]$AgentReadTimeoutMs
$env:LIBRA_AGENT_FALLBACK_TO_STUB = $FallbackToStub.ToString().ToLowerInvariant()
$env:LIBRA_KNOWLEDGE_LOCAL_DIR = $KnowledgeLocalDir
$env:LIBRA_KNOWLEDGE_MAX_AGE_MINUTES = [string]$KnowledgeMaxAgeMinutes
$env:SERVER_PORT = [string]$ServerPort

Push-Location $repoRoot
try {
    .\gradlew.bat bootRun
}
finally {
    Pop-Location
}
