# CLAUDE.md — TinyOscillator Project Guide

## Project overview
TinyOscillator is a Korean stock market analysis Android app targeting retail investors. It provides oscillator-based technical analysis, DeMark TD Sequential, financial statement analysis, ETF sector analysis, market Fear & Greed index, analyst consensus reports, AI-powered analysis (Claude/Gemini API), and portfolio management. The app is written entirely in Kotlin. It is in active development with ~1,400 passing tests and a mature MVVM + Clean Architecture codebase.

## Repository layout
```
TinyOscillator/
├── app/
│   ├── build.gradle.kts              # App-level build config
│   ├── proguard-rules.pro
│   ├── schemas/                       # Room schema exports (v2–v31)
│   └── src/
│       ├── main/
│       │   └── java/com/tinyoscillator/
│       │       ├── TinyOscillatorApp.kt       # Application (@HiltAndroidApp)
│       │       ├── MainActivity.kt            # Single-Activity + Compose NavHost
│       │       ├── core/
│       │       │   ├── api/           # API clients (Kiwoom, KIS, KRX, AI)
│       │       │   ├── config/        # ApiConfigProvider (credential cache)
│       │       │   ├── database/      # Room AppDatabase, DAOs, Entities
│       │       │   ├── di/            # Hilt modules (App, Database, Statistical, Worker)
│       │       │   ├── network/       # NetworkUtils (connectivity check)
│       │       │   ├── scraper/       # Web scrapers (Naver, Equity, FnGuide)
│       │       │   ├── util/          # DateFormats
│       │       │   └── worker/        # WorkManager workers (14 workers + 4 helpers)
│       │       ├── data/
│       │       │   ├── dto/           # API response DTOs
│       │       │   ├── engine/        # 7 statistical engines + orchestrator
│       │       │   ├── mapper/        # PromptBuilder, ResponseParser
│       │       │   └── repository/    # Repository implementations
│       │       ├── domain/
│       │       │   ├── model/         # Domain data classes
│       │       │   ├── repository/    # Repository interfaces
│       │       │   └── usecase/       # Business logic use cases
│       │       ├── presentation/
│       │       │   ├── ai/            # AI analysis screens + ViewModel
│       │       │   ├── chart/         # OscillatorChart, ConsensusChart
│       │       │   ├── common/        # Shared Compose components
│       │       │   ├── consensus/     # Consensus report screen
│       │       │   ├── demark/        # DeMark TD screen
│       │       │   ├── etf/           # ETF analysis screens (5+ screens)
│       │       │   ├── financial/     # Financial info, DuPont, NaverWeb
│       │       │   ├── fundamental/   # Fundamental history charts
│       │       │   ├── market/        # Market oscillator + deposit tabs
│       │       │   ├── marketanalysis/# Fear&Greed, Market DeMark
│       │       │   ├── portfolio/     # Portfolio management screens
│       │       │   ├── report/        # Analyst report screens
│       │       │   ├── settings/      # Settings, Backup, Schedule config
│       │       │   ├── theme/         # 테마 (Kiwoom ka90001/ka90002) screens + ViewModel
│       │       │   └── viewmodel/     # OscillatorVM, StockAnalysisVM
│       │       └── ui/theme/          # Material3 theme (Color, Type, Theme)
│       └── test/                      # 160 unit test files (~1,400 tests)
├── build.gradle.kts                   # Root build (plugin versions)
└── settings.gradle.kts                # includeBuild("../kotlin_krx")
```

## Tech stack
### Android / Kotlin
| Property | Value |
|----------|-------|
| compileSdk | 35 |
| minSdk | 26 |
| targetSdk | 35 |
| Kotlin | 2.1.0 |
| AGP | 8.7.3 |
| Compose BOM | 2024.02.00 |
| JVM target | 17 |

