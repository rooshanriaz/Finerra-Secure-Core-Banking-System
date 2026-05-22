<#
.SYNOPSIS
  End-to-end verification of loan application review (Fineract via core banking connector).

.DESCRIPTION
  Exercises the same API flow as the Loan Review page:
  1. Admin JWT (Keycloak password grant).
  2. Resolve clientId and productId:
       a. From -ClientId / -ProductId if supplied.
       b. From GET /api/v1/clients and GET /api/v1/loans/products (CBC -> Fineract).
       c. Directly from Fineract if CBC returns empty (using -FineractUrl).
  3. POST /api/v1/loans to submit an application.
  4. GET /api/v1/loans/{id} to verify the application exists.
  5. Unless -SkipApprove, POST approve (command=approve) with Fineract date format.

  Before first run, seed your Fineract tenant:
    powershell -ExecutionPolicy Bypass -File .\scripts\fineract-seed.ps1

  Requires:
    - Keycloak running on KeycloakUrl
    - API Gateway running on GatewayUrl
    - CBC container built with FINERACT_BASE_URL pointing at host Fineract (see .env)
    - Fineract running (separate compose: Downloads\FYP\fineract)

.PARAMETER SkipApprove
  Do not call approve after create.

.PARAMETER FineractUrl
  Direct Fineract URL (bypassing CBC) used only for fallback id lookup.
  Default: https://localhost:8443/fineract-provider/api
  Set to empty string to disable direct-Fineract fallback.

.PARAMETER FineractUser / FineractPassword / FineractTenant
  Credentials for direct Fineract calls (not through CBC).
#>
param(
    [string]$GatewayUrl       = "http://localhost:8080",
    [string]$KeycloakUrl      = "http://localhost:8090",
    [string]$Realm            = "finnera",
    [string]$ClientIdWeb      = "finnera-web",
    [string]$AdminUser        = "admin",
    [string]$AdminPassword    = "Admin@2026!",
    [long]  $ClientId         = 0,
    [long]  $ProductId        = 0,
    [string]$FineractUrl      = "https://localhost:8443/fineract-provider/api",
    [string]$FineractUser     = "mifos",
    [string]$FineractPassword = "password",
    [string]$FineractTenant   = "default",
    [switch]$SkipApprove
)

$ErrorActionPreference = "Stop"

# Trust self-signed certs for direct Fineract calls
try {
    if (-not ([System.Management.Automation.PSTypeName]"TrustAll").Type) {
        Add-Type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAll : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int p) { return true; }
}
"@
    }
    [System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAll
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
} catch { }

# ----------------------------------------------------------
# Helpers
# ----------------------------------------------------------
function Get-AdminToken {
    $tokenUrl = "$KeycloakUrl/realms/$Realm/protocol/openid-connect/token"
    $body = @{
        grant_type = "password"
        client_id  = $ClientIdWeb
        username   = $AdminUser
        password   = $AdminPassword
    }
    $resp = Invoke-RestMethod -Method Post -Uri $tokenUrl -Body $body -ContentType "application/x-www-form-urlencoded"
    if (-not $resp.access_token) { throw "No access_token from Keycloak" }
    return $resp.access_token
}

