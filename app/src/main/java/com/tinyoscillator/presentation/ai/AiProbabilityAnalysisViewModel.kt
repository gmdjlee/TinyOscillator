package com.tinyoscillator.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.entity.AnalysisSnapshotEntity
import com.tinyoscillator.data.mapper.AnalysisResponseParser
import com.tinyoscillator.data.repository.SignalHistoryRepository
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AlgoAccuracyRow
import com.tinyoscillator.domain.model.AlgoResult
import com.tinyoscillator.domain.model.CacheStats
import com.tinyoscillator.domain.model.MetaLearnerStatus
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.ProbabilityAnalysisUseCase
import com.tinyoscillator.domain.usecase.ProbabilityInterpreter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 확률분석 탭 전담 ViewModel.
 *
 * 책임: 9+ 통계 엔진 병렬 실행 결과 관리, 스냅샷 저장/조회, 로컬/AI 해석,
 * 메타 학습기 상태, 앙상블 확률, Feature Store 캐시 통계.
 * 대상 종목은 [AiStockAnalysisViewModel.selectedStock]에서 Screen이 받아 넘긴다.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AiProbabilityAnalysisViewModel @Inject constructor(
    private val aiApiClient: AiApiClient,
    private val aiPreparer: AiAnalysisPreparer,
    private val apiConfigProvider: ApiConfigProvider,
    private val probabilityAnalysisUseCase: ProbabilityAnalysisUseCase,
    private val probabilityInterpreter: ProbabilityInterpreter,
    private val signalHistoryRepository: SignalHistoryRepository,
    private val analysisSnapshotDao: AnalysisSnapshotDao,
    private val analysisResponseParser: AnalysisResponseParser
) : ViewModel() {

    private val _probabilityState = MutableStateFlow<ProbabilityAnalysisState>(ProbabilityAnalysisState.Idle)
    val probabilityState: StateFlow<ProbabilityAnalysisState> = _probabilityState.asStateFlow()

    /**
     * 분석 결과 → 알고리즘별 점수·근거 맵.
     * Success 상태일 때만 채워지며, Screen은 이 값을 바로 표시한다.
     */
    val algoResults: StateFlow<Map<String, AlgoResult>> = _probabilityState
        .map { state ->
            if (state is ProbabilityAnalysisState.Success)
                probabilityAnalysisUseCase.buildAlgoRationales(state.result)
            else emptyMap()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    private val _algoAccuracy = MutableStateFlow<Map<String, AlgoAccuracyRow>>(emptyMap())
    val algoAccuracy: StateFlow<Map<String, AlgoAccuracyRow>> = _algoAccuracy.asStateFlow()

    val cacheStats: StateFlow<CacheStats> = probabilityAnalysisUseCase.cacheStats
        .stateIn(viewModelScope, SharingStarted.Lazily, CacheStats())

    private val _metaLearnerStatus = MutableStateFlow(MetaLearnerStatus())
    val metaLearnerStatus: StateFlow<MetaLearnerStatus> = _metaLearnerStatus.asStateFlow()

    private val _ensembleProbability = MutableStateFlow<Double?>(null)
    val ensembleProbability: StateFlow<Double?> = _ensembleProbability.asStateFlow()

    private val _interpretationState = MutableStateFlow<InterpretationState>(InterpretationState.Idle)
    val interpretationState: StateFlow<InterpretationState> = _interpretationState.asStateFlow()

    private val _snapshots = MutableStateFlow<List<AnalysisSnapshotEntity>>(emptyList())
    val snapshots: StateFlow<List<AnalysisSnapshotEntity>> = _snapshots.asStateFlow()

    /** 현재 분석 대상 티커 (AI 해석 캐시 조회용) */
    private var currentTicker: String? = null

    /** 마지막 저장 스냅샷 id — AI 해석을 해당 스냅샷에 귀속 저장 */
    private var lastSnapshotId: Long? = null

    /** 확률 분석 실행 — 9+ 통계 엔진 병렬 실행 (API 키 불필요) */
    fun analyzeProbability(stock: SelectedStockInfo) {
        viewModelScope.launch {
            currentTicker = stock.ticker
            val total = probabilityAnalysisUseCase.totalEngines
            _probabilityState.value = ProbabilityAnalysisState.Computing("통계 알고리즘 실행 중...", 0, total)
            val completedCount = java.util.concurrent.atomic.AtomicInteger(0)
            try {
                val result = probabilityAnalysisUseCase.analyze(stock.ticker) { engineName ->
                    val n = completedCount.incrementAndGet()
                    _probabilityState.value = ProbabilityAnalysisState.Computing(
                        "$engineName 완료", n.coerceAtMost(total), total
                    )
                }
                _probabilityState.value = ProbabilityAnalysisState.Success(result)

                saveSnapshot(stock, result)

                try {
                    _algoAccuracy.value = signalHistoryRepository.getAccuracy(stock.ticker)
                } catch (e: Exception) {
                    Timber.w(e, "적중률 로드 실패")
                }

                try {
                    _ensembleProbability.value = probabilityAnalysisUseCase.getEnsembleProbability(result)
                    _metaLearnerStatus.value = probabilityAnalysisUseCase.getMetaLearnerStatus()
                } catch (e2: Exception) {
                    Timber.w(e2, "앙상블 확률 계산 실패")
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _probabilityState.value = ProbabilityAnalysisState.Error(e.message ?: "확률 분석 실패")
            }
        }
    }

    private fun saveSnapshot(stock: SelectedStockInfo, result: StatisticalResult) {
        viewModelScope.launch {
            try {
                val snapshot = probabilityAnalysisUseCase.buildSnapshot(stock.ticker, stock.name, result)
                lastSnapshotId = analysisSnapshotDao.insert(snapshot)
                analysisSnapshotDao.deleteOldSnapshots(stock.ticker, 20)
                loadSnapshots(stock.ticker)
            } catch (e: Exception) {
                Timber.w(e, "분석 스냅샷 저장 실패")
            }
        }
    }

    fun loadSnapshots(ticker: String) {
        viewModelScope.launch {
            _snapshots.value = analysisSnapshotDao.getRecentByTicker(ticker, 10)
        }
    }

    /** 로컬 규칙 기반 해석 실행 */
    fun interpretLocal() {
        val result = (_probabilityState.value as? ProbabilityAnalysisState.Success)?.result ?: return

        val summary = probabilityInterpreter.summarize(result)
        val engines = mutableMapOf<String, String>()

        result.bayesResult?.let { engines["bayes"] = probabilityInterpreter.interpretBayes(it) }
        result.logisticResult?.let { engines["logistic"] = probabilityInterpreter.interpretLogistic(it) }
        result.hmmResult?.let { engines["hmm"] = probabilityInterpreter.interpretHmm(it) }
        result.patternAnalysis?.let { engines["pattern"] = probabilityInterpreter.interpretPattern(it) }
        result.signalScoringResult?.let { engines["signal"] = probabilityInterpreter.interpretSignalScoring(it) }
        result.correlationAnalysis?.let { engines["correlation"] = probabilityInterpreter.interpretCorrelation(it) }
        result.bayesianUpdateResult?.let { engines["bayesian"] = probabilityInterpreter.interpretBayesianUpdate(it) }
        result.orderFlowResult?.let { engines["orderflow"] = probabilityInterpreter.interpretOrderFlow(it) }
        result.dartEventResult?.let { engines["dartevent"] = probabilityInterpreter.interpretDartEvent(it) }
        result.korea5FactorResult?.let { engines["korea5factor"] = probabilityInterpreter.interpretKorea5Factor(it) }
        result.sectorCorrelationResult?.let { engines["sectorcorr"] = probabilityInterpreter.interpretSectorCorrelation(it) }
        result.marketRegimeResult?.let { engines["regime"] = probabilityInterpreter.interpretMarketRegime(it) }
        result.macroSignalResult?.let { engines["macro"] = probabilityInterpreter.interpretMacro(it) }
        result.positionRecommendation?.let { engines["position"] = probabilityInterpreter.interpretPositionRecommendation(it) }

        _interpretationState.value = InterpretationState.Success(
            summary = summary,
            engineInterpretations = engines,
            provider = InterpretationProvider.LOCAL
        )
    }

    /**
     * AI 기반 해석 실행.
     * [force]가 false이면 신선한(4시간 이내) 스냅샷에 저장된 해석을 먼저 재사용해
     * API 호출 비용을 아낀다. "새로 분석" 버튼이 force=true로 호출한다.
     */
    fun interpretWithAi(force: Boolean = false) {
        val result = (_probabilityState.value as? ProbabilityAnalysisState.Success)?.result ?: return

        viewModelScope.launch {
            val aiConfig = apiConfigProvider.getAiConfig()
            if (!aiConfig.isValid()) {
                _interpretationState.value = InterpretationState.NoApiKey
                return@launch
            }

            if (!force && restoreCachedInterpretation()) return@launch

            _interpretationState.value = InterpretationState.Loading
            try {
                val userMessage = probabilityInterpreter.buildPromptForAi(result)
                val aiResult = aiApiClient.analyzeStructured(
                    config = aiConfig,
                    systemPrompt = aiPreparer.getStructuredInterpretationPrompt(),
                    userMessage = userMessage,
                    schema = AnalysisResponseParser.STOCK_ANALYSIS_SCHEMA,
                    analysisType = AiAnalysisType.PROBABILITY_INTERPRETATION,
                    maxTokens = 2000
                )
                aiResult.fold(
                    onSuccess = { ai ->
                        val structured = analysisResponseParser.parseOrNull(ai.content)
                        _interpretationState.value = InterpretationState.Success(
                            summary = structured?.summary ?: ai.content,
                            engineInterpretations = emptyMap(),
                            provider = InterpretationProvider.AI,
                            structured = structured,
                            aiResult = ai
                        )
                        if (structured != null) saveInterpretationToSnapshot(ai.content)
                    },
                    onFailure = { e ->
                        _interpretationState.value = InterpretationState.Error(aiErrorMessage(e))
                    }
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _interpretationState.value = InterpretationState.Error(aiErrorMessage(e))
            }
        }
    }

    /** 신선한 스냅샷에 저장된 AI 해석이 있으면 복원. 복원 성공 시 true. */
    private suspend fun restoreCachedInterpretation(): Boolean {
        val ticker = currentTicker ?: return false
        return try {
            val latest = analysisSnapshotDao.getRecentByTicker(ticker, 1).firstOrNull() ?: return false
            val saved = latest.aiInterpretation ?: return false
            if (System.currentTimeMillis() - latest.analyzedAt > AI_INTERPRETATION_TTL_MS) return false

            val structured = analysisResponseParser.parseOrNull(saved) ?: return false
            _interpretationState.value = InterpretationState.Success(
                summary = structured.summary,
                engineInterpretations = emptyMap(),
                provider = InterpretationProvider.AI,
                structured = structured,
                fromCache = true
            )
            true
        } catch (e: Exception) {
            Timber.w(e, "저장된 AI 해석 복원 실패")
            false
        }
    }

    private fun saveInterpretationToSnapshot(content: String) {
        viewModelScope.launch {
            try {
                val id = lastSnapshotId
                    ?: currentTicker?.let { analysisSnapshotDao.getRecentByTicker(it, 1).firstOrNull()?.id }
                    ?: return@launch
                analysisSnapshotDao.updateAiInterpretation(id, content)
            } catch (e: Exception) {
                Timber.w(e, "AI 해석 저장 실패")
            }
        }
    }

    fun dismissInterpretation() {
        _interpretationState.value = InterpretationState.Idle
    }

    fun dismissProbability() {
        _probabilityState.value = ProbabilityAnalysisState.Idle
        _interpretationState.value = InterpretationState.Idle
    }

    /** 특정 종목의 분석 캐시 무효화 (수동 새로고침) */
    fun clearAnalysisCache(ticker: String) {
        viewModelScope.launch {
            probabilityAnalysisUseCase.clearAnalysisCache(ticker)
        }
    }

    companion object {
        /** 저장된 AI 해석 재사용 유효 기간 — FeatureStore Daily TTL(4시간)과 동일 */
        private const val AI_INTERPRETATION_TTL_MS = 4 * 60 * 60 * 1000L
    }
}