### Dependencies
| Library | Version | Purpose |
|---------|---------|---------|
| OkHttp | 4.12.0 | HTTP client for all APIs |
| Jsoup | 1.17.2 | HTML scraping (Naver, Equity, FnGuide) |
| kotlinx-serialization-json | 1.6.3 | JSON parsing |
| kotlinx-coroutines | 1.7.3 | Async (core + android) |
| Compose UI/Material3 | BOM 2024.02.00 | UI framework |
| Material3 window-size-class | (BOM) | Adaptive layout (phone/tablet/foldable) |
| Material Icons Extended | (BOM) | Icon library |
| Navigation Compose | 2.7.7 | Screen navigation |
| Room | 2.6.1 | Local database (runtime + ktx + KSP compiler) |
| Hilt | 2.54 | Dependency injection |
| Hilt Navigation Compose | 1.2.0 | ViewModel injection in Compose |
| Hilt Work | 1.2.0 | WorkManager injection |
| WorkManager | 2.9.0 | Background scheduled jobs |
| kotlin_krx | 1.0.0-SNAPSHOT | KRX data (composite build from ../kotlin_krx) |
| MPAndroidChart | v3.1.0 | Stock charts |
| Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| Browser | 1.8.0 | Custom Tabs for WebView fallback |
| Timber | 5.0.1 | Logging |
| JUnit4 | 4.13.2 | Unit testing |
| MockK | 1.13.9 | Mocking |
| Turbine | 1.0.0 | Flow testing |
| MockWebServer | 4.12.0 | HTTP testing |
| KSP | 2.1.0-1.0.29 | Annotation processing |

### Python layer
**None.** There is no Chaquopy integration, no Python files in the app module, and no Python bridge.

## Architecture
### Pattern
MVVM + Clean Architecture confirmed. Clear layer separation:
- **Domain**: interfaces (`StatisticalRepository`, `LlmRepository`), use cases (`AnalyzeStockProbabilityUseCase`, `CalcOscillatorUseCase`, etc.), domain models
- **Data**: repository implementations, 7 statistical engines, API clients, scrapers, mappers
- **Presentation**: Compose screens, ViewModels, theme
- **Core**: cross-cutting concerns (API clients, database, DI, network, workers)

### Module structure
| Module | Description |
|--------|-------------|
| `:app` | Single application module containing all layers |
| `../kotlin_krx` | External composite build — KRX data client library |

### Key classes
| Class | Layer | File | Responsibility |
|-------|-------|------|----------------|
| `TinyOscillatorApp` | core | `TinyOscillatorApp.kt` | Application entry, WorkManager schedule restoration |
| `MainActivity` | presentation | `MainActivity.kt` | Single Activity, NavHost, bottom/rail navigation |
| `StatisticalAnalysisEngine` | data/engine | `StatisticalAnalysisEngine.kt` | 7-engine parallel orchestrator |
| `AnalyzeStockProbabilityUseCase` | domain/usecase | `AnalyzeStockProbabilityUseCase.kt` | Full analysis pipeline (stats → cache → LLM) |
| `ApiConfigProvider` | core/config | `ApiConfigProvider.kt` | Thread-safe credential cache (volatile + mutex) |
| `WorkManagerHelper` | core/worker | `WorkManagerHelper.kt` | Centralized worker scheduling |
| `AppDatabase` | core/database | `AppDatabase.kt` | Room DB v31, 28 entities, 22 DAOs |
| `ProbabilisticPromptBuilder` | data/mapper | `ProbabilisticPromptBuilder.kt` | ChatML prompt for LLM analysis |
| `FearGreedCalculator` | domain/usecase | `FearGreedCalculator.kt` | 7-indicator fear/greed index |
| `MarketOscillatorCalculator` | domain/usecase | `MarketOscillatorCalculator.kt` | Market overbought/oversold index |

## Kotlin–Python bridge
**Not applicable.** There is no Python bridge in this project. All analysis is implemented in pure Kotlin.

## Data sources
### KIS OpenAPI
- **Credential storage**: EncryptedSharedPreferences (`api_settings_encrypted`), AES256-GCM
- **Token refresh**: OAuth2 `/oauth2/tokenP` with mutex-protected cache, auto-refresh 1 min before expiry
- **Rate limiting**: 500ms per request (mutex-based)
- **Circuit breaker**: 3 consecutive failures → 5 min cooldown
- **Endpoints**: Financial statements (balance sheet, income, profitability/stability/growth ratios)

