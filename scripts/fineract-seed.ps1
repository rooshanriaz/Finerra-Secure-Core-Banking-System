<#
.SYNOPSIS
  Seeds the Fineract dev tenant with a loan product and a client so the loan E2E test can run.

.DESCRIPTION
  Hits Fineract's REST API directly (bypassing CBC) with Basic auth.
  Creates:
    1. A loan product (Personal Loan, monthly installments, 15% interest)
    2. A client (the E2E test borrower)

  Run this once after starting the Fineract dev stack.
  After seeding, use the printed ClientId / ProductId with:
    powershell -ExecutionPolicy Bypass -File .\scripts\e2e\test-loan-review-e2e.ps1 -ClientId <id> -ProductId <id>

.PARAMETER FineractUrl
  Base URL for Fineract (default: https://localhost:8443/fineract-provider/api).

.PARAMETER Username
  Fineract username (default: mifos).

.PARAMETER Password
  Fineract password (default: password).

.PARAMETER TenantId
  Fineract tenant identifier (default: default).
#>
param(
    [string]$FineractUrl = "https://localhost:8443/fineract-provider/api",
    [string]$Username = "mifos",
    [string]$Password = "password",
    [string]$TenantId = "default"
)

$ErrorActionPreference = "Stop"

# Ignore self-signed TLS cert from Fineract dev stack
if (-not ([System.Management.Automation.PSTypeName]"TrustAllCerts").Type) {
    Add-Type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCerts : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
"@
}
[System.Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCerts
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$basicToken = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))
$headers = @{
    Authorization          = "Basic $basicToken"
    "Fineract-Platform-TenantId" = $TenantId
    "Content-Type"         = "application/json"
    Accept                 = "application/json"
}

