# PROGRESS — BearSignal 이식 진행 기록

> 근거: `TASK_bear_signal_console.md` (「주도주 붕괴 판단 계기판」 이식 명세서 **v1.2**). 각 Phase 완료 시 `PROGRESS:` 마커 갱신. 실행 순서는 `PHASE_RUNBOOK.md`.

## v1.2 재편성 노트 (2026-07-12)

명세가 v1.0 → v1.2로 개정되며 Phase 번호가 재편성됐다(`TASK_bear_signal_console.md` §6). 혼동 방지를 위해 **v1.0 구계획의 P4(UI 조립)·P5(폴리시) 마커는 `P4(v1.0-UI)`/`P5(v1.0-폴리시)`로 재태깅** — 아래 상세 절 내용은 유효한 완료 기록이다. v1.2 기준 매핑:

| v1.2 Phase | 상태 | 비고 |
|---|---|---|
| P0~P3 | 완료(마커 유지) | v1.0과 범위 동일. P3 핵심 화면은 구 P4(v1.0-UI)가 충족 |
| §3.0 임계치 외부화 | **완료(retrofit)** | BearThresholds 주입 — 하단 「임계치 외부화 (v1.2 §3.0 retrofit)」 절 참조 |
| P3.5-1 | **완료** | Room 스냅샷 이력 영속(`bear_snapshot`, Room v37) + 국면/방아쇠 전이 감지 — 하단 「Phase 3.5-1 상세」 절 참조 |
| P3.5 | **완료** | Sparkline·TransitionLog·신선도 제안 배너(ViewModel/UI 조립) — 하단 「Phase 3.5 상세」 절 참조 |
| P4 (웹/LLM 갱신+승인) | **완료** | §4.5 신설 — 구 P4(v1.0-UI)와 별개, `LlmMarketDataSource`+승인 흐름 — 하단 「Phase 4 상세」 절 참조 |
| P5-1 | **완료** | 워커 일/주 Tier 분리 + MINOR 2건(TZ·rememberSaveable) + 레이더 라벨 보정 — 하단 「Phase 5-1 상세」 절 참조. 진입 시 신선도 검사는 P3.5 기충족 재확인 |
| P5-2 (QA) | 잔여 | qa-verifier — §7 v1.2 확장 항목 포함 |

기존 잔여 QA(월간 워커 실발화·관세청/FRED 실키·MINOR 3건)는 P5-1/P5-2에서 흡수 — P5-1에서 TZ·rememberSaveable·레이더 라벨 해소, SignColor 다크는 앱 전역 백로그로 이관(하단 P5-1 상세 참조). "월간 워커 실발화" 실기 항목은 주기 재편으로 "일간/주간 워커 실발화"로 대체돼 P5-2로 이월.

PROGRESS: P5-1 — 완료 (워커 주기 일/주 Tier 분리(§2·§4 주기 열) — `BearSignalDailyUpdateWorker` 신규(Tier A:
`RefreshAutoInputsUseCase` 단독 — 신호2 통계·코스피 2사 비중, 매일 06:30, 알림 ID 1017, TAG
`collection_bear_signal_daily`) + 기존 `BearSignalUpdateWorker` 주간 전환(Tier B:
`RefreshExternalAutoInputsUseCase`+`RefreshMarketReturnsUseCase` 2계열, WORK_NAME
`bear_signal_monthly_update`→`bear_signal_weekly_update`, 매주 월요일 06:00 KST, 2계열 전부 실패 시에만
retry) + `WorkManagerHelper.scheduleWeeklyWorker` 신설(7일 주기·flex 금지 사유 계승·**Asia/Seoul TZ +
Locale.US 고정**으로 QA MINOR "TZ 미고정" 해소, `calculateWeeklyInitialDelayMillis` 순수 함수 분리) +
`scheduleMonthlyWorker` 삭제(호출자 0) + `TinyOscillatorApp` 두 워커 복원 + MINOR 마감 2건(체크리스트
`rememberSaveable(key)` 전환 — TypesHistorySection, 레이더 라벨 텍스트 실측 투영 배치로 폴리곤 겹침 해소) +
테스트: core.worker 42건/0실패(신규 BearSignalDailyUpdateWorkerTest 6·CalculateWeeklyInitialDelayMillisTest 4)
+ bearsignal 352건/0실패(골든·경계 무변경), qa-verifier 게이트 8/8 PASS, 2026-07-13 — 하단 「Phase 5-1 상세」 절 참조)

PROGRESS: P4 — 완료 (§4.5 웹/LLM 3-tier 수집·승인 흐름 — `LlmMarketDataSource` 신규(Anthropic
`/v1/messages` + `web_search_20250305` 서버 도구, 그룹①rate/dir·②bigDeal/lossRatio·③credit
`supervisorScope`+그룹별 격리, 열거형 위반 필드만 폐기, `pause_turn` 재개(최대 3회, 추가 user
메시지 없이 assistant content append), 타임아웃 30s+백오프 1회, 급변 재확인(금리 ±0.5%p·신용잔고
±30%) 1회 재호출 후 일치 시에만 채택) + `Suggestion`/`SuggestionField`/`SuggestionValidation` 도메인
모델 신규(신선도 STALE 허용연령 §3 비임계치) + `ApplySuggestionUseCase`/`FetchSuggestionsUseCase` +
`SuggestionRepository`/`SuggestionRepositoryImpl`(Claude 전용 키/제공자 검증, 미설정 시 안내) +
`BearIndicatorKey` 3종 신규(GATE_CREDIT/S3_LOSS_RATIO/S3_BIG_DEAL, 기존 ManualIndicatorKey와 별도
키 공간) + `MergeBearSignalInputsUseCase` AUTO 확장(loss/big/credit이 MANUAL〉AUTO〉BASELINE 3단
우선순위로, 기존 MANUAL 불패 회귀 없음) + `SuggestionPanel` UI 신규(명시적 "AI 제안 가져오기" 버튼
— init/화면진입 자동 호출 금지, 개별/일괄 승인+무시, STALE 배지 색+텍스트 병기) + `BearSignalScreen`
방아쇠·증폭 카드 아래 섹션 배선 + JVM 테스트 60건 신규(총 352건 — `bearsignal` 패키지 단독 필터 기준,
기존 마이그레이션 테스트 3건은 별도 필터라 제외), 2026-07-13 — 하단 「Phase 4 상세」 절 참조)

PROGRESS: P3.5 — 완료 (Sparkline(lead·gate 90일 Canvas)+TransitionLog("6/30 GREEN→AMBER · 방아쇠 경계 접근"
형식, 이력 상태 3종 empty/단일/다수 처리) `BearSignalSparklineSection` 신규 + 신선도 제안 배너
`BearSignalUpdateSuggestionBanner` 신규(승인 원칙 — 수락 버튼만 refresh 트리거, 자동 반영 없음) +
`BearSignalViewModel` 스냅샷 배선(세션 진입·refresh 시 `upsertToday`, 90일 `observeRange`→스파크라인 이력+
`DetectTransitionsUseCase` 전이 목록, `EvaluateSnapshotFreshnessUseCase`→`updateSuggestion` 노출) +
JVM 테스트 11건 신규(총 295건), qa-verifier 게이트 7항목 전부 PASS, 2026-07-13 — 하단 「Phase 3.5 상세」 절 참조)

PROGRESS: P3.5-1 — 완료 (Room 스냅샷 이력 영속(`BearSnapshotEntity`/`BearSnapshotDao`, Room v36→v37) +
`SnapshotRepository`/`SnapshotRepositoryImpl` + `DetectTransitionsUseCase`(국면·방아쇠 전이 감지) +
`BuildBearSnapshotUseCase`(§4.6 bear-snapshot/1 스키마 직렬화) + `EvaluateSnapshotFreshnessUseCase`(세션
진입 신선도 "제안", 자동 반영 없음) + JVM/Robolectric 테스트 36건 신규(총 284건), 2026-07-13 —
하단 「Phase 3.5-1 상세」 절 참조)

PROGRESS: P5(v1.0-폴리시) — 구현 마감·에뮬레이터 QA 1차 통과·잔여 QA 대기(WorkManager 월간 주기 갱신(`BearSignalUpdateWorker`, 매월 5일 06:00) + 앱 시작 시 스케줄 복원 + shimmer 로딩(`BearSignalScreenSkeleton`) + 오프라인 폴백(`NetworkUtils`+`StaleBanner`, 캐시 데이터 유지+최신 갱신일 표기) + 접근성 최종 점검(신호1~4/증폭 카드·국가별 표 행 contentDescription 보강, 48dp 터치 타깃/색+텍스트 병기 확인) + JVM 테스트 신규 `feature.bearsignal` 패키지 +3건(ViewModel isLoading/isOffline, 총 226건) + `core.worker` 패키지 +6건(`BearSignalUpdateWorkerTest` companion 상수·알림ID 유일성·스케줄 입력 검증), 2026-07-10; 에뮬레이터 실기 QA 1차 통과 2026-07-10 — 하단 「실기 QA」 절 참조, 잔여: 360dp/다크/폰트스케일 렌더·월간 워커 실발화·관세청/FRED 실키·스펙 조정 2건; Stooq 대체 소스 결정은 2026-07-10 Yahoo 기본+Stooq 백업 멀티소스로 해소 — 하단 「시세 소스 교체」 절 참조)

PROGRESS: P4(v1.0-UI) — 완료 (UI 조립 — `BearSignalScreen` LazyColumn 7섹션(헤더·선행신호3카드·국가별수익률표(전치)·방아쇠증폭·3유형·역사검증·푸터) + Canvas 신호등/게이지/레이더 + `ObserveBearSignalStateUseCase` 신규 + 시장분석 탭 진입점 카드 + Pull-to-refresh/리셋/수동입력 BottomSheet 연결 + JVM 테스트 18건 신규(총 223건), 2026-07-10)

PROGRESS: P3 — 완료 ([C]/[D] 수동 입력 계층(신용잔고·적자상장비중·신주비중·대어소화·정책방향·반대매매임박·미커버 해외지수) + auto⊕manual 병합(MANUAL 우선) + 리포트 기준값 리셋 + Room v36 + JVM 테스트 46건 신규(총 205건), 2026-07-10)

PROGRESS: P2 — 완료 ([B] 자동 연동 4지표(관세청 수출비중·FRED/ECOS 금리·IPO ETF 방향·해외 19개 지수) + Room v35 + JVM 테스트 89건 신규(총 159건), 2026-07-10)

PROGRESS: P1 — 완료 ([A] 자동 연동 2지표(신호2 통계·코스피 2사 비중) + Room v34 + JVM 테스트 29건 신규(총 70건), 2026-07-09)

PROGRESS: P0 — 완료 (스캐폴딩·도메인 모델·순수 스코어링·JVM 테스트 41건, 2026-07-09; 시드 데이터 정정 2026-07-09 점검)

## 임계치 외부화 (v1.2 §3.0 retrofit) — 완료 (2026-07-12)

기존 하드코딩 스코어링 임계치를 `bear_thresholds.json`(리포지토리 루트 SSOT, 값은 `app/src/main/assets/bear_thresholds.json` 사본과 사전 검증상 100% 동일)에서 로드해 생성자 주입하는 retrofit. 신규 기능 추가 없음, 값 변경 0.

- **변경 파일**
  - 신규: `app/src/main/java/com/tinyoscillator/feature/bearsignal/domain/model/BearThresholds.kt`(§3.0 데이터클래스, `@Serializable`), `app/src/main/java/com/tinyoscillator/feature/bearsignal/data/local/ThresholdsProvider.kt`(assets 로드 + Context 비의존 `decode()` 순수 함수)
  - 수정: `ComputeBearSignalUseCase.kt`(companion 정적 함수 전부 인스턴스 메서드화, 생성자 `(private val t: BearThresholds)` 주입, `analyzeMarkets`/`scoreS1`~`scoreS3`/`scoreGate`/`amplifier`/`invoke`의 리터럴 임계치를 `t.*` 참조로 치환), `BearSignalModule.kt`(`ThresholdsProvider`/`BearThresholds`/`ComputeBearSignalUseCase` Hilt 프로바이더 갱신), `BearSignalViewModel.kt`(콜드스타트 `DEFAULT_RESULT` 재구성 — 아래 결정 사항 참조)
  - 테스트 신규: `app/src/test/java/.../domain/model/BearThresholdsFixture.kt`(JSON 리터럴 미러 fixture), `app/src/test/java/.../data/local/ThresholdsProviderTest.kt`(디코딩 3건)
  - 테스트 수정: `ComputeBearSignalUseCaseTest.kt`(companion static import → `useCase` 인스턴스 위임 래퍼로 전환 + config 구동 3건 추가), `ObserveBearSignalStateUseCaseTest.kt`, `MergeBearSignalInputsUseCaseTest.kt`, `BearSignalViewModelTest.kt`(전부 `ComputeBearSignalUseCase(BearThresholdsFixture.DEFAULT)`로 전환)
  - KDoc `TASK.md §N` → `TASK_bear_signal_console.md §N` 갱신: `BearSignalViewModel.kt`, `BearSignalModule.kt`, `MergeBearSignalInputsUseCaseTest.kt`, `BearSignalViewModelTest.kt`(수정한 파일에 한함)

