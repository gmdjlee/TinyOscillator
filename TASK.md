# TASK.md — Active Work Queue

_Last updated: 2026-04-05 by SC-04 수익률 비교_

## Current session
**SC-04 — 수익률 비교 (종목·섹터·KOSPI 오버레이 차트)** COMPLETE.

### Delivered
- `domain/model/ComparisonData.kt` — ComparisonSeries, ComparisonData, ComparisonPeriod 데이터 모델
- `domain/usecase/BuildComparisonUseCase.kt` — 수익률 정규화, OLS 베타 추정, 섹터 평균 계산 (Room 캐시 전용)
- `presentation/comparison/ComparisonViewModel.kt` — HiltViewModel (검색+기간+비교 StateFlow)
- `presentation/comparison/ComparisonScreen.kt` — 수익률 비교 화면 (LineChart 오버레이 + 신호 강도 패널 + α/β 카드)
- `core/database/dao/RegimeDao.kt` — getKospiIndexByDateRange() 쿼리 추가
- `MainActivity.kt` — COMPARISON BottomNavItem (CompareArrows 아이콘)
- 1 test file, 8 tests — all passing (0.092s)

## Previous session
**SC-03 — 섹터/테마 그룹화 (KRX 섹터 + 사용자 테마)** COMPLETE.

### Delivered
- `domain/model/StockGroup.kt` — StockGroup data class, GroupType enum, DEFAULT_THEMES (5개 기본 테마)
- `core/database/entity/UserThemeEntity.kt` — Room 엔티티 (user_themes 테이블)
- `core/database/dao/UserThemeDao.kt` — CRUD + Flow 관찰 + count/maxSortOrder
- `data/repository/StockGroupRepository.kt` — KRX 섹터/사용자 테마 관찰, 신호 점수 집계, 기본 테마 초기화
- `presentation/sector/SectorGroupViewModel.kt` — HiltViewModel (섹터+테마 StateFlow)
- `presentation/sector/SectorGroupScreen.kt` — 메인 화면 (GroupCard, 내 테마/KRX 섹터 리스트)
- `presentation/sector/AddThemeDialog.kt` — 테마 추가 다이얼로그 (종목코드 파싱)
- `presentation/sector/GroupDetailScreen.kt` — 그룹 내 종목 리스트 드릴다운 화면
- `StockMasterDao` — observeAllSectors(), getAllTickersBySector() 추가
- `AppDatabase` v22→v23 (user_themes 테이블)
- `DatabaseModule` — MIGRATION_22_23 + UserThemeDao provider
- `MainActivity.kt` — SECTOR_THEME BottomNavItem (Icons.Default.Category) + group_detail 라우트
- 1 test file, 11 tests — all passing

## Previous session
**SC-02 — 종목 스크리��� (필터/정렬/DataStore 저장)** COMPLETE.

### Delivered
- `domain/model/ScreeningModels.kt` — ScreenerFilter, ScreenerSortKey 추가
- `data/datasource/ScreenerDataSource.kt` — Room DB 기반 스크리너 엔진 (마스터+분석캐시+펀더멘탈+신호점수)
- `data/preferences/ScreenerFilterPreferences.kt` — DataStore 기반 필터 조건 저장/복원
- `presentation/screener/ScreenerViewModel.kt` — 필터/정렬 상태 관리, debounce 500ms
- `presentation/screener/ScreenerScreen.kt` — 스크리너 결과 리스트 + 정렬 칩 + SignalBadge
- `presentation/screener/ScreenerFilterSheet.kt` — ModalBottomSheet 필터 UI (RangeSlider, SegmentedButton, Dropdown)
- `StockMasterDao` — getFilteredCandidates(), getAllSectors() 쿼리 추가
- `CalibrationDao` — getLatestAvgScoresByTicker() 벌크 신호 점수 쿼리 추가
- `MainActivity.kt` — SCREENER BottomNavItem 추가 (Icons.Default.Tune)
- 1 test file, 9 tests — all passing (0.173s)

## Previous session
**SEARCH-01 — 종목 검색 자동완성 (초성 + 최근 검색)** COMPLETE.