### Kiwoom OpenAPI
- **Credential storage**: Same EncryptedSharedPreferences
- **Token refresh**: OAuth2 `/oauth2/token` (api-id: au10001), same pattern as KIS
- **Rate limiting**: 500ms per request
- **Circuit breaker**: 3 failures → 5 min cooldown
- **Certificate pinning**: Intermediate CAs for *.kiwoom.com and *.koreainvestment.com
- **Endpoints**: Daily OHLCV, investor trend, stock info, stock list (KOSPI + KOSDAQ), 테마 그룹/구성종목 (`/api/dostk/thme` — ka90001/ka90002, cont-yn 페이지네이션 지원)
- **Pagination**: `KiwoomApiClient.callWithHeaders(...)` returns `PageHeaders(contYn, nextKey, apiId)`; ThemeRepository iterates while `cont-yn == "Y"` with `MAX_PAGES=50` 안전장치 + per-theme try/catch 격리

### KRX (via kotlin_krx)
- **Credential storage**: EncryptedSharedPreferences (KRX username + password)
- **Functions in use**: `login()`, `getEtfTickerList()`, `getPortfolio()`, `getKrxIndex()`, `getKrxStock()`
- **Caching**: Room DB (ETF holdings 365-day TTL, fundamental 730-day TTL, incremental updates)
- **Rate limiting**: 500ms in EtfRepository

### AI API (Claude / Gemini)
- **Credential storage**: EncryptedSharedPreferences (provider + API key)
- **Providers**: Claude Haiku, Claude Sonnet, Gemini Flash
- **Rate limiting**: 1000ms per request
- **Endpoints**: Claude `/v1/messages`, Gemini `/v1beta/models/{id}:generateContent`

### Web scrapers
| Scraper | Source | Data | Rate limit |
|---------|--------|------|------------|
| NaverFinanceScraper | finance.naver.com | Market deposit/credit trends | 500ms |
| EquityReportScraper | equity.co.kr | Analyst consensus reports | 8-16s (gamma) |
| FnGuideReportScraper | comp.fnguide.com | Analyst report summaries | 1-5s (random) |

### DART OpenAPI
- **Credential storage**: EncryptedSharedPreferences (`api_settings_encrypted`)
- **Rate limiting**: 1000ms per request (mutex-based)
- **Daily limit**: 10,000 API calls
- **Corp code mapping**: corpCode.xml (ZIP download → XML parse), cached in Room `dart_corp_code` table (30-day TTL)
- **Endpoints**: `/api/list.json` (disclosure list), `/api/corpCode.xml` (corp code master)
- **Event study**: OLS beta (120-day estimation window), CAR with [-5, +20] event window
- **Classification**: 7 event types via Korean keyword matching

### BOK ECOS
- **Credential storage**: EncryptedSharedPreferences (`api_settings_encrypted`)
- **Rate limiting**: 1000ms per request (mutex-based)
- **Base URL**: `https://ecos.bok.or.kr/api/StatisticSearch/{api_key}/json/kr/1/100/{stat_code}/{freq}/{start}/{end}/{item_code}`
- **Indicators**: base_rate (722Y001), m2 (101Y004), iip (901Y033), usd_krw (731Y001), cpi (901Y009)
- **Data lag**: 1-2 months — referenceDate minus 2 months for safety
- **Caching**: Room `macro_indicator` table + FeatureStore (Weekly TTL)
- **Macro environments**: EASING, TIGHTENING, NEUTRAL, STAGFLATION
- **Ensemble overlay**: Adjusts regime weights (not a standalone engine)

