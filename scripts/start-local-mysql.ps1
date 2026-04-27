param(
    [string]$DataDir = "D:/libra-data/mysql",
    [int]$Port = 3306,
    [string]$Database = "libra",
    [string]$User = "libra",
    [string]$Password = "libra_dev_password",
    [string]$RootPassword = "libra_root_password"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$composeFile = Join-Path $repoRoot "docker-compose.local.yml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI was not found. Start Docker Desktop and ensure docker is on PATH."
}

New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

$env:LIBRA_MYSQL_DATA_DIR = $DataDir
$env:LIBRA_DB_PORT = [string]$Port
$env:LIBRA_DB_NAME = $Database
$env:LIBRA_DB_USER = $User
$env:LIBRA_DB_PASSWORD = $Password
$env:LIBRA_DB_ROOT_PASSWORD = $RootPassword

docker compose -f $composeFile up -d mysql
docker compose -f $composeFile ps

Write-Host ""
Write-Host "MySQL local settings for this shell:"
Write-Host "`$env:LIBRA_DB_HOST='localhost'"
Write-Host "`$env:LIBRA_DB_PORT='$Port'"
Write-Host "`$env:LIBRA_DB_NAME='$Database'"
Write-Host "`$env:LIBRA_DB_USER='$User'"
Write-Host "`$env:LIBRA_DB_PASSWORD='$Password'"