function Invoke-GatewayJson {
    param([string]$Method, [string]$Path, [string]$Token, [object]$Body = $null)
    $uri = "$GatewayUrl$Path"
    $headers = @{ Authorization = "Bearer $Token"; Accept = "application/json" }
    if ($null -ne $Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers `
            -Body ($Body | ConvertTo-Json -Depth 8) -ContentType "application/json"
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

function Invoke-FineractDirect {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    if (-not $FineractUrl) { return $null }
    $token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${FineractUser}:${FineractPassword}"))
    $hdrs = @{
        Authorization                = "Basic $token"
        "Fineract-Platform-TenantId" = $FineractTenant
        Accept                       = "application/json"
    }
    $uri = "$FineractUrl$Path"
    if ($Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $hdrs `
            -Body ($Body | ConvertTo-Json -Depth 8) -ContentType "application/json"
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $hdrs
}

function Get-FineractDisplayDate {
    (Get-Date).ToString("dd MMMM yyyy", [System.Globalization.CultureInfo]::InvariantCulture)
}

# ----------------------------------------------------------
# Main
# ----------------------------------------------------------
Write-Host "=== Loan application review E2E ===" -ForegroundColor Cyan
Write-Host "Gateway: $GatewayUrl"

$token = Get-AdminToken
Write-Host "[1] Admin token acquired."

# ------ Resolve ClientId -------------------------------------------------------
if ($ClientId -le 0) {
    $clientsRes = Invoke-GatewayJson -Method Get -Path "/api/v1/clients" -Token $token
    $list = @()
    if ($clientsRes.success -ne $false) {
        $list = @($clientsRes.data)
    }
    if ($list.Count -gt 0) {
        $ClientId = [long]$list[0].id
        Write-Host "[2] ClientId resolved from CBC: $ClientId  ($($list[0].displayName))"
    } else {
        # Fallback: query Fineract directly
        Write-Host "[2] CBC returned empty client list. Trying Fineract directly..."
        try {
            $fRes = Invoke-FineractDirect Get "/v1/clients"
            $fList = @()
            if ($fRes.pageItems) { $fList = @($fRes.pageItems) }
            elseif ($fRes -is [array]) { $fList = $fRes }
            if ($fList.Count -gt 0) {
                $ClientId = [long]$fList[0].id
                Write-Host "    Fineract direct ClientId=$ClientId ($($fList[0].displayName))" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "    Fineract direct query failed: $($_.Exception.Message)" -ForegroundColor Yellow
        }
        if ($ClientId -le 0) {
            Write-Warning "No clients in Fineract. Run: powershell -ExecutionPolicy Bypass -File .\scripts\fineract-seed.ps1"
            Write-Warning "Then rerun with -ClientId <id> -ProductId <id>"
            throw "Cannot continue without a valid clientId."
        }
    }
} else {
    Write-Host "[2] Using supplied clientId=$ClientId"
}

# ------ Resolve ProductId -------------------------------------------------------
if ($ProductId -le 0) {
    $prodRes = Invoke-GatewayJson -Method Get -Path "/api/v1/loans/products" -Token $token
    $arr = @()
    if ($prodRes.success -ne $false) { $arr = @($prodRes.data) }
    if ($arr.Count -gt 0) {
        $firstP = $arr[0]
        if ($firstP.id) { $ProductId = [long]$firstP.id }
        elseif ($firstP.PSObject.Properties.Name -contains 'productId') { $ProductId = [long]$firstP.productId }
        Write-Host "[3] ProductId resolved from CBC: $ProductId  ($($firstP.name))"
    } else {
        # Fallback: query Fineract directly
        Write-Host "[3] CBC returned empty product list. Trying Fineract directly..."
        try {
            $fProd = Invoke-FineractDirect Get "/v1/loanproducts"
            $fPArr = @()
            if ($fProd -is [array]) { $fPArr = $fProd }
            elseif ($fProd.pageItems) { $fPArr = @($fProd.pageItems) }
            if ($fPArr.Count -gt 0) {
                $ProductId = [long]$fPArr[0].id
                Write-Host "    Fineract direct ProductId=$ProductId ($($fPArr[0].name))" -ForegroundColor Yellow
            }
        } catch {
            Write-Host "    Fineract direct query failed: $($_.Exception.Message)" -ForegroundColor Yellow
        }
        if ($ProductId -le 0) {
            Write-Warning "No loan products in Fineract. Run: powershell -ExecutionPolicy Bypass -File .\scripts\fineract-seed.ps1"
            throw "Cannot continue without a valid productId."
        }
    }
} else {
    Write-Host "[3] Using supplied productId=$ProductId"
}

# ------ Create Loan ------------------------------------------------------------
$submitted = Get-FineractDisplayDate
$expected  = (Get-Date).AddDays(14).ToString("dd MMMM yyyy", [System.Globalization.CultureInfo]::InvariantCulture)
$externalId = "E2E-LOAN-" + [Guid]::NewGuid().ToString("N").Substring(0, 10).ToUpperInvariant()

$createBody = @{
    clientId                      = $ClientId
    productId                     = $ProductId
    loanType                      = "individual"
    principal                     = 25000
    loanTermFrequency             = 12
    loanTermFrequencyType         = 2
    numberOfRepayments            = 12
    repaymentEvery                = 1
    repaymentFrequencyType        = 2
    interestRatePerPeriod         = 15
    interestType                  = 0
    interestCalculationPeriodType = 1
    amortizationType              = 1
    transactionProcessingStrategyCode = "mifos-standard-strategy"
    submittedOnDate               = $submitted
    expectedDisbursementDate      = $expected
    externalId                    = $externalId
    locale                        = "en"
    dateFormat                    = "dd MMMM yyyy"
}

Write-Host "[4] Creating loan application (externalId=$externalId)..."
try {
    $createRes = Invoke-GatewayJson -Method Post -Path "/api/v1/loans" -Token $token -Body $createBody
} catch {
    $msg    = $_.Exception.Message
    $detail = $_.ErrorDetails.Message
    if (-not $detail -and $_.Exception.Response) {
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            if ($stream) { $detail = (New-Object System.IO.StreamReader($stream)).ReadToEnd() }
        } catch { }
    }
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }

    if ($code -eq 400 -or $msg -match '\(400\)' -or $detail -match 'validation|Client|product|does not exist|clientId|productId') {
        throw @"
POST /loans failed with 400 - Fineract validation error.
ClientId=$ClientId and/or ProductId=$ProductId may not exist in the Fineract tenant.

Fix: run the seed script to create them:
  powershell -ExecutionPolicy Bypass -File .\scripts\fineract-seed.ps1

Then rerun with the printed IDs:
  powershell -ExecutionPolicy Bypass -File .\scripts\e2e\test-loan-review-e2e.ps1 -ClientId <id> -ProductId <id>

Fineract error detail:
$detail
"@
    }
    if ($code -eq 503 -or $msg -match '503') {
        throw @"
Gateway or core-banking-connector returned 503.

CBC inside Docker cannot reach localhost. Set FINERACT_BASE_URL in .env:
  FINERACT_BASE_URL=https://host.docker.internal:8443/fineract-provider/api

Then rebuild and restart CBC:
  docker compose build core-banking-connector
  docker compose up -d core-banking-connector api-gateway
"@
    }
    throw
}
if (-not $createRes.success) { throw "POST /loans failed: $($createRes | ConvertTo-Json -Depth 6)" }