### Delivered
- `core/util/KoreanUtils.kt` — 초성 추출, 초성 쿼리 감지, 통합 매칭 (순수 Kotlin)
- `data/preferences/RecentSearchPreferences.kt` — DataStore 기반 최근 검색 5개 기록
- `presentation/common/StockSearchBar.kt` — 검색 결과 + 최근 검색 드롭다운 콘텐츠 Composable
- `StockMasterEntity` + `StockMasterDao` — initial_consonants 컬럼 + 초성/텍스트 검색 쿼리
- DB v21→v22 마이그레이션 + 앱 시작 시 초성 백필
- OscillatorVM, AiAnalysisVM, PortfolioVM — 초성 통합 검색으로 교체
- 1 test file, 25 tests — all passing (0.078s)

## Previous session
**SIGNAL-T05 — 알고리즘 신호 충돌 감지 + 앰버 경고 배너** COMPLETE.

### Delivered
- `domain/usecase/SignalConflictDetector.kt` — 순수 Kotlin 충돌 감지 계산기 (σ 기반 4단계 분류, 포지션 배수 산출)
- `presentation/common/ConflictWarningBanner.kt` — 충돌 경고 배너 Composable (LOW/HIGH/CRITICAL 색상, 강세/중립/약세 분포 바)
- `AiAnalysisScreen.kt` — ConflictWarningBanner 통합 (SignalRationaleCard 상단) + PositionGuideCard 충돌 축소 적용
- Kelly 포지션 연동: conflictMultiplier → PositionGuideCard, "충돌 축소" 칩 표시
- 2 test files, 20 tests — all passing

## Previous session
**SIGNAL-T04 — 신호 강도 히트맵 (날짜×종목)** COMPLETE.

### Delivered
- `domain/model/HeatmapData.kt` — 히트맵 데이터 모델 (tickers, dates, scores, scoreAt())
- `domain/usecase/BuildHeatmapUseCase.kt` — 분석 이력 종목의 일별 앙상블 평균 점수 구축
- `core/database/dao/CalibrationDao.kt` — getAverageScoreForDay, getDistinctDates 쿼리 추가
- `presentation/common/SignalHeatmap.kt` — Canvas 기반 히트맵 (한국 관례 색상, 탭 감지, 수평 스크롤)
- `presentation/common/HeatmapViewModel.kt` — 기간 선택 (7/14/20일) + 데이터 로드
- `presentation/common/HeatmapScreen.kt` — 히트맵 화면 + WindowSelector + HeatmapLegend
- `AiAnalysisScreen.kt` — 히트맵 버튼 (GridView 아이콘) 추가
- `MainActivity.kt` — heatmap NavHost 라우트 + onHeatmapClick 콜백 전달
- 2 test files, 15 tests — all passing (0.088s)

## Previous session
**SIGNAL-T03 — 신호 이력 저장 + T+N 수익률 수집 + 적중률 UI** COMPLETE.

### Delivered
- `core/database/entity/SignalHistoryEntity.kt` — outcome_t1/t5/t20 컬럼 추가 (DB v20→v21)
- `core/database/dao/CalibrationDao.kt` — T+N 업데이트 쿼리, 적중률 집계 쿼리, observeAllHistory, getPendingTickers
- `domain/model/SignalTransparencyModels.kt` — AlgoAccuracyRow 데이터 클래스 (accuracy 계산 프로퍼티)
- `data/repository/SignalHistoryRepository.kt` — 신호 기록·적중률 조회·T+N 업데이트·pruneOldData
- `core/worker/SignalOutcomeUpdateWorker.kt` — 매일 18:00 T+N 결과 수집 HiltWorker
- `presentation/common/AlgoAccuracyCard.kt` — 적중��� 진행 바 + % + 건수 카드 (0건 시 안내 ��시지)
- `AiAnalysisScreen.kt` — AlgoAccuracyCard 통합 (AlgoContributionView 하단)
- `AiAnalysisViewModel.kt` — algoAccuracy StateFlow 추가, 분석 완료 시 자동 로드
- DB v20→v21 마이그레이션 (signal_history 테이블 3개 컬럼 추가)
- WorkManagerHelper: scheduleSignalOutcomeUpdate (매일 18:00)
- TinyOscillatorApp: 워커 자동 등록
- 2 test files, 16 tests — all passing (17s)

