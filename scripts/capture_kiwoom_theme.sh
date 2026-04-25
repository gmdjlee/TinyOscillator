#!/usr/bin/env bash
# Kiwoom ka90001/ka90002 캡처 스크립트 POSIX 래퍼.
# Git Bash / MSYS / WSL 환경에서 .ps1을 실행하기 위한 얇은 래퍼.
#
# 환경변수 (필수):
#   KIWOOM_APP_KEY
#   KIWOOM_APP_SECRET
#
# 사용법:
#   KIWOOM_APP_KEY=... KIWOOM_APP_SECRET=... ./scripts/capture_kiwoom_theme.sh
#   KIWOOM_APP_KEY=... KIWOOM_APP_SECRET=... ./scripts/capture_kiwoom_theme.sh -Mode prod -Exchange 3

set -euo pipefail

if [ -z "${KIWOOM_APP_KEY:-}" ] || [ -z "${KIWOOM_APP_SECRET:-}" ]; then
    echo "[ERROR] KIWOOM_APP_KEY / KIWOOM_APP_SECRET 환경변수가 필요합니다." >&2
    echo "사용법:" >&2
    echo "  KIWOOM_APP_KEY=<key> KIWOOM_APP_SECRET=<secret> ./scripts/capture_kiwoom_theme.sh [옵션]" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PS1_PATH="${SCRIPT_DIR}/capture_kiwoom_theme.ps1"

# MSYS/Cygwin 환경 여부에 따라 경로를 Windows 형식으로 변환
if command -v cygpath >/dev/null 2>&1; then
    PS1_PATH_WIN="$(cygpath -w "$PS1_PATH")"
else
    PS1_PATH_WIN="$PS1_PATH"
fi

# 환경변수는 PowerShell 자식 프로세스로 자동 전파됨.
powershell.exe -ExecutionPolicy Bypass -NoProfile \
    -File "$PS1_PATH_WIN" "$@"
