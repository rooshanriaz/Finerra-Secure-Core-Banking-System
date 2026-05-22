<#
.SYNOPSIS
  Seeds synthetic customer + loan repayment history for risk-scoring experiments.

.DESCRIPTION
  This script is designed for demo/training environments where real customer volume is limited.
  It creates synthetic customers through the KYC onboarding API, then creates/disburses loans and
  generates repayment transactions to build transaction history usable by fraud/risk models.

  Security posture:
  - Customer PII is synthetic (fake) only.
  - KYC onboarding path is used so PII is persisted using the service's encryption workflow.
  - DID/credential issuance and audit anchoring are triggered by existing service logic where enabled.

  Data flow used:
  1) POST /api/v1/kyc/onboard
  2) POST /api/v1/loans
  3) POST /api/v1/loans/{id}?command=approve
  4) POST /api/v1/loans/{id}?command=disburse
  5) POST /api/v1/transactions/loans/{id}/repayment  (multiple times)

.PARAMETER Count
  Number of synthetic customers to create (recommended 50-60).

.PARAMETER GatewayUrl
  API Gateway base URL.

.PARAMETER AccessToken
  Bearer token with sufficient roles (ADMIN or LOAN_OFFICER + MANAGER compatible privileges).
  If omitted, the script attempts Keycloak password grant using Username/Password.

.PARAMETER KeycloakBaseUrl
  Keycloak base URL used when AccessToken is not provided.

.PARAMETER Realm
  Keycloak realm name.

.PARAMETER ClientId
  OIDC client id for token grant.

.PARAMETER Username
  Username for token grant fallback.

.PARAMETER Password
  Password for token grant fallback.

.PARAMETER OutputPath
  Path to write a run manifest (contains IDs and risk profile labels; no raw PII dump).

.PARAMETER DryRun
  If supplied, prints intended actions without calling APIs.

.PARAMETER AllowCbcFallback
  If KYC onboarding is unavailable, fall back to CBC client creation (/api/v1/clients).
  Note: fallback path does not use KYC encrypted-PII persistence or DID issuance.
#>
param(
    [ValidateRange(1, 200)]
    [int]$Count = 55,
    [string]$GatewayUrl = "http://localhost:8080",
    [string]$AccessToken = "",
    [string]$KeycloakBaseUrl = "http://localhost:8090",
    [string]$Realm = "finnera",
    [string]$ClientId = "finnera-web",
    [string]$Username = "admin",
    [string]$Password = "admin",
    [string]$OutputPath = ".\scripts\seed-synthetic-risk-data-output.json",
    [long]$SavingsProductId = 0,
    [switch]$DryRun,
    [switch]$AllowCbcFallback
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$msg) {
    Write-Host "[*] $msg" -ForegroundColor Cyan
}

function Write-Ok([string]$msg) {
    Write-Host "[+] $msg" -ForegroundColor Green
}

function Write-WarnMsg([string]$msg) {
    Write-Host "[!] $msg" -ForegroundColor Yellow
}

function Write-ErrMsg([string]$msg) {
    Write-Host "[-] $msg" -ForegroundColor Red
}

function Get-FineractDate([datetime]$d) {
    return $d.ToString("dd MMMM yyyy", [System.Globalization.CultureInfo]::GetCultureInfo("en-US"))
}

function New-StrongPassword {
    $rand = [System.Guid]::NewGuid().ToString("N").Substring(0, 10)
    return "Fyp!A1$rand"
}

function New-Cnic {
    # Pakistan CNIC-like synthetic format: XXXXX-XXXXXXX-X
    $a = Get-Random -Minimum 10000 -Maximum 99999
    $b = Get-Random -Minimum 1000000 -Maximum 9999999
    $c = Get-Random -Minimum 0 -Maximum 9
    return "{0}-{1}-{2}" -f $a, $b, $c
}

function Get-RandomItem([object[]]$items) {
    return $items[(Get-Random -Minimum 0 -Maximum $items.Count)]
}

function Get-EntityIdFromObject([object]$obj) {
    if (-not $obj) { return $null }
    if ($obj.PSObject.Properties.Name -contains "id" -and $obj.id) { return [long]$obj.id }
    if ($obj.PSObject.Properties.Name -contains "savingsId" -and $obj.savingsId) { return [long]$obj.savingsId }
    if ($obj.PSObject.Properties.Name -contains "accountId" -and $obj.accountId) { return [long]$obj.accountId }
    if ($obj.PSObject.Properties.Name -contains "loanId" -and $obj.loanId) { return [long]$obj.loanId }
    if ($obj.PSObject.Properties.Name -contains "resourceId" -and $obj.resourceId) { return [long]$obj.resourceId }
    return $null
}

function Get-ExceptionDetail([object]$err) {
    try {
        if ($err -and $err.Exception -and $err.Exception.Response) {
            $resp = $err.Exception.Response
            $statusCode = [int]$resp.StatusCode
            $stream = $resp.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $body = $reader.ReadToEnd()
                if ($body) {
                    return "HTTP $statusCode | $body"
                }
            }
            return "HTTP $statusCode"
        }
    } catch {
        # ignore best-effort detail extraction
    }
    return ($err.Exception.Message)
}

