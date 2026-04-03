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
- **Aggregation**: Regime-aware weighting via `RegimeWeightTable` — all 9 engines run in parallel via coroutineScope/async; results collected into `StatisticalResult` with individual fields + `MarketRegimeResult`. The AI API (via `ProbabilisticPromptBuilder`) or `ProbabilityInterpreter` synthesizes the final interpretation with regime context.
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

### [COMPLETE] PROMPT 03 — Feature Store (2026-04-02)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01/02
- New source files:
  - `core/database/entity/FeatureCacheEntity.kt` — Room entity (PK=key, ticker index, computed_at index)
  - `core/database/dao/FeatureCacheDao.kt` — get, upsert, evictExpired, evictByTicker, evictAll, count()
  - `domain/model/FeatureStoreModels.kt` — FeatureKey (ticker:feature:date), FeatureTtl (Intraday/Daily/Weekly/Custom), CacheStats
  - `data/engine/FeatureStore.kt` — Singleton with generic `getOrCompute<T>(key, ttl, serializer, compute)`, cache stats Flow
  - `core/worker/FeatureCacheEvictionWorker.kt` — Daily 06:00 KST eviction of expired entries
- Modified files:
  - `core/database/AppDatabase.kt` — v15→v16, FeatureCacheEntity + featureCacheDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_15_16 (feature_cache table), provideFeatureCacheDao()
  - `core/worker/WorkManagerHelper.kt` — scheduleFeatureCacheEviction(), cancelFeatureCacheEviction(), runFeatureCacheEvictionNow()
  - `TinyOscillatorApp.kt` — Schedule feature cache eviction on startup
  - `domain/model/StatisticalModels.kt` — @Serializable on all result types (StatisticalResult, BayesResult, LogisticResult, HmmResult, PatternAnalysis, SignalScoringResult, CorrelationAnalysis, BayesianUpdateResult, etc.)
  - `domain/model/RegimeModels.kt` — @Serializable on MarketRegimeResult
  - `data/engine/StatisticalAnalysisEngine.kt` — FeatureStore injection, analyze() wraps analyzeInternal() via getOrCompute (Daily TTL), clearAnalysisCache()
  - `presentation/ai/AiAnalysisViewModel.kt` — FeatureStore injection, cacheStats StateFlow, clearAnalysisCache()
  - `presentation/ai/AiAnalysisScreen.kt` — Cached/Live SuggestionChip in probability result header
- Tests added (1 file):
  - `FeatureStoreTest.kt` — 12 tests: cache miss/hit, TTL expiry, recompute, invalidate, key format, TTL constants, cache stats
- Existing tests updated: StatisticalAnalysisEngineTest, AnalyzeStockProbabilityUseCaseTest, AiAnalysisViewModelTest (added featureStore parameter)

### [COMPLETE] PROMPT 04 — Order Flow Features (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation — investor data (foreignNetBuy, instNetBuy) already available in DailyTrading via AnalysisCacheEntity
- No new API calls needed — data source: existing KRX/Kiwoom investor trend endpoints cached in analysis_cache table
- New source files:
  - `data/engine/OrderFlowEngine.kt` — 8th engine: OFI (5d/20d), institutional divergence, foreign buy pressure, Z-score sigmoid signal, trend alignment, mean reversion detection
  - `domain/model/StatisticalModels.kt` — OrderFlowResult data class with 12 fields
