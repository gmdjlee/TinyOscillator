# CLAUDE.md — TinyOscillator Project Guide

> **파일 규칙**: 이 파일은 **코드베이스 사실**(구조·스택·컨벤션)만 담는다. 60~120줄 유지 — 갱신 시 오래된 항목을 지우고 추가하며 줄 수를 넘기지 마라. 에이전트 운영 방식(역할 분담·명령어)은 `AGENTS.md` 참조.

## Overview
한국 주식시장 분석 Android 앱(리테일 투자자 대상). 오실레이터 기술분석, DeMark TD Sequential, 재무제표 분석, ETF 섹터 분석, Fear&Greed 지수, 애널리스트 컨센서스, AI 분석(Claude/Gemini API), 포트폴리오 관리. 순수 Kotlin. MVVM + Clean Architecture, ~1,420 passing tests, Room DB v31.

## Tech stack
| Property | Value | | Library | Ver | Purpose |
|---|---|---|---|---|---|
| compileSdk / target | 35 | | Hilt | 2.54 | DI (+ Work 1.2.0, Nav 1.2.0) |
| minSdk | 26 | | Room | 2.6.1 | Local DB (KSP) |
| Kotlin / AGP | 2.1.0 / 8.7.3 | | WorkManager | 2.9.0 | 백그라운드 잡 |
| Compose BOM | 2024.02.00 | | OkHttp / Jsoup | 4.12.0 / 1.17.2 | HTTP / 스크래핑 |
| JVM target | 17 | | kotlin_krx | SNAPSHOT | KRX 데이터 (composite build) |
| Nav Compose | 2.7.7 | | MPAndroidChart | 3.1.0 | 차트 |
| KSP | 2.1.0-1.0.29 | | Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |

테스트: JUnit4 · MockK 1.13.9 · Turbine 1.0.0 · coroutines-test · MockWebServer · Robolectric · Timber 5.0.1.

## Repository layout
```
app/src/main/java/com/tinyoscillator/
  TinyOscillatorApp.kt      # @HiltAndroidApp, WorkManager 스케줄 복원
  MainActivity.kt           # Single-Activity + Compose NavHost
  core/    api|config|database|di|network|scraper|util|worker
  data/    dto|engine|mapper|repository
  domain/  model|repository|usecase
  presentation/  ai|chart|consensus|demark|etf|financial|fundamental|
                 market|marketanalysis|portfolio|report|settings|theme|viewmodel
  ui/theme/                 # Material3 theme
app/src/test/               # 164 test files (~1,420 tests)
app/src/androidTest/        # Compose UI smoke tests
app/schemas/                # Room schema exports v2~v31
settings.gradle.kts         # includeBuild("../kotlin_krx")
```
- 소스: `app/src/main` 아래 ~323 Kotlin 파일. `:app` 단일 모듈 + `../kotlin_krx` composite build.

## Architecture (MVVM + Clean)
- **Domain**: interfaces(`StatisticalRepository`, `LlmRepository`), use cases(`AnalyzeStockProbabilityUseCase`, `CalcOscillatorUseCase`), 도메인 모델
- **Data**: repository impl, 9 통계 엔진, API 클라이언트, 스크래퍼, mapper
- **Presentation**: Compose 스크린, ViewModel, theme
- **Core**: API 클라이언트, DB, DI, network, worker

핵심 클래스: `StatisticalAnalysisEngine`(9-엔진 병렬 오케스트레이터, 개별 실패 격리) → `ProbabilisticPromptBuilder`(ChatML) → `AiApiClient` → `AnalysisResponseParser`. AI 불가 시 `ProbabilityInterpreter` 로컬 해석. `ApiConfigProvider`(volatile+mutex 자격증명 캐시), `WorkManagerHelper`(스케줄), `AppDatabase`(v31, 28 entities, 22 DAOs).