function Test-SavingsProductMissingError([object]$err) {
    if (-not $err -or -not $err.Exception) { return $false }
    $msg = [string]$err.Exception.Message
    return ($msg -match "Saving product with identifier .* does not exist")
}

function Get-DotEnvValue([string]$Key, [string]$DefaultValue = "") {
    $envPath = ".\.env"
    if (-not (Test-Path $envPath)) { return $DefaultValue }
    $lines = Get-Content -Path $envPath -ErrorAction SilentlyContinue
    foreach ($line in $lines) {
        if (-not $line -or $line.Trim().StartsWith("#")) { continue }
        $parts = $line.Split("=", 2)
        if ($parts.Count -lt 2) { continue }
        if ($parts[0].Trim() -eq $Key) {
            return $parts[1].Trim()
        }
    }
    return $DefaultValue
}

function Invoke-FineractCurlRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Password,
        [Parameter(Mandatory = $true)][string]$TenantId,
        [object]$Body = $null
    )

    $args = @(
        "-k", "-sS",
        "-u", "$Username`:$Password",
        "-H", "Fineract-Platform-TenantId: $TenantId",
        "-H", "Accept: application/json",
        "-X", $Method
    )
    $tmpBodyPath = $null
    try {
        if ($null -ne $Body) {
            $bodyJson = $Body | ConvertTo-Json -Depth 20
            $tmpBodyPath = Join-Path $env:TEMP ("fineract-body-" + [guid]::NewGuid().ToString("N") + ".json")
            [System.IO.File]::WriteAllText($tmpBodyPath, $bodyJson, [System.Text.UTF8Encoding]::new($false))
            $args += @("-H", "Content-Type: application/json", "--data-binary", "@$tmpBodyPath")
        }
        $args += @("-w", "`nHTTPSTATUS:%{http_code}", $Url)

        $raw = & curl.exe @args
        if ($LASTEXITCODE -ne 0) {
            throw "curl request failed (exit $LASTEXITCODE) for $Method $Url :: $raw"
        }
    } finally {
        if ($tmpBodyPath -and (Test-Path $tmpBodyPath)) {
            Remove-Item -Path $tmpBodyPath -Force -ErrorAction SilentlyContinue
        }
    }
    $text = [string]$raw
    $marker = "HTTPSTATUS:"
    $idx = $text.LastIndexOf($marker)
    if ($idx -lt 0) {
        return @{
            statusCode = 0
            bodyText = $text
            bodyJson = $null
        }
    }
    $bodyText = $text.Substring(0, $idx).Trim()
    $statusText = $text.Substring($idx + $marker.Length).Trim()
    $statusCode = 0
    [void][int]::TryParse($statusText, [ref]$statusCode)
    $bodyJson = $null
    if ($bodyText) {
        try { $bodyJson = $bodyText | ConvertFrom-Json -Depth 30 } catch { }
    }
    return @{
        statusCode = $statusCode
        bodyText = $bodyText
        bodyJson = $bodyJson
    }
}

