# TASK.md — Active Work Queue

_Last updated: 2026-04-04 by CHART-K01 Chart Interaction Upgrade_

## Current session
**CHART-K01 — MPAndroidChart 인터랙션 개선** COMPLETE.

### Delivered
- `presentation/chart/marker/OhlcvMarkerView.kt` — OHLCV 크로스헤어 MarkerView (경계 감지)
- `presentation/chart/interaction/InertialScrollHandler.kt` — 핀치줌 후 관성 스크롤
- `presentation/chart/interaction/ChartSyncManager.kt` — 캔들 ↔ 거래량 크로스헤어 동기화
- `presentation/chart/formatter/KoreanVolumeFormatter.kt` — 거래량 축 한국식 단위 (조/억/만)
- `presentation/chart/formatter/KoreanPriceFormatter.kt` — 가격 축 천 단위 쉼표
- `presentation/chart/formatter/IndexDateFormatter.kt` — X축 인덱스→날짜 매핑
- `presentation/chart/ext/FormatExt.kt` — Long/Float.formatKRW()
- `presentation/chart/ext/CandleDataExt.kt` — toCandleData() / toVolumeBarData()
- `presentation/chart/composable/KoreanCandleChartView.kt` — Compose 래퍼 (캔들 70% + 거래량 30%)
- `res/layout/view_ohlcv_marker.xml` + `res/drawable/ohlcv_marker_bg.xml`
- 4 test files, 22 tests — all passing

## Previous session
**PROMPT 11 — Sector Network + Vectorized Indicators** COMPLETE. All 11 algorithms registered.

## Upcoming tasks (ordered)

### PROMPT 02 — Market Regime Detection
**Status:** COMPLETE (2026-04-02)
**Decision:** Pure Kotlin implementation (no Python) — matches PROMPT 01 decision
**Delivers:**
- `data/engine/regime/GaussianHmm.kt` — Pure Kotlin Gaussian HMM with Baum-Welch EM
- `data/engine/regime/MarketRegimeClassifier.kt` — 4-state KOSPI regime classifier
- `data/engine/regime/RegimeWeightTable.kt` — Regime-specific ensemble weights
- `domain/model/RegimeModels.kt` — MarketRegimeResult data class
- `core/database/entity/KospiIndexEntity.kt` + `RegimeStateEntity.kt` — Room entities
- `core/database/dao/RegimeDao.kt` — DAO
- `core/worker/RegimeUpdateWorker.kt` — Weekly retraining WorkManager job
- DB v14→v15 migration (kospi_index + regime_state tables)
- `StatisticalAnalysisEngine` integration: regime result in `StatisticalResult`, weight table
- Regime badge chip in AiAnalysisScreen + expandable card with probabilities
- 3 test files with full coverage
**Acceptance test:** 4 distinct regimes returned; regime weights visibly change per regime; badge in UI

### PROMPT 03 — Feature Store
**Status:** COMPLETE (2026-04-02)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01/02
**Delivers:**
- `FeatureCacheEntity`, `FeatureCacheDao` (Room v15→v16)
- `FeatureStore.kt` (singleton, TTL-aware, generic `getOrCompute<T>` with KSerializer)
- `FeatureCacheEvictionWorker.kt` (daily 06:00 KST)
- `FeatureStoreModels.kt` (FeatureKey, FeatureTtl, CacheStats)
- `@Serializable` on all StatisticalResult types for JSON cache round-trip
- StatisticalAnalysisEngine integration (Daily TTL per ticker+date)
- AiAnalysisViewModel: cacheStats exposure + clearAnalysisCache()
- UI: Cached/Live indicator chip in probability analysis results
- FeatureStoreTest.kt (12 tests)
**Acceptance test:** Second call for same ticker+feature+date returns cached value without calling compute