- **레이어별 요약**
  - **domain**: `BearThresholds`(+ 중첩 S1/S2/S3/Gate/Amp/PhaseCfg, kotlinx.serialization 코어 어노테이션만 사용해 안드로이드 무의존 유지) 신규. `ComputeBearSignalUseCase`는 companion object 없이 전부 public 인스턴스 메서드(`analyzeMarkets`/`scoreS1`/`scoreS2`/`scoreS3`/`scoreGate`/`amplifier`/`invoke`)로 전환, 전 임계치 참조를 `t.s1.manyCountries` 등으로 치환. 구조 상수(leadPct 분모 9.0, scoreS2 up==0 폴백 9.0, 레벨 0~3, `dir=="hike"` 문자열 비교)는 리터럴 유지(주입 대상 아님, KDoc 명시).
  - **data**: `ThresholdsProvider(context, json)` — `load()`는 `context.assets.open("bear_thresholds.json")` 읽어 `decode()`에 위임. `decode(content, json)`는 companion 순수 함수로 Context 없이 JVM 테스트 가능. `Json { ignoreUnknownKeys = true }` 기본값(기존 앱 전역 파싱 관례 — `KiwoomApiClient.createDefaultJson()` 등과 동일 패턴)으로 JSON의 `note` 등 스코어링 무관 필드를 흡수.
  - **presentation**: `BearSignalModule`에 `provideThresholdsProvider`(Context 필요) → `provideBearThresholds`(`@Singleton`, 앱 기동 시 1회 로드) → `provideComputeBearSignalUseCase(thresholds)` 체인 추가. `BearSignalViewModel`의 top-level `DEFAULT_RESULT`(stateIn 콜드스타트 초기값, Room 4-Flow 최초 방출 전에만 노출되고 `isLoading=true` 구간에서 화면은 스켈레톤으로 대체 렌더)는 `ComputeBearSignalUseCase()` 무인자 호출이 불가능해졌으므로, 재계산 대신 리포트 골든 케이스(2026.6.30)의 **알려진 결과값**을 `BearSignalResult` 리터럴로 직접 스냅샷하도록 재작성(§3 스코어링 로직 자체에는 임계치 하드코딩 없음, 값만 결과 스냅샷).

- **결정 사항**
  1. companion 정적 함수 → 인스턴스 메서드 전환(파라미터 추가 대신)을 채택 — TASK.md §3.0 스켈레톤(`class ComputeBearSignalUseCase(private val t: BearThresholds) { ... }`)이 명시한 목표 형태와 직접 일치. 기존 테스트(companion static import로 `scoreS1(...)` 등을 직접 호출)는 `useCase.scoreS1(...)`에 위임하는 동일 시그니처 `private fun` 래퍼로 감싸 테스트 본문(약 40개 assert) 무변경 유지.
  2. `BearSignalViewModel.kt`의 콜드스타트 `DEFAULT_RESULT`는 임계치를 프로덕션 코드에 재하드코딩하지 않기 위해 `ComputeBearSignalUseCase` 호출 자체를 제거하고, 리포트 골든 케이스의 알려진 결과값(s1=1,s2=1,s3=1,gate=1,amp=1.30,phase=AMBER 등)을 `BearSignalResult` 리터럴로 직접 구성했다. 이 값은 `isLoading=true` 구간에만 노출되고 화면은 스켈레톤을 렌더하므로 사용자에게 실질적 영향이 없으며, `BearSignalViewModelTest`의 "초기 uiState 기본값은 리포트 기준값 골든 케이스(AMBER)다" 테스트를 무변경으로 통과시킨다.
  3. config 구동(§7) 증명 중 "골든 케이스가 AMBER→GREEN으로 바뀐다" 예시는 실제 2026.6.30 리포트 골든 입력(`dir="hike"`)으로는 수학적으로 불가능함을 확인했다 — `scoreGate`의 `else -> if (dir=="hike") 1 else 0` 분기는 `BearThresholds`에 없는 구조 상수(문자열 비교)라 어떤 임계치 조합으로도 `gate`를 0으로 내릴 수 없고, `composite`의 `gate>=1` OR-절 때문에 phase는 항상 최소 AMBER로 고정된다. 대신 (a) 기존 `composite — lead 3 그리고 gate 0이면 AMBER` 테스트와 동일한 합성 입력(`gate=0`)에 `leadAmber`를 3→10으로 올려 AMBER→GREEN 전환을 코드 무수정으로 증명하고, (b) 실제 골든 마켓 데이터(20지수)에 `s1.manyCountries`를 7→20으로 올려 서브스코어 `s1`이 1→0으로 바뀜을 별도로 증명해 "골든 케이스도 config에 반응한다"는 사실을 함께 보강했다.

- **테스트 결과**
  - `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"` — **246건, 0 실패**(기존 240건 회귀 통과 + 신규 6건: `ThresholdsProviderTest` 3건 + `ComputeBearSignalUseCaseTest` config 구동 3건). `ComputeBearSignalUseCaseTest`는 41건(P0)→44건(41 기존 + config 구동 신규 3건).
  - 골든 케이스(`BearThresholdsFixture.DEFAULT`로 구성한 `ComputeBearSignalUseCase`가 도표48 20지수 입력에 대해 s1=1/s2=1/s3=1/gate=1/amp=1.30/phase=AMBER) 무변경 재현 확인.
  - 신규 config 구동 테스트: (a) fixture JSON 문자열 디코딩 → 전 필드값 단언(`ThresholdsProviderTest`), (b) `leadAmber` 3→10 주입 시 동일 입력이 AMBER→GREEN(`ComputeBearSignalUseCaseTest`), (c) `gate.critical` 4.5→5.0 주입 시 `rate=4.5`가 3이 아니라 2로 하향(`ComputeBearSignalUseCaseTest`), 보강: `s1.manyCountries` 7→20 주입 시 실제 골든 마켓 데이터의 `s1`이 1→0(`ComputeBearSignalUseCaseTest`).

- **빌드**: `:app:compileDebugKotlin` BUILD SUCCESSFUL / `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"` BUILD SUCCESSFUL(246/246) / `:app:assembleDebug` BUILD SUCCESSFUL(Hilt 그래프 검증 겸용 — `ThresholdsProvider`→`BearThresholds`→`ComputeBearSignalUseCase` 프로바이더 체인 정상 해석).
- **미해결/차단 요소**: 없음. `bear_thresholds.json`(루트) ↔ `app/src/main/assets/bear_thresholds.json` 값은 diff 확인상 완전 동일(변경하지 않음). 다음 Phase(P3.5-1 Room 스냅샷 이력)는 이 retrofit과 독립적으로 착수 가능.

### 후속 수정 — presentation 계층 잔존 하드코딩 제거 (2026-07-13)

QA 검증 결과 §3 임계치가 presentation 계층에 3곳 복제돼 있음을 확인(§7 "config 구동" 위반). 스위프로 1건 추가 발견(총 4곳), 전부 수정.

- **발견 및 수정 (총 4곳, 전부 `s1` 임계치 복제)**
  1. `BearSignalCountryTableSection.kt:84` — `result.ma.neg >= 7`(강조 배경색) → `s1.manyCountries` 복제
  2. `BearSignalCountryTableSection.kt:91` — `result.ma.neg >= 7`(텍스트 색) → 동일
  3. `BearSignalLeadingSignalsSection.kt:53` — `result.ma.neg >= 7`(이탈 지수 수 색) → 동일
  4. `BearSignalLeadingSignalsSection.kt:54` — `result.ma.worstNew <= -6`(낙폭 색, 스위프로 신규 발견) → `s1.deepeningPct` 복제. 원 QA 목록엔 없었으나 동일 파일·동일 신호(신호1)·동일 결함 유형(§3 값 복제)이라 1~3과 함께 수정.

- **변경 파일**
  - `presentation/BearSignalViewModel.kt` — 생성자에 `thresholds: BearThresholds` 추가(기존 `BearSignalModule.provideBearThresholds` 싱글턴 그대로 재사용, 신규 Hilt 배선 불요). `BearSignalUiState`에 `manyCountriesBreached`/`deepeningBreached: Boolean` 파생 플래그 추가 — `combine{}` 람다와 `stateIn` 콜드스타트 초기값(`DEFAULT_RESULT` + 주입된 `thresholds`로 계산, 재하드코딩 없음) 양쪽에서 산출.
  - `presentation/ui/BearSignalCountryTableSection.kt` — `manyCountriesBreached: Boolean` 파라미터 추가, 리터럴 비교 2곳을 파라미터 참조로 치환.
  - `presentation/ui/BearSignalLeadingSignalsSection.kt` — `manyCountriesBreached`/`deepeningBreached: Boolean` 파라미터 추가, 리터럴 비교 2곳을 파라미터 참조로 치환.
  - `presentation/ui/BearSignalScreen.kt` — 두 Section 호출부에 `uiState.manyCountriesBreached`/`uiState.deepeningBreached` 전달.
  - 테스트 수정: `BearSignalViewModelTest.kt` — 생성자 호출 2곳에 `thresholds` 인자 추가(테스트별 교체 가능한 `private var thresholds` 도입), 골든 케이스 단언에 `manyCountriesBreached=true`/`deepeningBreached=false` 추가, config 구동 신규 2건.

- **데이터 흐름 결정**: "권장 옵션" 중 파생 불리언 노출을 채택(원값/`BearThresholds` 객체 자체를 UiState에 얹는 대신). Section 컴포저블이 도메인 `BearThresholds` 타입을 몰라도 되고(결합도 최소화), ViewModel 한 곳에서만 임계치를 참조하면 되므로 "최소 변경" 기준에 부합. `BearThresholds`는 Hilt가 `BearSignalViewModel` 생성자에 싱글턴으로 주입(기존 `ComputeBearSignalUseCase`와 동일한 프로바이더 재사용) — UI 컴포저블은 Hilt를 전혀 참조하지 않음(기존 패턴 준수).

- **추가 스위프 결과 (패턴: `>= 4.0/4.5/35/45/60/80/0.7/0.95`, `> 1.0`, `>= 20/50`, `>= 3/6`)**
  - 위 4곳 외 §3 임계치(JSON 필드) 값 비교 로직 **잔존 없음**. 다음은 검토했으나 JSON 필드의 직접 복제가 아니라 조치 불요로 판단(발견 목록 보고):
    - `BearSignalGateAmpSection.kt:92`, `BearSignalHeaderSection.kt:101` — `result.amp >= 1.3`: `1.3`은 `bear_thresholds.json`의 어떤 필드값도 아니며(가장 가까운 값은 `amp.cap=1.6`), domain `amplifier()`에도 `>=1.3` 분기가 없다(도메인은 연속값 1.0~1.6만 산출) — 순수 UI 전용 강조 컷오프. 단, `1.0+wSemi+wKospi2`(현재 0.15+0.15=0.30)와 우연히 일치해 향후 가중치가 바뀌면 시각적 의미가 stale해질 수 있다는 점은 리스크로 기록(§3.0 스코프 밖이라 이번 수정 대상에서는 제외).
    - `BearSignalHeaderSection.kt:106` — `result.warn >= 2`: `warn`은 `count{ it>=2 }`로 이미 도메인에서 산출된 파생값이고, 비교 대상 `2`는 JSON 필드가 아니라 레벨 상수(WARN=2) — §3.0에서 이미 예외 처리한 "레벨 0~3 자체는 로직 구조" 범주와 동일해 조치 불요.
    - `BearSignalTypesHistorySection.kt:41` — `gate >= 1`: 도메인 `composite()`의 `gate>=1` OR-절(구조 상수, JSON에 없음)과 동일한 구조적 비교 — 조치 불요.
    - `BearSignalLeadingSignalsSection.kt:79`(구 72) — `inputs.up == 0`: 도메인 `scoreS2`의 `up==0` 폴백과 동일한 구조 상수(0으로 나눔 방지, §3.0에서 명시적으로 주입 대상 제외) — 조치 불요.
    - 정적 인용 카피("임계 4.5% = 진짜 긴축", "≈4.5%", "닷컴 정점 직전 1개월 = 7개국") — 로직 비교가 아니라 리포트 해설 문구, §3.0 범위 밖.

- **테스트 결과**: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"` — **248건, 0 실패**(기존 246건 + 신규 2건: `BearSignalViewModelTest`의 config 구동 테스트 — `manyCountries` 7→20 주입 시 골든 상태의 `manyCountriesBreached`가 true→false, `deepeningPct` -6.0→-5.0 주입 시 `deepeningBreached`가 false→true). `BearSignalViewModelTest`는 13건→15건.
- **빌드**: `:app:compileDebugKotlin` BUILD SUCCESSFUL / `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"` BUILD SUCCESSFUL(248/248) / `:app:assembleDebug` BUILD SUCCESSFUL(Hilt 그래프 재검증 — `BearSignalViewModel` 신규 `BearThresholds` 파라미터 정상 해석).

## Phase 5-1 상세 — 워커 주기 일/주 Tier 분리 · MINOR 마감 (2026-07-13)

TASK_bear_signal_console.md §2(111행 "Phase 5-1에서 일 Tier A / 주 Tier B 조정 검토")·§4 주기 열·§6 Phase 5-1 구현. shimmer·접근성·오프라인·워커 골격은 구 P5(v1.0-폴리시)가 기충족 — 이 절은 델타만 기록한다.

- **변경/추가 파일**
  - 신규: `core/worker/BearSignalDailyUpdateWorker.kt`(Tier A 일간), 테스트 `BearSignalDailyUpdateWorkerTest.kt`(6건)·`CalculateWeeklyInitialDelayMillisTest.kt`(4건)
  - 수정: `core/worker/BearSignalUpdateWorker.kt`(Tier B 주간 전환), `core/worker/WorkManagerHelper.kt`(`scheduleWeeklyWorker` 신설·`scheduleMonthlyWorker` 삭제·BearSignal 스케줄 3함수 추가/변경), `core/worker/CollectionNotificationHelper.kt`(`BEAR_SIGNAL_DAILY_NOTIFICATION_ID=1017`), `TinyOscillatorApp.kt`(일간 워커 복원 추가), `presentation/ui/BearSignalTypesHistorySection.kt`(rememberSaveable), `presentation/ui/BearSignalGraphics.kt`(레이더 라벨), 테스트 `BearSignalUpdateWorkerTest.kt`(주간 계약으로 갱신)

