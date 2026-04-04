# PROGRESS.md — Implementation State

_Last updated: 2026-04-04 | Session: CHART-K02 — Technical Indicator Overlay_

---

## CHART-K02 — 기술 지표 오버레이 (EMA/볼린저/MACD/RSI/스토캐스틱)

### New files
| File | Purpose |
|------|---------|
| `domain/model/Indicator.kt` | Indicator enum, OverlayType, IndicatorParams 도메인 모델 |
| `domain/indicator/IndicatorCalculator.kt` | EMA, 볼린저밴드, MACD, RSI, 스토캐스틱 순수 Kotlin 계산 |
| `presentation/chart/ext/IndicatorDataSetExt.kt` | FloatArray → LineDataSet, IndicatorData → LineData 변환 |
| `presentation/chart/composable/OscillatorChartView.kt` | MACD (CombinedChart), RSI (LineChart), 스토캐스틱 서브차트 |
| `presentation/chart/composable/IndicatorSheet.kt` | BottomSheet 지표 선택기 (최대 4 가격 / 1 오실레이터) |
| `data/preferences/IndicatorPreferencesRepository.kt` | DataStore 기반 지표 선택 + 파라미터 영속화 |
| `presentation/viewmodel/StockChartViewModel.kt` | 지표 계산 + preferences 연동 ViewModel |

### Modified files
| File | Change |
|------|--------|
| `presentation/chart/composable/KoreanCandleChartView.kt` | CandleStickChart → CombinedChart, indicatorData 파라미터 추가 |
| `presentation/chart/interaction/ChartSyncManager.kt` | CandleStickChart → BarLineChartBase<*> (CombinedChart 호환) |
| `presentation/chart/interaction/InertialScrollHandler.kt` | CandleStickChart → BarLineChartBase<*> |
| `core/di/AppModule.kt` | DataStore + IndicatorPreferencesRepository DI 등록 |
| `build.gradle.kts` | DataStore preferences 의존성 추가 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `IndicatorCalculatorTest.kt` | 17 | PASS |
| `IndicatorConstraintTest.kt` | 2 | PASS |
| **Total** | **19** | **ALL PASS** |

---

## CHART-K01 — MPAndroidChart 인터랙션 개선

### New files
| File | Purpose |
|------|---------|
| `presentation/chart/marker/OhlcvMarkerView.kt` | OHLCV 크로스헤어 MarkerView (경계 감지, 시가/고가/저가/종가 + 패턴) |
| `presentation/chart/interaction/InertialScrollHandler.kt` | VelocityTracker + OverScroller 관성 스크롤 |
| `presentation/chart/interaction/ChartSyncManager.kt` | 캔들 ↔ 거래량 뷰포트/하이라이트 동기화 |
| `presentation/chart/formatter/KoreanVolumeFormatter.kt` | 거래량 축 한국식 단위 (조/억/만) |
| `presentation/chart/formatter/KoreanPriceFormatter.kt` | 가격 축 천 단위 쉼표 |
| `presentation/chart/formatter/IndexDateFormatter.kt` | X축 인덱스→날짜 포매터 |
| `presentation/chart/ext/FormatExt.kt` | Long/Float.formatKRW() 확장 |
| `presentation/chart/ext/CandleDataExt.kt` | toCandleData() / toVolumeBarData() 확장 |
| `presentation/chart/composable/KoreanCandleChartView.kt` | Compose 래퍼 (캔들 70% + 거래량 30%) |
| `res/layout/view_ohlcv_marker.xml` | 마커 레이아웃 (6 TextViews) |
| `res/drawable/ohlcv_marker_bg.xml` | 마커 배경 (rounded, semi-transparent white) |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `FormatExtTest.kt` | 5 | PASS |
| `KoreanVolumeFormatterTest.kt` | 6 | PASS |
| `CandleDataExtTest.kt` | 6 | PASS |
| `MarkerOffsetTest.kt` | 5 | PASS |
| **Total** | **22** | **ALL PASS** |

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
| 8 | Order Flow | `OrderFlowEngine` | `data/engine/OrderFlowEngine.kt` | [0,1] buyerDominanceScore | No | Regime-weighted |
| 9 | DART Event Study | `DartEventEngine` | `data/engine/DartEventEngine.kt` | [0,1] signalScore | No | Regime-weighted |
| 10 | Korea 5-Factor | `Korea5FactorEngine` | `data/engine/Korea5FactorEngine.kt` | [0,1] signalScore (sigmoid(alpha_zscore)) | No | Regime-weighted |
| 11 | Sector Correlation | `SectorCorrelationNetwork` | `data/engine/network/SectorCorrelationNetwork.kt` | [0,1] signalScore (outlier=0.6~1.0, normal=0.3~0.5) | No | Regime-weighted |