### PROMPT 04 — Order Flow Features
**Status:** COMPLETE (2026-04-03)
**Decision:** Pure Kotlin implementation — investor data (foreignNetBuy, instNetBuy) already in DailyTrading
**Delivers:**
- `data/engine/OrderFlowEngine.kt` — OFI, institutional divergence, foreign buy pressure, signal scoring
- `domain/model/StatisticalModels.kt` — `OrderFlowResult` data class (12 fields)
- Ensemble extended to 8 algorithms (8th: OrderFlow)
- `RegimeWeightTable` updated: OrderFlow gets 0.14–0.18 weight (highest in CRISIS/BEAR_HIGH_VOL)
- `SignalScoreExtractor`: extracts `buyerDominanceScore` for calibration
- `ProbabilityInterpreter.interpretOrderFlow()` — rule-based Korean interpretation
- `ProbabilisticPromptBuilder` — OrderFlow section in AI prompt
- UI: Order Flow expandable card in probability analysis results
- `OrderFlowEngineTest.kt` — 14 tests
**Acceptance test:** Engine runs on 60-day data with correct direction/signal; OFI bounded [-1,1]; signal bounded [0,1]

### PROMPT 05 — DART Event Study
**Status:** COMPLETE (2026-04-03)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01–04 (no Chaquopy/Python)
**Delivers:**
- `core/api/DartApiClient.kt` — DART REST API client (corp_code XML download, disclosure list)
- `data/engine/DartEventEngine.kt` — 9th engine: disclosure classification, OLS beta, CAR, signal
- `domain/model/DartModels.kt` — DartDisclosure, DartEventResult, EventStudyResult, DartEventType
- `core/database/entity/DartCorpCodeEntity.kt` — Room entity for corp_code cache
- `core/database/dao/DartDao.kt` — DAO for corp_code lookup
- Ensemble extended to 9 algorithms (9th: DartEvent)
- `RegimeWeightTable` updated: DartEvent gets 0.10–0.14 weight (highest in CRISIS/BEAR_HIGH_VOL)
- `SignalScoreExtractor`: extracts signalScore for DartEvent calibration
- `ProbabilityInterpreter.interpretDartEvent()` — rule-based Korean interpretation
- `ProbabilisticPromptBuilder` — DartEvent section in AI prompt (9개 알고리즘)
- UI: DART 공시 이벤트 expandable card in probability analysis results
- Settings: DART API key field in EncryptedSharedPreferences
- DB v16→v17 migration (dart_corp_code table)
- `DartEventEngineTest.kt` — 20 tests
**Acceptance test:** CAR computed for synthetic data; classify_disclosure covers all 7 event types; signal bounded [0,1]

### PROMPT 06 — BOK ECOS Macro
**Status:** COMPLETE (2026-04-03)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01–05 (no Chaquopy/Python)
**Delivers:**
- `core/api/BokEcosApiClient.kt` — ECOS REST API client (5 indicators, 1000ms rate limit)
- `data/engine/macro/BokEcosCollector.kt` — Fetches 24 months, computes YoY, ffill gaps
- `data/engine/macro/MacroRegimeOverlay.kt` — 4-environment classification + weight adjustment
- `domain/model/MacroModels.kt` — EcosIndicatorSpec, EcosDataPoint, MacroEnvironment, MacroSignalResult
- `core/database/entity/MacroIndicatorEntity.kt` — Room entity for macro data cache
- `core/database/dao/MacroDao.kt` — DAO
- `core/worker/MacroUpdateWorker.kt` — Weekly (Sunday 05:30) background fetch
- DB v17→v18 migration (macro_indicator table)
- StatisticalAnalysisEngine: macro overlay applied to regime weights, macroSignalResult in StatisticalResult
- Settings: ECOS API key in EncryptedSharedPreferences
- UI: Macro environment chip (color-coded) + expandable card with 5 YoY values
- 2 test files with full coverage
**Acceptance test:** macroSignalVector returns valid result; 4 environments classified correctly; adjusted weights sum to 1.0