- **워커 주기 재편 (§4 주기 열 근거)**
  - **Tier A 일간(06:30)**: `RefreshAutoInputsUseCase` 단독 — 신호2 통계·코스피 2사 비중(kotlin_krx, §4 주기 "일"). 기존 `scheduleDailyWorker` 재사용(기기 TZ — 앱 전역 관례 유지, 변경 안 함).
  - **Tier B 주간(월 06:00 KST)**: 기존 워커에서 `RefreshAutoInputsUseCase` 제거(일간과 중복 제거), `RefreshExternalAutoInputsUseCase`(IPO ETF=주 충족, 수출=월·금리=이벤트는 상위 빈도 실행 무해 — 캐시 폴백 기존 보장)+`RefreshMarketReturnsUseCase`(해외지수) 2계열. 실패 집계 3→2(전부 실패 시에만 retry). WORK_NAME `bear_signal_weekly_update`로 개명(미출시 브랜치 — 구 이름 정리 코드 불요).
  - **`scheduleWeeklyWorker`**: `PeriodicWorkRequestBuilder(7, DAYS)`+initialDelay(다음 dayOfWeek hh:mm), flex 금지(구 monthly KDoc 사유 계승). **Asia/Seoul TZ 고정** — 관세청/KRX/KOFIA 등 한국 발표 주기 앵커이므로 기기 TZ 아닌 KST 기준(QA MINOR "monthly TZ 미고정" 해소). `Locale.US` 명시 고정으로 주 시작 요일 로케일 편차 차단. `calculateWeeklyInitialDelayMillis(nowMillis, …)` 순수 함수 분리로 JVM 결정적 테스트(당일 경과→다음 주, 미래 시각→당일, 주중 이월 등 4건). require fail-fast(dayOfWeek 1..7·hour·minute)는 WorkManager 호출 전 위치.
  - 월요일 06:00 KST 선택 근거: 주말 미국 마감 데이터(IPO ETF·해외지수) 반영 후 첫 시점.

- **MINOR 마감**
  - **TZ**: 위 KST 고정으로 해소(주간 워커에 한함 — `scheduleDailyWorker`의 기기 TZ는 앱 전역 관례라 유지, KDoc에 결정 근거).
  - **rememberSaveable**: 체크리스트 상태 `remember`→`rememberSaveable(key = "bear_type{index}_monitor{idx}")` — back→재진입 시 소실(실기 재현 기지 이슈) 해소, 위치 기반 명시 키로 텍스트 중복 대비.
  - **레이더 라벨 겹침**: 고정 배율(3.55/3) 대신 텍스트 실측 반너비/반높이를 방사 방향으로 투영한 앵커 반경(`r + 투영 + 3dp`) — 4개 라벨 공통 로직. qa-verifier 산술 검토: 최광폭 라벨이 Canvas 124dp 자체 경계를 수 dp 넘을 수 있으나 drawBehind 비클리핑+배치처 Box 좌우 여백(360dp 기준 ≥80dp)이 흡수 — 가시 클리핑 없음(주석에 명시, P5-2 실기 1.3x 렌더에서 재확인 예정).
  - **SignColor 다크 변형 미사용**: 앱 전역 이슈(bearsignal 밖 전 화면 공통)라 Phase 스코프 밖 — **앱 전역 백로그로 이관**, 코드 무변경. QA상 가독성 저해 수준 아님 재확인됨.

- **진입 시 신선도 검사(P5-1 델타 항목)**: P3.5에서 기구현(`EvaluateSnapshotFreshnessUseCase` → 제안 배너, 자동 반영 금지) — qa-verifier가 이번 변경으로 회귀 없음 확인(ViewModel 무수정).

- **스펙 조정 2건 판단(P4 이월)** — 현행 유지로 확정(사용자 승인 대상):
  1. 수동입력 버튼은 MANUAL 경로 존재 필드(신호3 loss/big, 금리 dir/credit/margin)만 노출 — 신호2·증폭은 v1 범위 밖(§1.1)이라 §5.2 "3카드 모두" 대비 축소.
  2. 국가별 표 §5.3 "인라인 편집" 대신 행 탭→편집 다이얼로그 — 20×4 텍스트필드 포커스/성능 리스크 회피.

- **테스트/게이트 결과**: `core.worker.*` 42건/0실패(신규 10건 포함) + `feature.bearsignal.*` 352건/0실패(골든 2026.6.30→AMBER·경계 neg 6/7, rate 4.49/4.5, ratio 0.94/0.95/1.0, loss 44/45/59/60/79/80 무변경 통과) — qa-verifier 독립 `--rerun` 재실행. `assembleDebug` BUILD SUCCESSFUL(Hilt 그래프 — 일간 워커 `@HiltWorker` 정상 해석). **qa-verifier 게이트 8/8 PASS**(Tier 분리·주간 헬퍼·배선·rememberSaveable·레이더·신선도 회귀·불변 제약(임계치 SSOT/스코어링/§4.5 승인 경로 무변경)·테스트 재실행). 비차단 관찰 1건(레이더 주석 산술 부정확)은 메인 스레드가 주석 정정으로 즉시 해소.
- **잔여(P5-2로)**: 일간/주간 워커 실발화(에뮬레이터), 관세청/FRED 실키 응답 검증, 실 Claude 키 `web_search` 실기, §7 전 항목 최종 QA, 스펙 조정 2건 사용자 재가.

## Phase 4 상세 — 웹/LLM 3-tier 수집 · 승인 흐름 (2026-07-13)

TASK_bear_signal_console.md §4.5 구현 범위 전체(Anthropic API + `web_search` 서버 도구, 그룹 분할
수집, 급변 재확인, 승인 반영). 기존 P4(v1.0-UI, 하단 「P4 상세」 절)와 별개 — 마커 충돌을 피하기 위해
이 절은 "Phase 4 상세"로 표기한다.

