# PROGRESS.md — Implementation State

_Last updated: 2026-04-02 | Session: PROMPT 00 — Baseline Sync_

---

## Baseline architecture snapshot
_Captured by PROMPT 00. Do not manually edit this section — it is the reference state
against which all future changes are measured._

### Kotlin source files
134 source files in `app/src/main/java/com/tinyoscillator/`
98 test files in `app/src/test/java/com/tinyoscillator/`
0 androidTest files

### Python analysis layer
| File | Purpose | Status |
|------|---------|--------|
| `concen/equity_report_scraper_450.py` | Standalone analyst report scraper (not in Android build) | External tool |

**No Python files exist in the Android app.** There is no `app/src/main/python/` directory and no Chaquopy integration.

### C++ / JNI layer
| File | Purpose | Status |
|------|---------|--------|
| `app/src/main/cpp/CMakeLists.txt` | CMake build for llama.cpp JNI | Present |
| `app/src/main/cpp/llama_jni.cpp` | JNI bridge to llama.cpp for local LLM inference | Present |
| `app/src/main/cpp/llama_jni_stub.cpp` | Stub implementation for builds without llama.cpp | Present |

### Algorithm registry
| # | Algorithm | Class | File | Output range | Calibrated | Ensemble weight |
|---|-----------|-------|------|-------------|------------|----------------|
| 1 | Naive Bayes | `NaiveBayesEngine` | `data/engine/NaiveBayesEngine.kt` | [0,1] × 3 classes | No | Equal (no weighting) |
| 2 | Logistic Regression | `LogisticScoringEngine` | `data/engine/LogisticScoringEngine.kt` | [0,1] + 0–100 | No | Equal |
| 3 | HMM Regime | `HmmRegimeEngine` | `data/engine/HmmRegimeEngine.kt` | 4 regimes, [0,1]⁴ | No | Equal |
| 4 | Pattern Scan | `PatternScanEngine` | `data/engine/PatternScanEngine.kt` | Win rates [0,1] | No | Equal |
| 5 | Signal Scoring | `SignalScoringEngine` | `data/engine/SignalScoringEngine.kt` | 0–100 + direction | No | Win-rate weighted (internal) |
| 6 | Rolling Correlation | `CorrelationEngine` | `data/engine/CorrelationEngine.kt` | r ∈ [-1,1] | No | Equal |
| 7 | Bayesian Updating | `BayesianUpdateEngine` | `data/engine/BayesianUpdateEngine.kt` | [0.001,0.999] | No | Equal |

### Ensemble orchestrator
- **Class**: `StatisticalAnalysisEngine`
- **File**: `data/engine/StatisticalAnalysisEngine.kt`
- **Aggregation**: Regime-aware weighting via `RegimeWeightTable` — all 7 engines run in parallel via coroutineScope/async; results collected into `StatisticalResult` with individual fields + `MarketRegimeResult`. The AI API (via `ProbabilisticPromptBuilder`) or `ProbabilityInterpreter` synthesizes the final interpretation with regime context.
- **Regime integration**: `MarketRegimeClassifier` provides current market regime (BULL_LOW_VOL/BEAR_HIGH_VOL/SIDEWAYS/CRISIS) and per-algorithm weight table. Regime result cached and updated weekly by `RegimeUpdateWorker`.

### Room database entities (v13)
| Entity | DAO | Purpose |
|--------|-----|---------|
| `StockMasterEntity` | `StockMasterDao` | KOSPI/KOSDAQ stock list |
| `AnalysisCacheEntity` | `AnalysisCacheDao` | Per-stock per-date OHLCV + indicators |
| `AnalysisHistoryEntity` | `AnalysisHistoryDao` | Recently analyzed stocks |
| `FinancialCacheEntity` | `FinancialCacheDao` | KIS financial statements (24h TTL) |
| `EtfEntity` | `EtfDao` | ETF master list |
| `EtfHoldingEntity` | `EtfDao` | ETF portfolio composition |
| `MarketOscillatorEntity` | `MarketOscillatorDao` | Market overbought/oversold |
| `MarketDepositEntity` | `MarketDepositDao` | Market deposit/credit |
| `PortfolioEntity` | `PortfolioDao` | User portfolios |
| `PortfolioHoldingEntity` | `PortfolioDao` | Portfolio holdings |
| `PortfolioTransactionEntity` | `PortfolioDao` | Buy/sell transactions |
| `FundamentalCacheEntity` | `FundamentalCacheDao` | KRX fundamental data (730d TTL) |
| `WorkerLogEntity` | `WorkerLogDao` | Worker execution logs |
| `ConsensusReportEntity` | `ConsensusReportDao` | Analyst consensus reports |
| `FearGreedEntity` | `FearGreedDao` | Fear & Greed index |

