# Rebuild and replace the running nginx frontend on host port 3001 (container fyp-frontend).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

Write-Host "Stopping and removing existing fyp-frontend (if any)..."
docker compose rm -fs frontend 2>$null | Out-Null

Write-Host "Building frontend image (--no-cache)..."
docker compose build --no-cache frontend
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting fresh container on http://localhost:3001 ..."
docker compose up -d --no-deps frontend
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Done. Open http://localhost:3001 (Ctrl+Shift+R to bypass browser cache)."