### PROMPT 07 — Stacking Ensemble
**Status:** COMPLETE (2026-04-03)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01–06 (no Chaquopy/Python)
**Delivers:**
- `data/engine/ensemble/StackingEnsemble.kt` — 2-level stacking: L2-regularized LogisticRegression meta-learner on OOF predictions
- `data/engine/ensemble/RegimeStackingEnsemble.kt` — Regime-conditional meta-learner (per-regime subclass)
- `data/engine/ensemble/SignalHistoryStore.kt` — Room-backed OOF training data store
- `domain/model/StackingModels.kt` — MetaLearnerStatus, MetaLearnerState, EnsembleHistoryEntry
- `core/database/entity/EnsembleHistoryEntity.kt` — Room entity for ensemble training history
- `core/database/dao/EnsembleHistoryDao.kt` — DAO for ensemble history
- `core/worker/MetaLearnerRefitWorker.kt` — Weekly refit WorkManager job (Sunday 06:30)
- DB v18→v19 migration (ensemble_history table)
- StatisticalAnalysisEngine: stacking ensemble integration, cold-start fallback, refitMetaLearner()
- UI: Ensemble probability card with Meta-Learner/가중합 badge, training info
- 2 test files with full coverage (StackingEnsembleTest: 14 tests, RegimeStackingEnsembleTest: 6 tests)
**Acceptance test:** Cold-start fallback tested; meta-learner fits on 60+ synthetic samples; save/load roundtrip; MetaLearnerStatus in UI

### PROMPT 08 — Kelly + CVaR
**Status:** COMPLETE (2026-04-03)
**Prerequisite:** PROMPT 01 (calibration), PROMPT 07 (stacking)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01–07 (no Chaquopy/Python)
**Delivers:**
- `data/engine/risk/KellyPositionSizer.kt` — Fractional Kelly criterion (quarter-Kelly default)
- `data/engine/risk/CVaRRiskOverlay.kt` — Cornish-Fisher CVaR with position limit
- `data/engine/risk/PositionRecommendationEngine.kt` — Orchestrates Kelly + CVaR → PositionRecommendation
- `domain/model/PositionModels.kt` — PositionRecommendation, KellyResult, SizeReasonCode
- StatisticalResult: `positionRecommendation` field computed after ensemble probability
- UI: Position Guide card with horizontal bar, CVaR subtitle, disclaimer
- ProbabilityInterpreter: `interpretPositionRecommendation()` + AI prompt section
- 3 test files: KellyPositionSizerTest (17 tests), CVaRRiskOverlayTest (16 tests), PositionRecommendationEngineTest (14 tests)
**Acceptance test:** CVaR bound reduces Kelly size in stress test; recommend() returns finite result for Samsung with 252 days; NO_EDGE when signal_prob=0.48

### PROMPT 09 — Incremental Learning
**Status:** COMPLETE (2026-04-03)
**Prerequisite:** PROMPT 07 (stacking — history store must exist)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01–08 (no Chaquopy/Python)
**Delivers:**
- `data/engine/incremental/IncrementalNaiveBayes.kt` — 3-bin discretized NB with partial_fit
- `data/engine/incremental/IncrementalLogisticRegression.kt` — SGD logistic regression (adaptive LR)
- `data/engine/incremental/IncrementalModelManager.kt` — Manager + Brier drift detection
- `domain/model/IncrementalModels.kt` — State/alert data classes
- `core/database/entity/IncrementalModelStateEntity.kt` — Room entity for model state
- `core/database/entity/ModelDriftAlertEntity.kt` — Room entity for drift alerts
- `core/database/dao/IncrementalModelDao.kt` — DAO for state + alerts
- `core/worker/IncrementalModelUpdateWorker.kt` — Daily 19:00 KST worker
- DB v19→v20 migration (incremental_model_state + model_drift_alert tables)
- StatisticalAnalysisEngine: 70/30 blending (meta-learner + incremental)
- 3 test files: IncrementalNaiveBayesTest (10), IncrementalLogisticRegressionTest (11), IncrementalModelManagerTest (10)
**Acceptance test:** dailyUpdate() < 200ms on 1-sample; save/load roundtrip identical predictions; drift detection fires