## Analysis engine
### Algorithm inventory
| # | Algorithm | Class | File | Output range | Calibrated |
|---|-----------|-------|------|-------------|------------|
| 1 | Naive Bayes | `NaiveBayesEngine` | `data/engine/NaiveBayesEngine.kt` | [0,1] × 3 classes (UP/DOWN/SIDEWAYS) | No |
| 2 | Logistic Regression | `LogisticScoringEngine` | `data/engine/LogisticScoringEngine.kt` | [0,1] probability + 0–100 score | No |
| 3 | Hidden Markov Model | `HmmRegimeEngine` | `data/engine/HmmRegimeEngine.kt` | 4 regimes, [0,1]⁴ probabilities | No |
| 4 | Pattern Scanning | `PatternScanEngine` | `data/engine/PatternScanEngine.kt` | Win rates [0,1], returns | No |
| 5 | Weighted Signal | `SignalScoringEngine` | `data/engine/SignalScoringEngine.kt` | 0–100 score + direction | No |
| 6 | Rolling Correlation | `CorrelationEngine` | `data/engine/CorrelationEngine.kt` | r ∈ [-1,1], lag ∈ [-5,5] | No |
| 7 | Bayesian Updating | `BayesianUpdateEngine` | `data/engine/BayesianUpdateEngine.kt` | [0.001,0.999] posterior | No |
| 8 | Order Flow | `OrderFlowEngine` | `data/engine/OrderFlowEngine.kt` | [0,1] buyerDominanceScore | No |
| 9 | DART Event Study | `DartEventEngine` | `data/engine/DartEventEngine.kt` | [0,1] signalScore | No |

### Ensemble orchestrator
- **Class**: `StatisticalAnalysisEngine`
- **File**: `data/engine/StatisticalAnalysisEngine.kt`
- **Aggregation**: Regime-aware weighting via `RegimeWeightTable` — runs all 9 engines in parallel via coroutines and returns individual results in `StatisticalResult`. The LLM (or AI API) synthesizes the final interpretation via `ProbabilisticPromptBuilder`.
- **Failure isolation**: Each engine failure is caught individually; remaining results are returned with `null` for failed engines.

### Probability interpreter
- **Class**: `ProbabilityInterpreter`
- **File**: `domain/usecase/ProbabilityInterpreter.kt`
- **Purpose**: Local (non-LLM) interpretation of statistical results — provides textual summary when AI API is unavailable

### AI analysis pipeline
- **Use case**: `AnalyzeStockProbabilityUseCase` → `StatisticalAnalysisEngine` → `ProbabilisticPromptBuilder` → `AiApiClient` → `AnalysisResponseParser`
- **Fallback**: If AI unavailable, `ProbabilityInterpreter` provides local interpretation

## Korean market conventions
### Session hours
`TradingHours` object in `domain/model/RealtimeSupplyModels.kt`: 09:00–15:30 KST, weekday check (Saturday/Sunday excluded). Used by `OscillatorViewModel` to gate auto-refresh.

### Holiday handling
**No KRX holiday calendar.** Only weekend exclusion via `DayOfWeek` check. Korean market holidays (Lunar New Year, Chuseok, etc.) are not handled — the app will attempt data fetches on holidays and rely on API no-data responses + 1-hour cooldown.

### Ticker format
6-digit numeric strings (e.g., `005930` for Samsung Electronics). KOSPI and KOSDAQ tickers follow the same format. Market type stored in `StockMasterEntity.market` field.

## Security rules (KIS API credentials)
- All API credentials stored in `EncryptedSharedPreferences` (`api_settings_encrypted`) with AES256-SIV key encryption and AES256-GCM value encryption
- `ApiConfigProvider` caches credentials in memory with volatile + mutex pattern
- Certificate pinning for Kiwoom and KIS TLS connections
- **Never hardcode** API keys, tokens, or secrets in source files
- **Never log** credential values — use masked `toString()` for debug output
- OAuth2 tokens auto-expire 1 min before server expiry; refresh handled in API clients
- ProGuard strips logs in release builds

## Background jobs (WorkManager)
| Worker class | Default schedule | Purpose | Trigger |
|-------------|-----------------|---------|---------|
| `EtfUpdateWorker` | 00:30 daily | Sync ETF list + holdings from KRX | Scheduled + manual |
| `MarketOscillatorUpdateWorker` | 01:00 daily | KOSPI/KOSDAQ overbought/oversold indices | Scheduled + manual |
| `MarketDepositUpdateWorker` | 02:00 daily | Market deposit/credit from Naver | Scheduled + manual |
| `ThemeUpdateWorker` | 02:30 daily | Kiwoom 테마(ka90001/ka90002) 그룹 + 구성종목 | Scheduled + manual |
| `ConsensusUpdateWorker` | 03:00 daily | Analyst consensus reports (Equity + FnGuide) | Scheduled + manual |
| `FearGreedUpdateWorker` | 04:00 daily | 7-indicator fear/greed index | Scheduled + manual |
| `MarketCloseRefreshWorker` | 19:00 daily | Replace intraday data with confirmed close-of-day | Scheduled + manual |
| `MacroUpdateWorker` | Sun 05:30 weekly | BOK ECOS 5 macro indicators + YoY | Scheduled + manual |
| `DataIntegrityCheckWorker` | Manual only | Comprehensive data validation and repair | Manual |