### Hilt DI modules
| Module | File | Bindings |
|--------|------|----------|
| `AppModule` | `core/di/AppModule.kt` | OkHttpClient, API clients, repositories, use cases |
| `DatabaseModule` | `core/di/DatabaseModule.kt` | AppDatabase (v1→v13 migrations), all DAOs |
| `StatisticalModule` | `core/di/StatisticalModule.kt` | StatisticalRepository, LlmRepository bindings, LogisticPrefs |
| `WorkerModule` | `core/di/WorkerModule.kt` | WorkManager Configuration with HiltWorkerFactory |

### WorkManager jobs
| Worker class | Default schedule | Purpose | Status |
|-------------|-----------------|---------|--------|
| `EtfUpdateWorker` | 00:30 | Sync ETF list + holdings (KRX) | Active |
| `MarketOscillatorUpdateWorker` | 01:00 | KOSPI/KOSDAQ oscillator (KRX) | Active |
| `MarketDepositUpdateWorker` | 02:00 | Deposit/credit (Naver scrape) | Active |
| `ConsensusUpdateWorker` | 03:00 | Analyst reports (Equity + FnGuide) | Active |
| `FearGreedUpdateWorker` | 04:00 | Fear/Greed 7-indicator index | Active |
| `MarketCloseRefreshWorker` | 19:00 | Replace intraday with close-of-day | Active |
| `DataIntegrityCheckWorker` | Manual | Comprehensive data validation | Active |

### API clients
| Client | Base URL | Auth | Rate limit |
|--------|----------|------|------------|
| `KiwoomApiClient` | mockapi/api.kiwoom.com | OAuth2 token | 500ms |
| `KisApiClient` | KIS OpenAPI | OAuth2 token | 500ms |
| `KrxApiClient` | kotlin_krx library | Username/password | None (library-level) |
| `AiApiClient` | Claude/Gemini endpoints | API key header | 1000ms |

### Web scrapers
| Scraper | Source | Rate limit |
|---------|--------|------------|
| `NaverFinanceScraper` | finance.naver.com | 500ms |
| `EquityReportScraper` | equity.co.kr | 8-16s gamma |
| `FnGuideReportScraper` | comp.fnguide.com | 1-5s random |

### KIS API integration
- **Credential storage**: EncryptedSharedPreferences (`api_settings_encrypted`), AES256
- **Token refresh**: `KisApiClient.getToken()` → mutex-protected, 1 min early expiry
- **Rate limiting**: 500ms mutex-based in `BaseApiClient`
- **Active endpoints**: Financial statements (balance sheet, income, profitability, stability, growth)

### Known technical debt
**Blocking**: None

**Non-blocking**:
- No KRX holiday calendar in `TradingHours` (weekday-only check)
- No androidTest infrastructure
- MarketOscillatorCalculator lacks Room caching for KRX OHLCV
- JNI llama.cpp bridge present but unused (AI API used instead)
- No TODO/FIXME/HACK markers found in codebase

---

## Feature expansion log
_Each completed PROMPT session appends one block below._

### [COMPLETE] PROMPT 01 — Signal Calibration (2026-04-02)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python)
- New source files:
  - `data/engine/calibration/SignalCalibrator.kt` — Isotonic (PAVA) + Platt sigmoid calibrators
  - `data/engine/calibration/WalkForwardValidator.kt` — Walk-forward time-series cross-validation
  - `data/engine/calibration/CalibrationMonitor.kt` — Rolling window Brier/ECE monitor with recalibration flag
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts raw 0-1 bullish scores from 6 engines
  - `domain/model/CalibrationModels.kt` — CalibrationMetrics, CalibratedScore, CalibratorState, WalkForwardResult, RawSignalScore, ReliabilityBin
  - `core/database/entity/SignalHistoryEntity.kt` — signal_history table
  - `core/database/entity/CalibrationStateEntity.kt` — calibration_state table
  - `core/database/dao/CalibrationDao.kt` — DAO for both tables
- Modified files:
  - `core/database/AppDatabase.kt` — v13→v14, 2 new entities + calibrationDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_13_14, provideCalibrationDao()
  - `data/engine/StatisticalAnalysisEngine.kt` — records signal history, exposes getCalibratedScores()
