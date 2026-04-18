#!/usr/bin/env bash
# PowerShell 래퍼: MSYS 경로 변환 이슈 없이 .ps1 실행

OUT_DIR="${1:-D:/android_screenshots}"

# Windows 경로로 변환 (PowerShell에 전달용)
WIN_OUT_DIR=$(cygpath -w "$OUT_DIR" 2>/dev/null || echo "$OUT_DIR" | sed 's|/|\\|g')

# PowerShell 실행
powershell.exe -ExecutionPolicy Bypass -NoProfile \
  -File "./scripts/capture.ps1" -OutDir "$WIN_OUT_DIR"
EXIT_CODE=$?

exit $EXIT_CODE