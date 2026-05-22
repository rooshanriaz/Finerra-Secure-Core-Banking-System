param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$ManifestPath = ".\scripts\seed-synthetic-risk-data-output.json",
    [string]$KeycloakBaseUrl = "http://localhost:8090",
    [string]$Realm = "finnera",
    [string]$ClientId = "finnera-web",
    [string]$Username = "admin",
    [string]$Password = "Admin@2026!",
    [string]$DatasetOutputPath = ".\scripts\risk-scoring-dataset.json",
    [string]$RecommendationOutputPath = ".\scripts\loan-disbursal-recommendations.json"
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$msg) { Write-Host "[*] $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg) { Write-Host "[+] $msg" -ForegroundColor Green }
function Write-WarnMsg([string]$msg) { Write-Host "[!] $msg" -ForegroundColor Yellow }

function Get-FineractDate([datetime]$d) {
    return $d.ToString("dd MMMM yyyy", [System.Globalization.CultureInfo]::GetCultureInfo("en-US"))
}

function Get-AccessTokenFromKeycloak {
    param([string]$BaseUrl,[string]$RealmName,[string]$OidcClientId,[string]$User,[string]$Pass)
    $tokenUrl = "$BaseUrl/realms/$RealmName/protocol/openid-connect/token"
    $body = "grant_type=password&client_id=$([uri]::EscapeDataString($OidcClientId))&username=$([uri]::EscapeDataString($User))&password=$([uri]::EscapeDataString($Pass))&scope=openid"
    $headers = @{ "Content-Type" = "application/x-www-form-urlencoded" }
    $resp = Invoke-RestMethod -Method Post -Uri $tokenUrl -Headers $headers -Body $body
    if (-not $resp.access_token) { throw "Keycloak did not return access_token." }
    return [string]$resp.access_token
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [int]$Retries = 1
    )
    $uri = "$($GatewayUrl.TrimEnd('/'))$Path"
    for ($attempt = 0; $attempt -le $Retries; $attempt++) {
        try {
            $headers = @{
                "Authorization" = "Bearer $script:ResolvedAccessToken"
                "Content-Type" = "application/json"
                "Accept" = "application/json"
            }
            if ($null -ne $Body) {
                $json = $Body | ConvertTo-Json -Depth 20
                return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $json
            }
            return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
        } catch {
            # Token might expire during long loops; refresh once and retry.
            $msg = $_.Exception.Message
            if ($msg -match "\(401\)" -and $attempt -lt $Retries) {
                Write-WarnMsg "Received 401, refreshing token and retrying..."
                $script:ResolvedAccessToken = Get-AccessTokenFromKeycloak -BaseUrl $KeycloakBaseUrl -RealmName $Realm -OidcClientId $ClientId -User $Username -Pass $Password
                continue
            }
            if ($attempt -eq $Retries) { throw }
            Start-Sleep -Milliseconds (500 * ($attempt + 1))
        }
    }
}

function Invoke-ApiWithFallback {
    param(
        [Parameter(Mandatory = $true)][string]$PrimaryPath,
        [Parameter(Mandatory = $true)][string]$FallbackPath,
        [Parameter(Mandatory = $true)][object]$Body
    )
    try {
        $null = Invoke-Api -Method Post -Path $PrimaryPath -Body $Body -Retries 1
        return "PRIMARY"
    } catch {
        $null = Invoke-Api -Method Post -Path $FallbackPath -Body $Body -Retries 1
        return "FALLBACK"
    }
}

Write-Host ""
Write-Host "=== Backfill Succeeded Synthetic Customers ===" -ForegroundColor Magenta
Write-Step "Loading manifest: $ManifestPath"
if (-not (Test-Path $ManifestPath)) { throw "Manifest not found: $ManifestPath" }
$manifest = Get-Content -Path $ManifestPath -Raw | ConvertFrom-Json
$records = @($manifest.records)
if ($records.Count -eq 0) { throw "No records in manifest." }
$eligible = @($records | Where-Object { $_.fineractClientId -and $_.loanId -and $_.savingsAccountId })
if ($eligible.Count -eq 0) { throw "No eligible succeeded records found." }

Write-Step "Acquiring token..."
$script:ResolvedAccessToken = Get-AccessTokenFromKeycloak -BaseUrl $KeycloakBaseUrl -RealmName $Realm -OidcClientId $ClientId -User $Username -Pass $Password
Write-Ok "Token acquired."
Write-Step "Eligible customers for backfill: $($eligible.Count)"

$dataset = [System.Collections.Generic.List[object]]::new()
$recommendations = [System.Collections.Generic.List[object]]::new()

