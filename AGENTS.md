# AGENTS.md — TinyOscillator 에이전트 운영 지침

> **파일 규칙**: 이 파일은 **에이전트가 이 저장소에서 일하는 방식**(역할 분담·명령어·워크플로)만 담는다. 60~120줄 유지 — 갱신 시 오래된 항목을 지우고 추가하며 줄 수를 넘기지 마라. 코드베이스 사실(구조·스택·컨벤션)은 `CLAUDE.md` 참조.

## 모델 역할 분담

최상위 모델은 **Advisor**로 지정하고 판단에 집중한다. 구현 노동은 **Worker**에게 위임한다.

### Advisor (메인 세션)가 직접 하는 일
- 요구사항 분석, 작업 분해, 설계 결정
- Worker에게 줄 작업 브리프 작성
- 결과 검증: diff 직접 확인, 테스트 직접 실행
- 최종 커밋 승인, 사용자 보고

### Worker (Opus 서브에이전트)에게 위임하는 일
- 코드 작성과 수정, 테스트 작성 등 구현 작업 전부
- `Agent` 도구로 위임하고 `model`은 `"opus"`를 지정한다
- 서로 독립적인 작업은 병렬로 위임한다 (한 메시지에 다중 `Agent` 호출)

### 브리프 기준
- 네가 이미 파악한 컨텍스트를 담아 Worker가 재탐색하지 않게 하라
- 파일 경로, 프로젝트 컨벤션, 알려진 함정, 완료 기준(통과해야 할 테스트)을 포함하라

### 경계
- Worker의 완료 보고를 그대로 믿지 마라. diff와 테스트로 직접 확인한 뒤 승인하라
- 검증 실패는 수정 브리프로 재위임하라. 직접 수정은 사소한 마무리에만 허용된다
- 한두 줄 수정처럼 위임 오버헤드가 더 큰 작업은 직접 처리해도 된다

## 명령어

환경: Windows, PowerShell 우선. `JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`.

```powershell
# 빌드
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug --console=plain

# 유닛 테스트 — 타겟만. 전체 스위트 절대 돌리지 마라(~1,420건, 느림)
.\gradlew.bat :app:testDebugUnitTest --tests "com.tinyoscillator.<Class>"

# 계측 테스트 (실기기/에뮬레이터)
.\gradlew.bat :app:connectedDebugAndroidTest
```

- Android SDK: `$env:LOCALAPPDATA\Android\Sdk`. adb/emulator는 그 하위.
- Robolectric DAO 테스트: `@Config(application = android.app.Application::class)`로 `@HiltAndroidApp` 초기화 회피(AndroidKeyStore 크래시 방지). 네이밍 `ClassNameInMemoryTest.kt`.

## 검증 규칙
- **완료 표시 전 lint/테스트 통과 확인.** 실패하면 실패라고 보고 — 출력 첨부.
- 커밋은 사용자가 명시 요청할 때만. main 브랜치면 먼저 브랜치 분기.
- 커밋 메시지 끝: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Read 먼저, 그 다음 Edit/Write. 절대 경로 사용.

## 함정 (알려진)
- 자격증명은 `EncryptedSharedPreferences`에만. 하드코딩·로깅 금지 (상세 `CLAUDE.md` Security).
- Room 마이그레이션은 명시적 — `fallbackToDestructiveMigration()` 추가 금지. 엔티티 변경 시 Migration + schema JSON export 동반.
- KRX 휴일 캘린더 없음 — 날짜 관련 로직 주의.
- 테마 메뉴(ka90001/ka90002)와 11번째 엔진 `SectorCorrelationNetwork`는 별개 — 혼동 금지.
- Turbine은 중간 emit 관찰(Loading→Success→Error)·cold Flow 검증에만. 단건 `.value` 체크로 충분하면 쓰지 마라.
- 새 라이브러리 쓰기 전 `app/build.gradle.kts` 확인 — 기존 의존성·import 스타일 따르라.

## 참조
- 코드베이스 사실(레이어·엔진·데이터 소스·Room 스키마): `CLAUDE.md`
- 프로젝트 메모리 인덱스: `~/.claude/projects/D--android-2025-TinyOscillator/memory/MEMORY.md`