function Invoke-Fineract {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    $uri = "$FineractUrl$Path"
    if ($Body) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body ($Body | ConvertTo-Json -Depth 10)
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

function Get-EntityId {
    param([object]$entity)
    if (-not $entity) { return $null }
    if ($entity.PSObject.Properties.Name -contains "id" -and $entity.id) { return [long]$entity.id }
    if ($entity.PSObject.Properties.Name -contains "clientId" -and $entity.clientId) { return [long]$entity.clientId }
    if ($entity.PSObject.Properties.Name -contains "resourceId" -and $entity.resourceId) { return [long]$entity.resourceId }
    return $null
}

function Get-ClientRows {
    param([object]$clientsResponse)
    $rows = @()
    if ($clientsResponse -and $clientsResponse.pageItems) {
        $rows = @($clientsResponse.pageItems)
    } elseif ($clientsResponse -is [array]) {
        $rows = @($clientsResponse)
    } elseif ($clientsResponse) {
        $rows = @($clientsResponse)
    }
    # Keep only objects that look like real clients (have any known identifier)
    return @($rows | Where-Object { (Get-EntityId $_) })
}

Write-Host "=== Fineract Seed Script ===" -ForegroundColor Cyan
Write-Host "Fineract: $FineractUrl  Tenant: $TenantId"
Write-Host ""

# ----------------------------------------------------------
# 1. Check connectivity
# ----------------------------------------------------------
Write-Host "[1] Checking Fineract connectivity..." -NoNewline
$officeId = 1
try {
    $offices = Invoke-Fineract Get "/v1/offices"
    if ($offices -and $offices.pageItems -and @($offices.pageItems).Count -gt 0 -and $offices.pageItems[0].id) {
        $officeId = [int]$offices.pageItems[0].id
    } elseif ($offices -and @($offices).Count -gt 0 -and $offices[0].id) {
        $officeId = [int]$offices[0].id
    }
    Write-Host " OK" -ForegroundColor Green
} catch {
    Write-Host " FAILED" -ForegroundColor Red
    Write-Host "Cannot reach Fineract at $FineractUrl"
    Write-Host "Make sure the Fineract dev stack is running:"
    Write-Host "  cd Downloads\FYP\fineract"
    Write-Host "  docker compose -f docker-compose-development.yml up -d"
    Write-Host ""
    Write-Host "Error: $($_.Exception.Message)"
    exit 1
}

# ----------------------------------------------------------
# 2. Check / create loan product
# ----------------------------------------------------------
Write-Host "[2] Checking existing loan products..."
$today = Get-Date -Format "dd MMMM yyyy"
$existingProducts = Invoke-Fineract Get "/v1/loanproducts"
$productId = $null
$strategyCode = "mifos-standard-strategy"
try {
    $template = Invoke-Fineract Get "/v1/loanproducts/template"
    if ($template -and $template.transactionProcessingStrategyOptions -and @($template.transactionProcessingStrategyOptions).Count -gt 0) {
        $opt = @($template.transactionProcessingStrategyOptions)[0]
        if ($opt.code) { $strategyCode = [string]$opt.code }
    }
} catch {
    Write-Host "    Could not read loan product template; using default strategy code." -ForegroundColor Yellow
}

# Keep payload strict/minimal but valid for current Fineract validators.
$loanProduct = @{
    name                            = "Personal Loan"
    shortName                       = "PL"
    description                     = "Seed loan product for FYP demo"
    currencyCode                    = "PKR"
    digitsAfterDecimal              = 2
    inMultiplesOf                   = 0
    principal                       = 10000
    minPrincipal                    = 1000
    maxPrincipal                    = 1000000
    numberOfRepayments              = 12
    minNumberOfRepayments           = 1
    maxNumberOfRepayments           = 60
    repaymentEvery                  = 1
    repaymentFrequencyType          = 2
    interestRatePerPeriod           = 15
    interestRateFrequencyType       = 2
    interestType                    = 1
    interestCalculationPeriodType   = 1
    amortizationType                = 1
    transactionProcessingStrategyCode = $strategyCode
    accountingRule                  = 1
    daysInYearType                  = 1
    daysInMonthType                 = 1
    isInterestRecalculationEnabled  = $false
    locale                          = "en"
}

if ($existingProducts -and @($existingProducts).Count -gt 0) {
    $first = @($existingProducts)[0]
    $productId = $first.id
    $existingCurrency = if ($first.currency -and $first.currency.code) { [string]$first.currency.code } else { [string]$first.currencyCode }
    if ([string]::IsNullOrWhiteSpace($existingCurrency)) { $existingCurrency = "UNKNOWN" }
    Write-Host "    Found existing product: id=$productId  name='$($first.name)'  currency='$existingCurrency'" -ForegroundColor Yellow

    if ($existingCurrency -ne "PKR") {
        Write-Host "    Existing product currency is not PKR. Attempting in-place update..." -ForegroundColor Yellow
        try {
            $null = Invoke-Fineract Put "/v1/loanproducts/$productId" $loanProduct
            Write-Host "    Updated existing product id=$productId to PKR." -ForegroundColor Green
        } catch {
            Write-Host "    In-place update rejected by this Fineract build: $($_.Exception.Message)" -ForegroundColor Yellow
            Write-Host "    Creating PKR replacement product and using that id instead..." -ForegroundColor Yellow
            try {
                $replacement = $loanProduct.Clone()
                $replacement.name = "Personal Loan PKR"
                $replacement.shortName = "PLP"
                $created = Invoke-Fineract Post "/v1/loanproducts" $replacement
                $productId = Get-EntityId $created
                Write-Host "    Created replacement PKR product: id=$productId" -ForegroundColor Green
            } catch {
                Write-Host "    ERROR creating PKR replacement product: $($_.Exception.Message)" -ForegroundColor Red
                if ($_.ErrorDetails.Message) {
                    Write-Host "    Response: $($_.ErrorDetails.Message)" -ForegroundColor Yellow
                }
                exit 1
            }
        }
    }
} else {
    Write-Host "    No products found. Creating loan product..."
    try {
        $created = Invoke-Fineract Post "/v1/loanproducts" $loanProduct
        $productId = $created.resourceId
        Write-Host "    Created loan product: id=$productId  name='Personal Loan'" -ForegroundColor Green
    } catch {
        Write-Host "    ERROR creating loan product: $($_.Exception.Message)" -ForegroundColor Red
        if ($_.ErrorDetails.Message) {
            Write-Host "    Response: $($_.ErrorDetails.Message)" -ForegroundColor Yellow
        }
        Write-Host "    Hint: check required fields for your Fineract version via /v1/loanproducts/template" -ForegroundColor Yellow
        exit 1
    }
}

# ----------------------------------------------------------
# 3. Check / create client
# ----------------------------------------------------------
Write-Host "[3] Checking existing clients..."
$existingClients = Invoke-Fineract Get "/v1/clients"
$clientId = $null
$clientRows = Get-ClientRows $existingClients
if ($clientRows.Count -gt 0) {
    $first = $clientRows[0]
    $clientId = Get-EntityId $first
    $displayName = if ($first.displayName) { $first.displayName } elseif ($first.firstname -or $first.lastname) { "$($first.firstname) $($first.lastname)".Trim() } else { "(unnamed)" }
    Write-Host "    Found existing client: id=$clientId  name='$displayName'" -ForegroundColor Yellow
}

if (-not $clientId) {
    Write-Host "    No clients found. Creating client..."
    $activationDate = Get-Date -Format "dd MMMM yyyy"
    $seedMobile = "+923001234567"
    $clientPayload = @{
        officeId        = $officeId
        firstname       = "Ali"
        lastname        = "Khan"
        legalFormId     = 1
        active          = $true
        activationDate  = $activationDate
        dateFormat      = "dd MMMM yyyy"
        locale          = "en"
        submittedOnDate = $activationDate
        mobileNo        = $seedMobile
    }
    try {
        $created = Invoke-Fineract Post "/v1/clients" $clientPayload
        $clientId = Get-EntityId $created
        Write-Host "    Created client: id=$clientId  name='Ali Khan'" -ForegroundColor Green
    } catch {
        $raw = $_.ErrorDetails.Message
        if ($raw -match "duplicate\.mobileNo|already exists") {
            Write-Host "    Mobile number already exists. Resolving existing client id..." -ForegroundColor Yellow
            try {
                $q = [uri]::EscapeDataString("mobileNo like '$seedMobile'")
                $search = Invoke-Fineract Get "/v1/clients?sqlSearch=$q"
                $rows = Get-ClientRows $search
                if ($rows.Count -gt 0) {
                    $existing = $rows[0]
                    $clientId = Get-EntityId $existing
                    $displayName = if ($existing.displayName) { $existing.displayName } else { "(existing client)" }
                    Write-Host "    Reusing existing client: id=$clientId  name='$displayName'" -ForegroundColor Green
                }
            } catch {
                Write-Host "    Could not resolve duplicate client automatically." -ForegroundColor Yellow
            }

            if (-not $clientId) {
                Write-Host "    Creating client with a unique mobile number..." -ForegroundColor Yellow
                $clientPayload.mobileNo = "+92" + (Get-Random -Minimum 3000000000 -Maximum 3999999999)
                try {
                    $created = Invoke-Fineract Post "/v1/clients" $clientPayload
                    $clientId = Get-EntityId $created
                    Write-Host "    Created client with new mobile: id=$clientId" -ForegroundColor Green
                } catch {
                    Write-Host "    ERROR creating client after duplicate handling: $($_.Exception.Message)" -ForegroundColor Red
                    Write-Host "    Response: $($_.ErrorDetails.Message)"
                    exit 1
                }
            }
        } else {
            Write-Host "    ERROR creating client: $($_.Exception.Message)" -ForegroundColor Red
            Write-Host "    Response: $raw"
            exit 1
        }
    }
}

# ----------------------------------------------------------
# 4. Summary
# ----------------------------------------------------------
Write-Host ""
Write-Host "=== Seed Complete ===" -ForegroundColor Green
Write-Host "Use these IDs for the E2E test:"
Write-Host ""
Write-Host "  ClientId  : $clientId" -ForegroundColor Cyan
Write-Host "  ProductId : $productId" -ForegroundColor Cyan
Write-Host ""
Write-Host "Run the E2E:" -ForegroundColor White
Write-Host "  powershell -ExecutionPolicy Bypass -File .\scripts\e2e\test-loan-review-e2e.ps1 -ClientId $clientId -ProductId $productId"
Write-Host ""
Write-Host "Or rebuild CBC first (picks up new FINERACT_BASE_URL from .env):"
Write-Host "  docker compose build core-banking-connector"
Write-Host "  docker compose up -d core-banking-connector api-gateway"
