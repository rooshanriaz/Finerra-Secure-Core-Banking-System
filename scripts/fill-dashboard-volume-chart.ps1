param(
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$ManifestPath = ".\scripts\seed-synthetic-risk-data-output.json",
    [string]$KeycloakBaseUrl = "http://localhost:8090",
    [string]$Realm = "finnera",
    [string]$ClientId = "finnera-web",
    [string]$Username = "admin",
    [string]$Password = "Admin@2026!"
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
Write-Host "=== Fill dashboard volume (deposits / withdrawals / loan repayments) ===" -ForegroundColor Magenta
Write-Step "Manifest: $ManifestPath"
if (-not (Test-Path $ManifestPath)) { throw "Manifest not found: $ManifestPath" }
$manifest = Get-Content -Path $ManifestPath -Raw | ConvertFrom-Json
$records = @($manifest.records | Where-Object { $_.savingsAccountId -and $_.loanId })
if ($records.Count -eq 0) { throw "No records with savingsAccountId and loanId." }

Write-Step "Acquiring token..."
$script:ResolvedAccessToken = Get-AccessTokenFromKeycloak -BaseUrl $KeycloakBaseUrl -RealmName $Realm -OidcClientId $ClientId -User $Username -Pass $Password
Write-Ok "Token acquired. Customers: $($records.Count)"

$rounds = @(
    @{ kind = "deposit";    dayBack = 6; note = "Volume sample: deposit (week spread)" },
    @{ kind = "withdrawal"; dayBack = 5; note = "Volume sample: withdrawal" },
    @{ kind = "repayment";  dayBack = 4; note = "Volume sample: loan repayment (chart transfers)" },
    @{ kind = "deposit";    dayBack = 3; note = "Volume sample: deposit" },
    @{ kind = "withdrawal"; dayBack = 2; note = "Volume sample: withdrawal" },
    @{ kind = "repayment";  dayBack = 1; note = "Volume sample: loan repayment" }
)

$ok = 0
$fail = 0

foreach ($rec in $records) {
    $sid = [long]$rec.savingsAccountId
    $lid = [long]$rec.loanId
    $ref = [string]$rec.syntheticUserRef
    Write-Step "Customer $ref (savings=$sid loan=$lid)"

    foreach ($r in $rounds) {
        $txDate = (Get-Date).Date.AddDays(-[int]$r.dayBack)
        $payload = @{
            transactionAmount = [double](Get-Random -Minimum 3500 -Maximum 14000)
            transactionDate     = Get-FineractDate $txDate
            paymentTypeId       = 1
            note                = "$($r.note) ref=$ref"
            locale              = "en"
            dateFormat          = "dd MMMM yyyy"
        }
        try {
            switch ($r.kind) {
                "deposit" {
                    $mode = Invoke-ApiWithFallback `
                        -PrimaryPath "/api/v1/transactions/savings/$sid/deposit" `
                        -FallbackPath "/api/v1/savingsaccounts/$sid/transactions?command=deposit" `
                        -Body $payload
                }
                "withdrawal" {
                    $mode = Invoke-ApiWithFallback `
                        -PrimaryPath "/api/v1/transactions/savings/$sid/withdrawal" `
                        -FallbackPath "/api/v1/savingsaccounts/$sid/transactions?command=withdrawal" `
                        -Body $payload
                }
                "repayment" {
                    $mode = Invoke-ApiWithFallback `
                        -PrimaryPath "/api/v1/transactions/loans/$lid/repayment" `
                        -FallbackPath "/api/v1/loans/$lid/transactions?command=repayment" `
                        -Body $payload
                }
            }
            $ok++
            Write-Host "    $($r.kind) dayBack=$($r.dayBack) -> OK ($mode)" -ForegroundColor DarkGray
        } catch {
            $fail++
            Write-WarnMsg "    $($r.kind) failed: $($_.Exception.Message)"
        }
    }
}

Write-Host ""
Write-Ok "Completed. Succeeded: $ok  Failed: $fail"
Write-Host "Refresh the Super Admin dashboard; volume chart uses transactionType (WITHDRAWAL / LOAN_REPAYMENT counts as Transfers bar)." -ForegroundColor Gray
Write-Host ""
