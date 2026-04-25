<#
.SYNOPSIS
    Kiwoom REST API ka90001/ka90002 응답 캡처 (Windows PowerShell).

.DESCRIPTION
    모의투자 또는 실전 환경에서 OAuth2 토큰을 발급받고 ka90001(테마그룹별요청) →
    ka90002(테마구성종목요청)을 차례로 1회씩 호출하여 응답 본문과 헤더를
    fixture 파일로 저장한다.

    산출물 (모두 app/src/test/resources/fixtures/ 아래):
      - ka90001_sample.json         : ka90001 응답 본문 (pretty-print)
      - ka90001_headers.json        : cont-yn, next-key, api-id 등 응답 헤더
      - ka90002_sample.json         : ka90002 응답 본문
      - ka90002_headers.json        : ka90002 응답 헤더

.PARAMETER Mode
    mock 또는 prod. 기본값 mock (https://mockapi.kiwoom.com).
    prod는 https://api.kiwoom.com.

.PARAMETER OutDir
    fixture 저장 디렉터리. 기본값 app/src/test/resources/fixtures.

.PARAMETER Exchange
    stex_tp: 1=KRX, 2=NXT, 3=통합. 기본값 1.

.PARAMETER DateTp
    date_tp: 1~99 기간 일수. 기본값 30.

.PARAMETER QryTp
    qry_tp: 0=전체검색, 1=테마검색, 2=종목검색. 기본값 0.

.PARAMETER FluPlAmtTp
    flu_pl_amt_tp: 1=상위기간수익률, 2=하위기간수익률, 3=상위등락률, 4=하위등락률.
    기본값 1.

.PARAMETER ThemeCode
    ka90002 호출에 사용할 테마그룹코드. 미지정 시 ka90001 응답의 첫 항목을 사용.

.ENV
    필수:
      KIWOOM_APP_KEY    앱 키
      KIWOOM_APP_SECRET 앱 시크릿

.EXAMPLE
    # PowerShell 세션에서 1회 설정 (세션 종료 시 소멸)
    $env:KIWOOM_APP_KEY    = "PSxxxxxxxxxxxx"
    $env:KIWOOM_APP_SECRET = "yyyyyyyyyyyyyyyyyyyyyyyy"
    ./scripts/capture_kiwoom_theme.ps1

.EXAMPLE
    ./scripts/capture_kiwoom_theme.ps1 -Mode prod -Exchange 3 -DateTp 60
#>
param(
    [ValidateSet("mock", "prod")]
    [string]$Mode = "mock",

    [string]$OutDir = "app/src/test/resources/fixtures",

    [ValidateSet("1", "2", "3")]
    [string]$Exchange = "1",

    [ValidateRange(1, 99)]
    [int]$DateTp = 30,

    [ValidateSet("0", "1", "2")]
    [string]$QryTp = "0",

    [ValidateSet("1", "2", "3", "4")]
    [string]$FluPlAmtTp = "1",

    [string]$ThemeCode = ""
)

$ErrorActionPreference = "Stop"

# --- 1. 자격증명 확인 ---
$appKey = $env:KIWOOM_APP_KEY
$appSecret = $env:KIWOOM_APP_SECRET

if ([string]::IsNullOrWhiteSpace($appKey) -or [string]::IsNullOrWhiteSpace($appSecret)) {
    [Console]::Error.WriteLine("[ERROR] 환경변수 KIWOOM_APP_KEY / KIWOOM_APP_SECRET 가 설정되지 않았습니다.")
    [Console]::Error.WriteLine("사용법:")
    [Console]::Error.WriteLine('  $env:KIWOOM_APP_KEY    = "<appkey>"')
    [Console]::Error.WriteLine('  $env:KIWOOM_APP_SECRET = "<secret>"')
    [Console]::Error.WriteLine("  ./scripts/capture_kiwoom_theme.ps1")
    exit 1
}

$baseUrl = if ($Mode -eq "prod") { "https://api.kiwoom.com" } else { "https://mockapi.kiwoom.com" }
[Console]::Error.WriteLine("[INFO] 대상 환경: $Mode ($baseUrl)")

# --- 2. 출력 디렉터리 준비 ---
# 스크립트 위치 기준 프로젝트 루트로 이동 (어디서 실행해도 동일 경로에 저장)
$scriptRoot = Split-Path -Parent $PSCommandPath
$projectRoot = Split-Path -Parent $scriptRoot
$resolvedOutDir = if ([System.IO.Path]::IsPathRooted($OutDir)) {
    $OutDir
} else {
    Join-Path $projectRoot $OutDir
}
if (-not (Test-Path -LiteralPath $resolvedOutDir)) {
    New-Item -ItemType Directory -Path $resolvedOutDir -Force | Out-Null
}
[Console]::Error.WriteLine("[INFO] 출력 디렉터리: $resolvedOutDir")

# --- 3. OAuth2 토큰 발급 ---
[Console]::Error.WriteLine("[INFO] 토큰 발급 요청: POST $baseUrl/oauth2/token")

$tokenBody = @{
    grant_type = "client_credentials"
    appkey     = $appKey
    secretkey  = $appSecret
} | ConvertTo-Json -Compress

$tokenResponse = $null
try {
    $tokenResponse = Invoke-WebRequest `
        -Uri "$baseUrl/oauth2/token" `
        -Method Post `
        -Headers @{ "Content-Type" = "application/json;charset=UTF-8"; "api-id" = "au10001" } `
        -Body $tokenBody `
        -UseBasicParsing
} catch {
    [Console]::Error.WriteLine("[ERROR] 토큰 요청 실패: $($_.Exception.Message)")
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $errBody = $reader.ReadToEnd()
        [Console]::Error.WriteLine("응답: $errBody")
    }
    exit 2
}

$tokenJson = $tokenResponse.Content | ConvertFrom-Json
if ($tokenJson.return_code -ne 0) {
    [Console]::Error.WriteLine("[ERROR] 토큰 발급 실패: return_code=$($tokenJson.return_code), msg=$($tokenJson.return_msg)")
    exit 3
}
$accessToken = $tokenJson.token
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    [Console]::Error.WriteLine("[ERROR] token 필드가 응답에 없습니다.")
    exit 3
}
[Console]::Error.WriteLine("[INFO] 토큰 발급 성공 (만료 $($tokenJson.expires_dt))")

# --- 4. 공통 호출 헬퍼 ---
function Invoke-KiwoomTr {
    param(
        [string]$ApiId,
        [hashtable]$Body,
        [string]$ContYn = "N",
        [string]$NextKey = ""
    )
    $headers = @{
        "Content-Type"  = "application/json;charset=UTF-8"
        "authorization" = "Bearer $accessToken"
        "api-id"        = $ApiId
        "cont-yn"       = $ContYn
        "next-key"      = $NextKey
    }
    $bodyJson = $Body | ConvertTo-Json -Compress
    [Console]::Error.WriteLine("[INFO] POST $baseUrl/api/dostk/thme (api-id=$ApiId, body=$bodyJson)")

    $resp = $null
    try {
        $resp = Invoke-WebRequest `
            -Uri "$baseUrl/api/dostk/thme" `
            -Method Post `
            -Headers $headers `
            -Body $bodyJson `
            -UseBasicParsing
    } catch {
        [Console]::Error.WriteLine("[ERROR] $ApiId 호출 실패: $($_.Exception.Message)")
        if ($_.Exception.Response) {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $errBody = $reader.ReadToEnd()
            [Console]::Error.WriteLine("응답: $errBody")
        }
        throw
    }

    # 응답 헤더를 평탄화 (Invoke-WebRequest의 Headers는 string[] 값)
    $flatHeaders = [ordered]@{}
    foreach ($key in $resp.Headers.Keys) {
        $val = $resp.Headers[$key]
        if ($val -is [System.Array]) { $val = ($val -join ", ") }
        $flatHeaders[$key] = $val
    }

    return [pscustomobject]@{
        StatusCode = $resp.StatusCode
        Body       = $resp.Content
        Headers    = $flatHeaders
    }
}

