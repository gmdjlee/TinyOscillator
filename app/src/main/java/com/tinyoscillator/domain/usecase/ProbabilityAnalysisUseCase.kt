package com.tinyoscillator.domain.usecase

import com.tinyoscillator.data.engine.FeatureStore
import com.tinyoscillator.data.engine.RationaleBuilder
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.domain.model.AlgoResult
import com.tinyoscillator.domain.model.CacheStats
import com.tinyoscillator.domain.model.MetaLearnerStatus
import com.tinyoscillator.domain.model.StatisticalResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 확률 분석 유스케이스 — Presentation 계층이 사용할 단일 진입점.
 *
 * `StatisticalAnalysisEngine`, `FeatureStore`, `RationaleBuilder` 등 data 레이어
 * 구현체를 ViewModel/Compose에서 직접 참조하지 않도록 래핑한다.
 */
@Singleton
class ProbabilityAnalysisUseCase @Inject constructor(
    private val engine: StatisticalAnalysisEngine,
    featureStore: FeatureStore,
) {

    /** Feature Store 캐시 통계 스트림 */
    val cacheStats: Flow<CacheStats> = featureStore.cacheStats

    /** 9개 통계 엔진 병렬 실행 (FeatureStore 캐시 적용) */
    suspend fun analyze(ticker: String): StatisticalResult = engine.analyze(ticker)

    /** 앙상블(스태킹 + 점진적) 상승 확률 — 학습 전이면 가중합 폴백 */
    fun getEnsembleProbability(result: StatisticalResult): Double =
        engine.getEnsembleProbability(result)

    /** 메타 학습기(스태킹) 학습 상태 */
    fun getMetaLearnerStatus(): MetaLearnerStatus = engine.getMetaLearnerStatus()

    /** 특정 종목의 분석 캐시 무효화 (수동 새로고침 용) */
    suspend fun clearAnalysisCache(ticker: String) = engine.clearAnalysisCache(ticker)

    /** 분석 결과 → 알고리즘별 점수·한국어 근거 매핑 */
    fun buildAlgoRationales(result: StatisticalResult): Map<String, AlgoResult> =
        RationaleBuilder.build(result)
}