### Ensemble orchestrator
- **Class**: `StatisticalAnalysisEngine`
- **File**: `data/engine/StatisticalAnalysisEngine.kt`
- **Aggregation**: Regime-aware weighting via `RegimeWeightTable` — all 11 engines run in parallel via coroutineScope/async; results collected into `StatisticalResult` with individual fields + `MarketRegimeResult`. The AI API (via `ProbabilisticPromptBuilder`) or `ProbabilityInterpreter` synthesizes the final interpretation with regime context.
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

### [COMPLETE] PROMPT 07 — Stacking Ensemble (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–06
- 2-level stacking ensemble: Level-0 = 9 existing calibrated algorithms, Level-1 = L2-regularized LogisticRegression
- TimeSeriesSplit (not KFold) for OOF predictions — no future leakage
- LogisticRegression C=0.5, minimum 60 labeled samples, gradient descent with L2 penalty
- Cold-start fallback: regime-weighted sum when meta-learner not yet fitted
- New source files:
  - `data/engine/ensemble/StackingEnsemble.kt` — Core stacking: TimeSeriesSplit CV, OOF collection, fit/predict_proba, feature importance, save/load state
  - `data/engine/ensemble/RegimeStackingEnsemble.kt` — Regime-conditional subclass: per-regime meta-learner, fallback to global if <60 samples in regime
  - `data/engine/ensemble/SignalHistoryStore.kt` — Room-backed training data store: append, updateOutcome, getHistory, toTrainingData
  - `domain/model/StackingModels.kt` — MetaLearnerStatus, MetaLearnerState, EnsembleHistoryEntry
  - `core/database/entity/EnsembleHistoryEntity.kt` — Room entity (PK=ticker+date, signals_json, actual_outcome, regime_id)
  - `core/database/dao/EnsembleHistoryDao.kt` — DAO (upsert, getCompleted, getPending, updateOutcome, getByRegime)
  - `core/worker/MetaLearnerRefitWorker.kt` — Weekly HiltWorker (Sunday 06:30, extends BaseCollectionWorker)
- Modified files:
  - `core/database/AppDatabase.kt` — v18→v19, EnsembleHistoryEntity + ensembleHistoryDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_18_19 (ensemble_history table), provideEnsembleHistoryDao()
  - `data/engine/StatisticalAnalysisEngine.kt` — StackingEnsemble injection, getEnsembleProbability(), refitMetaLearner(), getMetaLearnerStatus(), recordEnsembleHistory(), cold-start fallback
  - `core/worker/WorkManagerHelper.kt` — scheduleMetaLearnerRefit(), cancelMetaLearnerRefit(), runMetaLearnerRefitNow()
  - `core/worker/CollectionNotificationHelper.kt` — META_LEARNER_NOTIFICATION_ID = 1010
  - `TinyOscillatorApp.kt` — Schedule meta learner refit on startup
  - `presentation/ai/AiAnalysisViewModel.kt` — metaLearnerStatus, ensembleProbability StateFlows
  - `presentation/ai/AiAnalysisScreen.kt` — EnsembleProbabilityCard (Meta-Learner/가중합 badge, probability display, training stats)
  - `domain/usecase/ProbabilityInterpreter.kt` — Updated buildPromptForAi() mention of stacking
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Updated system prompt for stacking
- Tests added (2 files):
  - `StackingEnsembleTest.kt` — 14 tests: TimeSeriesSplit no overlap, train < test ordering, OOF in [0,1], insufficient samples rejection, fit/predict roundtrip, bullish vs bearish differentiation, feature importance sum=1, save/load identical predictions, getStatus fitted/unfitted, min 60 samples enforcement
  - `RegimeStackingEnsembleTest.kt` — 6 tests: regime-specific model creation, insufficient samples skip, regime vs global differentiation, global fallback for unfitted regime, save/load roundtrip, fittedRegimes listing
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added signalHistoryStore parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added signalHistoryStore parameter

### [COMPLETE] PROMPT 08 — Kelly + CVaR (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–07
- Fractional Kelly criterion: f* = (p·b − q)/b × 0.25 (quarter-Kelly), with volatility adjustment
- Cornish-Fisher CVaR: skewness + excess kurtosis correction for tail risk, fallback to historical CVaR
- Position limit: daily loss budget (2%) / |CVaR|, clipped to [0, 1]
- 4 SizeReasonCodes: KELLY_BOUND, CVAR_BOUND, MAX_POSITION, NO_EDGE
- No DB migration needed — recommendation is computed on-the-fly and cached via FeatureStore as part of StatisticalResult
- New source files:
  - `data/engine/risk/KellyPositionSizer.kt` — Fractional Kelly: estimateWinLossRatio, kellyFraction, size(), computeReturns, realizedVolatility
  - `data/engine/risk/CVaRRiskOverlay.kt` — historicalCvar, cornishFisherCvar, positionLimit, riskAdjustedSize, normalPdf/Cdf/Quantile (Abramowitz-Stegun + Beasley-Springer-Moro)
  - `data/engine/risk/PositionRecommendationEngine.kt` — Orchestrates Kelly + CVaR → PositionRecommendation with sizeReasonCode
  - `domain/model/PositionModels.kt` — PositionRecommendation (13 fields), KellyResult, SizeReasonCode enum