function Resolve-SavingsProductIdFromFineract([long]$PreferredId) {
    if ($PreferredId -gt 0) { return $PreferredId }
    $configuredBase = Get-DotEnvValue -Key "FINERACT_BASE_URL" -DefaultValue "https://localhost:8443/fineract-provider/api"
    $candidateBases = @(
        "https://localhost:8443/fineract-provider/api",
        $configuredBase,
        "https://host.docker.internal:8443/fineract-provider/api"
    ) | Select-Object -Unique
    $errors = [System.Collections.Generic.List[string]]::new()
    foreach ($fineractBaseUrl in $candidateBases) {
    try {
        $fineractUser = Get-DotEnvValue -Key "FINERACT_USERNAME" -DefaultValue "mifos"
        $fineractPass = Get-DotEnvValue -Key "FINERACT_PASSWORD" -DefaultValue "password"
        $fineractTenant = Get-DotEnvValue -Key "FINERACT_TENANT_ID" -DefaultValue "default"
        $skipSsl = (Get-DotEnvValue -Key "FINERACT_SKIP_SSL_VERIFICATION" -DefaultValue "true").ToLowerInvariant()

        $uri = "$($fineractBaseUrl.TrimEnd('/'))/v1/savingsproducts?limit=50"
        $respWrap = Invoke-FineractCurlRequest -Method "GET" -Url $uri -Username $fineractUser -Password $fineractPass -TenantId $fineractTenant
        if ($respWrap.statusCode -lt 200 -or $respWrap.statusCode -ge 300) {
            throw "GET savingsproducts failed with HTTP $($respWrap.statusCode): $($respWrap.bodyText)"
        }
        if (-not $respWrap.bodyJson -and $respWrap.bodyText) {
            try { $respWrap.bodyJson = $respWrap.bodyText | ConvertFrom-Json -Depth 30 } catch { }
        }
        $resp = $respWrap.bodyJson
        $items = @()
        if ($resp.pageItems -is [array]) {
            $items = @($resp.pageItems)
        } elseif ($resp -is [array]) {
            $items = @($resp)
        } elseif ($resp -and $resp.id) {
            $items = @($resp)
        }
        if ($items.Count -eq 0 -and $respWrap.bodyText -match '"id"\s*:\s*(\d+)') {
            $script:FineractDirectBaseUrl = $fineractBaseUrl
            $script:FineractDirectUsername = $fineractUser
            $script:FineractDirectPassword = $fineractPass
            $script:FineractDirectTenant = $fineractTenant
            return [long]$Matches[1]
        }
        Write-Step "Savings product probe at $fineractBaseUrl returned $($items.Count) product(s)."
        if ($items.Count -gt 0 -and $items[0].id) {
            $script:FineractDirectBaseUrl = $fineractBaseUrl
            $script:FineractDirectUsername = $fineractUser
            $script:FineractDirectPassword = $fineractPass
            $script:FineractDirectTenant = $fineractTenant
            return [long]$items[0].id
        }

        # No savings products exist: bootstrap one for synthetic balance/transaction history.
        $createUri = "$($fineractBaseUrl.TrimEnd('/'))/v1/savingsproducts"
        $productPayload = @{
            name                               = "Synthetic Savings Product"
            shortName                          = "SYNS"
            description                        = "Auto-created by synthetic risk data seeder"
            currencyCode                       = "PKR"
            digitsAfterDecimal                 = 2
            inMultiplesOf                      = 1
            nominalAnnualInterestRate          = 5
            interestCompoundingPeriodType      = 1
            interestPostingPeriodType          = 4
            interestCalculationType            = 1
            interestCalculationDaysInYearType  = 365
            minRequiredOpeningBalance          = 0
            lockinPeriodFrequency              = 0
            lockinPeriodFrequencyType          = 0
            withdrawalFeeForTransfers          = $false
            allowOverdraft                     = $false
            enforceMinRequiredBalance          = $false
            withHoldTax                        = $false
            accountingRule                     = 1
            locale                             = "en"
        }
        $createdWrap = Invoke-FineractCurlRequest -Method "POST" -Url $createUri -Username $fineractUser -Password $fineractPass -TenantId $fineractTenant -Body $productPayload
        if ($createdWrap.statusCode -lt 200 -or $createdWrap.statusCode -ge 300) {
            throw "POST savingsproducts failed with HTTP $($createdWrap.statusCode): $($createdWrap.bodyText)"
        }
        $created = $createdWrap.bodyJson
        $createdId = Get-EntityIdFromObject $created
        if ($createdId) {
            $script:FineractDirectBaseUrl = $fineractBaseUrl
            $script:FineractDirectUsername = $fineractUser
            $script:FineractDirectPassword = $fineractPass
            $script:FineractDirectTenant = $fineractTenant
            return [long]$createdId
        }
        if ($created.resourceId) {
            $script:FineractDirectBaseUrl = $fineractBaseUrl
            $script:FineractDirectUsername = $fineractUser
            $script:FineractDirectPassword = $fineractPass
            $script:FineractDirectTenant = $fineractTenant
            return [long]$created.resourceId
        }
        if ($created.id) {
            $script:FineractDirectBaseUrl = $fineractBaseUrl
            $script:FineractDirectUsername = $fineractUser
            $script:FineractDirectPassword = $fineractPass
            $script:FineractDirectTenant = $fineractTenant
            return [long]$created.id
        }
        $errors.Add("Savings product create at $fineractBaseUrl did not return id/resourceId. Response: $($createdWrap.bodyText)")
    } catch {
        $errBody = ""
        try { $errBody = $_.ErrorDetails.Message } catch { }
        $errors.Add("Base=$fineractBaseUrl :: $($_.Exception.Message) $errBody")
        continue
    }
    }
    if ($errors.Count -gt 0) {
        Write-WarnMsg ("Savings product auto-create failed. Details:`n - " + ($errors -join "`n - "))
    }
    return $PreferredId
}