## Analysis engines (9)
NaiveBayes · LogisticScoring · HmmRegime · PatternScan · SignalScoring · Correlation · BayesianUpdate · OrderFlow · DartEvent. 모두 `data/engine/`. `StatisticalAnalysisEngine`가 coroutine 병렬 실행 + `RegimeWeightTable` regime-aware 가중. (별개 11번째 엔진 `SectorCorrelationNetwork` — `stock_master.sector`만 사용, 테마 메뉴와 무관, 혼동 금지.)

## Data sources
- **KIS / Kiwoom OpenAPI**: OAuth2 토큰(mutex 캐시, 만료 1분 전 갱신), 500ms rate limit, circuit breaker(3 실패→5분), 인증서 피닝. Kiwoom 테마 `/api/dostk/thme`(ka90001/ka90002, cont-yn 페이지네이션, MAX_PAGES=50).
- **KRX** (via kotlin_krx): ETF/portfolio/index/stock. Room 캐시(ETF 365d, fundamental 730d TTL), 500ms.
- **AI API**: Claude Haiku/Sonnet, Gemini Flash. 1000ms rate limit.
- **DART**: corpCode.xml→Room(30d TTL), 10k/day, event study(OLS beta, CAR [-5,+20]).
- **BOK ECOS**: 5 macro indicators(base_rate/m2/iip/usd_krw/cpi), 1000ms, Weekly TTL, 2개월 지연 보정.
- **Scrapers**: NaverFinance(500ms) · EquityReport(8-16s) · FnGuide(1-5s).

## Room DB (v31)
- Migration `MIGRATION_1_2`~`MIGRATION_30_31` in `core/database/migration/AppDatabaseMigrations.kt`, `.addMigrations(*AppDatabaseMigrations.ALL)`. **`fallbackToDestructiveMigration()` 없음 — 모든 업그레이드 명시적.** Schema JSON `app/schemas/.../2.json~31.json`.
- v29→30: `theme_group`+`theme_stock` 추가. v30→31: legacy `sector_master`+`sector_index_candle` DROP.

## Background jobs (WorkManager)
EtfUpdate(00:30) · MarketOscillator(01:00) · MarketDeposit(02:00) · ThemeUpdate(02:30) · Consensus(03:00) · FearGreed(04:00) · MarketCloseRefresh(19:00) · Macro(Sun 05:30) · DataIntegrityCheck(수동). 모두 network-constrained, exp backoff(30s), foreground(DATA_SYNC), `worker_logs` 기록. 스케줄 사용자 설정 가능, 앱 시작 시 복원.

## Security (API 자격증명)
- 모든 자격증명 `EncryptedSharedPreferences`(`api_settings_encrypted`, AES256-SIV key / AES256-GCM value)
- `ApiConfigProvider` volatile+mutex 메모리 캐시. KIS/Kiwoom TLS 인증서 피닝.
- **API key/token/secret 하드코딩 금지. 자격증명 값 로깅 금지** (masked `toString()` 사용). OAuth2 토큰 만료 1분 전 자동 갱신. ProGuard가 release 로그 strip.

## Korean market conventions
- 세션: 09:00–15:30 KST, 주말 제외(`TradingHours` in `domain/model/RealtimeSupplyModels.kt`). `OscillatorViewModel` auto-refresh 게이트.
- **KRX 휴일 캘린더 없음** — 주말만 제외. 명절 등은 API no-data + 1시간 쿨다운 의존.
- 티커: 6자리 숫자(예 `005930`). KOSPI/KOSDAQ 동일 포맷, `StockMasterEntity.market`에 시장 구분.

## Naming conventions
- UseCase: `동사+명사+UseCase` · Repository: `명사+Repository` · Engine: `명사+Engine` · Result: `명사+Result` · Worker: `명사+Worker`
- 한글 주석 허용, 코드 식별자는 영어.

## Known issues
- KRX 휴일 캘린더 없음(쿨다운 의존). `MarketOscillatorCalculator`는 raw KRX OHLCV를 Room 증분 캐시하지 않음.
- TODO/FIXME/HACK 마커 없음. Refactor Phase 1~8+3.5+4.5+Polish 완료.
