# TASK.md — Active Work Queue

_Last updated: 2026-04-03 by PROMPT 06 BOK ECOS Macro_

## Current session
**PROMPT 06 — BOK ECOS Macro** COMPLETE. Ready to begin PROMPT 07.

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
**Status:** NOT STARTED
**Prerequisite:** PROMPT 01 (calibration) ✅, PROMPT 02 (regime) ✅
**Delivers:**
- `app/src/main/python/ensemble/stacking_ensemble.py`
- `app/src/main/python/ensemble/signal_history_store.py`
- Room entity `SignalHistoryEntry`
- Kotlin: `MetaLearnerStatusDto`
**Acceptance test:** Cold-start fallback tested; meta-learner fits on 60+ synthetic samples

### PROMPT 08 — Kelly + CVaR
**Status:** NOT STARTED
**Prerequisite:** PROMPT 01 (calibration), PROMPT 07 (stacking)
**Delivers:**
- `app/src/main/python/risk/kelly_position_sizer.py`
- `app/src/main/python/risk/cvar_risk_overlay.py`
- `app/src/main/python/risk/position_recommendation.py`
- Kotlin: `PositionRecommendationDto`, Position Guide UI card
**Acceptance test:** CVaR bound reduces Kelly size in stress test with -15% daily returns

### PROMPT 09 — Incremental Learning
**Status:** NOT STARTED
**Prerequisite:** PROMPT 07 (stacking — history store must exist)
**Delivers:**
- `app/src/main/python/models/incremental_models.py`
- `app/src/main/python/models/model_persistence.py`
- `IncrementalModelUpdateWorker.kt`
**Acceptance test:** daily_update() completes in < 200ms on 1-sample input; save/load roundtrip

### PROMPT 10 — Korea 5-Factor Model
**Status:** NOT STARTED
**Prerequisite:** PROMPT 03 (FeatureStore), PROMPT 06 (macro data for RF rate)
**Delivers:**
- `app/src/main/python/factors/factor_data_builder.py`
- `app/src/main/python/factors/factor_model.py`
- `app/src/main/python/factors/factor_cache.py`
- Ensemble extended to 10 algorithms
- Kotlin: `FactorAlphaDto`
**Acceptance test:** rolling_alpha runs on 005930 with 36-month history

### PROMPT 11 — Sector Network + Vectorized Indicators
**Status:** NOT STARTED
**Prerequisite:** PROMPT 03 (FeatureStore)
**Delivers:**
- `app/src/main/python/network/sector_correlation_network.py`
- `app/src/main/python/network/sector_mapper.py`
- `app/src/main/python/indicators/vectorized_indicators.py`
- Ensemble extended to 11 algorithms
- Benchmark log: ema_numpy vs pandas speedup ratio
**Acceptance test:** All 11 algorithms registered; regime weights sum to 1.0 per regime

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

## Deferred / backlog
_Items discovered during PROMPT 00 audit that are not part of the 11-prompt roadmap:_

- **Holiday calendar**: Add KRX Korean market holiday calendar to `TradingHours` — currently only skips weekends
- **androidTest infrastructure**: Set up Compose UI tests + Room DAO instrumented tests
- **MarketOscillator caching**: Cache raw KRX OHLCV data in Room for incremental market oscillator updates
- **Local LLM cleanup**: JNI bridge exists but local LLM is unused in favor of AI API — decide whether to remove or complete
- **Python layer decision**: RESOLVED — all features will be implemented in pure Kotlin (no Chaquopy). Decision made in PROMPT 01.