function Get-AccessTokenFromKeycloak {
    param(
        [string]$BaseUrl,
        [string]$RealmName,
        [string]$OidcClientId,
        [string]$User,
        [string]$Pass
    )
    $tokenUrl = "$BaseUrl/realms/$RealmName/protocol/openid-connect/token"
    $body = "grant_type=password&client_id=$([uri]::EscapeDataString($OidcClientId))&username=$([uri]::EscapeDataString($User))&password=$([uri]::EscapeDataString($Pass))&scope=openid"
    $headers = @{ "Content-Type" = "application/x-www-form-urlencoded" }

    try {
        $resp = Invoke-RestMethod -Method Post -Uri $tokenUrl -Headers $headers -Body $body
        if ($resp.access_token) {
            return [string]$resp.access_token
        }
        return ""
    } catch {
        throw "Failed to obtain access token from Keycloak ($tokenUrl): $($_.Exception.Message)"
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [int]$Retries = 2
    )

    $uri = "$($GatewayUrl.TrimEnd('/'))$Path"
    $headers = @{
        "Authorization" = "Bearer $script:ResolvedAccessToken"
        "Content-Type"  = "application/json"
        "Accept"        = "application/json"
    }

    if ($DryRun) {
        Write-Host "DRY-RUN $Method $uri" -ForegroundColor DarkGray
        return $null
    }

    for ($attempt = 0; $attempt -le $Retries; $attempt++) {
        try {
            if ($null -ne $Body) {
                $json = $Body | ConvertTo-Json -Depth 20
                return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $json
            }
            return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
        } catch {
            $isLast = ($attempt -eq $Retries)
            if ($isLast) {
                throw
            }
            Start-Sleep -Milliseconds (600 * ($attempt + 1))
        }
    }
}

function Invoke-ApiWithFallback {
    param(
        [Parameter(Mandatory = $true)][string]$PrimaryPath,
        [Parameter(Mandatory = $true)][string]$FallbackPath,
        [Parameter(Mandatory = $true)][object]$Body,
        [int]$Retries = 1
    )
    try {
        $null = Invoke-Api -Method Post -Path $PrimaryPath -Body $Body -Retries $Retries
        return "PRIMARY"
    } catch {
        try {
            $null = Invoke-Api -Method Post -Path $FallbackPath -Body $Body -Retries $Retries
            return "FALLBACK"
        } catch {
            throw
        }
    }
}

function Invoke-DirectFineractPost {
    param(
        [Parameter(Mandatory = $true)][string]$RelativePath,
        [Parameter(Mandatory = $true)][object]$Body
    )
    if (-not $script:FineractDirectBaseUrl) {
        throw "Direct Fineract base URL is not initialized."
    }
    $url = "$($script:FineractDirectBaseUrl.TrimEnd('/'))$RelativePath"
    $resp = Invoke-FineractCurlRequest -Method "POST" -Url $url -Username $script:FineractDirectUsername -Password $script:FineractDirectPassword -TenantId $script:FineractDirectTenant -Body $Body
    if ($resp.statusCode -lt 200 -or $resp.statusCode -ge 300) {
        throw "Direct Fineract POST failed ($($resp.statusCode)): $($resp.bodyText)"
    }
    return $resp.bodyJson
}

Write-Host ""
Write-Host "=== Synthetic Risk Data Seeder ===" -ForegroundColor Magenta
Write-Host "Target count : $Count"
Write-Host "Gateway      : $GatewayUrl"
if ($DryRun) { Write-Host "Mode         : DRY-RUN" -ForegroundColor Yellow }
Write-Host ""

$script:RunTag = (Get-Date).ToString("yyyyMMddHHmmss") + "-" + (Get-Random -Minimum 1000 -Maximum 9999)
Write-Host "Run tag      : $script:RunTag"

if ($DryRun) {
    $script:ResolvedAccessToken = "DRY-RUN-TOKEN"
    Write-Ok "Dry-run mode: skipping token acquisition."
} elseif (-not $AccessToken) {
    Write-Step "AccessToken not supplied; attempting Keycloak password grant..."
    $script:ResolvedAccessToken = Get-AccessTokenFromKeycloak -BaseUrl $KeycloakBaseUrl -RealmName $Realm -OidcClientId $ClientId -User $Username -Pass $Password
    if (-not $script:ResolvedAccessToken) {
        throw "Unable to resolve access token. Provide -AccessToken explicitly."
    }
    Write-Ok "Access token acquired via Keycloak."
} else {
    $script:ResolvedAccessToken = $AccessToken
    Write-Ok "Using provided access token."
}

Write-Step "Resolving available loan products..."
$loanProductsResp = Invoke-Api -Method Get -Path "/api/v1/loans/products"
$loanProducts = @()
if ($loanProductsResp) {
    if ($loanProductsResp.data -is [array]) {
        $loanProducts = @($loanProductsResp.data)
    } elseif ($loanProductsResp -is [array]) {
        $loanProducts = @($loanProductsResp)
    }
}
if (-not $DryRun -and $loanProducts.Count -eq 0) {
    throw "No loan products found. Create at least one loan product first."
}

$selectedProductId = if ($DryRun) { 1 } else { [long]($loanProducts[0].id) }
$selectedProductName = if ($DryRun) { "DRY-RUN-PRODUCT" } else { [string]($loanProducts[0].name) }
Write-Ok "Using loan product: $selectedProductName (ID: $selectedProductId)"

