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

# --- 7. 이미지 리사이즈 + JPEG 재인코딩 (Anthropic API 호환) ---
# 근본 원인: Pixel Fold 등 고해상도 단말의 PNG는 장변 2000px 초과 + 파일 크기 2MB+ 로
# Anthropic /v1/messages 의 "Could not process image" 400 에러를 유발. 장변 1280px 이하,
# 파일 크기 < 1MB 의 JPEG로 재인코딩해 권장 치수와 포맷을 보장한다.
# 리사이즈/재인코딩에 실패하면 Raw PNG를 넘기지 않고 스크립트를 exit 1 — 업스트림이
# 거대 이미지를 Anthropic에 전송해 400을 유발하는 것을 원천 차단.
# Ref: https://docs.anthropic.com/en/docs/build-with-claude/vision#image-size
$maxLongEdge = 1280
$jpegQuality = 82
$maxOutputBytes = 3MB  # Anthropic base64 5MB 제한 대비 여유 확보
$jpegFile = [System.IO.Path]::ChangeExtension($outFile, ".jpg")
$resizeSucceeded = $false

try {
    Add-Type -AssemblyName System.Drawing -ErrorAction Stop

    $srcStream = [System.IO.File]::OpenRead($outFile)
    try {
        $srcImage = [System.Drawing.Image]::FromStream($srcStream)
    } finally {
        $srcStream.Close()
    }

    try {
        $origW = $srcImage.Width
        $origH = $srcImage.Height
        $longEdge = [Math]::Max($origW, $origH)

        if ($longEdge -gt $maxLongEdge) {
            $scale = $maxLongEdge / [double]$longEdge
            $newW = [int][Math]::Round($origW * $scale)
            $newH = [int][Math]::Round($origH * $scale)
            [Console]::Error.WriteLine("[INFO] Resizing ${origW}x${origH} -> ${newW}x${newH} (long edge $maxLongEdge)")
        } else {
            $newW = $origW
            $newH = $origH
        }

        $bitmap = New-Object System.Drawing.Bitmap($newW, $newH, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $graphics.Clear([System.Drawing.Color]::White)
                $graphics.DrawImage($srcImage, 0, 0, $newW, $newH)
            } finally {
                $graphics.Dispose()
            }

            $jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
                Where-Object { $_.MimeType -eq "image/jpeg" } | Select-Object -First 1
            if ($null -eq $jpegCodec) {
                throw "JPEG encoder not available on this system"
            }
            $encoderParams = New-Object System.Drawing.Imaging.EncoderParameters(1)
            $encoderParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
                [System.Drawing.Imaging.Encoder]::Quality, [long]$jpegQuality
            )

            $bitmap.Save($jpegFile, $jpegCodec, $encoderParams)
            $encoderParams.Dispose()
        } finally {
            $bitmap.Dispose()
        }
    } finally {
        $srcImage.Dispose()
    }

    if (-not (Test-Path -LiteralPath $jpegFile)) {
        throw "JPEG output missing after Save: $jpegFile"
    }
    $finalSize = (Get-Item -LiteralPath $jpegFile).Length
    if ($finalSize -lt 1024) {
        throw "JPEG output too small ($finalSize bytes) — likely corrupt"
    }
    if ($finalSize -gt $maxOutputBytes) {
        throw "JPEG output too large ($finalSize bytes > $maxOutputBytes) — would trigger Anthropic 400"
    }

    Remove-Item -LiteralPath $outFile -ErrorAction SilentlyContinue
    $outFile = $jpegFile
    $resizeSucceeded = $true
    [Console]::Error.WriteLine("[INFO] JPEG re-encoded: ${newW}x${newH}, $([int]($finalSize / 1024)) KB")
} catch {
    $errMsg = $_.Exception.Message
    [Console]::Error.WriteLine("[ERROR] Image resize/encode failed: $errMsg")
    # Raw PNG를 넘기면 Anthropic 400을 유발하므로 원본과 부분 출력물을 모두 삭제하고 exit 1.
    Remove-Item -LiteralPath $outFile -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jpegFile -ErrorAction SilentlyContinue
    Write-Error "Failed to produce Anthropic-compatible JPEG: $errMsg"
    exit 1
}

if (-not $resizeSucceeded) {
    Write-Error "Resize pipeline did not complete successfully"
    exit 1
}

# stdout으로 경로 출력 (Claude Code가 파싱)
Write-Output $outFile