$loanPayload  = $createRes.data
$loanNumericId = $loanPayload.id
if (-not $loanNumericId -and $loanPayload.loanId)     { $loanNumericId = $loanPayload.loanId }
if (-not $loanNumericId -and $loanPayload.resourceId) { $loanNumericId = $loanPayload.resourceId }
if (-not $loanNumericId) { throw "Created loan response missing id: $($createRes | ConvertTo-Json -Depth 6)" }
Write-Host "    Created loan id=$loanNumericId" -ForegroundColor Green

# ------ GET loan ---------------------------------------------------------------
Write-Host "[5] GET loan by id..."
$getRes = Invoke-GatewayJson -Method Get -Path "/api/v1/loans/$loanNumericId" -Token $token
if (-not $getRes.success) { throw "GET /loans/$loanNumericId failed: $($getRes | ConvertTo-Json -Compress)" }
Write-Host "    GET loan OK  status=$($getRes.data.status.value)" -ForegroundColor Green

# ------ Approve ----------------------------------------------------------------
if (-not $SkipApprove) {
    $approveDate = Get-FineractDisplayDate
    $note = "E2E auto-approve"
    $approveUri = "$GatewayUrl/api/v1/loans/$loanNumericId`?command=approve&date=$([uri]::EscapeDataString($approveDate))&note=$([uri]::EscapeDataString($note))"
    Write-Host "[6] Approving loan (command=approve)..."
    try {
        $hdrs = @{ Authorization = "Bearer $token"; Accept = "application/json" }
        $approveRes = Invoke-RestMethod -Method Post -Uri $approveUri -Headers $hdrs
        if ($approveRes.success -eq $false) {
            Write-Warning "Approve returned success=false: $($approveRes | ConvertTo-Json -Compress)"
        } else {
            Write-Host "    Approve OK" -ForegroundColor Green
        }
    } catch {
        Write-Warning "Approve step failed (workflow may require manual steps): $($_.Exception.Message)"
    }
} else {
    Write-Host "[6] Skipping approve (-SkipApprove)."
}

Write-Host ""
Write-Host "[PASS] Loan review pipeline OK (create + get)." -ForegroundColor Green
Write-Host "Done."