$firstNames = @(
    "Ali","Ayesha","Umar","Fatima","Hassan","Zainab","Bilal","Sana","Hamza","Hira",
    "Saad","Iqra","Usman","Maryam","Talha","Noor","Ahmed","Mahnoor","Danish","Kiran"
)
$lastNames = @(
    "Khan","Ahmed","Malik","Siddiqui","Raza","Hussain","Farooq","Qureshi","Sheikh","Iqbal",
    "Javed","Nawaz","Shah","Bhatti","Chaudhry","Rehman","Aslam","Akhtar","Butt","Saleem"
)
$streets = @(
    "Street 12, F-8","Block C, DHA Phase 6","Gulshan-e-Iqbal Block 9","Satellite Town","Model Town",
    "Johar Town","Hayatabad Phase 3","University Road","Canal View","Bahria Town Sector B"
)
$profiles = @("LOW_RISK","MEDIUM_RISK","HIGH_RISK")

$runManifest = [System.Collections.Generic.List[object]]::new()
$success = 0
$failed = 0
$fallbackCount = 0
$resolvedSavingsProductId = Resolve-SavingsProductIdFromFineract -PreferredId $SavingsProductId
if ($resolvedSavingsProductId -le 0) {
    throw "Unable to resolve a valid savings product ID. Pass -SavingsProductId <id>."
}
Write-Ok "Using savings product ID: $resolvedSavingsProductId"

