package com.tinyoscillator.presentation.ai

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.AiStreamEvent
import com.tinyoscillator.domain.model.ChatMessage
import com.tinyoscillator.domain.model.ChatRole
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.FinancialData
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.SignalAnalysis
import com.tinyoscillator.domain.model.StatisticalResult
import com.tinyoscillator.domain.model.StockAggregatedTimePoint
import com.tinyoscillator.domain.model.StockAnalysis

enum class AiTab(val label: String) {
    MARKET("시장지표"),
    STOCK("종목"),
    PROBABILITY("확률분석")
}

data class SelectedStockInfo(
    val ticker: String,
    val name: String,
    val market: String?,
    val sector: String?
)

sealed class StockDataState {
    data object Idle : StockDataState()

    /** [completedSources]: 수집 완료된 데이터 소스명 (체크리스트 진행 표시용) */
    data class Loading(val completedSources: Set<String> = emptySet()) : StockDataState()

    data class Loaded(
        val oscillatorRows: List<OscillatorRow>,
        val signals: List<SignalAnalysis>,
        val demarkRows: List<DemarkTDRow>,
        val financialData: FinancialData?,
        val etfAggregated: List<StockAggregatedTimePoint>
    ) : StockDataState()
    data class Error(val message: String) : StockDataState()
}

/** 종목 데이터 수집 소스 (체크리스트 표시 순서) */
internal val STOCK_DATA_SOURCES = listOf("일별 매매", "재무정보", "ETF 추이")

sealed class ProbabilityAnalysisState {
    data object Idle : ProbabilityAnalysisState()

    /** [completed]/[total]: 엔진 진행률 (total=0이면 indeterminate) */
    data class Computing(
        val message: String,
        val completed: Int = 0,
        val total: Int = 0
    ) : ProbabilityAnalysisState()

    data class Success(val result: StatisticalResult) : ProbabilityAnalysisState()
    data class Error(val message: String) : ProbabilityAnalysisState()
}

enum class InterpretationProvider(val label: String) {
    LOCAL("로컬 분석"),
    AI("AI 분석")
}

sealed class InterpretationState {
    data object Idle : InterpretationState()
    data object Loading : InterpretationState()
    data class Success(
        val summary: String,
        val engineInterpretations: Map<String, String>,
        val provider: InterpretationProvider,
        /** AI 구조화 해석 (판단/신뢰도/인사이트/충돌/리스크/행동) — 파싱 성공 시에만 */
        val structured: StockAnalysis? = null,
        /** AI 호출 메타 (토큰 사용량 등) */
        val aiResult: AiAnalysisResult? = null,
        /** 저장된 해석 재사용 여부 (API 호출 없음) */
        val fromCache: Boolean = false
    ) : InterpretationState()
    data class Error(val message: String) : InterpretationState()
    data object NoApiKey : InterpretationState()
}

/** 채팅 세션 누적 토큰 사용량 */
data class ChatTokenUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val cacheReadTokens: Int = 0
) {
    operator fun plus(done: AiStreamEvent.Done) = ChatTokenUsage(
        inputTokens = inputTokens + done.inputTokens,
        outputTokens = outputTokens + done.outputTokens,
        cacheReadTokens = cacheReadTokens + done.cacheReadTokens
    )

    val isEmpty: Boolean get() = inputTokens == 0 && outputTokens == 0
}

/**
 * API에 전송할 대화 히스토리 상한 — 오래된 턴은 잘라 토큰 비용을 제한한다.
 * Claude는 첫 메시지가 user여야 하므로 자른 뒤 assistant 선두를 제거한다.
 */
internal fun trimChatHistory(messages: List<ChatMessage>, maxMessages: Int = 12): List<ChatMessage> {
    if (messages.size <= maxMessages) return messages
    return messages.takeLast(maxMessages).dropWhile { it.role != ChatRole.USER }
}

/** 채팅/해석 공용 에러 메시지 — 429 등 주요 오류에 행동 안내 포함 */
internal fun aiErrorMessage(e: Throwable?): String = when {
    e is ApiError.ApiCallError && e.code == 429 ->
        "요청 한도에 도달했습니다. 잠시 후 다시 시도하거나, 설정에서 다른 모델(예: Haiku/Flash)로 전환해보세요."
    e is ApiError.AuthError ->
        "API 키 인증에 실패했습니다. 설정에서 API 키를 확인해주세요."
    e is ApiError.CircuitBreakerOpenError ->
        "연속 오류로 호출이 일시 중단되었습니다. 5분 후 자동 복구됩니다."
    else -> "오류: ${e?.message ?: "알 수 없는 오류"}"
}
