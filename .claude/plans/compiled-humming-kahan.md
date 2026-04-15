# Plan: Worker 알림 아이콘으로 인한 앱 크래시 수정

## Context
설정 화면에서 "모든 데이터 업데이트" 실행 시 앱이 강제 종료됨. Worker가 foreground notification을 표시할 때 `R.drawable.ic_launcher_foreground` (108dp 복합 벡터 드로어블)을 smallIcon으로 사용하는데, 이 아이콘은 StatusBar에서 렌더링할 수 없어 `BadForegroundServiceNotificationException` 발생.

## Root Cause
`CollectionNotificationHelper.kt`에서 `.setSmallIcon(R.drawable.ic_launcher_foreground)` 사용 → StatusBar 아이콘은 단순한 단색 벡터여야 함. 복잡한 adaptive icon foreground는 사용 불가.

## Solution

### Step 1: 알림 전용 아이콘 생성
- `res/drawable/ic_notification.xml` — 24dp 단색 벡터 아이콘 (차트/파형 모양)
- 알림 smallIcon 규격: 24×24dp, 단색, 투명 배경

### Step 2: CollectionNotificationHelper 수정
- **File**: `app/src/main/java/com/tinyoscillator/core/worker/CollectionNotificationHelper.kt`
- `R.drawable.ic_launcher_foreground` → `R.drawable.ic_notification` (2곳: progress + completion)

## Verification
- `./gradlew assembleDebug` 빌드 성공
- 기기에서 설정 > "모든 데이터 업데이트" 실행 시 크래시 없이 알림 표시