- Tests added (4 files):
  - `SignalCalibratorTest.kt` — fit/transform roundtrip, save/load state, all 6 algo names, edge cases
  - `WalkForwardValidatorTest.kt` — no overlap, no future leakage, correct fold count, metrics
  - `CalibrationMonitorTest.kt` — rolling window, recalibration flag trigger, ECE, multi-algo
  - `SignalScoreExtractorTest.kt` — extraction from all engine types, range validation
- Calibrated algorithms: NaiveBayes, Logistic, HMM, PatternScan, SignalScoring, BayesianUpdate (6 of 7; Correlation excluded — no scalar probability output)

### [COMPLETE] PROMPT 02 — Market Regime Detection (2026-04-02)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01
- KOSPI index data: `KrxIndex.getKospi(startDate, endDate)` returns `List<IndexOhlcv>` with OHLCV data
- New source files:
  - `data/engine/regime/GaussianHmm.kt` — 4-state diagonal-covariance Gaussian HMM with Baum-Welch (EM) training, Forward-Backward, Viterbi, serialization
  - `data/engine/regime/MarketRegimeClassifier.kt` — KOSPI regime classifier (4 features: log return, 20d realized vol, 60d momentum, 20d skewness), state labeling, heuristic fallback
  - `data/engine/regime/RegimeWeightTable.kt` — Regime-specific ensemble algorithm weights (BULL_LOW_VOL: momentum, BEAR_HIGH_VOL: correlation/HMM, SIDEWAYS: mean-reversion, CRISIS: HMM/Bayesian), `validateWeights()` assertion
  - `domain/model/RegimeModels.kt` — MarketRegimeResult(regimeId, regimeName, regimeDescription, confidence, probaVec, regimeDurationDays)
  - `core/database/entity/KospiIndexEntity.kt` — KOSPI daily close cache (504d, 1-day TTL)
  - `core/database/entity/RegimeStateEntity.kt` — HMM model state persistence (JSON serialized)
  - `core/database/dao/RegimeDao.kt` — DAO for kospi_index + regime_state
  - `core/worker/RegimeUpdateWorker.kt` — Weekly (Sunday 05:00) retraining WorkManager job
- Modified files:
  - `core/database/AppDatabase.kt` — v14→v15, 2 new entities + regimeDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_14_15, provideRegimeDao()
  - `data/engine/StatisticalAnalysisEngine.kt` — MarketRegimeClassifier injection, cachedRegimeResult, updateRegimeResult(), getRegimeWeights(), regime transition logging
  - `domain/model/StatisticalModels.kt` — Added marketRegimeResult field to StatisticalResult
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretMarketRegime(), regime info in AI prompt
  - `presentation/ai/AiAnalysisScreen.kt` — Regime badge chip (color-coded) + expandable card with probabilities
  - `presentation/ai/AiAnalysisViewModel.kt` — regime interpretation in interpretLocal()
  - `TinyOscillatorApp.kt` — Regime model restore on startup + schedule weekly update
  - `core/worker/WorkManagerHelper.kt` — scheduleRegimeUpdate() (weekly), cancelRegimeUpdate(), runRegimeUpdateNow()
  - `core/worker/CollectionNotificationHelper.kt` — REGIME_NOTIFICATION_ID = 1008
- Tests added (3 files):
  - `GaussianHmmTest.kt` — fit/predict, save/load roundtrip, probability normalization, determinism, transition matrix validation
  - `MarketRegimeClassifierTest.kt` — buildFeatures NaN check, fit/predict validity, save/load roundtrip, duration counter, heuristic fallback
  - `RegimeWeightTableTest.kt` — validateWeights(), weight sums, regime-specific strategy priorities, equal weights fallback
- Existing tests updated: StatisticalAnalysisEngineTest, AnalyzeStockProbabilityUseCaseTest (added MarketRegimeClassifier parameter)

### [PENDING] PROMPT 03 — Feature Store
- Status: NOT STARTED

### [PENDING] PROMPT 04 — Order Flow
- Status: NOT STARTED

### [PENDING] PROMPT 05 — DART Event Study
- Status: NOT STARTED

### [PENDING] PROMPT 06 — BOK ECOS Macro
- Status: NOT STARTED

### [PENDING] PROMPT 07 — Stacking Ensemble
- Status: NOT STARTED

### [PENDING] PROMPT 08 — Kelly + CVaR
- Status: NOT STARTED

### [PENDING] PROMPT 09 — Incremental Learning
- Status: NOT STARTED

### [PENDING] PROMPT 10 — Korea 5-Factor
- Status: NOT STARTED

### [PENDING] PROMPT 11 — Sector Network + Vectorized
- Status: NOT STARTED
