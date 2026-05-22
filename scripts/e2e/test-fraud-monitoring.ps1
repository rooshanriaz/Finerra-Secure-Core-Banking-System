<#
.SYNOPSIS
  End-to-end verification of fraud detection & monitoring (score → alert → list → stats).

.DESCRIPTION
  1. Obtains an admin JWT (Keycloak password grant via finnera-web).
  2. Reads current `flag` / `block` thresholds, then sets `flag` to 0 so the next scored
     transaction is guaranteed to produce a FLAG (and a persisted fraud alert).
  3. POST /api/v1/fraud/score through the API gateway.
  4. Asserts alerts list and stats reflect the new alert.
  5. Restores original thresholds.

  Requires: stack up (Keycloak 8090, API gateway 8080, fraud + ML services).

.PARAMETER GatewayUrl
  Base URL of the API gateway (default http://localhost:8080).

.PARAMETER KeycloakUrl
  Keycloak base (default http://localhost:8090).

.PARAMETER AdminUser
  Realm user with ROLE_ADMIN (default admin).

.PARAMETER AdminPassword
  Admin password (default from docker/keycloak realm import).
#>
param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$KeycloakUrl = "http://localhost:8090",
    [string]$Realm = "finnera",
    [string]$ClientId = "finnera-web",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "Admin@2026!"
)

$ErrorActionPreference = "Stop"

function Get-AdminToken {
    $tokenUrl = "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token"
    $body = @{
        grant_type = "password"
        client_id  = $ClientId
        username   = $AdminUser
        password   = $AdminPassword
    }
    $resp = Invoke-RestMethod -Method Post -Uri $tokenUrl -Body $body -ContentType "application/x-www-form-urlencoded"
    if (-not $resp.access_token) { throw "No access_token from Keycloak" }
    return $resp.access_token
}

function Invoke-GatewayJson {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        [object]$Body = $null
    )
    $uri = "$GatewayUrl$Path"
    $headers = @{
        Authorization = "Bearer $Token"
        Accept        = "application/json"
    }
    if ($Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body ($Body | ConvertTo-Json -Depth 6) -ContentType "application/json"
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

Write-Host "=== Fraud detection & monitoring E2E ===" 
Write-Host "Gateway: $GatewayUrl"

$token = Get-AdminToken
Write-Host "[1] Admin token acquired."

$thresholdsBefore = Invoke-GatewayJson -Method Get -Path "/api/v1/fraud/thresholds" -Token $token
if (-not $thresholdsBefore.success) { throw "GET thresholds failed: $($thresholdsBefore | ConvertTo-Json -Compress)" }

$flagBefore = ($thresholdsBefore.data | Where-Object { $_.thresholdName -eq "flag" } | Select-Object -First 1).value
$blockBefore = ($thresholdsBefore.data | Where-Object { $_.thresholdName -eq "block" } | Select-Object -First 1).value
Write-Host "[2] Current thresholds: flag=$flagBefore block=$blockBefore"

try {
    Write-Host "[3] Temporarily setting flag=0.0 so next score creates an alert..."
    $null = Invoke-GatewayJson -Method Put -Path "/api/v1/fraud/thresholds" -Token $token -Body @{
        thresholdName = "flag"
        value           = 0.0
        description     = "E2E test; restored after run"
    }

    $txnId = "E2E-FRD-" + [Guid]::NewGuid().ToString("N").Substring(0, 12).ToUpperInvariant()
    Write-Host "[4] Scoring transaction $txnId ..."
    $scoreRes = Invoke-GatewayJson -Method Post -Path "/api/v1/fraud/score" -Token $token -Body @{
        transactionId     = $txnId
        accountId         = 880001
        transactionType   = "WITHDRAWAL"
        amount            = 75000
        initiatedBy       = "e2e-fraud-test"
    }

    if (-not $scoreRes.success) { throw "Score API returned success=false: $($scoreRes | ConvertTo-Json -Depth 5)" }
    $data = $scoreRes.data
    if ($data.recommendation -eq "ALLOW") {
        throw "Expected FLAG or BLOCK after flag=0, got ALLOW. Response: $($scoreRes | ConvertTo-Json -Depth 5)"
    }
    if (-not $data.alertId) {
        throw "Expected alertId for non-ALLOW recommendation. Response: $($scoreRes | ConvertTo-Json -Depth 5)"
    }
    Write-Host "    recommendation=$($data.recommendation) alertId=$($data.alertId) riskScore=$($data.riskScore)"

    Write-Host "[5] Fetching alerts and stats..."
    $alertsRes = Invoke-GatewayJson -Method Get -Path "/api/v1/fraud/alerts" -Token $token
    $statsRes  = Invoke-GatewayJson -Method Get -Path "/api/v1/fraud/alerts/stats" -Token $token

    if (-not $alertsRes.success) { throw "GET alerts failed" }
    if (-not $statsRes.success) { throw "GET stats failed" }

    $found = $alertsRes.data | Where-Object { $_.transactionId -eq $txnId }
    if (-not $found) {
        throw "New alert not found in GET /alerts for transactionId=$txnId"
    }
    Write-Host "    Stats: total=$($statsRes.data.total) open=$($statsRes.data.open) critical=$($statsRes.data.critical) high=$($statsRes.data.high)"
    Write-Host "[PASS] Fraud monitoring pipeline OK (score, DB alert, list, stats)."
}
finally {
    if ($null -ne $flagBefore) {
        Write-Host "[6] Restoring flag threshold to $flagBefore ..."
        $null = Invoke-GatewayJson -Method Put -Path "/api/v1/fraud/thresholds" -Token $token -Body @{
            thresholdName = "flag"
            value           = [double]$flagBefore
            description     = "ML risk score at or above this value triggers FLAG"
        }
    }
    Write-Host "Done."
}
