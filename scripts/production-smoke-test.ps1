param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AuthUrl = "http://localhost:8082",
    [string]$AuditUrl = "http://localhost:8086"
)

$ErrorActionPreference = "Stop"

function Check-Http200 {
    param([string]$Name, [string]$Url, [hashtable]$Headers = @{})
    try {
        $resp = Invoke-WebRequest -UseBasicParsing -Uri $Url -Headers $Headers -TimeoutSec 30
        if ($resp.StatusCode -ne 200) {
            throw "$Name failed with status $($resp.StatusCode)"
        }
        Write-Host "[PASS] $Name => 200"
    } catch {
        Write-Host "[FAIL] $Name => $($_.Exception.Message)"
        throw
    }
}

Write-Host "=== Production Smoke Test ==="

Check-Http200 -Name "Gateway Health" -Url "$GatewayUrl/actuator/health"
Check-Http200 -Name "OIDC Discovery" -Url "$AuthUrl/.well-known/openid-configuration"
Check-Http200 -Name "OIDC JWKS" -Url "$AuthUrl/oauth2/jwks"

$pair = "admin:admin123"
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$headers = @{ Authorization = "Basic $basic" }
Check-Http200 -Name "Compliance CSV Export" -Url "$AuditUrl/api/v1/audit/reports/export?format=csv" -Headers $headers

Write-Host "All smoke checks passed."