## Previous session
**SIGNAL-T02 — 알고리즘 기여도 시각화 (레이더 + 폭포수)** COMPLETE.

### Delivered
- `presentation/common/AlgoRadarChartView.kt` — MPAndroidChart RadarChart, 알고리즘 꼭짓점 + 강도 면적
- `presentation/common/AlgoWaterfallChart.kt` — Compose Canvas 폭포수, 0.5 기준선 → 기여분 누적 → 앙상블 점수
- `presentation/common/AlgoContributionView.kt` — SegmentedButton 토글 래퍼 (레이더 ↔ 폭포수)
- `AiAnalysisScreen.kt` — SignalRationaleCard 하단에 AlgoContributionView 통합
- 1 test file, 10 tests — all passing (0.11s)

## Previous session
**SIGNAL-T01 — 신호 투명성: 알고리즘별 근거 카드** COMPLETE.

### Delivered
- `data/engine/RationaleBuilder.kt` — StatisticalResult → Map<String, AlgoResult> 변환, 10개 알고리즘 한국어 근거 생성 (50자 이내, 숫자+방향 포함)
- `presentation/common/SignalRationaleCard.kt` — 펼치기/접기 카드, 점수 바 (청색↔회색↔적색), ScoreBadge, AlgoRationaleRow
- `AiAnalysisScreen.kt` — EnsembleProbabilityCard 아래에 SignalRationaleCard 통합
- 2 test files, 23 tests — all passing

## Previous session
**CHART-K03 — 거래량 프로파일 오버레이** COMPLETE.

### Delivered
- `domain/model/VolumeProfile.kt` — VolumeBucket, VolumeProfile 데이터 모델
- `domain/usecase/BuildVolumeProfileUseCase.kt` — 버킷 집계, POC, Value Area 70% 계산 (순수 Kotlin)
- `presentation/chart/overlay/VolumeProfileOverlay.kt` — Compose Canvas DrawScope 오버레이 (VA 배경 + 버킷 바 + POC 라인)
- `presentation/chart/bridge/ChartAxisBridge.kt` — MPAndroidChart y축 범위 → Compose State 브릿지
- `KoreanCandleChartView.kt` — Box 래퍼 + VolumeProfileOverlay 통합, volumeProfile 파라미터 추가
- `StockChartViewModel.kt` — volumeProfile StateFlow (VOLUME_PROFILE 선택 시 자동 계산)
- 3 test files, 18 tests — all passing

## Previous session
**CHART-K02 — 기술 지표 오버레이** COMPLETE.

### Delivered
- `domain/model/Indicator.kt` — Indicator enum (8종), OverlayType, IndicatorParams
- `domain/indicator/IndicatorCalculator.kt` — EMA/볼린저/MACD/RSI/스토캐스틱 순수 Kotlin 계산
- `presentation/chart/ext/IndicatorDataSetExt.kt` — FloatArray → LineDataSet, 가격 오버레이 변환
- `presentation/chart/composable/OscillatorChartView.kt` — MACD/RSI/스토캐스틱 서브차트
- `presentation/chart/composable/IndicatorSheet.kt` — ModalBottomSheet 지표 선택기
- `data/preferences/IndicatorPreferencesRepository.kt` — Preferences DataStore 영속화
- `presentation/viewmodel/StockChartViewModel.kt` — 지표 상태 + 계산 ViewModel
- `KoreanCandleChartView.kt` — CandleStickChart → CombinedChart (EMA/볼린저 오버레이)
- ChartSyncManager + InertialScrollHandler — CombinedChart 호환 타입 변경
- build.gradle.kts — DataStore 의존성 추가
- AppModule.kt — DataStore + Repository DI 등록
- 2 test files, 19 tests — all passing

## Previous session
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
