param(
    [int]$ServerPort = 8080,
    [string]$AgentBaseUrl = "http://127.0.0.1:8010",
    [int]$AgentReadTimeoutMs = 240000,
    [string]$KnowledgeLocalDir = "D:\libra-data\knowledge\sample",
    [int]$KnowledgeMaxAgeMinutes = 240
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")

$env:SERVER_PORT = [string]$ServerPort
$env:SPRING_DATASOURCE_URL = "jdbc:h2:mem:libra_e2e;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = "org.h2.Driver"
$env:SPRING_DATASOURCE_USERNAME = "sa"
$env:SPRING_DATASOURCE_PASSWORD = ""
$env:LIBRA_AGENT_BASE_URL = $AgentBaseUrl
$env:LIBRA_AGENT_READ_TIMEOUT_MS = [string]$AgentReadTimeoutMs
$env:LIBRA_KNOWLEDGE_LOCAL_DIR = $KnowledgeLocalDir
$env:LIBRA_KNOWLEDGE_MAX_AGE_MINUTES = [string]$KnowledgeMaxAgeMinutes
$env:LIBRA_JWT_SECRET = "local-e2e-local-e2e-local-e2e-32bytes"

Push-Location $repoRoot
try {
    .\gradlew.bat bootRun --console=plain
}
finally {
    Pop-Location
}
