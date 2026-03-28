package com.tinyoscillator.domain.usecase

import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.data.mapper.AnalysisResponseParser
import com.tinyoscillator.data.mapper.ProbabilisticPromptBuilder
import com.tinyoscillator.domain.model.AnalysisState
import com.tinyoscillator.domain.repository.LlmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 확률적 주식 분석 전체 파이프라인 오케스트레이션
 *
 * StatisticalAnalysisEngine.analyze()
 *   → ProbabilisticPromptBuilder.build()
 *   → LlmRepository.generate()
 *   → AnalysisResponseParser.parse()
 *
 * Flow<AnalysisState>로 각 단계별 진행 상태를 방출.
 */
@Singleton
class AnalyzeStockProbabilityUseCase @Inject constructor(
    private val statisticalEngine: StatisticalAnalysisEngine,
    private val promptBuilder: ProbabilisticPromptBuilder,
    private val responseParser: AnalysisResponseParser,
    private val llmRepository: LlmRepository
) {

    fun execute(stockCode: String): Flow<AnalysisState> = flow {
        try {
            // Stage 1: 통계 엔진 실행
            emit(AnalysisState.Computing("7개 통계 알고리즘 실행 중...", 0.1f))
            Timber.d("통계 분석 시작: $stockCode")

            val statisticalResult = statisticalEngine.analyze(stockCode)

            val completedEngines = 8 - (statisticalResult.executionMetadata.failedEngines.size)
            emit(AnalysisState.Computing(
                "통계 분석 완료 ($completedEngines/8 엔진 성공, ${statisticalResult.executionMetadata.totalTimeMs}ms)",
                0.5f
            ))

            // Stage 2: 프롬프트 생성
            emit(AnalysisState.Computing("LLM 프롬프트 생성 중...", 0.6f))
            val prompt = promptBuilder.build(statisticalResult)
            val estimatedTokens = promptBuilder.estimateTokenCount(prompt)
            Timber.d("프롬프트 생성 완료: ~${estimatedTokens} tokens")

            // Stage 3: LLM 추론
            emit(AnalysisState.LlmProcessing("AI 분석 대기 중..."))
            val sb = StringBuilder()

            llmRepository.generate(prompt).collect { token ->
                sb.append(token)
                emit(AnalysisState.Streaming(sb.toString()))
            }

            // Stage 4: 응답 파싱
            emit(AnalysisState.Computing("분석 결과 처리 중...", 0.9f))
            val analysis = responseParser.parse(sb.toString())

            emit(AnalysisState.Success(analysis, statisticalResult))
            Timber.d("분석 완료: ${analysis.overallAssessment}")

        } catch (e: Exception) {
            Timber.e(e, "분석 실패: ${e.message}")
            emit(AnalysisState.Error(e.message ?: "알 수 없는 오류", e))
        }
    }
}