- **변경/추가 파일**
  - 신규(domain): `domain/model/Suggestion.kt`(`SuggestionField` 5종 enum·`Suggestion`·
    `SuggestionGroupOutcome`·`SuggestionFetchResult`·`SuggestionValidation` 순수 검증 함수),
    `domain/repository/SuggestionRepository.kt`, `domain/usecase/ApplySuggestionUseCase.kt`,
    `domain/usecase/FetchSuggestionsUseCase.kt`
  - 신규(data): `data/remote/LlmMarketDataSource.kt`(Anthropic `/v1/messages` + `web_search_20250305`
    호출·그룹①②③·재시도·pause_turn 재개), `data/remote/LlmResponseParsing.kt`(Context 없는 순수
    파싱 함수 — `parseLlmResponse`/`extractJsonObject`/그룹별 DTO 파서/`parseDateOrToday`/
    `formatNumber`), `data/repository/SuggestionRepositoryImpl.kt`(Claude 키/제공자 검증)
  - 신규(presentation): `presentation/ui/SuggestionPanel.kt`("AI 제안 가져오기" 버튼+제안 행+
    STALE 배지+개별/일괄 승인+무시)
  - 수정: `domain/model/BearSignalAutoModels.kt`(`BearIndicatorKey`에 `GATE_CREDIT`/`S3_LOSS_RATIO`/
    `S3_BIG_DEAL` 3종 추가, `AutoBearSignalInputs`에 `credit`/`lossRatio`/`bigDeal` nullable 필드
    추가), `domain/repository/BearSignalRepository.kt`(`applySuggestion` 추가),
    `domain/usecase/MergeBearSignalInputsUseCase.kt`(loss/big/credit이 AUTO 폴백 소비하도록 확장,
    MANUAL〉AUTO〉BASELINE 3단), `data/mapper/BearSignalAutoCacheMapper.kt`(Phase4 3필드 인코딩/
    디코딩 + `suggestionEntity(field, rawValue, updatedAt)` 신규 — 승인 개별 upsert 경로),
    `data/repository/BearSignalRepositoryImpl.kt`(`applySuggestion` 구현),
    `di/BearSignalModule.kt`(`LlmMarketDataSource`/`SuggestionRepository`/
    `FetchSuggestionsUseCase`/`ApplySuggestionUseCase` Hilt 프로바이더 추가),
    `presentation/BearSignalViewModel.kt`(제안 상태 배선 — 아래 결정 사항 참조),
    `presentation/ui/BearSignalScreen.kt`(방아쇠·증폭 카드 아래 제안 패널 섹션 삽입)
  - 테스트 신규: `domain/model/SuggestionValidationTest.kt`(13),
    `domain/usecase/ApplySuggestionUseCaseTest.kt`(4),
    `data/remote/LlmMarketDataSourceTest.kt`(14, MockWebServer),
    `data/repository/SuggestionRepositoryImplTest.kt`(4)
  - 테스트 수정: `domain/usecase/MergeBearSignalInputsUseCaseTest.kt`(+6, 기존 3건 제목의 "AUTO 경로
    없음" 문구를 "AUTO 미설정"으로 정정 — Phase4로 AUTO 경로가 실제로 생겼으므로), `data/mapper/BearSignalAutoCacheMapperTest.kt`(+9),
    `data/repository/BearSignalRepositoryImplTest.kt`(+3), `presentation/BearSignalViewModelTest.kt`(+7)

- **레이어별 요약**
  - **domain**: `Suggestion`(field/current/next/as_of/origin/stale) + `SuggestionField`(각 필드가
    자신의 `BearIndicatorKey`를 직접 보유해 반영 경로를 스키마 레벨에서 고정) + `SuggestionValidation`
    (열거형 화이트리스트·신선도·급변 판정 — 전부 순수 함수, 안드로이드/네트워크 의존성 0).
    `MergeBearSignalInputsUseCase`는 기존 `dir` 필드와 동일한 3단 우선순위 패턴을 loss/big/credit에도
    적용 — 새 코드 대신 기존 검증된 패턴을 재사용해 회귀 위험을 최소화했다.
  - **data**: `LlmMarketDataSource`는 그룹①(rate/dir)·②(bigDeal/lossRatio)·③(credit)을
    `supervisorScope`+`async`+그룹별 `try/catch`로 병렬 격리 수집한다. 각 그룹은 Anthropic
    `/v1/messages`에 `web_search_20250305` 서버 도구를 선언만 하고(클라이언트 측 실행 루프 없음),
    시스템 프롬프트로 "최종 답은 JSON 객체만"을 강제한 뒤 `LlmResponseParsing.kt`의 Context 없는
    순수 함수로 파싱한다. `stop_reason=="pause_turn"`이면 받은 `content` 배열을 그대로 assistant
    메시지로 append해 재요청(최대 3회, 추가 user 메시지 없이). 네트워크는 앱 전역 30s 타임아웃
    OkHttpClient(`KiwoomApiClient.createDefaultClient()`)를 재사용하고, 백오프 1회(`retryBackoffMs`,
    테스트에서만 단축)만 수행한다. 급변 재확인(금리 ±0.5%p·신용잔고 ±30% 초과)은 동일 그룹을 1회
    재호출해 두 결과가 정확히 일치할 때만 채택하고, 재확인 자체가 실패해도(네트워크 오류 등) 보수적으로
    폐기한다. `SuggestionRepositoryImpl`은 `ApiConfigProvider.getAiConfig()`로 Claude 전용 여부(§4.5
    "Anthropic 전용")·키 유효성을 확인해 미충족 시 네트워크 호출 자체를 시도하지 않고
    `ApiError.NoApiKeyError`로 안내한다.
  - **presentation**: `BearSignalViewModel`에 `FetchSuggestionsUseCase`/`ApplySuggestionUseCase`를
    추가 주입하고, 제안 관련 상태(`suggestions`/`suggestionsLoading`/`suggestionGroupErrors`)를
    별도 `SuggestionUiState` 한 묶음의 `MutableStateFlow`로 관리해 `uiState`의 `combine` 인자를
    4개로 유지한다(kotlinx.coroutines `combine`의 타입 지정 오버로드 한도 고려, 기존 `core`/`history`
    패턴과 동일한 설계 이유). `fetchSuggestions()`는 **init에서 호출되지 않고** 오직 `SuggestionPanel`의
    "AI 제안 가져오기" 버튼에서만 트리거된다. `SuggestionPanel`은 STALE 배지(errorContainer 색+"STALE"
    텍스트 병기), 개별 승인/무시 아이콘 버튼(48dp 기본 IconButton 터치 타깃), 제안 2건 이상일 때만
    "전체 승인" 노출.

- **결정 사항**
  1. **반영 경로**: §4.5 웹 수집이 승인되면 기존 `BearSignalAutoCacheEntity`(범용 key-value 자동
     캐시)에 upsert한다. `GATE_RATE`/`GATE_DIR`은 **기존 키를 재사용**(이미 Phase2 FRED/ECOS가
     채우던 것과 동일 키 — LLM 제안이 승인되면 그 값을 덮어쓴다). credit/lossRatio/bigDeal은
     **신규 `BearIndicatorKey`**(`GATE_CREDIT`/`S3_LOSS_RATIO`/`S3_BIG_DEAL`)를 추가했다 —
     기존 `ManualIndicatorKey.CREDIT`/`LOSS`/`BIG`(사용자 직접 입력, 별도 테이블)와 키 공간이
     완전히 분리돼 있어 승인된 LLM 제안이 사용자의 수동 입력을 물리적으로 덮어쓸 수 없다(MANUAL
     불패가 테이블 분리 수준에서부터 구조적으로 보장됨).
  2. **병합 확장**: `MergeBearSignalInputsUseCase`의 `loss`/`big`/`credit`은 원래 AUTO 경로가
     없었으나(Phase3까지 순수 수동), 이번에 `manual?.X?.value ?: auto?.X?.value ?: baseline.X`
     3단 우선순위로 확장했다 — 기존 `dir` 필드가 이미 검증해 온 동일 패턴이라 신규 로직 없이
     재사용, MANUAL 불패 테스트(`MergeBearSignalInputsUseCaseTest`)로 회귀 없음을 확인했다.
  3. **신선도 상수**: `SuggestionField.maxAgeDays`는 §3 `bear_thresholds.json` 임계치가 아니라
     §4.5가 명시한 "허용 연령" UI/수집 파라미터다. rate/dir=45일(미 연준 FOMC 정례회의 주기 약 6주
     + 여유), credit=10일(KOFIA 주간 통계 발표 주기 7일 + 여유), bigDeal/lossRatio=30일(이벤트성
     정성 지표, 뉴스 갱신 빈도가 낮아 더 넉넉히 허용) — 전부 KDoc에 근거 명시, Kotlin enum 제약(생성자
     인자가 자신의 companion object를 참조할 수 없음)으로 top-level private 상수로 선언했다.
  4. **급변 재확인 임계값**(금리 ±0.5%p·신용잔고 ±30%)도 §3 임계치가 아니라 §4.5가 명시한 수집
     파이프라인 파라미터로 분류해 `SuggestionValidation`에 상수로 정의했다(`bear_thresholds.json`
     변경 없음).
  5. **MockWebServer 테스트를 위한 `baseUrl` 주입**: 기존 관세청/FRED/Stooq 클라이언트는 고정
     URL이라 MockWebServer 통합 테스트가 불가능했지만(파싱 fixture 테스트만 존재), §4.5는 명세가
     HTTP 오류·타임아웃·pause_turn 재개 등 프로토콜 레벨 검증을 요구해 `LlmMarketDataSource`에만
     `baseUrl`(프로덕션 기본값 Anthropic API) 생성자 파라미터를 추가했다 — 다른 기존 클라이언트는
     변경하지 않았다(최소 변경 원칙).
  6. **부분 실패 격리 테스트 전략**: `fetchSuggestions()` 오케스트레이션은 3개 그룹이 동시에 같은
     엔드포인트를 호출하므로 FIFO 큐 기반 MockWebServer로는 그룹별 응답을 결정론적으로 배정할 수
     없다. 요청 body에 포함된 그룹별 고유 프롬프트 문구(예: "미국 연방준비제도"/"대어급"/"KOFIA")로
     분기하는 커스텀 `Dispatcher`를 사용해 동시 호출에서도 결정적으로 검증했다.

- **테스트 결과**: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"` —
  **352건, 0 실패**(기존 292건 회귀 통과 — 이전 마커의 295건은 `core.database.migration.*` 3건이
  포함된 합산이라 `bearsignal` 단독 필터 기준 292건이 정확한 베이스라인 + 신규 60건: `SuggestionValidationTest`
  13(열거형·신선도 경계·급변 재확인 임계 경계 0.5/0.30 포함), `ApplySuggestionUseCaseTest` 4(승인
  전 상태 불변·개별/일괄 위임), `LlmMarketDataSourceTest` 14(성공/열거형 위반 필드만 폐기/HTTP
  500 재시도 후 실패/타임아웃 재시도 후 실패/pause_turn 재개+assistant append 검증/금리·신용잔고
  급변 재확인 일치·불일치 각 2쌍/응답 JSON 아님/부분 실패 격리 오케스트레이션), `SuggestionRepositoryImplTest`
  4(키 미설정·Gemini 제공자·정상 위임·예외 매핑), `MergeBearSignalInputsUseCaseTest` +6(loss/big/credit
  AUTO 채택 및 MANUAL 불패 각 2건×3필드), `BearSignalAutoCacheMapperTest` +9(Phase4 3필드 인코딩·
  왕복·구버전 호환 + `suggestionEntity` 5건), `BearSignalRepositoryImplTest` +3(`applySuggestion`
  RATE 기존 키 재사용·CREDIT 신규 키·BIG_DEAL 열거형 인코딩), `BearSignalViewModelTest` +7(init 시
  자동 호출 없음·성공 시 상태 반영·상태 불변(승인 전)·최상위 실패 안내·개별 승인·일괄 승인·무시). 골든
  (2026.6.30→AMBER)·경계(neg 6/7, rate 4.49/4.5, ratio 0.94/0.95/1.0, loss 44/45/59/60/79/80)
  무변경 통과.
- **빌드**: `:app:compileDebugKotlin` BUILD SUCCESSFUL / `:app:testDebugUnitTest --tests
  "com.tinyoscillator.feature.bearsignal.*"` BUILD SUCCESSFUL(352/352) / `:app:assembleDebug`
  BUILD SUCCESSFUL(Hilt 그래프 검증 — `LlmMarketDataSource`→`SuggestionRepository`→
  `FetchSuggestionsUseCase`/`ApplySuggestionUseCase` 프로바이더 체인 및 `BearSignalViewModel` 신규
  2개 생성자 파라미터 정상 해석). Room 스키마·마이그레이션 변경 없음(범용 key-value 캐시 테이블
  재사용, 신규 컬럼 없음 — v37 유지).
- **미해결/차단 요소**: 관세청/FRED와 마찬가지로 §4.5도 실제 Claude API 키로 web_search 응답 형태를
  검증하는 실기 테스트는 미수행(비용·네트워크 필요, qa-verifier/실기 QA 단계 권장). Gemini 지원은
  명시적으로 범위 밖(§4.5 "Anthropic 전용"). 다음 Phase는 `P5-1`(WorkManager 주기 조정·접근성
  최종 마감)이다.

## Phase 3.5 상세 — Sparkline · TransitionLog · 신선도 제안 배선 (2026-07-13)

TASK_bear_signal_console.md §6.1 구현 범위의 잔여분 — P3.5-1 인프라(SnapshotRepository/DetectTransitions/BuildBearSnapshot/EvaluateSnapshotFreshness) 위에 ViewModel 배선과 프레젠테이션(Sparkline+TransitionLog+제안 배너)을 조립. 담당: kotlin-implementer, 게이트 검증: qa-verifier(7항목 전부 PASS).

- **변경/추가 파일**
  - 신규: `presentation/ui/BearSignalSparklineSection.kt`(lead%·gate 2계열 Canvas 스파크라인 + TransitionLog), `presentation/ui/BearSignalUpdateSuggestionBanner.kt`(신선도 갱신 제안 배너)
  - 수정: `domain/model/BearSnapshotModels.kt`(`BearSnapshot.leadPct` 확장 프로퍼티 — `lead/9*100` 구조 상수 재사용, 새 임계치 없음), `presentation/BearSignalViewModel.kt`(스냅샷 배선 전체), `presentation/ui/BearSignalScreen.kt`(헤더 바로 아래 배너→섹션 삽입, §5.2-1)
  - 테스트 수정: `BearSignalViewModelTest.kt`(신규 11건 + 생성자 배선 갱신, 15건→26건)
  - `BearSignalModule.kt`/data 계층/Room 스키마(v37): 변경 없음 — P3.5-1에서 완결된 프로바이더·인프라 그대로 사용

- **레이어별 요약**
  - **domain**: `BearSnapshot.leadPct`만 추가(java.time만 import, 안드로이드 무의존 유지). Room에는 원시 `lead` 저장, 백분율은 표시용 파생.
  - **presentation(ViewModel)**: 생성자에 `SnapshotRepository`/`BuildBearSnapshotUseCase`/`EvaluateSnapshotFreshnessUseCase`/`DetectTransitionsUseCase` 4개 추가(전부 기존 Hilt 프로바이더 재사용, 배선 변경 불요). `uiState`는 기존 4-Flow `core` combine → (core, history, updateSuggestion) 3-way nested combine으로 재구성, `snapshotHistory`/`transitions`/`updateSuggestion` 노출. 스냅샷 저장 시점 2곳: (1) `init{}` 세션 진입 시 1회 — **신선도 평가 → 저장 순서 고정**(역순이면 방금 저장한 오늘자 스냅샷 때문에 제안이 절대 뜨지 않음, KDoc 명시), (2) `refresh()` 온라인 분기 실행 시(지표별 성공/실패 무관 — 각 UseCase가 캐시 폴백을 가져 병합 상태 항상 유효). 이력 조회창 90일(`SPARKLINE_WINDOW_DAYS`, §6.1 "60~90일" 상한 채택).
  - **presentation(UI)**: `BearSignalSparklineSection` — lead%(0~100)·gate(0~3) 각각 Canvas 라인차트, 이력 상태 3종(empty=안내 문구/단일=점/다수=라인) 분기, TransitionLog는 동일 날짜 `PhaseChange`+`GateAdvance`를 `groupBy(asOf)`로 " · " 결합해 §6.1 예시 형식("6/30 GREEN→AMBER · 방아쇠 경계 접근") 그대로 재현(`GateState.entries[n].label` 재사용). `BearSignalUpdateSuggestionBanner` — 기존 `StaleBanner` 시각 문법 참고하되 tertiaryContainer로 "오류 아님, 제안" 구분, **수락 버튼만이 상태를 바꾼다**(`acceptUpdateSuggestion()` → 제안 클리어 + `refresh()` 트리거). 접근성: contentDescription 3곳, 색+텍스트 병기, 차트 높이 48dp, M3 TextButton 터치 타깃.

- **결정 사항**
  1. **저장 시점**: 세션 진입 1회 + refresh 시 — "일 단위 upsert"(§6.1)이므로 같은 날 중복 호출은 `day` PK 덮어쓰기로 멱등. `BearSignalEntryCard`(시장분석 탭 미리보기)가 별도 `hiltViewModel()` 인스턴스를 만들어 진입 시 저장이 중복 발생하나 동일 근거로 무해(기존 ManualInputViewModel 인스턴스 패턴과 동일 성격, 별도 조치 없음).
  2. **승인 원칙(§7)**: `updateSuggestion` 쓰기 경로는 init 세팅 1곳 + `acceptUpdateSuggestion()` 클리어 1곳뿐(qa-verifier 전수 추적 확인). `EvaluateSnapshotFreshnessUseCase`는 순수 비교 함수라 Room 무변경. 제안 존재만으로는 result/inputs 불변 + refresh 미호출을 테스트로 단언.
  3. qa-verifier 참고 노트(결함 아님): 승인 직후 오프라인이면 배너는 닫히고 갱신은 스킵 — 승인 없는 상태 변경이 아니라 승인 후 갱신 실패 UX이며 다음 세션 진입 시 재평가로 복원. 허용 범위로 기록만.

- **테스트 결과**: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*" --tests "com.tinyoscillator.core.database.migration.*"` — **295건, 0 실패**(기존 284건 회귀 통과 + 신규 11건: 저장 시점 3건, 이력 3종 3건, 신선도 제안·승인 흐름 5건). 골든(2026.6.30→AMBER)·경계(neg 6/7, rate 4.49/4.5, ratio 0.94/0.95/1.0, loss 44/45/59/60/79/80) 무변경 통과. qa-verifier가 `--rerun` 강제 재실행으로 XML 집계 독립 재현(tests=295 failures=0).
- **빌드**: `:app:compileDebugKotlin` / `:app:assembleDebug` BUILD SUCCESSFUL(Hilt 그래프 검증 — ViewModel 신규 4개 생성자 파라미터 정상 해석). Room v37 유지(스키마·마이그레이션 변경 없음).
- **§3 임계치 스위프**: 신규 파일+수정분에 `bear_thresholds.json` 필드값 리터럴 비교 0건(qa-verifier 확인). Sparkline `100f`/`3f`는 leadPct 스케일·gate 레벨 구조 상수, `SPARKLINE_WINDOW_DAYS=90`은 §6.1 UI 파라미터 — 예외 범주.
- **미해결/차단 요소**: 없음. 신규 섹션(스파크라인·배너)의 360dp/다크/폰트 1.3x 실기 렌더 검증은 기존 QA 항목과 함께 P5-2로 승계. 다음 Phase는 `P4`(§4.5 웹/LLM 갱신+승인 흐름).

## Phase 3.5-1 상세 — Room 스냅샷 이력 영속 + 국면·방아쇠 전이 감지 (2026-07-13)

TASK_bear_signal_console.md §6.1 구현 범위 중 "Room 스냅샷 이력 영속 + 전이 감지" 인프라(P3.5-1).
Sparkline/TransitionLog(ViewModel/UI 조립)는 후속 `P3.5` 마커로 이연 — PROGRESS.md 재편성 테이블의
Phase 3.5 행이 P3.5-1/P3.5 두 마커로 나뉘어 있는 것과 동일한 분리.

- **변경/추가 파일**
  - 신규(domain): `domain/model/BearSnapshotModels.kt`(`BearSnapshot`, `TransitionKind`/`PhaseChange`/`GateAdvance`/`Transition`, `ValueSource`, `FieldSource`, `SnapshotUpdateSuggestion`), `domain/model/BearSnapshotPayload.kt`(`SnapshotInputsPayload`/`SnapshotMarketEntry`/`SnapshotFieldMetaEntry` — §4.6 JSON 스키마 DTO), `domain/repository/SnapshotRepository.kt`, `domain/usecase/DetectTransitionsUseCase.kt`, `domain/usecase/BuildBearSnapshotUseCase.kt`, `domain/usecase/EvaluateSnapshotFreshnessUseCase.kt`
  - 신규(data): `data/local/BearSnapshotEntity.kt`, `data/local/BearSnapshotDao.kt`, `data/mapper/BearSnapshotMapper.kt`, `data/repository/SnapshotRepositoryImpl.kt`
  - 수정: `domain/model/BearSignalReportBaseline.kt`(`CONFIG_BASIS = "신영 2026.6.30"` 상수 추가), `di/BearSignalModule.kt`(`SnapshotRepository`/`DetectTransitionsUseCase`/`BuildBearSnapshotUseCase`/`EvaluateSnapshotFreshnessUseCase` Hilt 프로바이더 추가), `core/database/AppDatabase.kt`(`BearSnapshotEntity`/`BearSnapshotDao` 추가, v36→v37), `core/database/migration/AppDatabaseMigrations.kt`(`MIGRATION_36_37` 추가), `core/di/DaoModule.kt`(`provideBearSnapshotDao` 추가)
  - 테스트 신규: `domain/usecase/DetectTransitionsUseCaseTest.kt`(10), `domain/usecase/BuildBearSnapshotUseCaseTest.kt`(3), `domain/usecase/EvaluateSnapshotFreshnessUseCaseTest.kt`(5), `data/mapper/BearSnapshotMapperTest.kt`(3), `data/local/BearSnapshotDaoInMemoryTest.kt`(6), `data/repository/SnapshotRepositoryImplTest.kt`(6), `core/database/migration/Migration36To37Test.kt`(3)

- **레이어별 요약**
  - **domain**: `BearSnapshot`은 §6.1 코드 블록의 Entity와 1:1 필드(day/phase/lead/gate/s1/s2/s3/amp/configBasis/inputsJson/fieldMetaJson/createdAt)를 그대로 가진 도메인 표현. `DetectTransitionsUseCase`는 §6.1 의사코드를 문자 그대로 구현(국면 변화는 방향 무관, gate는 **상승**만 기록). `BuildBearSnapshotUseCase`는 `ObserveBearSignalStateUseCase.State`(inputs/result/auto/manual)를 §4.6 `inputs`/`field_meta` 서브 스키마로 직렬화한다 — `field_meta`는 `MergeBearSignalInputsUseCase`의 실제 병합 우선순위(MANUAL 〉 AUTO 〉 BASELINE)를 병합 로직 재구현 없이 `auto`/`manual` 원본에서 역산한다. `EvaluateSnapshotFreshnessUseCase`는 최신 스냅샷 `day`가 "오늘"보다 이전일 때만 `SnapshotUpdateSuggestion`을 반환하는 순수 비교 함수 — 어떤 Room 캐시도 갱신하지 않는다(승인 원칙).
  - **data**: `BearSnapshotEntity`/`BearSnapshotDao`는 §6.1 코드 블록을 그대로 구현(`@Upsert`, `day` PK, `observeLatest`/`observeRange`/`latest`). `SnapshotRepositoryImpl`은 `BearSnapshotMapper`로 Entity↔domain 변환 후 Dao에 위임하는 얇은 어댑터.
  - **presentation**: 이번 Phase는 미변경(ViewModel/UI 조립은 `P3.5`로 이연 — 하드 게이트가 domain/data 계층 테스트만 요구하고, Sparkline/TransitionLog는 명세상 별도 마커이므로 이번 세션은 인프라만 완결해 블라스트 반경을 좁혔다).

- **결정 사항**
  1. **`ValueSource.SNAPSHOT` 미사용**: §4.6 예시는 `s2_up`에 `"source": "SNAPSHOT"`을 쓰지만, 현재 도메인의 `AutoIndicator.source`는 AUTO/MANUAL 2종만 구분해 "이 앱이 오늘 직접 수집" vs "외부 API의 오래된 값"을 구분할 근거가 없다. `ValueSource` enum 자체는 4종(§4.6 그대로) 정의하되, `BuildBearSnapshotUseCase`는 AUTO/MANUAL/BASELINE 3종만 산출하도록 범위를 좁히고 KDoc에 근거를 명시했다(`SNAPSHOT` 세분화는 §4.5 웹/LLM tier, Phase 4에서 재검토).
  2. **`origin` 값은 필드별 정적 상수**: 인스턴스별 실제 티커/엔드포인트를 전부 추적하려면 수집 파이프라인 전반의 재배선이 필요해 범위를 벗어난다. 필드별로 고정된 파이프라인 식별 문자열(예: `s2_up`→`kotlin_krx:KS11`, `s4_rate`→`FRED:DFEDTARU`, MANUAL→`user`, BASELINE→`report_baseline`)을 사용했다 — §4.6 스키마 *구조*(키·값 형식)는 그대로 유지하면서 값의 정밀도만 낮춘 의도적 절충.
  3. **마이그레이션 테스트는 `MigrationTestHelper` 대신 raw `SupportSQLiteDatabase` 직접 호출**: 프로젝트에 Room 스키마 asset 배선(`sourceSets.test.assets`) 선례가 없어, 신규 배선 리스크를 피하고자 `AppDatabaseMigrations.ALL`에서 `startVersion==36 && endVersion==37`로 실제 프로덕션 `Migration` 객체를 찾아 raw DB에 직접 `.migrate(db)` 호출하는 방식을 채택(`MIGRATION_36_37`은 파일-private이라 이름으로 직접 참조 불가 — 배열 필터링 필수). 기존 테이블(`bear_signal_auto_cache`) 데이터 보존 + 신규 테이블 컬럼 정확성(스키마 export `37.json`의 `createSql`과 문자 단위 일치 확인 완료)을 결정적으로 검증한다.
  4. **세션 진입 "state:latest 저장/로드" 범위**: `SnapshotRepository.upsertToday`(저장)/`observeLatest`·`latestOrNull`(로드) + `EvaluateSnapshotFreshnessUseCase`(신선도 "제안")까지 domain/data 계층에 완결했다. `BearSignalViewModel`이 실제로 `upsertToday`를 호출(예: refresh 성공 시 오늘자 스냅샷 저장)하고 `EvaluateSnapshotFreshnessUseCase` 결과를 `BearSignalUiState`에 노출하는 배선은 **P3.5**(Sparkline/TransitionLog 조립)로 이연했다 — 근거: (a) TASK.md §6.2 마커 순서가 `P3.5-1 → P3.5`로 명시적으로 분리돼 있고 §6.1 상세의 "구현 범위" 마지막 항목("프레젠테이션: Sparkline+TransitionLog")이 P3.5 몫으로 별도 기술됨, (b) 이번 세션의 하드 게이트(Room in-memory/DetectTransitionsUseCase/마이그레이션 테스트)가 domain/data만 요구, (c) `combine()` 4-Flow에 5번째 Flow를 추가하는 ViewModel 변경은 최소 변경 원칙에 부합하지 않아 P3.5에서 Sparkline 데이터 흐름과 함께 한 번에 배선하는 것이 안전.

- **테스트 결과**: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*" --tests "com.tinyoscillator.core.database.migration.*"` — **284건, 0 실패**(기존 248건 회귀 통과 + 신규 36건: `DetectTransitionsUseCaseTest` 10 — 국면 변화/gate 상승/무변화·동시발생·양방향 국면전이·다중스냅샷 경계 포함, `BuildBearSnapshotUseCaseTest` 3 — 골든케이스(전 필드 BASELINE, AMBER 재현) + AUTO 필드 채움 + MANUAL이 AUTO보다 우선(dir 3단), `EvaluateSnapshotFreshnessUseCaseTest` 5 — 이력없음/오늘과 동일/오래됨/경계값(하루 차이)/미래(방어), `BearSnapshotMapperTest` 3 — 왕복 변환, `BearSnapshotDaoInMemoryTest` 6 — 동일 day upsert 덮어쓰기·observeRange 오름차순·양끝 경계 포함·latest, `SnapshotRepositoryImplTest` 6 — 위임·매핑·null 처리, `Migration36To37Test` 3 — 기존 데이터 보존·신규 테이블 컬럼 정확성·PK 유일성).
- **빌드**: `:app:compileDebugKotlin` BUILD SUCCESSFUL / `:app:compileDebugUnitTestKotlin` BUILD SUCCESSFUL / `:app:testDebugUnitTest`(위 필터, 284/284) BUILD SUCCESSFUL / `:app:assembleDebug` BUILD SUCCESSFUL(Hilt 그래프 검증 겸용 — `SnapshotRepository`→`SnapshotRepositoryImpl`, `BearSnapshotDao` 프로바이더 체인 정상 해석). 스키마 `app/schemas/.../37.json` 자동 export 확인 — `bear_snapshot` `createSql`이 수기 마이그레이션 SQL과 문자 단위 일치.
- **미해결/차단 요소**: 없음. `BearSignalViewModel`/`BearSignalScreen`에 스냅샷 저장·신선도 제안 배선 + Sparkline/TransitionLog Canvas 컴포넌트는 `P3.5`에서 착수(위 결정 사항 4 참조). 그 다음은 `P4`(§4.5 웹/LLM 갱신+승인 흐름).

## P0 상세
- `feature/bearsignal/domain/model/BearSignalModels.kt` — SignalLevel·GateState·BearPhase·Depth·InputSource·MarketReturns·MarketAnalysis·BearSignalInputs·BearSignalResult + 플레이스홀더(BearType·MonitorItem)
- `feature/bearsignal/domain/model/BearSignalReportBaseline.kt` — 2026.6.30 리포트 기준값 스칼라 + 도표48 전체 20지수 시드(`MARKETS`, 프로토타입 jsx `MARKETS` 그대로 이관)
- `feature/bearsignal/domain/repository/BearSignalRepository.kt` — Phase 1+ 확장용 마커 인터페이스
- `feature/bearsignal/domain/usecase/ComputeBearSignalUseCase.kt` — §3/부록 A 1:1 순수 스코어링(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite), 안드로이드 의존성 0
- 테스트: `ComputeBearSignalUseCaseTest.kt` 41건 전부 통과 (골든 케이스 2026.6.30 → AMBER 재현, 도표48 실데이터 20지수 사용 + 전 임계 경계)

## P1 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalAutoModels.kt` — `AutoIndicator<T>`(값+source+updatedAt), `BearIndicatorKey` enum(캐시 키, Phase2+ 재사용 전제), `AutoBearSignalInputs`(up3/down3/up4/down4/kospi2)
  - `feature/bearsignal/domain/usecase/VolatilityStatsCalculator.kt` — §3.2 신호2 입력(±3σ/±4σ 카운트) 순수 계산. jsx 프로토타입에 σ 계산 로직이 없음을 확인(Stepper 수동입력 상수만 존재) → **단순수익률**(로그수익률 아님) + **표본표준편차(ddof=1)** 채택, KDoc에 근거 명시. 경계는 `strict >`(정확히 σ 도달은 미카운트)로 고정, `countBreaches`를 `internal`로 분리해 자기참조 없는 경계 테스트 가능하게 함
  - `feature/bearsignal/domain/usecase/Kospi2Calculator.kt` — §3.5 kospi2(삼성전자+SK하이닉스 시총/코스피 전체 시총×100) 순수 계산, `Map<String, Long>` 입력(kotlin_krx 타입 비의존)
  - `feature/bearsignal/domain/usecase/RefreshAutoInputsUseCase.kt` — repository.refreshAutoInputs() 위임 얇은 진입점
  - `feature/bearsignal/domain/repository/BearSignalRepository.kt` — Phase 0 마커 인터페이스 확장(`observeAutoInputs`/`getCachedAutoInputs`/`refreshAutoInputs`)
- **data**
  - `feature/bearsignal/data/local/BearSignalAutoCacheEntity.kt` + `BearSignalDao.kt` — 범용 key-value 캐시(지표키·값·source·updatedAt), Phase2+ [B] 지표도 키 추가만으로 재사용
  - `feature/bearsignal/data/mapper/BearSignalAutoCacheMapper.kt` — Entity↔AutoBearSignalInputs 변환, 필수 5키 중 하나라도 없으면 null
  - `feature/bearsignal/data/repository/BearSignalRepositoryImpl.kt` — KRX 로그인(ApiConfigProvider 자격증명) → `KrxIndex.getKospi("1001")` 130영업일 종가(200일 버퍼) → `VolatilityStatsCalculator` → `KrxStock.getMarketCap(KOSPI)` → `Kospi2Calculator` → Room upsert. 계정 미설정/로그인 실패/데이터 부족/예외 시 기존 캐시로 폴백(`Result.success`), 캐시도 없으면 `Result.failure`. 기존 `FearGreedRepository` 패턴(로그인→조회→계산→저장→finally close) 준수
  - `feature/bearsignal/di/BearSignalModule.kt` — Hilt `@Module` (`BearSignalRepository`, `RefreshAutoInputsUseCase` 제공)
- **Room**: `AppDatabase` v33→v34(`MIGRATION_33_34`, `bear_signal_auto_cache` 테이블 신규), `AppDatabaseMigrations.ALL`/`DaoModule`/`core/di/DaoModule` 갱신. 스키마 `app/schemas/.../34.json` 자동 export 확인 — Room 생성 SQL이 수기 마이그레이션 SQL과 문자 단위 일치.
- **테스트**: 신규 29건(총 70건, 0 실패) — `VolatilityStatsCalculatorTest`(Fixture A/B 골든값 Python 사전검증, MIN_RETURNS 경계, 표준편차 0 경계, 정확히 3σ/4σ 미카운트 경계, up=0 케이스), `Kospi2CalculatorTest`(합산·백분율·누락·0시총 경계), `BearSignalAutoCacheMapperTest`(왕복 변환, 키 누락, source 파싱 폴백), `BearSignalRepositoryImplTest`(MockK — 정상 수집, 로그인 실패/계정 미설정/데이터 부족 시 캐시 폴백, 폴백 캐시도 없을 때 failure), `RefreshAutoInputsUseCaseTest`(위임 검증). 기존 41건 회귀 통과.
- **assembleDebug**: BUILD SUCCESSFUL (Room v34 마이그레이션·Hilt 그래프 검증 겸용).

## P2 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalAutoModels.kt` — `BearIndicatorKey`에 [B] 등급 5키 추가(`AMP_SEMI`/`AMP_BUFFER`/`GATE_RATE`/`GATE_DIR`/`S3_ETF`). `AutoBearSignalInputs`에 `semi`/`buffer`/`rate`/`dir`/`etf`(전부 nullable, 구버전 5키 캐시 하위 호환) 추가
  - `feature/bearsignal/domain/model/MarketReturnsSnapshot.kt` — `MarketCoverage`(AUTO/MANUAL_REQUIRED), `AutoMarketReturn`, `MarketReturnsSnapshot`(도표48 20지수 스냅샷 + `manualRequiredNames`)
  - `feature/bearsignal/domain/model/GlobalIndexRegistry.kt` — 도표48 19개 해외지수(코스피 제외) ↔ Stooq 티커 매핑표. 신뢰도 높은 6개(다우·S&P·나스닥·DAX·닛케이·항생)만 AUTO, 나머지 13개(대만·CAC40·호주·유로·FTSE·태국·베트남·상하이·인도·멕시코·브라질·인니·RTS)는 MANUAL_REQUIRED
  - `feature/bearsignal/domain/model/CustomsTradeModels.kt` — 관세청 `getNitemtradeList` 파싱 결과 모델(`CustomsTradeItem`)
  - `feature/bearsignal/domain/usecase/CustomsTradeCalculator.kt` — §3.5 `semi`(15대 품목 합계 대비 반도체 비중 근사치, 총수출 미제공 한계 KDoc 명시) · `buffer`(완충 3산업 YoY 증감률 ≥ −20% 이면 건재, 구현 결정 임계값) 순수 계산
  - `feature/bearsignal/domain/usecase/RateGateInputCalculator.kt` — §3.4 `dir`(ECOS 기준금리 최신·직전 비교 hike/ease/hold) 순수 계산. `rate`는 FRED 값을 그대로 사용(근거: 리포트 기준값 3.75%가 미 연준 금리대와 정합)
  - `feature/bearsignal/domain/usecase/IpoEtfDirectionCalculator.kt` — §3.3 `etf`(최근 60거래일 고점 대비 괴리율로 up/flat/down, jsx에 계산 로직 부재 확인 후 본 구현이 결정) 순수 계산
  - `feature/bearsignal/domain/usecase/GlobalIndexReturnCalculator.kt` — §3.1 4기간 수익률(거래일 근사 252/126/63/21) 순수 계산
  - `feature/bearsignal/domain/usecase/RefreshExternalAutoInputsUseCase.kt` / `RefreshMarketReturnsUseCase.kt` — Phase 2 얇은 진입점(Phase 1 `RefreshAutoInputsUseCase`와 별도 경로, 상호 실패 격리)
- **data**
  - `feature/bearsignal/data/remote/CustomsTradeApiClient.kt` — 관세청 무역통계 Open API(`apis.data.go.kr/1220000/nitemtrade/getNitemtradeList`), 공공데이터포털 표준 응답 래퍼 JSON 파싱(단건/배열 방어)
  - `feature/bearsignal/data/remote/FredApiClient.kt` — FRED `series/observations`(기본 `DFEDTARU`), 결측치(`"."`) 스킵 후 최신 유효값 반환
  - `feature/bearsignal/data/remote/StooqCsvClient.kt` — Stooq 무료 CSV(`q/d/l`, 인증키 불필요), IPO ETF·해외지수 공용
  - `feature/bearsignal/data/local/BearSignalCountryReturnEntity.kt` + `BearSignalDao.kt` 확장 — 국가별 4기간 수익률 전용 테이블(범용 스칼라 캐시와 분리)
  - `feature/bearsignal/data/mapper/BearSignalAutoCacheMapper.kt` — Boolean/String 지표를 Double로 인코딩(buffer: 1.0/0.0, dir: −1/0/1, etf: −1/0/1), 기존 5키 우선순위·null 안전성 유지
  - `feature/bearsignal/data/mapper/BearSignalCountryReturnMapper.kt` — `MarketReturnsSnapshot` ↔ Entity 변환
  - `feature/bearsignal/data/repository/BearSignalRepositoryImpl.kt` — `refreshExternalAutoInputs()`(관세청/FRED/ECOS/Stooq 지표별 best-effort, 개별 실패는 해당 지표만 이전 캐시 유지) · `refreshMarketReturns()`(코스피 KRX 재사용 + Stooq 커버 6지수, 실패 지수만 캐시 폴백, 미커버 13지수는 MANUAL_REQUIRED로 표시)
  - `feature/bearsignal/di/BearSignalModule.kt` — 신규 API 클라이언트 3종 + UseCase 2종 Hilt 프로바이더 추가
  - `core/config/ApiConfigProvider.kt` + `presentation/settings/SettingsPreferences.kt`/`ApiKeySettingsSection.kt`/`SettingsScreen.kt` — 관세청·FRED API 키를 기존 KIS/DART/ECOS 키 관리 패턴(EncryptedSharedPreferences, masked, 로깅 금지)으로 저장·설정 UI(API 탭) 추가
- **Room**: `AppDatabase` v34→v35(`MIGRATION_34_35`, `bear_signal_country_return` 테이블 신규). 스칼라 5지표는 마이그레이션 없이 기존 `bear_signal_auto_cache`(v34) 재사용(P1 설계 의도대로). 스키마 `app/schemas/.../35.json` 자동 export 확인.
- **테스트**: 신규 89건(총 159건, 0 실패) — 순수 계산기(`CustomsTradeCalculatorTest` 11, `RateGateInputCalculatorTest` 4, `IpoEtfDirectionCalculatorTest` 10, `GlobalIndexReturnCalculatorTest` 7, 전부 경계값 포함), API 클라이언트 fixture 파싱(`CustomsTradeApiClientTest` 7, `FredApiClientTest` 6, `StooqCsvClientTest` 7 — 실 응답 형태 JSON/CSV 기반), 매퍼(`BearSignalAutoCacheMapperTest` +9, `BearSignalCountryReturnMapperTest` 7), 레지스트리(`GlobalIndexRegistryTest` 6), UseCase 위임(`RefreshExternalAutoInputsUseCaseTest`/`RefreshMarketReturnsUseCaseTest` 각 1), 리포지토리(`BearSignalRepositoryImplTest` +13 — 지표별 best-effort 폴백·Phase1 캐시 보존·전체 실패 시나리오). 기존 70건 회귀 통과.
- **빌드**: `:app:compileDebugKotlin`/`:app:compileDebugUnitTestKotlin`/`:app:assembleDebug` 전부 BUILD SUCCESSFUL.
- **수동 폴백(MANUAL_REQUIRED) 처리 지수**: 대만, CAC40, 호주, 유로, FTSE, 태국, 베트남, 상하이, 인도, 멕시코, 브라질, 인니, RTS (13개) — Stooq 티커 신뢰도·RTS 접근성 이슈로 v1은 수동 입력 폴백(§1.1 각주1). v2에서 실기 검증 후 확장 검토.

## P3 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalManualModels.kt` — `ManualIndicatorKey`(LOSS/BIG/ISSUE_RATIO/CREDIT/MARGIN/DIR, `BearIndicatorKey`와 별도 키 공간), `IpoBigConsumption`(smooth/pending/failed 상수+VALID), `ManualBearSignalInputs`(6필드, 전부 `AutoIndicator<T>?`), `ManualMarketReturn`(국가별 수동 오버라이드, 기간별 null 허용), `ManualFieldUpdate` sealed interface(BottomSheet → UseCase 갱신 요청 페이로드, MarketReturn 포함)
  - `feature/bearsignal/domain/usecase/MergeBearSignalInputsUseCase.kt` — **auto⊕manual 병합 핵심 로직**(§1.2). 필드별 우선순위 MANUAL > AUTO > 리포트 기준값(`BearSignalReportBaseline`). `dir`만 AUTO(P2 ECOS)·MANUAL 양쪽 경로가 있어 실제로 MANUAL 우선 규칙이 검증되는 필드. 국가별 수익률은 지수×기간 단위로 동일 우선순위 적용(`mergeMarkets`, 기간별 부분 오버라이드 지원). `issueRatio`(신주비중)는 §3 스코어링 파라미터가 아니므로 조립 대상에서 명시적으로 제외. 순수 함수, 안드로이드 의존성 0
  - `feature/bearsignal/domain/usecase/UpdateManualInputUseCase.kt` — `ManualFieldUpdate` 검증(big/dir 허용값, MarketReturn 4기간) 후 repository 위임
  - `feature/bearsignal/domain/usecase/ResetToReportBaselineUseCase.kt` — repository.resetToReportBaseline() 위임. **범위 결정**: 수동 오버라이드만 삭제하고 [A]/[B] 자동 수집 캐시는 보존(최소 부작용 원칙, KDoc에 근거 명시) — `loss`/`big`/`credit`/`margin`/미커버 해외지수는 AUTO 경로가 아예 없으므로 이 리셋만으로 리포트 기준값과 완전히 일치, `dir`처럼 AUTO 경로가 있는 필드는 리셋 후 최신 자동 수집값이 다시 노출됨(의도된 동작)
  - `feature/bearsignal/domain/repository/BearSignalRepository.kt` — `observeManualInputs`/`getManualInputs`/`updateManualInput`/`observeManualMarketReturns`/`getManualMarketReturns`/`resetToReportBaseline` 추가
- **data**
  - `feature/bearsignal/data/local/BearSignalManualInputEntity.kt` + `BearSignalManualCountryReturnEntity.kt` — 자동 캐시(`bear_signal_auto_cache`/`bear_signal_country_return`)와 분리된 전용 테이블. 자동 갱신이 매번 지표 행을 덮어쓰므로 같은 테이블에 수동값을 두면 유실되기 때문(별도 테이블 필수). `source` 컬럼 없음(테이블 존재 자체가 MANUAL 의미)
  - `feature/bearsignal/data/mapper/BearSignalManualInputMapper.kt` — margin(Boolean)/big(smooth=0/pending=1/failed=2)/dir(ease=-1/hold=0/hike=1, `BearSignalAutoCacheMapper`와 동일 코드값) Double 인코딩, 키 누락 시 필드별 null
  - `feature/bearsignal/data/mapper/BearSignalManualCountryReturnMapper.kt` — `ManualMarketReturn` ↔ Entity 변환
  - `feature/bearsignal/data/repository/BearSignalRepositoryImpl.kt` — `updateManualInput`(6개 `ManualFieldUpdate` 하위타입 → 인코딩 후 upsert) · `resetToReportBaseline`(`clearManualInputs`+`clearManualCountryReturns`, 자동 캐시 테이블 미터치)
  - `feature/bearsignal/data/local/BearSignalDao.kt` — 수동 오버라이드 CRUD 8메서드 추가(observe/get/upsert/clear ×2테이블)
  - `feature/bearsignal/di/BearSignalModule.kt` — `UpdateManualInputUseCase`/`ResetToReportBaselineUseCase`/`MergeBearSignalInputsUseCase` Hilt 프로바이더 추가
- **presentation** (구현 항목 3 — "입력→상태 반영·병합·persistence" 중심, 전체 화면 조립은 Phase 4)
  - `feature/bearsignal/presentation/ManualInputViewModel.kt` — `@HiltViewModel`, auto/manual/marketsSnapshot/manualMarkets 4-Flow `combine` → `MergeBearSignalInputsUseCase`로 즉시 병합 미리보기(`ManualInputUiState`), 필드별 update 함수 + `reset()`
  - `feature/bearsignal/presentation/ui/ManualInputBottomSheet.kt` — Material3 `ModalBottomSheet` + Slider(loss/issueRatio) + SegmentedButton(big/dir) + Stepper(credit, +/- IconButton) + Switch(margin) + 리셋 버튼. 헤더·카드·표 등 전체 화면 조립은 Phase 4로 이연
- **Room**: `AppDatabase` v35→v36(`MIGRATION_35_36`, `bear_signal_manual_input`+`bear_signal_manual_country_return` 테이블 신규). 스키마 `app/schemas/.../36.json` 자동 export 확인 — Room 생성 `createSql`이 수기 마이그레이션 SQL과 문자 단위 일치.
- **테스트**: 신규 46건(총 205건, 0 실패) — `MergeBearSignalInputsUseCaseTest` 16(골든 케이스 재현: AUTO/MANUAL 전무 → `BearSignalReportBaseline.toInputs()`와 완전 동일 + AMBER 재현, `dir` MANUAL>AUTO>기준값 3단 우선순위, 국가별 수익률 지수×기간 단위 병합 및 부분 오버라이드), `UpdateManualInputUseCaseTest` 8(big/dir 유효값·잘못된 값 예외, MarketReturn 기간 수 검증), `ResetToReportBaselineUseCaseTest` 1, `BearSignalManualInputMapperTest` 8(인코딩 왕복, 키 누락 시 null), `BearSignalManualCountryReturnMapperTest` 3, `BearSignalRepositoryImplTest` +12(6개 `ManualFieldUpdate` 타입별 upsert 검증, get/observe 매핑, 리셋 시 수동 테이블만 삭제·자동 캐시 미터치 검증). 기존 159건 회귀 통과.
- **빌드**: `:app:compileDebugKotlin`/`:app:compileDebugUnitTestKotlin`/`:app:assembleDebug` 전부 BUILD SUCCESSFUL. Compose `ModalBottomSheet`/`SegmentedButton` 실험적 API `@OptIn(ExperimentalMaterial3Api::class)` 필요(private 헬퍼 컴포저블에도 개별 부여).
- **범위 참고**: TASK.md 사용자 보충 지시가 나열한 "정책 방향"은 §1.1 매트릭스상 [A] 등급(ECOS 자동, Phase 2 완료)이지만 §4 폴백 열에 "수동"이 명시돼 있어 Phase 3에서 MANUAL 오버라이드 경로를 추가했다(`ManualIndicatorKey.DIR`) — MANUAL 우선 규칙이 실제로 auto와 충돌하는 유일한 필드.

## P4 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalModels.kt` — `BearType`을 프로토타입 `TYPES` 구조에 맞춰 확장(index/title/axis/recoveryLabel/recoveryOutlook/theory/cases/why/monitor), `RecoveryOutlook`(LOWEST/MEDIUM/PATIENCE) enum 신규
  - `feature/bearsignal/domain/model/BearSignalStaticContent.kt` — 약세장 3유형(TYPES, 프로토타입 1:1) + 활성 방아쇠 인덱스(유형3) + 역사 검증 3대 모니터링 지표 + 지표↔리포트 매핑 + 면책 문구, 전부 정적 데이터(§3 SSOT 무관)
  - `feature/bearsignal/domain/usecase/ObserveBearSignalStateUseCase.kt` — TASK.md §2 아키텍처가 명시한 화면 조립 UseCase 신규 구현. Room 4-Flow(auto/manual/marketsSnapshot/manualMarkets) + 기간 선택 Flow를 `combine`해 `MergeBearSignalInputsUseCase` → `ComputeBearSignalUseCase`로 이어지는 파이프라인을 하나의 `State`로 합성. 안드로이드 의존성 0
  - `feature/bearsignal/di/BearSignalModule.kt` — `ComputeBearSignalUseCase`/`ObserveBearSignalStateUseCase` Hilt 프로바이더 추가
- **presentation**
  - `feature/bearsignal/presentation/BearSignalViewModel.kt` — `@HiltViewModel`. `ObserveBearSignalStateUseCase(periodIdx)` + `isRefreshing` + `errorMessage` 3-Flow `combine` → `BearSignalUiState`(`stateIn(WhileSubscribed(5_000))`). `refresh()`(Phase1/2 자동 UseCase 3종 순차 호출, 지표별 실패 메시지 합성) · `selectPeriod()`(§5.3 FilterChip) · `updateMarketReturn()`/`updateLoss()` 등(Phase3 `UpdateManualInputUseCase` 위임) · `reset()`(Phase3 `ResetToReportBaselineUseCase` 위임) · `lastUpdatedAt`(자동+수동 전 지표 `updatedAt` 중 최댓값, §5.2 섹션7)
  - `feature/bearsignal/presentation/ui/BearSignalScreen.kt` — `LazyColumn` 7섹션 조립(헤더/선행신호3카드/국가별수익률표/방아쇠·증폭/3유형/역사검증/푸터), TopAppBar(뒤로·새로고침·리셋), `PullToRefreshContainer`(material3 1.2.0 API, `rememberPullToRefreshState`+`nestedScroll`), 리셋 확인 다이얼로그, Phase3 `ManualInputBottomSheet` 재사용(별도 `ManualInputViewModel` 인스턴스 — 동일 Room 소스라 자동 동기화)
  - `feature/bearsignal/presentation/ui/BearSignalHeaderSection.kt` — 섹션1: 신호등+국면 라벨+선행점수 게이지(0~100)+금리방아쇠/증폭/경고신호 readout+레이더+해설문구
  - `feature/bearsignal/presentation/ui/BearSignalLeadingSignalsSection.kt` — 섹션2: 신호1/2/3 카드(4단 게이지+레벨칩+readout). 신호1은 §섹션3 표에서, 신호3(loss/big)만 실제 MANUAL 오버라이드 경로가 있어 "수동 입력" 버튼 노출 — 신호2(±3σ)는 [A] 완전자동이라 버튼 없음(§1.1 근거, 상세는 "범위 참고" 항목)
  - `feature/bearsignal/presentation/ui/BearSignalCountryTableSection.kt` — 섹션3: 국가=행(20)×기간=열(4) 전치 표, FilterChip 기간 선택 + 이탈수 요약 칩, 행 탭 → 4기간 편집 다이얼로그(20×4 텍스트필드를 LazyColumn에 직접 배치하지 않고 행 단위 다이얼로그로 대체 — 포커스/성능 리스크 회피)
  - `feature/bearsignal/presentation/ui/BearSignalGateAmpSection.kt` — 섹션4: 금리 GATE 카드(4상태 게이지+수동입력 버튼) + 증폭 계수 카드(읽기전용, semi/kospi2/buffer는 MANUAL 경로 없음)
  - `feature/bearsignal/presentation/ui/BearSignalTypesHistorySection.kt` — 섹션5(3유형 카드+로컬 체크박스 모니터링 체크리스트+유형3 활성 하이라이트)+섹션6(일본 3충격 역사 검증+3대 지표)
  - `feature/bearsignal/presentation/ui/BearSignalFooterSection.kt` — 섹션7: 지표↔리포트 매핑+면책+`LastUpdatedText`(기존 컴포넌트 재사용)
  - `feature/bearsignal/presentation/ui/BearSignalGraphics.kt` — Canvas 커스텀 그래픽: `TrafficLightColumn`(4구 신호등), `SignalGauge`(4단 세그먼트+라벨, Layout 기반), `BearSignalRadar`(4축 미니 레이더, `TextMeasurer`로 축 라벨 렌더)
  - `feature/bearsignal/presentation/ui/BearSignalColors.kt` — LEVEL 색 매핑(안전=primary·주의=secondary·경고=오렌지 보강 상수·위험=error, 다크/라이트 대응) + `PhaseMeta`(국면별 라벨·해설, 프로토타입 `PHASE_META` 1:1) + `SourceBadge`(AUTO/MANUAL/기준값+갱신일)
  - `feature/bearsignal/presentation/ui/BearSignalEntryCard.kt` — 신규 메뉴 진입점(§5.1 권장안), 실시간 국면 미리보기(자체 `BearSignalViewModel` 인스턴스, Room 캐시 기반)
- **네비게이션**: `MainActivity.kt`에 `composable("bear_signal")` 라우트 추가(기존 4→5탭 유지, 6번째 하단 탭 신설 안함 — §5.1 "권장안" 채택). `MarketAnalysisScreen`(Fear&Greed 탭) `MarketSummaryCard` 바로 아래 `BearSignalEntryCard` 배치, `onBearSignalClick` 콜백을 `MainActivity` → `MainScaffold` → `MarketAnalysisScreen` → `FearGreedTab`으로 스레딩
- **접근성/모바일**: LEVEL 색 항상 텍스트 라벨 병기(`SignalLevel.label`/`GateState.label`), 헤더 카드에 `Modifier.semantics { contentDescription }` 요약 부여, 표는 전치(국가=행)로 360dp 세로 스크롤 대응, Material3 기본 컴포넌트(Card/Chip/Button) 사용으로 폰트 스케일 대응은 시스템 기본값 상속
- **테스트**: 신규 18건(총 223건, 0 실패) — `BearSignalStaticContentTest` 5(3유형 개수·순서·활성인덱스·모니터링 비어있지 않음·면책문구), `ObserveBearSignalStateUseCaseTest` 3(Room 캐시 전무 시 골든 케이스 AMBER 재현, 수동 오버라이드 즉시 반영, 기간 선택 반영), `BearSignalViewModelTest` 10(초기값·Room 방출 반영·lastUpdatedAt 계산·refresh 성공/실패/에러클리어·selectPeriod 내부 Flow 갱신·수동입력 위임 3종·reset 위임). `WhileSubscribed(5_000)` StateFlow 테스트 시 `backgroundScope.launch { uiState.collect() }`로 구독 유지 필요(주석에 근거 명시). 기존 205건 회귀 통과.
- **빌드**: `:app:compileDebugKotlin`/`:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"`/`:app:assembleDebug` 전부 BUILD SUCCESSFUL.
- **미검증(가능 범위 밖)**: 360dp 실기/에뮬레이터 렌더링, 다크·라이트 모드 시각 대비, 폰트 스케일 1.3x 붕괴 여부 — 이번 세션은 Bash/Gradle만 사용 가능해 실기 캡처 불가. 코드 레벨로는 Material3 표준 컴포넌트·기존 테마 토큰만 사용해 다크/라이트 자동 대응하도록 작성했으나 시각 검증은 Phase 5(qa-verifier)에서 필요.
- **PullToRefreshContainer**: Compose Material3 1.2.0(BOM 2024.02.00) API(`rememberPullToRefreshState`+`Modifier.nestedScroll`+`PullToRefreshContainer`, `PullToRefreshBox` 상위 헬퍼는 1.3.0+에만 존재) 확인 후 수동 조립.
- **범위 참고**: §5.2 섹션2 "자동값 표시/수동 입력 버튼"은 실제 Phase3 도메인에 MANUAL 경로가 있는 필드(신호3의 loss/big, 금리의 dir/credit/margin)에만 노출했다. 신호1(국가별 수익률은 섹션3 표에서 별도 편집)·신호2(±3σ, [A] 완전자동)·증폭(semi/kospi2/buffer, MANUAL 경로 없음)은 §1.1 매트릭스상 애초에 수동 입력이 불필요하거나(완전자동) Phase3에서 그 경로를 구현하지 않았다(v1 범위 밖) — Phase4는 UI 조립만 담당하므로 Room 스키마·Phase3 완료 항목을 확장하지 않았다.

## P5 상세

- **WorkManager 월간 주기 갱신**
  - `core/worker/BearSignalUpdateWorker.kt` 신규 — `BaseCollectionWorker` 상속(기존 패턴: HiltWorker, foreground DATA_SYNC, exp backoff 30s, `worker_logs` 기록). `RefreshAutoInputsUseCase`([A])→`RefreshExternalAutoInputsUseCase`([B])→`RefreshMarketReturnsUseCase`(도표48) 3단계를 `BearSignalViewModel.refresh()`와 동일 순서로 호출. 각 UseCase는 이미 Phase1/2에서 개별 실패 시 캐시 폴백을 구현했으므로, 워커는 3개 결과를 집계해 **3개 전부 실패했을 때만** retry/failure, 일부 성공 시 success로 처리(부분 갱신도 캐시 신선도 개선에 기여)
  - `core/worker/WorkManagerHelper.kt` — `scheduleMonthlyWorker` 제네릭 헬퍼 신규(`scheduleDailyWorker`와 대칭 구조) + `scheduleBearSignalUpdate`/`cancelBearSignalUpdate`/`runBearSignalUpdateNow`. 기본 스케줄 매월 5일 06:00, flex 1일(월별 일수 차이 흡수). WorkManager `PeriodicWorkRequest`는 고정 길이 간격만 지원해 정확한 "매월 N일" 보장은 불가하나, 관세청/ECOS 조회가 이미 전월(lag) 데이터를 쓰므로(Phase 2) ±수일 드리프트는 스코어링에 영향 없음 — KDoc에 근거 명시
  - `core/worker/CollectionNotificationHelper.kt` — `BEAR_SIGNAL_NOTIFICATION_ID = 1016` 추가(기존 1001~1015와 겹치지 않는 다음 번호)
  - `TinyOscillatorApp.kt` — `onCreate()`에 `WorkManagerHelper.scheduleBearSignalUpdate(this)` 추가(Macro/Regime/MetaLearner와 동일하게 사용자 토글 없이 항상 복원 — 기존 관례 준수)
  - **캐시 우선 렌더**: `ObserveBearSignalStateUseCase`가 Room 4-Flow를 구독하는 기존 구조 자체가 이미 "워커가 백그라운드에서 갱신 → 화면은 Room 변경을 자동 반영"을 보장하므로 Phase5에서 화면 쪽 추가 배선은 불필요(그대로 재사용 확인)
- **shimmer 로딩**
  - `feature/bearsignal/presentation/BearSignalViewModel.kt` — `BearSignalUiState.isLoading: Boolean = true`(기존 shimmer 관례와 동일하게 "Room 캐시 최초 방출 전"만 true). `stateIn`의 초기값(`BearSignalUiState()`)에서만 true, `combine(...)` 람다는 Room 4-Flow가 최소 한 번 합성된 뒤에만 실행되므로 그 시점부터 항상 `isLoading = false`로 고정 — 별도 플래그 오케스트레이션 없이 "초기값 vs 합성값" 차이만으로 로딩 상태를 표현
  - `presentation/common/skeleton/ScreenSkeletons.kt` — `BearSignalScreenSkeleton` 신규(기존 `ShimmerBox`/`ShimmerLine` 그대로 재사용, §5.2 7섹션 구조를 헤더/3카드/표/방아쇠·증폭/유형·역사 6블록으로 축약)
  - `feature/bearsignal/presentation/ui/BearSignalScreen.kt` — `uiState.isLoading`이면 `Scaffold` 컨텐츠를 스켈레톤으로 조기 반환(`return@Scaffold`), TopAppBar/BottomSheet/리셋 다이얼로그는 그대로 유지
- **오프라인 폴백**
  - `BearSignalViewModel.refresh()` — `NetworkUtils.isNetworkAvailable(context)`를 API 호출 전에 확인(기존 `OscillatorViewModel`/`QuickAnalysisViewModel` 관례 재사용). 오프라인이면 3개 UseCase를 아예 호출하지 않고 `isOffline=true`만 설정 — 화면은 이미 Room 캐시로 렌더돼 있으므로(§1.2 하이브리드 아키텍처) 재시도할 필요가 없다
  - `BearSignalScreen.kt` — `uiState.isOffline`이면 `LazyColumn` 최상단에 기존 `core/ui/composable/UiStateContent.kt`의 `StaleBanner`(문구 "오프라인 · 마지막 저장 데이터를 표시 중입니다" + 새로고침 버튼)를 노출. 캐시 데이터(`inputs`/`result`)와 "전체 최신 갱신일"(`BearSignalFooterSection`의 `LastUpdatedText`, Phase4에서 이미 구현)은 그대로 유지되므로 요구사항("캐시 데이터 렌더 + 최신 갱신일 표기") 충족
  - `@ApplicationContext Context` 생성자 주입 추가(`EtfViewModel` 등 기존 패턴과 동일) — Hilt가 자동 주입, JVM 테스트는 `mockk<Context>(relaxed = true)` + `mockkObject(NetworkUtils)`로 검증(기존 `QuickAnalysisViewModelTest` 패턴 재사용)
- **접근성 최종 점검**
  - `BearSignalLeadingSignalsSection.kt` — `SignalCard`에 선택적 `contentDescription` 파라미터 추가, 신호1/2/3 카드에 레벨+핵심수치 요약 부여(Phase4는 헤더 카드에만 적용돼 있었음)
  - `BearSignalGateAmpSection.kt` — 금리 방아쇠·증폭 계수 카드에 각각 `Modifier.semantics { contentDescription }` 추가
  - `BearSignalCountryTableSection.kt` — `CountryRow`(이미 `.heightIn(min = 48.dp)` 확보돼 있던 터치 타깃)에 국가명+4기간 값 요약 `contentDescription` 추가
  - **점검 결과(기존 구현 재확인, 변경 불요)**: `TextButton`/`IconButton`/`FilterChip`/`Checkbox` 등 Material3 표준 컴포넌트는 라이브러리 차원에서 최소 48dp 터치 타깃을 강제하므로 추가 조치 불요. 모든 LEVEL 색상은 `SignalLevel.label`/`GateState.label` 텍스트와 항상 병기(Phase4부터 일관). Canvas 커스텀 그래픽(`BearSignalGraphics.kt`)의 라벨은 `sp` 단위 사용으로 시스템 폰트 스케일에 자동 반응 — 다만 **360dp 실기 렌더·다크모드 대비·폰트 1.3x 실측 검증은 이번 세션(Bash/Gradle 전용) 범위 밖**으로 미검증 상태 유지(Phase4와 동일한 한계)
- **테스트**: `BearSignalViewModelTest` +3(오프라인 시 UseCase 미호출·캐시 데이터 유지·네트워크 복구 후 재갱신, 초기/Room방출 테스트에 isLoading/isOffline 단언 추가) — `feature.bearsignal` 패키지 총 226건. `core/worker/BearSignalUpdateWorkerTest.kt` 신규 6건(companion 상수, 알림ID 유일성, `scheduleBearSignalUpdate` 입력 검증 4종 — `dayOfMonth`/`hour`/`minute` 경계). `doWork()` 자체는 `BaseCollectionWorker.setForeground()`가 실제 WorkManager/Android 런타임을 요구해 JVM 테스트 불가 — 기존 `MacroUpdateWorker`/`ThemeUpdateWorker`도 동일한 이유로 `doWork()` 단위테스트가 없는 것과 동일한 구조적 한계(KDoc·PROGRESS에 명시).
- **빌드**: `:app:compileDebugKotlin`/`:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"`(226건, 0 실패)/`:app:testDebugUnitTest --tests "com.tinyoscillator.core.worker.*"`(0 실패)/`:app:assembleDebug` 전부 BUILD SUCCESSFUL.
- **미검증(가능 범위 밖, Phase4와 동일한 한계 승계)**: 실기/에뮬레이터에서 shimmer 애니메이션·오프라인 배너·다크모드 대비·폰트 스케일 1.3x 렌더링 확인, WorkManager 월간 트리거의 실제 발화(달력월 30일 스케줄이므로 단시간 세션에서 실행 확인 불가 — `runBearSignalUpdateNow()` 수동 트리거 경로는 존재하나 실기 실행은 미검증), 관세청 API 실키 검증.

## 실기 QA 1차 (2026-07-10, 에뮬레이터 pixel_fold · 라이트 테마)

- **결과: 통과 (크래시 0, 앱 레벨 에러 로그 0)** — `assembleDebug` APK 설치 후 검증.
- **렌더**: `BearSignalScreen` 7섹션 전부 정상 — 헤더(신호등 "신호 점등·방아쇠 대기"·선행점수 33/100 게이지·금리 방아쇠 "경계 접근"·집중 증폭 ×1.30·4축 레이더 Canvas), 선행신호 3카드(전부 주의 + 세그먼트 게이지), 국가별 수익률 표 20행(기간 FilterChip·이탈 지수 11/20 배지·"수동 필요" 13지수 배지·양/음수 색 구분), 방아쇠·증폭 카드(기준값 배지), 3유형 카드(유형3 활성 방아쇠 강조 테두리·체크리스트), 역사검증(일본 1980s), 매핑·면책 푸터.
- **진입점**: 시장분석 탭 `BearSignalEntryCard` 실시간 상태 미리보기("주의" 배지) → 진입 → back 왕복 정상.
- **수동입력 BottomSheet**: 슬라이더 드래그 중 실시간 반영 확인(logcat `updateManualInput` 5회 연속 발화, 45.2→67.7%) → "리포트 기준값으로 리셋" → `resetToReportBaseline` 로그 + 재오픈 시 45.0% 복원 확인.
- **월간 워커(ecf4682 수정 실기 검증)**: 앱 시작 로그 `BearSignal 지표 월간 업데이트 스케줄 등록: 매월 5일 06:00 (초기 딜레이: 37571분, policy=KEEP)` — 37571분 ≈ 26.1일 = 정확히 다음 달 5일 06:00. flex 제거 수정이 실기에서 의도대로 동작.
- **새로고침 폴백**: KRX [A] 지표 수집 성공(up3/down3/kospi2 산출), [B] 외부 지표 전부 null(관세청/FRED 키 미설정 — 기준값 폴백 정상), 해외지수 수집 실패 시 "기존 캐시 유지" 로그 + UI 값 유지.
- **MINOR 재현**: 체크리스트 체크 → back → 재진입 시 소실 (`remember`→`rememberSaveable` 기지 이슈, `TypesHistorySection`).
- **신규 발견(외부 요인, 앱 버그 아님)**: **Stooq 안티봇 차단** — `stooq.com/q/d/l/` CSV 엔드포인트가 JS SHA-256 proof-of-work 챌린지 HTML을 HTTP 200으로 반환(호스트 curl 재현 동일). `StooqCsvClient.parseCsv`가 CSV 헤더 불일치로 0건 → AUTO 해외지수 6종(닛케이/다우/S&P/DAX/나스닥/항생) + IPO ETF(`ipo.us`) 자동수집 불가. 앱은 설계된 폴백(캐시/리포트 기준값 유지 + 수동입력 경로)으로 무해 강등. **대응 결정 필요**: 대체 무료 소스(예: Yahoo Finance chart API) 교체 vs 해당 7지표 MANUAL_REQUIRED 강등. → **해소(2026-07-10)**: Yahoo Finance chart API 기본 소스 채택 + Stooq 백업 강등, 설정에서 소스 선택 가능 — 하단 「시세 소스 교체」 절 참조.
- **미검증(잔여 QA)**: ~~360dp 폰 폭·다크 테마·폰트스케일 1.3x 렌더~~(2026-07-10 통과 — 하단 「렌더 변형 QA」 절), 월간 워커 실제 발화(`runBearSignalUpdateNow()` 수동 트리거 포함), 관세청/FRED 실키 응답 필드 검증, 스펙 조정 2건 판단(P4 절 참조).

## 렌더 변형 QA — 360dp·다크·폰트스케일 1.3x (2026-07-10, 통과)

- **방법**: 에뮬레이터 pixel_fold에 `wm size 1080x2340` + `wm density 480`(=360dp 폭), `cmd uimode night yes`, `settings put system font_scale 1.3` 순차 적용 — 최악 조합(360dp+다크+1.3x)까지 검증. 크래시 0 (`FATAL EXCEPTION` 0건). 검증 후 전부 원복.
- **360dp 라이트**: 하단 탭바 전환·진입 카드 2줄 래핑 정상. 헤더 3열 스탯(금리 방아쇠/집중 증폭/경고 신호)·게이지·레이더 Canvas 정상. 국가표 5열(국가+4기간) 클리핑 없음, 증폭 카드 3열·유형 카드·체크리스트·역사검증·푸터 전 섹션 정상.
- **다크**: 시스템 다크 전환 즉시 반영. 앰버 강조·게이지·레이더 대비 양호. 국가표 양수 적색/음수 청색 다크 배경에서 판독 가능 — 기지 MINOR(SignColor 다크 변형 미사용, 적색이 다소 어두움) 재확인, 가독성 저해 수준 아님. "수동 필요"(핑크)·"수동 입력"(민트) 배지 시인성 정상.
- **폰트스케일 1.3x (다크+360dp 동시)**: 헤더 타이틀 2줄 래핑, 3열 스탯 유지, 레이더 라벨 sp 스케일 반응, 신호3 카드·국가표 5열 오버랩 없음. 기간 FilterChip 행은 `horizontalScroll` 적용이라 우측 잘림은 스크롤 설계(버그 아님, 코드 확인).
- **부수 확인**: 다크·1.3x 상태에서 pull-to-refresh 트리거 시 Yahoo 재수집 성공(국가표 라이브 값 갱신) — 렌더 변형과 데이터 경로 상호작용 문제 없음.
- **잔여 관찰(MINOR, 기존 기지 이슈)**: 레이더 "변동성" 라벨이 폴리곤과 살짝 겹침(전 폭 공통, 코스메틱). SignColor 다크 변형 미사용(전역 이슈).

## 시세 소스 교체 — Stooq → Yahoo 기본 + 멀티소스 폴백 (2026-07-10)

- **배경**: 실기 QA 1차에서 Stooq 안티봇(JS PoW) 차단 확인 → AUTO 해외지수 6종 + IPO ETF 자동수집 전멸(위 절 참조).
- **백업 소스 검토 결과**: **Yahoo Finance chart API 채택(기본)** — `query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=2y&interval=1d`, 무인증 JSON, 필요 7개 티커(`^N225`/`^DJI`/`^GSPC`/`^GDAXI`/`^IXIC`/`^HSI`/`IPO`) 전부 실응답 검증(2y ≈ 501봉 ≥ 12M 수익률 요구 253봉). **Stooq는 백업으로 유지**(무인증·차단 해제 가능성). 탈락: FRED(S&P/다우/나스닥만 — DAX·항생·IPO ETF 불가), Alpha Vantage(무료 지수 미지원), Twelve Data(지수 유료 플랜), FMP(무료 제한), 스크래핑(취약).
- **구현**:
  - `domain/model/GlobalIndexSource.kt` 신규 — `YAHOO`(기본)/`STOOQ` enum, 검토 근거 KDoc.
  - `data/remote/YahooChartApiClient.kt` 신규 — chart JSON 파싱(`timestamp`+`quote[0].close`, null 종가 스킵, error 응답 방어), 브라우저 UA 명시(기본 okhttp UA 간헐 차단), 1000ms rate limit. `data/remote/IndexDailyBar.kt` 공용 모델(기존 `StooqDailyBar` 대체, `StooqCsvClient`도 공용화).
  - `GlobalIndexRegistry` — `GlobalIndexSpec`에 소스별 티커(`yahooTicker`/`stooqTicker`) + `tickerFor(source)`/`autoCovered`. AUTO 6지수는 두 소스 모두 티커 보유(커버리지 변화 없음, MANUAL 13지수 유지).
  - `BearSignalRepositoryImpl` — `fetchDailyClosesWithFallback()`: 사용자 선택 소스 우선 조회, 실패(빈 응답·예외) 시 나머지 소스 자동 폴백. IPO ETF 티커 소스별 맵(`IPO`/`ipo.us`). `indexSourceProvider` 주입(설정 즉시 반영 위해 매 갱신 시 prefs 재조회).
  - 설정 — `PrefsKeys.BEAR_SIGNAL_INDEX_SOURCE`(EncryptedSharedPreferences, 기존 관례) + API 탭 「해외지수 시세 소스 (계기판)」 드롭다운(Yahoo Finance/Stooq, 자동 폴백 안내 캡션).
- **테스트**: `YahooChartApiClientTest` 신규 7건(fixture 파싱·null 종가·error 응답·비JSON·길이 불일치), `GlobalIndexSourceTest` 3건, `GlobalIndexRegistryTest` 7건(소스별 티커 검증으로 갱신), `BearSignalRepositoryImplTest` 33건(Yahoo 우선·Stooq 폴백·양소스 실패·STOOQ 선택 시 역순 검증 추가). `feature.bearsignal` 전체 0 실패, `compileDebugKotlin` 통과.
- **실검증**: 호스트 curl로 7개 티커 전부 정상 JSON 응답 + `^DJI` 2y 501봉 확인(2026-07-10).
- **에뮬레이터 실기 검증 통과 (2026-07-10, pixel_fold, 크래시 0)**:
  - Yahoo 기본 소스 수집: 새로고침 시 `etf=up`(IPO ETF — Stooq 차단 시절 null이던 지표 라이브 복구) + `국가별 수익률 수집 완료: manualRequired=[13개]`(AUTO 6지수 전부 수집, 폴백·실패 로그 0건, 6지수 ~6초).
  - UI 라이브 반영: 신호3 IPO ETF 방향 "상승/회복", 국가표에 닛케이(+71.2/+32.6/+22.1/+7.0)·다우(+18.6/+6.1/+9.6/+3.4) 등 AUTO 라이브 값 + "자동 · 07/10" 배지, 미커버 13지수만 "수동 필요" 배지, 이탈 지수 수 재산출(9/20 @-1개월).
  - 설정 UI: API 탭 「해외지수 시세 소스 (계기판)」 카드·드롭다운(Yahoo Finance/Stooq)·캡션 렌더 정상, 저장→재진입 시 선택값 유지(EncryptedSharedPreferences 왕복).
  - **폴백 체인 실증**: Stooq로 전환 후 새로고침 → 7종 전부 `시세 응답 없음 (STOOQ:^nkx 등) — 다음 소스 폴백` 로그 후 Yahoo로 수집 완료(`etf=up`, manualRequired 13 동일) — 선택 소스 실패 시 자동 폴백이 실기에서 의도대로 동작. 검증 후 Yahoo로 원복 저장.

## 점검 이력 (2026-07-09)
- kotlin-implementer 셀프리뷰(qa 점검) 결과: 스코어링 5개 함수(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite) 전부 프로토타입 `bear_signal_dashboard.jsx`(작업 디렉터리 루트에서 재확보, git 미추적)와 문자 단위 일치 확인.
- **수정**: "도표48 전체 시드 미이관(18행 결손)" 편차 해소 — 확보된 `bear_signal_dashboard.jsx`의 `MARKETS` 상수(20지수) 전체를 `BearSignalReportBaseline.MARKETS`로 이관. 골든 케이스 테스트를 합성 픽스처 대신 실데이터로 교체(neg=11, worstNew=-5.1(나스닥), depth=SHALLOW → s1=1 재검증). 시드 검증 테스트 확장(20행 카운트 + 6개 지수 스팟체크).
- 재실행: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.*"` → 41 tests, 0 failures.