- Modified files:
  - `domain/model/StatisticalModels.kt` — Added positionRecommendation field to StatisticalResult
  - `data/engine/StatisticalAnalysisEngine.kt` — PositionRecommendationEngine integration, computes recommendation after ensemble probability
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretPositionRecommendation(), position guide in buildPromptForAi()
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Position Guide section in system + user prompts
  - `presentation/ai/AiAnalysisScreen.kt` — PositionGuideCard (horizontal bar 0%→rec%→max%, CVaR subtitle, details toggle, disclaimer)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes position recommendation
- Tests added (3 files):
  - `KellyPositionSizerTest.kt` — 17 tests: kellyFraction returns 0 when no edge, size bounds [0, maxPosition], WLR clipping, vol adjustment, computeReturns, realizedVolatility
  - `CVaRRiskOverlayTest.kt` — 16 tests: historical CVaR negative, CF fallback for small samples, CF non-positive, stress scenario, positionLimit 0 when CVaR >= 0, riskAdjustedSize ≤ min(kelly, cvar), normalCdf/Quantile/Pdf accuracy
  - `PositionRecommendationEngineTest.kt` — 14 tests: NO_EDGE when prob < 0.5, finite result for Samsung 252d, unavailable for insufficient data, CVaR bound in stress, signalEdge correctness, higher prob → larger position

### [COMPLETE] PROMPT 09 — Incremental Learning (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–08
- Incremental NaiveBayes: 3-bin discretization of calibrated signal scores, Laplace smoothing (alpha=0.5)
- Incremental LogisticRegression: SGD with adaptive learning rate (eta=eta0/(1+eta0*lambda*t)), L2 regularization, class-balanced weights
- Features: 9 calibrated signal scores from base engines (same input space as StackingEnsemble)
- Drift detection: Rolling 30-day Brier score vs 90-day baseline, threshold=0.05
- Ensemble blending: meta-learner 70% + incremental 30% when both fitted
- Cold start: warmStart on last 252 rows from SignalHistoryStore
- New source files:
  - `data/engine/incremental/IncrementalNaiveBayes.kt` — warmStart + update(partial_fit) + predictProba + save/load
  - `data/engine/incremental/IncrementalLogisticRegression.kt` — warmStart + SGD update + predictProba + save/load
  - `data/engine/incremental/IncrementalModelManager.kt` — coldStartIfNeeded, dailyUpdate, drift detection, state management
  - `domain/model/IncrementalModels.kt` — IncrementalNaiveBayesState, IncrementalLogisticRegressionState, IncrementalModelManagerState, BrierEntry, ModelDriftAlert, IncrementalUpdateSummary
  - `core/database/entity/IncrementalModelStateEntity.kt` — Room entity (PK=model_name, state_json, samples_seen)
  - `core/database/entity/ModelDriftAlertEntity.kt` — Room entity (auto PK, model_name, brier/baseline/degradation)
  - `core/database/dao/IncrementalModelDao.kt` — DAO for model state + drift alerts
  - `core/worker/IncrementalModelUpdateWorker.kt` — Daily 19:00 KST HiltWorker
- Modified files:
  - `core/database/AppDatabase.kt` — v19→v20, 2 new entities + incrementalModelDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_19_20, provideIncrementalModelDao()
  - `core/worker/WorkManagerHelper.kt` — scheduleIncrementalModelUpdate(), cancelIncrementalModelUpdate(), runIncrementalModelUpdateNow()
  - `core/worker/CollectionNotificationHelper.kt` — INCREMENTAL_MODEL_NOTIFICATION_ID = 1011
  - `TinyOscillatorApp.kt` — Schedule incremental model update on startup (daily 19:00)
  - `data/engine/StatisticalAnalysisEngine.kt` — IncrementalModelManager integration, 70/30 blending in getEnsembleProbability()
- Tests added (3 files):
  - `IncrementalNaiveBayesTest.kt` — 10 tests: warmStart, update stability, predict bounds, save/load roundtrip, discretize, missing features, error cases
  - `IncrementalLogisticRegressionTest.kt` — 11 tests: warmStart, SGD update stability, predict bounds, save/load roundtrip, adaptive LR, map-based predict, error cases
  - `IncrementalModelManagerTest.kt` — 10 tests: dailyUpdate <200ms, save/load roundtrip, drift detection, constants, both-model update