### PROMPT 10 — Korea 5-Factor Model
**Status:** COMPLETE (2026-04-03)
**Prerequisite:** PROMPT 03 (FeatureStore), PROMPT 06 (macro data for RF rate)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01–09 (no Chaquopy/Python)
**Delivers:**
- `data/engine/Korea5FactorEngine.kt` — 10th engine: OLS 5-factor regression, rolling alpha, z-score signal
- `domain/model/FactorModels.kt` — Korea5FactorResult, FactorBetas, MonthlyFactorRow, FactorDataCache
- Ensemble extended to 10 algorithms (10th: Korea5Factor)
- `RegimeWeightTable` updated: Korea5Factor gets 0.07–0.13 weight (highest in BULL_LOW_VOL)
- `SignalScoreExtractor`: extracts signalScore for Korea5Factor calibration
- `SignalCalibrator`: Korea5Factor added to ALGO_NAMES
- `ProbabilityInterpreter.interpretKorea5Factor()` — rule-based Korean interpretation
- `ProbabilisticPromptBuilder` — Korea5Factor section in AI prompt (10개 알고리즘)
- UI: 5팩터 알파 expandable card with betas, R², z-score
- Factor data cached via FeatureStore (Weekly TTL) — no new DB table needed
- `Korea5FactorEngineTest.kt` — 12 tests
**Acceptance test:** OLS alpha recovery within 0.015 on synthetic data; rolling_alpha correct entry count; signal bounded [0,1]; guard for < 24 obs

### PROMPT 11 — Sector Network + Vectorized Indicators
**Status:** COMPLETE (2026-04-04)
**Prerequisite:** PROMPT 03 (FeatureStore)
**Decision:** Pure Kotlin implementation — consistent with PROMPT 01–10 (no Chaquopy/Python)
**Delivers:**
- `data/engine/network/SectorCorrelationNetwork.kt` — 11th engine: Ledoit-Wolf shrinkage, graph-based outlier detection
- `data/engine/VectorizedIndicators.kt` — DoubleArray-based EMA/MACD/RSI, batch computation
- `domain/model/SectorCorrelationModels.kt` — SectorCorrelationResult data class
- Ensemble extended to 11 algorithms with regime weights summing to 1.0
- CalcOscillatorUseCase.calcEma() delegates to VectorizedIndicators.emaList()
- `SectorCorrelationNetworkTest.kt` — 7 tests
- `VectorizedIndicatorsTest.kt` — 13 tests
**Acceptance test:** All 11 algorithms registered in RegimeWeightTable; weights sum to 1.0 per regime; batchCompute 100×252 < 500ms

## Completed tasks

### PROMPT 01 — Signal Calibration
**Status:** COMPLETE (2026-04-02)
**Decision:** Implemented in pure Kotlin (no Chaquopy) — matches existing engine patterns
**Delivered:**
- `data/engine/calibration/SignalCalibrator.kt` — Isotonic (PAVA) + Platt sigmoid, per-algo calibrators
- `data/engine/calibration/WalkForwardValidator.kt` — Time-series CV with no future leakage
- `data/engine/calibration/CalibrationMonitor.kt` — Rolling Brier/ECE tracker with recalibration flag
- `data/engine/calibration/SignalScoreExtractor.kt` — Extracts raw bullish scores from 6 engines
- `domain/model/CalibrationModels.kt` — CalibrationMetrics, CalibratedScore, CalibratorState, etc.
- `core/database/entity/SignalHistoryEntity.kt` — Room entity for signal history
- `core/database/entity/CalibrationStateEntity.kt` — Room entity for calibrator state persistence
- `core/database/dao/CalibrationDao.kt` — DAO for signal history + calibration state
- Room DB v13→v14 migration (signal_history + calibration_state tables)
- `StatisticalAnalysisEngine` integration: records signal history after each analysis, exposes `getCalibratedScores()`
- 4 test files with full coverage
**Next steps:** PROMPT 03 (Feature Store)

### PROMPT 11 — Sector Network + Vectorized Indicators
**Status:** COMPLETE (2026-04-04)
See above.

## Deferred / backlog
_Items discovered during PROMPT 00 audit that are not part of the 11-prompt roadmap:_

- **Holiday calendar**: Add KRX Korean market holiday calendar to `TradingHours` — currently only skips weekends
- **androidTest infrastructure**: Set up Compose UI tests + Room DAO instrumented tests
- **MarketOscillator caching**: Cache raw KRX OHLCV data in Room for incremental market oscillator updates
- **Local LLM cleanup**: JNI bridge exists but local LLM is unused in favor of AI API — decide whether to remove or complete
- **Python layer decision**: RESOLVED — all features will be implemented in pure Kotlin (no Chaquopy). Decision made in PROMPT 01.