for ($i = 1; $i -le $Count; $i++) {
    $profile = Get-RandomItem $profiles
    $first = Get-RandomItem $firstNames
    $last = Get-RandomItem $lastNames
    $cnic = New-Cnic
    $dob = (Get-Date).AddYears(-(Get-Random -Minimum 21 -Maximum 60)).AddDays(-1 * (Get-Random -Minimum 0 -Maximum 365))
    $dobIso = $dob.ToString("yyyy-MM-dd")
    $phone = "+92" + (Get-Random -Minimum 3000000000 -Maximum 3999999999)
    $email = ("{0}.{1}.{2}@synthetic.finnera.local" -f $first.ToLower(), $last.ToLower(), $i)
    $address = Get-RandomItem $streets
    # Keep dates aligned with client activation while allowing realistic history windows.
    $submittedDate = (Get-Date).AddDays(-1 * (Get-Random -Minimum 10 -Maximum 40))
    $expectedDisbursement = $submittedDate.AddDays((Get-Random -Minimum 1 -Maximum 5))
    $principal = switch ($profile) {
        "LOW_RISK"    { Get-Random -Minimum 60000 -Maximum 180000 }
        "MEDIUM_RISK" { Get-Random -Minimum 180000 -Maximum 450000 }
        default       { Get-Random -Minimum 300000 -Maximum 850000 }
    }
    $interestRate = switch ($profile) {
        "LOW_RISK"    { 11 + (Get-Random -Minimum 0 -Maximum 5) }
        "MEDIUM_RISK" { 14 + (Get-Random -Minimum 0 -Maximum 5) }
        default       { 18 + (Get-Random -Minimum 0 -Maximum 6) }
    }
    $tenure = switch ($profile) {
        "LOW_RISK"    { 12 }
        "MEDIUM_RISK" { 18 }
        default       { 24 }
    }

    Write-Step ("[{0}/{1}] Onboarding synthetic customer ({2} {3}) profile={4}" -f $i, $Count, $first, $last, $profile)

    try {
        # 1) KYC onboarding (PII encryption + DID pipeline) with optional CBC fallback
        $onboardingMode = "KYC"
        $kycData = $null
        $clientId = $null
        try {
            $kycPayload = @{
                cnicNumber   = $cnic
                firstName    = $first
                lastName     = $last
                dateOfBirth  = $dobIso
                fatherName   = "$last Senior"
                address      = $address
                mobileNumber = $phone
                email        = $email
            }
            $kycResp = Invoke-Api -Method Post -Path "/api/v1/kyc/onboard" -Body $kycPayload
            $kycData = if ($kycResp -and $kycResp.data) { $kycResp.data } else { $kycResp }
            $clientId = if ($DryRun) { 1000 + $i } else { [long]$kycData.fineractClientId }
            if (-not $DryRun -and ($null -eq $clientId -or $clientId -le 0)) {
                if (-not $AllowCbcFallback) {
                    throw "KYC returned non-usable fineractClientId ($clientId)"
                }
                $onboardingMode = "KYC_PLUS_CBC_CLIENT"
                $fallbackCount++
                Write-Step ("KYC completed without clientId for synthetic customer #{0}; creating CBC client to continue." -f $i)
                $clientPayload = @{
                    officeId        = 1
                    firstname       = $first
                    lastname        = $last
                    externalId      = ("SYN-CLIENT-{0}-{1:0000}" -f $script:RunTag, $i)
                    dateOfBirth     = Get-FineractDate $dob
                    mobileNo        = $phone
                    emailAddress    = $email
                    legalFormId     = 1
                    active          = $true
                    activationDate  = Get-FineractDate $submittedDate
                    submittedOnDate = Get-FineractDate $submittedDate
                    locale          = "en"
                    dateFormat      = "dd MMMM yyyy"
                }
                $clientResp = Invoke-Api -Method Post -Path "/api/v1/clients" -Body $clientPayload
                $clientData = if ($clientResp -and $clientResp.data) { $clientResp.data } else { $clientResp }
                $clientId = if ($DryRun) { 1000 + $i } else { Get-EntityIdFromObject $clientData }
            }
        } catch {
            if (-not $AllowCbcFallback) { throw }
            $onboardingMode = "CBC_FALLBACK"
            $fallbackCount++
            Write-WarnMsg ("KYC unavailable/invalid for synthetic customer #{0}; falling back to CBC client create. Detail: {1}" -f $i, (Get-ExceptionDetail $_))
            $clientPayload = @{
                officeId        = 1
                firstname       = $first
                lastname        = $last
                externalId      = ("SYN-CLIENT-{0}-{1:0000}" -f $script:RunTag, $i)
                dateOfBirth     = Get-FineractDate $dob
                mobileNo        = $phone
                emailAddress    = $email
                legalFormId     = 1
                active          = $true
                activationDate  = Get-FineractDate $submittedDate
                submittedOnDate = Get-FineractDate $submittedDate
                locale          = "en"
                dateFormat      = "dd MMMM yyyy"
            }
            $clientResp = Invoke-Api -Method Post -Path "/api/v1/clients" -Body $clientPayload
            $clientData = if ($clientResp -and $clientResp.data) { $clientResp.data } else { $clientResp }
            $clientId = if ($DryRun) { 1000 + $i } else { Get-EntityIdFromObject $clientData }
        }
        if (-not $clientId -or $clientId -le 0) { throw "Client onboarding did not return a valid clientId" }

        # 2) Create + activate savings account for transaction history / balance profile
        $savingsPayload = @{
            clientId        = $clientId
            productId       = $resolvedSavingsProductId
            externalId      = ("SYN-SAV-{0}-{1:0000}" -f $script:RunTag, $i)
            submittedOnDate = Get-FineractDate $submittedDate
            locale          = "en"
            dateFormat      = "dd MMMM yyyy"
        }
        $savingsResp = Invoke-Api -Method Post -Path "/api/v1/savingsaccounts" -Body $savingsPayload
        $savingsData = if ($savingsResp -and $savingsResp.data) { $savingsResp.data } else { $savingsResp }
        $savingsAccountId = if ($DryRun) { 7000 + $i } else { Get-EntityIdFromObject $savingsData }
        if (-not $savingsAccountId) {
            throw "Savings account creation did not return account ID"
        }

        $savingsDate = Get-FineractDate $submittedDate
        $null = Invoke-Api -Method Post -Path "/api/v1/savingsaccounts/${savingsAccountId}?command=approve&date=$([uri]::EscapeDataString($savingsDate))"
        $null = Invoke-Api -Method Post -Path "/api/v1/savingsaccounts/${savingsAccountId}?command=activate&date=$([uri]::EscapeDataString($savingsDate))"

        # Savings transactions via transaction-service (fraud/audit flow in place)
        $savingsTxCount = switch ($profile) {
            "LOW_RISK"    { Get-Random -Minimum 18 -Maximum 28 }
            "MEDIUM_RISK" { Get-Random -Minimum 14 -Maximum 24 }
            default       { Get-Random -Minimum 12 -Maximum 22 }
        }
        $openingDeposit = switch ($profile) {
            "LOW_RISK"    { Get-Random -Minimum 120000 -Maximum 250000 }
            "MEDIUM_RISK" { Get-Random -Minimum 70000 -Maximum 160000 }
            default       { Get-Random -Minimum 35000 -Maximum 120000 }
        }
        $savingsBalanceEstimate = [double]$openingDeposit
        $savingsTxFailed = 0
        $savingsTxSucceeded = 0
        $savingsTxFallbackUsed = 0

        $openingPayload = @{
            transactionAmount = [double]$openingDeposit
            transactionDate   = Get-FineractDate $submittedDate
            paymentTypeId     = 1
            note              = "Synthetic opening deposit profile=$profile"
            locale            = "en"
            dateFormat        = "dd MMMM yyyy"
        }
        try {
            $mode = Invoke-ApiWithFallback `
                -PrimaryPath "/api/v1/transactions/savings/$savingsAccountId/deposit" `
                -FallbackPath "/api/v1/savingsaccounts/$savingsAccountId/transactions?command=deposit" `
                -Body $openingPayload `
                -Retries 1
            $savingsTxSucceeded++
            if ($mode -eq "FALLBACK") { $savingsTxFallbackUsed++ }
        } catch {
            try {
                $null = Invoke-DirectFineractPost -RelativePath "/v1/savingsaccounts/$savingsAccountId/transactions?command=deposit" -Body $openingPayload
                $savingsTxSucceeded++
                $savingsTxFallbackUsed++
            } catch {
                $savingsTxFailed++
            }
        }

        for ($st = 1; $st -le $savingsTxCount; $st++) {
            $savingsWindowDays = [Math]::Max(1, ((Get-Date) - $submittedDate).Days)
            $txDate = $submittedDate.AddDays((Get-Random -Minimum 0 -Maximum ($savingsWindowDays + 1)))
            $isDeposit = switch ($profile) {
                "LOW_RISK"    { (Get-Random -Minimum 1 -Maximum 100) -le 68 }
                "MEDIUM_RISK" { (Get-Random -Minimum 1 -Maximum 100) -le 55 }
                default       { (Get-Random -Minimum 1 -Maximum 100) -le 45 }
            }
            $txAmount = if ($isDeposit) {
                switch ($profile) {
                    "LOW_RISK"    { Get-Random -Minimum 4000 -Maximum 35000 }
                    "MEDIUM_RISK" { Get-Random -Minimum 3000 -Maximum 26000 }
                    default       { Get-Random -Minimum 2000 -Maximum 18000 }
                }
            } else {
                switch ($profile) {
                    "LOW_RISK"    { Get-Random -Minimum 2000 -Maximum 22000 }
                    "MEDIUM_RISK" { Get-Random -Minimum 2500 -Maximum 24000 }
                    default       { Get-Random -Minimum 3000 -Maximum 28000 }
                }
            }
            if ((-not $isDeposit) -and ($savingsBalanceEstimate -lt [double]$txAmount)) {
                $isDeposit = $true
                $txAmount = Get-Random -Minimum 3500 -Maximum 12000
            }

            $txPayload = @{
                transactionAmount = [double]$txAmount
                transactionDate   = Get-FineractDate $txDate
                paymentTypeId     = 1
                note              = if ($isDeposit) { "Synthetic salary/credit #$st profile=$profile" } else { "Synthetic debit/spend #$st profile=$profile" }
                locale            = "en"
                dateFormat        = "dd MMMM yyyy"
            }

            $txPath = if ($isDeposit) {
                "/api/v1/transactions/savings/$savingsAccountId/deposit"
            } else {
                "/api/v1/transactions/savings/$savingsAccountId/withdrawal"
            }

            try {
                $fallbackPath = if ($isDeposit) {
                    "/api/v1/savingsaccounts/$savingsAccountId/transactions?command=deposit"
                } else {
                    "/api/v1/savingsaccounts/$savingsAccountId/transactions?command=withdrawal"
                }
                $mode = Invoke-ApiWithFallback -PrimaryPath $txPath -FallbackPath $fallbackPath -Body $txPayload -Retries 1
                $savingsTxSucceeded++
                if ($mode -eq "FALLBACK") { $savingsTxFallbackUsed++ }
                if ($isDeposit) {
                    $savingsBalanceEstimate += [double]$txAmount
                } else {
                    $savingsBalanceEstimate -= [double]$txAmount
                }
            } catch {
                try {
                    $directCmd = if ($isDeposit) { "deposit" } else { "withdrawal" }
                    $null = Invoke-DirectFineractPost -RelativePath "/v1/savingsaccounts/$savingsAccountId/transactions?command=$directCmd" -Body $txPayload
                    $savingsTxSucceeded++
                    $savingsTxFallbackUsed++
                    if ($isDeposit) {
                        $savingsBalanceEstimate += [double]$txAmount
                    } else {
                        $savingsBalanceEstimate -= [double]$txAmount
                    }
                } catch {
                    $savingsTxFailed++
                }
            }
        }

        # 3) Create loan request
        $loanPayload = @{
            clientId                           = $clientId
            productId                          = $selectedProductId
            loanType                           = "individual"
            principal                          = [double]$principal
            loanTermFrequency                  = $tenure
            loanTermFrequencyType              = 2
            numberOfRepayments                 = $tenure
            repaymentEvery                     = 1
            repaymentFrequencyType             = 2
            interestRatePerPeriod              = [double]$interestRate
            interestType                       = 0
            interestCalculationPeriodType      = 1
            amortizationType                   = 1
            transactionProcessingStrategyCode  = "mifos-standard-strategy"
            submittedOnDate                    = Get-FineractDate $submittedDate
            expectedDisbursementDate           = Get-FineractDate $expectedDisbursement
            externalId                         = ("SYN-LOAN-{0}-{1:0000}" -f $script:RunTag, $i)
            locale                             = "en"
            dateFormat                         = "dd MMMM yyyy"
        }
        $loanResp = Invoke-Api -Method Post -Path "/api/v1/loans" -Body $loanPayload
        $loanData = if ($loanResp -and $loanResp.data) { $loanResp.data } else { $loanResp }
        $loanId = if ($DryRun) { 5000 + $i } else { Get-EntityIdFromObject $loanData }
        if (-not $loanId) {
            throw "Loan creation response did not include loan ID"
        }

        # 4) Approve + 5) Disburse loan
        $decisionDate = Get-FineractDate $submittedDate
        $null = Invoke-Api -Method Post -Path "/api/v1/loans/${loanId}?command=approve&date=$([uri]::EscapeDataString($decisionDate))"
        $null = Invoke-Api -Method Post -Path "/api/v1/loans/${loanId}?command=disburse&date=$([uri]::EscapeDataString($decisionDate))"

        # 6) Generate loan repayment history
        $repaymentCount = switch ($profile) {
            "LOW_RISK"    { Get-Random -Minimum 10 -Maximum 16 }
            "MEDIUM_RISK" { Get-Random -Minimum 8 -Maximum 13 }
            default       { Get-Random -Minimum 6 -Maximum 11 }
        }

        $failedTx = 0
        $loanRepaymentFallbackUsed = 0
        for ($t = 1; $t -le $repaymentCount; $t++) {
            $repaymentWindowDays = [Math]::Max(1, ((Get-Date) - $submittedDate).Days)
            $txDate = $submittedDate.AddDays((Get-Random -Minimum 0 -Maximum ($repaymentWindowDays + 1)))
            $txAmount = switch ($profile) {
                "LOW_RISK"    { Get-Random -Minimum 7000 -Maximum 18000 }
                "MEDIUM_RISK" { Get-Random -Minimum 4000 -Maximum 16000 }
                default       { Get-Random -Minimum 1500 -Maximum 14000 }
            }
            $txPayload = @{
                transactionAmount = [double]$txAmount
                transactionDate   = Get-FineractDate $txDate
                paymentTypeId     = 1
                note              = "Synthetic repayment #$t profile=$profile"
                locale            = "en"
                dateFormat        = "dd MMMM yyyy"
            }
            try {
                $mode = Invoke-ApiWithFallback `
                    -PrimaryPath "/api/v1/transactions/loans/$loanId/repayment" `
                    -FallbackPath "/api/v1/loans/$loanId/transactions?command=repayment" `
                    -Body $txPayload `
                    -Retries 1
                if ($mode -eq "FALLBACK") { $loanRepaymentFallbackUsed++ }
            } catch {
                try {
                    $null = Invoke-DirectFineractPost -RelativePath "/v1/loans/$loanId/transactions?command=repayment" -Body $txPayload
                    $loanRepaymentFallbackUsed++
                } catch {
                    $failedTx++
                }
            }
        }

        $runManifest.Add([pscustomobject]@{
            syntheticUserRef = ("SYN-{0:0000}" -f $i)
            riskProfile      = $profile
            onboardingMode   = $onboardingMode
            fineractClientId = $clientId
            savingsAccountId = $savingsAccountId
            savingsProductId = $resolvedSavingsProductId
            savingsTxSucceeded = $savingsTxSucceeded
            savingsTxFailed  = $savingsTxFailed
            savingsTxFallbackUsed = $savingsTxFallbackUsed
            estimatedSavingsBalance = [Math]::Round($savingsBalanceEstimate, 2)
            kycReferenceId   = if ($DryRun) { "KYC-DRY-$i" } else { $kycData.referenceId }
            didId            = if ($DryRun) { "did:example:dry-$i" } else { $kycData.didId }
            loanId           = $loanId
            repaymentsTarget = $repaymentCount
            repaymentsFailed = $failedTx
            loanRepaymentFallbackUsed = $loanRepaymentFallbackUsed
        })

        $success++
        Write-Ok ("Seeded synthetic customer #{0} | clientId={1}, loanId={2}, tx={3} (failed={4})" -f $i, $clientId, $loanId, $repaymentCount, $failedTx)
    } catch {
        $failed++
        Write-ErrMsg ("Failed synthetic customer #{0}: {1}" -f $i, $_.Exception.Message)
    }
}