### [COMPLETE] PROMPT 10 — Korea 5-Factor Model (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–09
- 5-factor: MKT excess (KOSPI − base rate), SMB (market cap proxy), HML (PBR inverse), RMW (ROE proxy), CMA (asset growth proxy)
- OLS rolling regression: 36-month window, 3-month step, min 24 observations
- Signal: alpha z-score → sigmoid → [0,1]
- Factor data cached via FeatureStore (Weekly TTL) — no new DB table needed
- New source files:
  - `data/engine/Korea5FactorEngine.kt` — 10th engine: OLS regression, rolling alpha, z-score signal
  - `domain/model/FactorModels.kt` — Korea5FactorResult, FactorBetas, MonthlyFactorRow, FactorDataCache
- Modified files:
  - `data/engine/StatisticalAnalysisEngine.kt` — Korea5FactorEngine injection, parallel execution
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_KOREA_5FACTOR constant, ALL_ALGOS updated to 10
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts signalScore for Korea5Factor
  - `data/engine/calibration/SignalCalibrator.kt` — Korea5Factor added to ALGO_NAMES
  - `domain/model/StatisticalModels.kt` — korea5FactorResult field in StatisticalResult
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretKorea5Factor(), updated summarize/AI prompt (10개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Korea5Factor section in AI prompt
  - `presentation/ai/AiAnalysisScreen.kt` — 5팩터 알파 expandable card
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes korea5factor
- Tests added (1 file):
  - `Korea5FactorEngineTest.kt` — 12 tests: OLS alpha recovery, rolling_alpha, signal bounds, guard for < 24 obs

### [COMPLETE] PROMPT 11 — Sector Network + Vectorized Indicators (2026-04-04)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–10
- Part A: Sector Correlation Network (11th engine)
  - Ledoit-Wolf shrinkage covariance estimation (analytical optimal shrinkage formula)
  - Correlation matrix from shrunk covariance with covToCorr transformation
  - Graph-based outlier detection: edge threshold |corr| >= 0.5, mean neighbor correlation
  - Outlier = potential divergence/reversal signal (signal_score → 0.6~1.0 for outliers, 0.3~0.5 for normal)
  - Sector peers via StockMasterDao.getTickersBySector(), price data from AnalysisCacheDao
  - Weekly update cadence (not intraday), cached via FeatureStore
- Part B: Vectorized Indicators
  - DoubleArray-based EMA/MACD/RSI (no autoboxing, pre-allocated arrays)
  - RSI uses Wilder smoothing (not SMA)
  - batchCompute: 100 tickers × 252 days in < 500ms
  - CalcOscillatorUseCase.calcEma() now delegates to VectorizedIndicators.emaList()
- New source files:
  - `data/engine/network/SectorCorrelationNetwork.kt` — 11th engine: Ledoit-Wolf, graph construction, outlier detection
  - `data/engine/VectorizedIndicators.kt` — DoubleArray-based emaArray, macdArray, rsiArray, batchCompute, emaList
  - `domain/model/SectorCorrelationModels.kt` — SectorCorrelationResult (12 fields)
- Modified files:
  - `core/database/dao/StockMasterDao.kt` — getTickersBySector() query added
  - `domain/model/StatisticalModels.kt` — sectorCorrelationResult field in StatisticalResult
  - `data/engine/StatisticalAnalysisEngine.kt` — SectorCorrelationNetwork injection, parallel async execution (11개 엔진)
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_SECTOR_CORRELATION constant, ALL_ALGOS updated to 11, weights redistributed per regime (sum=1.0)
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts signalScore for SectorCorrelation
  - `data/engine/calibration/SignalCalibrator.kt` — SectorCorrelation added to ALGO_NAMES
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretSectorCorrelation(), updated AI prompt (11개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Sector correlation section in AI prompt (11개 알고리즘)
  - `presentation/ai/AiAnalysisScreen.kt` — 섹터 상관 expandable card (outlier status, neighbors, avg corr, rank)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes sectorcorr
  - `domain/usecase/CalcOscillatorUseCase.kt` — calcEma() delegates to VectorizedIndicators.emaList()
- Tests added (2 files):
  - `SectorCorrelationNetworkTest.kt` — 7 tests: valid correlation matrix (diagonal=1, symmetric, [-1,1]), outlier detection (correlated→not outlier, uncorrelated→outlier), unavailable (no sector, too few peers), signal bounds, shrinkage intensity
  - `VectorizedIndicatorsTest.kt` — 13 tests: EMA parity with pandas ewm, period=1, single element, empty throws, list/array match, MACD correctness, RSI bounds [0,100], NaN for first period, monotonic increase/decrease, batch shape, batch timing (<500ms), batch EMA match, CalcOscillatorUseCase compatibility
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added sectorCorrelationNetwork parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added sectorCorrelationNetwork parameter