function Save-Capture {
    param(
        [string]$Name,
        [string]$Body,
        [object]$Headers
    )
    $bodyPath = Join-Path $resolvedOutDir "${Name}_sample.json"
    $headerPath = Join-Path $resolvedOutDir "${Name}_headers.json"

    # Body는 pretty-print으로 저장 (사람 검토 편의)
    $parsed = $Body | ConvertFrom-Json
    $pretty = $parsed | ConvertTo-Json -Depth 64
    # UTF-8 (BOM 없음)으로 기록 — 테스트 리소스 파일이 BOM을 가지면 Json 파서가 실패할 수 있음
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($bodyPath, $pretty, $utf8NoBom)

    $headerPretty = $Headers | ConvertTo-Json -Depth 8
    [System.IO.File]::WriteAllText($headerPath, $headerPretty, $utf8NoBom)

    [Console]::Error.WriteLine("[OK] $bodyPath")
    [Console]::Error.WriteLine("[OK] $headerPath")
}

# --- 5. ka90001 호출 ---
$ka90001Body = @{
    qry_tp         = $QryTp
    stk_cd         = ""
    date_tp        = "$DateTp"
    thema_nm       = ""
    flu_pl_amt_tp  = $FluPlAmtTp
    stex_tp        = $Exchange
}
$ka90001Result = Invoke-KiwoomTr -ApiId "ka90001" -Body $ka90001Body
Save-Capture -Name "ka90001" -Body $ka90001Result.Body -Headers $ka90001Result.Headers