$summary = [pscustomobject]@{
    generatedAt   = (Get-Date).ToString("o")
    countRequested = $Count
    countSucceeded = $success
    countFailed    = $failed
    usedLoanProductId = $selectedProductId
    usedLoanProductName = $selectedProductName
    records = $runManifest
}

if (-not $DryRun) {
    $outDir = Split-Path -Path $OutputPath -Parent
    if ($outDir -and -not (Test-Path $outDir)) {
        New-Item -ItemType Directory -Path $outDir | Out-Null
    }
    $summary | ConvertTo-Json -Depth 10 | Set-Content -Path $OutputPath -Encoding UTF8
    Write-Ok "Run manifest written to: $OutputPath"
}

Write-Host ""
Write-Host "=== Synthetic Seeding Complete ===" -ForegroundColor Magenta
Write-Host "Requested : $Count"
Write-Host "Succeeded : $success" -ForegroundColor Green
Write-Host "Failed    : $failed" -ForegroundColor Yellow
Write-Host "Fallbacks : $fallbackCount (KYC -> CBC client create)"
if ($fallbackCount -gt 0) {
    Write-WarnMsg "CBC fallback was used; these fallback records do not include KYC encrypted-PII/DID artifacts."
}
Write-Host ""
Write-Host "Next (model training):" -ForegroundColor Cyan
Write-Host "1) Pull transaction history from transaction-service/audit-service databases."
Write-Host "2) Use riskProfile + repayment behavior as initial labels/features."
Write-Host "3) Train/evaluate risk scoring model, then integrate as loan disbursal recommendation."
Write-Host ""