for ($i = 0; $i -lt $eligible.Count; $i++) {
    $rec = $eligible[$i]
    $profile = [string]$rec.riskProfile
    $loanId = [long]$rec.loanId
    $savingsAccountId = [long]$rec.savingsAccountId
    Write-Step ("[{0}/{1}] Backfilling {2} (clientId={3})" -f ($i + 1), $eligible.Count, $rec.syntheticUserRef, $rec.fineractClientId)

    $extraSavingsTarget = switch ($profile) {
        "LOW_RISK" { 14 }
        "MEDIUM_RISK" { 12 }
        default { 10 }
    }
    $extraRepaymentTarget = switch ($profile) {
        "LOW_RISK" { 6 }
        "MEDIUM_RISK" { 5 }
        default { 4 }
    }

    $extraSavingsOk = 0
    $extraSavingsFailed = 0
    $extraSavingsFallback = 0
    $extraRepaymentOk = 0
    $extraRepaymentFailed = 0
    $extraRepaymentFallback = 0

    # Savings random transactions
    for ($s = 1; $s -le $extraSavingsTarget; $s++) {
        $isDeposit = switch ($profile) {
            "LOW_RISK" { (Get-Random -Minimum 1 -Maximum 100) -le 70 }
            "MEDIUM_RISK" { (Get-Random -Minimum 1 -Maximum 100) -le 58 }
            default { (Get-Random -Minimum 1 -Maximum 100) -le 48 }
        }
        $amount = if ($isDeposit) {
            switch ($profile) {
                "LOW_RISK" { Get-Random -Minimum 5000 -Maximum 30000 }
                "MEDIUM_RISK" { Get-Random -Minimum 3500 -Maximum 22000 }
                default { Get-Random -Minimum 2500 -Maximum 17000 }
            }
        } else {
            switch ($profile) {
                "LOW_RISK" { Get-Random -Minimum 2500 -Maximum 18000 }
                "MEDIUM_RISK" { Get-Random -Minimum 3000 -Maximum 21000 }
                default { Get-Random -Minimum 3500 -Maximum 24000 }
            }
        }
        $txDate = (Get-Date).AddDays(-1 * (Get-Random -Minimum 0 -Maximum 45))
        $payload = @{
            transactionAmount = [double]$amount
            transactionDate = Get-FineractDate $txDate
            paymentTypeId = 1
            note = if ($isDeposit) { "Backfill savings credit #$s profile=$profile" } else { "Backfill savings debit #$s profile=$profile" }
            locale = "en"
            dateFormat = "dd MMMM yyyy"
        }
        $primary = if ($isDeposit) {
            "/api/v1/transactions/savings/$savingsAccountId/deposit"
        } else {
            "/api/v1/transactions/savings/$savingsAccountId/withdrawal"
        }
        $fallback = if ($isDeposit) {
            "/api/v1/savingsaccounts/$savingsAccountId/transactions?command=deposit"
        } else {
            "/api/v1/savingsaccounts/$savingsAccountId/transactions?command=withdrawal"
        }
        try {
            $mode = Invoke-ApiWithFallback -PrimaryPath $primary -FallbackPath $fallback -Body $payload
            $extraSavingsOk++
            if ($mode -eq "FALLBACK") { $extraSavingsFallback++ }
        } catch {
            $extraSavingsFailed++
        }
    }

    # Extra loan repayments
    for ($r = 1; $r -le $extraRepaymentTarget; $r++) {
        $amount = switch ($profile) {
            "LOW_RISK" { Get-Random -Minimum 7000 -Maximum 17000 }
            "MEDIUM_RISK" { Get-Random -Minimum 4500 -Maximum 14000 }
            default { Get-Random -Minimum 2500 -Maximum 12000 }
        }
        $txDate = (Get-Date).AddDays(-1 * (Get-Random -Minimum 0 -Maximum 30))
        $payload = @{
            transactionAmount = [double]$amount
            transactionDate = Get-FineractDate $txDate
            paymentTypeId = 1
            note = "Backfill repayment #$r profile=$profile"
            locale = "en"
            dateFormat = "dd MMMM yyyy"
        }
        try {
            $mode = Invoke-ApiWithFallback `
                -PrimaryPath "/api/v1/transactions/loans/$loanId/repayment" `
                -FallbackPath "/api/v1/loans/$loanId/transactions?command=repayment" `
                -Body $payload
            $extraRepaymentOk++
            if ($mode -eq "FALLBACK") { $extraRepaymentFallback++ }
        } catch {
            $extraRepaymentFailed++
        }
    }

    # Risk feature engineering + recommendation
    $historicalRepayTarget = [int]$rec.repaymentsTarget
    $historicalRepayFail = [int]$rec.repaymentsFailed
    $totalRepayTarget = $historicalRepayTarget + $extraRepaymentTarget
    $totalRepaySuccess = ($historicalRepayTarget - $historicalRepayFail) + $extraRepaymentOk
    $repaySuccessRate = if ($totalRepayTarget -gt 0) { [math]::Round(($totalRepaySuccess / $totalRepayTarget) * 100, 2) } else { 0 }

    $historicalSavingsSuccess = [int]$rec.savingsTxSucceeded
    $historicalSavingsFailed = [int]$rec.savingsTxFailed
    $totalSavings = $historicalSavingsSuccess + $historicalSavingsFailed + $extraSavingsTarget
    $totalSavingsSuccess = $historicalSavingsSuccess + $extraSavingsOk
    $savingsSuccessRate = if ($totalSavings -gt 0) { [math]::Round(($totalSavingsSuccess / $totalSavings) * 100, 2) } else { 0 }

    $balance = [double]$rec.estimatedSavingsBalance
    $balanceScore = if ($balance -ge 200000) { 100 } elseif ($balance -ge 100000) { 80 } elseif ($balance -ge 50000) { 60 } else { 40 }
    $profileAdjustment = switch ($profile) {
        "LOW_RISK" { 8 }
        "MEDIUM_RISK" { 0 }
        default { -10 }
    }
    $riskScore = [math]::Round(([double]$repaySuccessRate * 0.45) + ([double]$savingsSuccessRate * 0.30) + ([double]$balanceScore * 0.25) + $profileAdjustment, 2)
    if ($riskScore -gt 100) { $riskScore = 100 }
    if ($riskScore -lt 0) { $riskScore = 0 }

    $recommendation = if ($riskScore -ge 75) {
        "APPROVE_DISBURSAL"
    } elseif ($riskScore -ge 55) {
        "MANUAL_REVIEW"
    } else {
        "REJECT_OR_RESTRUCTURE"
    }

    $dataset.Add([pscustomobject]@{
        syntheticUserRef = $rec.syntheticUserRef
        fineractClientId = $rec.fineractClientId
        riskProfile = $profile
        savingsAccountId = $savingsAccountId
        loanId = $loanId
        initialEstimatedSavingsBalance = $balance
        historicalSavingsTxSucceeded = $historicalSavingsSuccess
        historicalSavingsTxFailed = $historicalSavingsFailed
        backfillSavingsTxSucceeded = $extraSavingsOk
        backfillSavingsTxFailed = $extraSavingsFailed
        backfillSavingsFallbackUsed = $extraSavingsFallback
        historicalRepaymentsTarget = $historicalRepayTarget
        historicalRepaymentsFailed = $historicalRepayFail
        backfillRepaymentsTarget = $extraRepaymentTarget
        backfillRepaymentsSucceeded = $extraRepaymentOk
        backfillRepaymentsFailed = $extraRepaymentFailed
        backfillRepaymentsFallbackUsed = $extraRepaymentFallback
        repaymentSuccessRate = $repaySuccessRate
        savingsSuccessRate = $savingsSuccessRate
        balanceScore = $balanceScore
        riskScore = $riskScore
        recommendation = $recommendation
    })

    $recommendations.Add([pscustomobject]@{
        syntheticUserRef = $rec.syntheticUserRef
        fineractClientId = $rec.fineractClientId
        loanId = $loanId
        riskScore = $riskScore
        repaymentSuccessRate = $repaySuccessRate
        savingsSuccessRate = $savingsSuccessRate
        recommendation = $recommendation
        rationale = "Computed from repayment behavior, savings transaction reliability, and risk profile."
    })
}

$datasetSummary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("o")
    sourceManifest = $ManifestPath
    customerCount = $dataset.Count
    records = $dataset
}
$recommendationSummary = [pscustomobject]@{
    generatedAt = (Get-Date).ToString("o")
    customerCount = $recommendations.Count
    recommendations = $recommendations
}

$datasetSummary | ConvertTo-Json -Depth 20 | Set-Content -Path $DatasetOutputPath -Encoding UTF8
$recommendationSummary | ConvertTo-Json -Depth 20 | Set-Content -Path $RecommendationOutputPath -Encoding UTF8

Write-Ok "Risk dataset written: $DatasetOutputPath"
Write-Ok "Recommendations written: $RecommendationOutputPath"
Write-Host ""
Write-Host "=== Backfill Complete ===" -ForegroundColor Magenta
Write-Host "Customers processed : $($dataset.Count)"
Write-Host "Manifest source     : $ManifestPath"
Write-Host ""
