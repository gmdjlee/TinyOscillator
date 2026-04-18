<#
.SYNOPSIS
    Android 화면 캡처 (Windows/PowerShell 안전 버전)
.PARAMETER OutDir
    저장 디렉토리. 기본값: D:\android_screenshots
#>
param(
    [string]$OutDir = "D:\android_screenshots"
)

# --- 1. adb 확인 ---
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Error "adb.exe not found at: $adb"
    exit 1
}

# --- 2. 단말 연결 확인 ---
$devicesOutput = & $adb devices 2>$null
$connectedDevice = $devicesOutput | Select-String -Pattern "^\S+\s+device$"
if (-not $connectedDevice) {
    Write-Error "No Android device/emulator connected."
    exit 1
}

# --- 3. 디스플레이 ID 감지 (멀티 디스플레이 경고 방지) ---
$displayOutput = & $adb shell "dumpsys SurfaceFlinger --display-id" 2>$null
$displayIds = @()
foreach ($line in $displayOutput) {
    if ($line -match "Display\s+(\d+)") {
        $displayIds += $Matches[1]
    }
}

$displayArg = ""
if ($displayIds.Count -gt 1) {
    $displayArg = "-d $($displayIds[0])"
    [Console]::Error.WriteLine("[INFO] Multiple displays detected, using display $($displayIds[0])")
}

# --- 4. 출력 디렉토리 준비 ---
if (-not (Test-Path $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outFile = Join-Path $OutDir "screen_$timestamp.png"

# --- 5. 단말 경유 캡처 (stdout 오염 완전 회피) ---
$devicePath = "/sdcard/tinyosc_capture.png"

# 단말에 캡처 (멀티 디스플레이 시 -d 옵션 사용)
$captureCmd = "screencap $displayArg -p $devicePath"
& $adb shell $captureCmd 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Error "screencap failed (exit code $LASTEXITCODE)"
    exit 1
}

# pull (adb pull은 진행률을 stderr로 출력 → $null로 버림)
& $adb pull $devicePath $outFile 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Error "adb pull failed (exit code $LASTEXITCODE)"
    exit 1
}

# 단말 측 파일 정리
& $adb shell "rm $devicePath" 2>$null

# bytes 변수에도 로드 (매직 바이트 검증용)
$bytes = [System.IO.File]::ReadAllBytes($outFile)

# --- 6. 방어적 검증 + PNG 시작 지점 탐색 ---
if ($null -eq $bytes -or $bytes.Length -lt 1024) {
    Write-Error "Captured data too small: $($bytes.Length) bytes"
    exit 1
}

$pngMagic = [byte[]]@(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

$pngStart = -1
$maxScan = [Math]::Min(2048, $bytes.Length - 8)
for ($i = 0; $i -le $maxScan; $i++) {
    $match = $true
    for ($j = 0; $j -lt 8; $j++) {
        if ($bytes[$i + $j] -ne $pngMagic[$j]) {
            $match = $false
            break
        }
    }
    if ($match) {
        $pngStart = $i
        break
    }
}

if ($pngStart -lt 0) {
    $hexHeader = ($bytes[0..([Math]::Min(31, $bytes.Length - 1))] | ForEach-Object { "{0:X2}" -f $_ }) -join " "
    Write-Error "PNG signature not found. Header hex: $hexHeader"
    exit 1
}

if ($pngStart -gt 0) {
    [Console]::Error.WriteLine("[INFO] Stripped $pngStart bytes of prefix garbage")
    $cleanBytes = New-Object byte[] ($bytes.Length - $pngStart)
    [Array]::Copy($bytes, $pngStart, $cleanBytes, 0, $cleanBytes.Length)
    $bytes = $cleanBytes
    [System.IO.File]::WriteAllBytes($outFile, $bytes)
}

# stdout으로 경로 출력 (Claude Code가 파싱)
Write-Output $outFile