All workers: network-constrained, exponential backoff (30s initial), foreground service (DATA_SYNC), results logged to `worker_logs` table. Schedules are user-configurable in Settings and restored on app startup in `TinyOscillatorApp.onCreate()`.

## Room database (v31)
### Migration
- All migrations `MIGRATION_1_2` ~ `MIGRATION_30_31` are defined in `core/database/migration/AppDatabaseMigrations.kt` (Phase 4.5 분할 결과) and wired via `.addMigrations(*AppDatabaseMigrations.ALL)` in `DatabaseModule.provideAppDatabase`. Schema JSONs are exported at `app/schemas/com.tinyoscillator.core.database.AppDatabase/2.json ~ 31.json`.
- No `fallbackToDestructiveMigration()` — all upgrades are explicit.
- `MIGRATION_29_30` (Step 3): adds `theme_group` + `theme_stock` tables (Kiwoom ka90001/ka90002).
- `MIGRATION_30_31` (Step 9): drops legacy `sector_master` + `sector_index_candle` tables and their indexes after the 업종지수 → 테마 메뉴 교체.

### Entities
| Entity | DAO | Purpose |
|--------|-----|---------|
| `StockMasterEntity` | `StockMasterDao` | KOSPI/KOSDAQ stock list (v22 added `initial_consonants`) |
| `AnalysisCacheEntity` | `AnalysisCacheDao` | Cached OHLCV + indicator data (v26 added OHLCV columns) |
| `AnalysisHistoryEntity` | `AnalysisHistoryDao` | User's analysis history (recently viewed stocks) |
| `AnalysisSnapshotEntity` | `AnalysisSnapshotDao` | Analysis snapshots (v25) |
| `FinancialCacheEntity` | `FinancialCacheDao` | KIS financial statement cache (24h TTL) |
| `EtfEntity` | `EtfDao` | ETF master list |
| `EtfHoldingEntity` | `EtfDao` | ETF portfolio composition |
| `MarketOscillatorEntity` | `MarketOscillatorDao` | Market overbought/oversold daily values |
| `MarketDepositEntity` | `MarketDepositDao` | Market deposit/credit daily values |
| `PortfolioEntity` | `PortfolioDao` | User portfolios |
| `PortfolioHoldingEntity` | `PortfolioDao` | Portfolio stock holdings (v8 added `target_price`) |
| `PortfolioTransactionEntity` | `PortfolioDao` | Buy/sell transaction records |
| `FundamentalCacheEntity` | `FundamentalCacheDao` | KRX fundamental data (730d TTL) |
| `WorkerLogEntity` | `WorkerLogDao` | Background job execution logs |
| `ConsensusReportEntity` | `ConsensusReportDao` | Analyst consensus reports (v12 added `stock_name`) |
| `FearGreedEntity` | `FearGreedDao` | Fear & Greed index daily values |
| `SignalHistoryEntity` | `CalibrationDao` | Signal calibration history (v21 added `outcome_t1/t5/t20`) |
| `CalibrationStateEntity` | `CalibrationDao` | Calibrator state persistence |
| `KospiIndexEntity` | `RegimeDao` | KOSPI daily close cache |
| `RegimeStateEntity` | `RegimeDao` | HMM regime model state |
| `FeatureCacheEntity` | `FeatureCacheDao` | Feature store cache |
| `DartCorpCodeEntity` | `DartDao` | DART corp_code ↔ ticker mapping |
| `MacroIndicatorEntity` | `MacroDao` | BOK ECOS macro indicator cache |
| `EnsembleHistoryEntity` | `EnsembleHistoryDao` | Stacking ensemble prediction history (v19) |
| `IncrementalModelStateEntity` | `IncrementalModelDao` | Online learning model state (v20) |
| `ModelDriftAlertEntity` | `IncrementalModelDao` | Drift detection alerts (v20) |
| `UserThemeEntity` | `UserThemeDao` | User-defined themes (v23) |
| `WatchlistGroupEntity` / `WatchlistItemEntity` | — | Watchlist groups and items (v24) |
| `ThemeGroupEntity` | `ThemeGroupDao` | Kiwoom ka90001 테마 그룹 마스터 (v30, PK `theme_code`) |
| `ThemeStockEntity` | `ThemeStockDao` | Kiwoom ka90002 테마 구성 종목 (v30, 복합 PK `(theme_code, stock_code)`) |

