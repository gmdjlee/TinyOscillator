---
description: Capture Android device screen via PowerShell and analyze
allowed-tools: Bash(./scripts/capture.sh:*), Read
---

Run `./scripts/capture.sh $ARGUMENTS` to capture the current Android device screen.

The script:
- Uses PowerShell internally with `adb exec-out` (avoids MSYS path conversion)
- Validates PNG magic bytes (rejects corrupted files that would poison the session)
- Returns the saved file path on stdout

Read the returned PNG path and analyze:
- Current screen/UI state (which tab, which screen)
- Visible errors, ANRs, or crash dialogs
- MPAndroidChart rendering quality
- KOSPI/KOSDAQ/consensus data correctness

If the script fails (exit != 0), show stderr and suggest:
- Run `adb devices` to verify device connection
- Check emulator status in Android Studio