- Modified files:
  - `data/engine/StatisticalAnalysisEngine.kt` — OrderFlowEngine injection, parallel async execution, result in StatisticalResult
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_ORDER_FLOW constant, ALL_ALGOS updated to 8, weights redistributed (OrderFlow: BULL 0.14, BEAR 0.17, SIDEWAYS 0.15, CRISIS 0.18)
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts buyerDominanceScore for OrderFlow
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretOrderFlow(), updated summarize(), assessOverallDirection(), buildPromptForAi() (8개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — OrderFlow section in AI prompt (8개 알고리즘)
  - `presentation/ai/AiAnalysisScreen.kt` — Order Flow expandable card (direction, OFI, divergence, trend alignment)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes orderflow
- Tests added (1 file):
  - `OrderFlowEngineTest.kt` — 14 tests: bounds checking (OFI [-1,1], signal [0,1], divergence [0,1], fbp [-1,1]), BUY/SELL direction, divergence high/low, trend alignment, zero flow, insufficient data, analysis details keys
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added orderFlowEngine parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added orderFlowEngine parameter
  - `RegimeWeightTableTest.kt` — updated from 7 to 8 algorithms

### [COMPLETE] PROMPT 05 — DART Event Study (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–04
- DART API key stored in EncryptedSharedPreferences via Settings screen
- Corp code mapping: DART corpCode.xml (ZIP) → Room `dart_corp_code` table (30-day cache)
- Event study: OLS beta estimation (120-day window, min 60 obs), CAR with [-5, +20] event window
- Classification: 7 event types (유상증자/자사주/지분변동/경영진변동/실적/배당/기타) via keyword matching
- Signal: time-weighted CAR average → sigmoid → [0,1] signal score
- New source files:
  - `core/api/DartApiClient.kt` — DART REST API client (corp_code XML download/parse, disclosure list fetch, 1000ms rate limit)
  - `data/engine/DartEventEngine.kt` — 9th engine: resolveCorpCode, fetchDisclosures, classify, estimateBeta, computeCar, buildSignal
  - `domain/model/DartModels.kt` — DartDisclosure, CorpCodeEntry, EventStudyResult, DartEventResult, DartEventType (7 types + classify + toKorean)
  - `core/database/entity/DartCorpCodeEntity.kt` — Room entity (PK=ticker, corp_code unique index)
  - `core/database/dao/DartDao.kt` — getCorpCode, insertAll, count, lastUpdatedAt, deleteAll
- Modified files:
  - `core/database/AppDatabase.kt` — v16→v17, DartCorpCodeEntity + dartDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_16_17 (dart_corp_code table), provideDartDao()
  - `core/di/AppModule.kt` — provideDartApiClient()
  - `core/config/ApiConfigProvider.kt` — getDartApiKey() with volatile+mutex cache
  - `presentation/settings/SettingsScreen.kt` — PrefsKeys.DART_API_KEY, loadDartApiKey(), saveDartApiKey(), dartApiKey state
  - `presentation/settings/ApiKeySettingsSection.kt` — DART OpenAPI section (API key field + info)
  - `domain/model/StatisticalModels.kt` — dartEventResult field in StatisticalResult
  - `data/engine/StatisticalAnalysisEngine.kt` — DartEventEngine injection, parallel async execution, apiConfigProvider
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_DART_EVENT constant, ALL_ALGOS updated to 9, weights redistributed (DartEvent: BULL 0.10, BEAR 0.13, SIDEWAYS 0.11, CRISIS 0.14)
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts signalScore for DartEvent (when nEvents > 0)
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretDartEvent(), updated summarize(), assessOverallDirection(), buildPromptForAi() (9개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — DartEvent section in AI prompt (9개 알고리즘)
  - `presentation/ai/AiAnalysisScreen.kt` — DART 공시 이벤트 expandable card (event type, CAR, t-stat, significance ★)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes dartevent
- Tests added (1 file):
  - `DartEventEngineTest.kt` — 20 tests: classify covers all 7 event types, computeCar zero AR, positive CAR, insufficient data, estimateBeta default/correct, analyze null/blank key, corp_code not found, no disclosures, signal bounds [0,1], computeLogReturns, all types have Korean labels, corp_code cache
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added dartEventEngine + apiConfigProvider parameters
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added dartEventEngine + apiConfigProvider parameters
  - `RegimeWeightTableTest.kt` — updated from 8 to 9 algorithms

### [COMPLETE] PROMPT 06 — BOK ECOS Macro (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–05
- BOK ECOS API key stored in EncryptedSharedPreferences via Settings screen
- 5 indicators: base_rate (722Y001), m2 (101Y004), iip (901Y033), usd_krw (731Y001), cpi (901Y009)
- YoY computation: base_rate uses absolute change (pp), others use percentage change
- 4 macro environments: EASING, TIGHTENING, NEUTRAL, STAGFLATION
- Classification priority: STAGFLATION > TIGHTENING/EASING > NEUTRAL
- Weight overlay: adjusts regime weights (momentum ↓/↑, HMM ↑/↓, DartEvent/OrderFlow ↑), normalizes to sum=1.0
- Data lag: ECOS data 1-2 month lag → referenceDate minus 2 months for safety
- Caching: FeatureStore with Weekly TTL + Room DB macro_indicator table
- New source files:
  - `core/api/BokEcosApiClient.kt` — ECOS REST API client (rate limited 1000ms, JSON parsing)
  - `data/engine/macro/BokEcosCollector.kt` — 24-month fetch, YoY computation, ffill (max 3 months)
  - `data/engine/macro/MacroRegimeOverlay.kt` — 4-env classification + ensemble weight adjustment (all rules as constants)
  - `domain/model/MacroModels.kt` — EcosIndicatorSpec, EcosDataPoint, MacroEnvironment, MacroSignalResult
  - `core/database/entity/MacroIndicatorEntity.kt` — Room entity (PK=id, indicator_key+year_month indexes)
  - `core/database/dao/MacroDao.kt` — getByIndicator, getByMonth, insertAll, deleteOlderThan
  - `core/worker/MacroUpdateWorker.kt` — Weekly HiltWorker (fetches, classifies, caches, cleans up 36-month cutoff)
- Modified files:
  - `core/database/AppDatabase.kt` — v17→v18, MacroIndicatorEntity + macroDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_17_18 (macro_indicator table), provideMacroDao()
  - `core/di/AppModule.kt` — provideBokEcosApiClient()
  - `core/config/ApiConfigProvider.kt` — getEcosApiKey() with volatile+mutex cache
  - `data/engine/FeatureStore.kt` — Added put() method for direct cache writes
  - `data/engine/StatisticalAnalysisEngine.kt` — MacroRegimeOverlay injection, cachedMacroSignal, updateMacroSignal(), macro overlay in getRegimeWeights(), macroSignalResult in analyzeInternal()
  - `domain/model/StatisticalModels.kt` — macroSignalResult field in StatisticalResult
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretMacro(), macro section in buildPromptForAi()
  - `data/mapper/ProbabilisticPromptBuilder.kt` — macro environment section in user prompt
  - `presentation/ai/AiAnalysisScreen.kt` — Macro environment chip (color-coded, tappable) + expandable bottom sheet with 5 YoY values + expandable card
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes macro
  - `presentation/settings/SettingsScreen.kt` — PrefsKeys.ECOS_API_KEY, loadEcosApiKey(), saveEcosApiKey(), ecosApiKey state
  - `presentation/settings/ApiKeySettingsSection.kt` — BOK ECOS API section (API key field + info)
  - `core/worker/WorkManagerHelper.kt` — scheduleMacroUpdate() (weekly), cancelMacroUpdate(), runMacroUpdateNow()
  - `TinyOscillatorApp.kt` — Schedule macro update on startup (Sunday 05:30)
- Tests added (2 files):
  - `BokEcosCollectorTest.kt` — 9 tests: blank key, insufficient data, valid result, ffill, ffill gap limit, base_rate absolute change, percentage change, zero division, closest month fallback
  - `MacroRegimeOverlayTest.kt` — 16 tests: all 4 environments classified, STAGFLATION priority, boundary value, NEUTRAL no-change, weight sum=1.0 for all regime×env combinations, TIGHTENING/EASING/STAGFLATION weight adjustments, empty weights, positive weights, normalize, applyClassification, MacroEnvironment round-trip
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added macroRegimeOverlay parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added macroRegimeOverlay parameter

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