# --- 6. ka90002용 테마코드 결정 ---
if ([string]::IsNullOrWhiteSpace($ThemeCode)) {
    $parsed = $ka90001Result.Body | ConvertFrom-Json
    # 응답 구조를 사전 확정할 수 없으므로 상위 프로퍼티를 순회하며 'thema_grp_cd' 필드를 보유한
    # 첫 행을 탐색한다. 실제 키 이름은 응답에 따라 다를 수 있음.
    $candidateArrays = @()
    foreach ($prop in $parsed.PSObject.Properties) {
        if ($prop.Value -is [System.Array] -and $prop.Value.Count -gt 0) {
            $candidateArrays += ,@($prop.Name, $prop.Value)
        }
    }
    $found = $null
    foreach ($pair in $candidateArrays) {
        $arrName = $pair[0]
        $arr = $pair[1]
        $first = $arr[0]
        if ($null -eq $first -or $first -isnot [psobject]) { continue }
        $codeField = $first.PSObject.Properties | Where-Object {
            $_.Name -match "(?i)thema.*grp.*cd|theme.*grp.*cd|thema.*cd"
        } | Select-Object -First 1
        if ($codeField -and -not [string]::IsNullOrWhiteSpace($codeField.Value)) {
            $ThemeCode = [string]$codeField.Value
            [Console]::Error.WriteLine("[INFO] ka90001.$arrName[0].$($codeField.Name) = $ThemeCode (ka90002에 사용)")
            $found = $true
            break
        }
    }
    if (-not $found) {
        [Console]::Error.WriteLine("[WARN] ka90001 응답에서 테마코드 필드를 자동 감지하지 못했습니다.")
        [Console]::Error.WriteLine("       -ThemeCode <코드> 인자로 재시도하거나 ka90001_sample.json 을 확인하세요.")
        exit 4
    }
}

# --- 7. ka90002 호출 ---
$ka90002Body = @{
    date_tp      = "$DateTp"
    thema_grp_cd = $ThemeCode
    stex_tp      = $Exchange
}
$ka90002Result = Invoke-KiwoomTr -ApiId "ka90002" -Body $ka90002Body
Save-Capture -Name "ka90002" -Body $ka90002Result.Body -Headers $ka90002Result.Headers

[Console]::Error.WriteLine("")
[Console]::Error.WriteLine("[SUCCESS] 캡처 완료. 다음 파일을 확인하세요:")
[Console]::Error.WriteLine("  $resolvedOutDir\ka90001_sample.json")
[Console]::Error.WriteLine("  $resolvedOutDir\ka90001_headers.json")
[Console]::Error.WriteLine("  $resolvedOutDir\ka90002_sample.json")
[Console]::Error.WriteLine("  $resolvedOutDir\ka90002_headers.json")
[Console]::Error.WriteLine("")
[Console]::Error.WriteLine("다음 단계: Plan의 Step 2 — KiwoomThemeModels.kt DTO 작성")

# stdout에 출력 디렉터리 경로 (다른 스크립트가 파싱하기 쉽게)
Write-Output $resolvedOutDir