## Testing conventions
- **Framework**: JUnit4 + MockK + Turbine (Flow) + coroutines-test + MockWebServer + Robolectric
- **Test count**: 164 test files, ~1,420 tests total (all passing as of 2026-04-21; Phase 8에서 Robolectric DAO 테스트 17건 + Turbine 예시 1건 추가)
- **Main source count**: 323 Kotlin files under `app/src/main` (Phase 3.5/4.5에서 `ScraperUtils`, `DaoModule`, `AppDatabaseMigrations` 3개 파일 추가)
- **Unit test location**: `app/src/test/` — JUnit/MockK + Robolectric (Room in-memory DAO 테스트 포함). `./gradlew :app:testDebugUnitTest` 실행.
- **Instrumented test location**: `app/src/androidTest/` — Compose UI smoke tests (오실레이터/DeMark/ETF + 인프라 검증 4건). `./gradlew :app:connectedDebugAndroidTest` 로 실기기/에뮬레이터에서 실행.
- **Naming**: `ClassNameTest.kt` in same package structure as source. Robolectric 기반 DAO 테스트는 `ClassNameInMemoryTest.kt` 네이밍 사용.
- **Config**: `testOptions { unitTests.isReturnDefaultValues = true; unitTests.isIncludeAndroidResources = true }`. Robolectric 테스트는 `@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)` 로 @HiltAndroidApp 우회.
- **Coverage**: All 9 statistical engines tested, all repositories tested, most ViewModels tested, 주요 DAO 4개(UserTheme·AnalysisHistory·WorkerLog·StockMaster) in-memory Room 테스트 완료
- **Turbine 사용 기준**: 단건 `.value` 체크가 대부분 충분. 중간 emit 관찰(Loading→Success→Error), cold `Flow`, "더 이상 emit 되지 않음" 검증 시에만 Turbine 사용.

## Known issues and TODOs
### Blocking
None found in current codebase (no TODO/FIXME/HACK/XXX markers).

### Non-blocking
- No KRX holiday calendar — app retries on holidays until cooldown
- MarketOscillatorCalculator does not cache raw KRX OHLCV in Room for incremental updates

### Refactor backlog (2026-04-20 code review → 2026-04-21 Phase 1~8 + 3.5 + 4.5 완료)
필수·선택·polish 전부 완료.

Phase 3.5 핵심 변경 (API/Scraper 중복 제거):
- `BaseApiClient.executeWithRetry` 헬퍼 도입 → 3개 API 클라이언트의 auth/retriable retry 중복 100+ 줄 제거
- `ScraperUtils` (uniform/gamma random delay) + `ApiConstants`의 scraper timeout 상수 추가

Phase 4.5 (DatabaseModule 분할):
- `DatabaseModule.kt` 804줄 → 101줄 (빌더 + 백업만)
- 신규 `DaoModule.kt` (102줄, DAO 프로바이더 전담), `AppDatabaseMigrations.kt` (683줄, 25개 Migration)

## Naming conventions
- Use case: `동사 + 명사 + UseCase` (e.g., `AnalyzeStockProbabilityUseCase`)
- Repository: `명사 + Repository` (e.g., `StatisticalRepository`)
- Engine: `명사 + Engine` (e.g., `NaiveBayesEngine`)
- Result: `명사 + Result` (e.g., `BayesResult`)
- Worker: `명사 + Worker` (e.g., `EtfUpdateWorker`)
- Korean comments allowed; code identifiers in English

