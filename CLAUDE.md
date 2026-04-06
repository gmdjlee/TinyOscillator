# CLAUDE.md — TinyOscillator Project Guide

## Project overview
TinyOscillator is a Korean stock market analysis Android app targeting retail investors. It provides oscillator-based technical analysis, DeMark TD Sequential, financial statement analysis, ETF sector analysis, market Fear & Greed index, analyst consensus reports, AI-powered analysis (Claude/Gemini API), and portfolio management. The app is written entirely in Kotlin. It is in active development with ~1,400 passing tests and a mature MVVM + Clean Architecture codebase.

## Repository layout
```
TinyOscillator/
├── app/
│   ├── build.gradle.kts              # App-level build config
│   ├── proguard-rules.pro
│   ├── schemas/                       # Room schema exports (v1–v13)
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
│       │       │   └── worker/        # WorkManager workers (7 workers + helpers)
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
│       │       │   └── viewmodel/     # OscillatorVM, StockAnalysisVM
│       │       └── ui/theme/          # Material3 theme (Color, Type, Theme)
│       └── test/                      # 98 unit test files
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
| `AppDatabase` | core/database | `AppDatabase.kt` | Room DB v18, 22 entities, 17 DAOs |
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
- **Endpoints**: Daily OHLCV, investor trend, stock info, stock list (KOSPI + KOSDAQ)

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
| `ConsensusUpdateWorker` | 03:00 daily | Analyst consensus reports (Equity + FnGuide) | Scheduled + manual |
| `FearGreedUpdateWorker` | 04:00 daily | 7-indicator fear/greed index | Scheduled + manual |
| `MarketCloseRefreshWorker` | 19:00 daily | Replace intraday data with confirmed close-of-day | Scheduled + manual |
| `MacroUpdateWorker` | Sun 05:30 weekly | BOK ECOS 5 macro indicators + YoY | Scheduled + manual |
| `DataIntegrityCheckWorker` | Manual only | Comprehensive data validation and repair | Manual |

All workers: network-constrained, exponential backoff (30s initial), foreground service (DATA_SYNC), results logged to `worker_logs` table. Schedules are user-configurable in Settings and restored on app startup in `TinyOscillatorApp.onCreate()`.

## Room database (v17)
### Entities
| Entity | DAO | Purpose |
|--------|-----|---------|
| `StockMasterEntity` | `StockMasterDao` | KOSPI/KOSDAQ stock list |
| `AnalysisCacheEntity` | `AnalysisCacheDao` | Cached OHLCV + indicator data per stock per date |
| `AnalysisHistoryEntity` | `AnalysisHistoryDao` | User's analysis history (recently viewed stocks) |
| `FinancialCacheEntity` | `FinancialCacheDao` | KIS financial statement cache (24h TTL) |
| `EtfEntity` | `EtfDao` | ETF master list |
| `EtfHoldingEntity` | `EtfDao` | ETF portfolio composition |
| `MarketOscillatorEntity` | `MarketOscillatorDao` | Market overbought/oversold daily values |
| `MarketDepositEntity` | `MarketDepositDao` | Market deposit/credit daily values |
| `PortfolioEntity` | `PortfolioDao` | User portfolios |
| `PortfolioHoldingEntity` | `PortfolioDao` | Portfolio stock holdings |
| `PortfolioTransactionEntity` | `PortfolioDao` | Buy/sell transaction records |
| `FundamentalCacheEntity` | `FundamentalCacheDao` | KRX fundamental data (730d TTL) |
| `WorkerLogEntity` | `WorkerLogDao` | Background job execution logs |
| `ConsensusReportEntity` | `ConsensusReportDao` | Analyst consensus reports |
| `FearGreedEntity` | `FearGreedDao` | Fear & Greed index daily values |
| `SignalHistoryEntity` | `CalibrationDao` | Signal calibration history |
| `CalibrationStateEntity` | `CalibrationDao` | Calibrator state persistence |
| `KospiIndexEntity` | `RegimeDao` | KOSPI daily close cache |
| `RegimeStateEntity` | `RegimeDao` | HMM regime model state |
| `FeatureCacheEntity` | `FeatureCacheDao` | Feature store cache |
| `DartCorpCodeEntity` | `DartDao` | DART corp_code ↔ ticker mapping |
| `MacroIndicatorEntity` | `MacroDao` | BOK ECOS macro indicator cache |

## Testing conventions
- **Framework**: JUnit4 + MockK + Turbine (Flow) + coroutines-test + MockWebServer
- **Test count**: 101 test files, ~1,429 tests total (all passing as of 2026-04-03)
- **Location**: `app/src/test/` (unit tests only; no androidTest instrumented tests)
- **Naming**: `ClassNameTest.kt` in same package structure as source
- **Config**: `testOptions { unitTests.isReturnDefaultValues = true }`
- **Coverage**: All 7 engines tested, all repositories tested, all ViewModels tested
- **Gap**: No Compose UI tests, no Room DAO instrumented tests (requires androidTest setup)

## Known issues and TODOs
### Blocking
None found in current codebase (no TODO/FIXME/HACK/XXX markers).

### Non-blocking
- No KRX holiday calendar — app retries on holidays until cooldown
- No androidTest infrastructure for Compose UI and Room DAO tests
- MarketOscillatorCalculator does not cache raw KRX OHLCV in Room for incremental updates

## Naming conventions
- Use case: `동사 + 명사 + UseCase` (e.g., `AnalyzeStockProbabilityUseCase`)
- Repository: `명사 + Repository` (e.g., `StatisticalRepository`)
- Engine: `명사 + Engine` (e.g., `NaiveBayesEngine`)
- Result: `명사 + Result` (e.g., `BayesResult`)
- Worker: `명사 + Worker` (e.g., `EtfUpdateWorker`)
- Korean comments allowed; code identifiers in English

## Upcoming feature expansion
The following 11 features will be added in numbered prompt sessions (PROMPT 01–11).
Each prompt will update TASK.md and PROGRESS.md on completion.

| # | Feature | Key new file(s) |
|---|---------|-----------------|
| 01 | Signal Calibration | calibration/signal_calibrator.py, validation/walk_forward_validator.py |
| 02 | Regime Detection | regime/market_regime_classifier.py |
| 03 | Feature Store | FeatureStore.kt, FeatureCacheDao.kt |
| 04 | Order Flow | features/order_flow_features.py |
| 05 | DART Event Study | dart/dart_disclosure_fetcher.py, dart/event_study_engine.py |
| 06 | BOK ECOS Macro | macro/bok_ecos_collector.py |
| 07 | Stacking Ensemble | ensemble/stacking_ensemble.py |
| 08 | Kelly + CVaR | risk/kelly_position_sizer.py, risk/cvar_risk_overlay.py |
| 09 | Incremental Learning | models/incremental_models.py |
| 10 | Korea 5-Factor | factors/factor_model.py |
| 11 | Sector Network + Vectorized | network/sector_correlation_network.py, indicators/vectorized_indicators.py |

**Note**: The roadmap references `.py` files, but the current codebase has no Python/Chaquopy layer. These features will require adding Chaquopy to the build or implementing in pure Kotlin. This decision should be made in PROMPT 01.
