package com.tinyoscillator.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.model.AiAnalysisState
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AiStreamEvent
import com.tinyoscillator.domain.model.ChatMessage
import com.tinyoscillator.domain.model.ChatRole
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 시장지표 탭 전담 ViewModel.
 *
 * 책임: 시장 데이터 수집 → AI 분석 결과 노출, 시장 채팅 컨텍스트/메시지 관리.
 * 시스템 프롬프트는 `prepareMarketData()`로 준비되고, `sendMarketChat`/`analyzeMarketWithAi`
 * 내부에서만 참조한다 (외부 노출 X).
 */
@HiltViewModel
class AiMarketAnalysisViewModel @Inject constructor(
    private val marketIndicatorRepository: MarketIndicatorRepository,
    private val aiApiClient: AiApiClient,
    private val aiPreparer: AiAnalysisPreparer,
    private val apiConfigProvider: ApiConfigProvider
) : ViewModel() {

    private val _marketAiState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val marketAiState: StateFlow<AiAnalysisState> = _marketAiState.asStateFlow()

    private val _marketDataPrepared = MutableStateFlow(false)
    val marketDataPrepared: StateFlow<Boolean> = _marketDataPrepared.asStateFlow()

    private val _marketDataSummary = MutableStateFlow("")
    val marketDataSummary: StateFlow<String> = _marketDataSummary.asStateFlow()

    private val _marketDataLoading = MutableStateFlow(false)
    val marketDataLoading: StateFlow<Boolean> = _marketDataLoading.asStateFlow()

    private var marketSystemPrompt: String = ""

    private val _marketChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val marketChatMessages: StateFlow<List<ChatMessage>> = _marketChatMessages.asStateFlow()

    private val _marketChatLoading = MutableStateFlow(false)
    val marketChatLoading: StateFlow<Boolean> = _marketChatLoading.asStateFlow()

    /** 스트리밍 중인 어시스턴트 응답 (진행 중일 때만 non-null) */
    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    /** 채팅 세션 누적 토큰 사용량 */
    private val _chatTokenUsage = MutableStateFlow(ChatTokenUsage())
    val chatTokenUsage: StateFlow<ChatTokenUsage> = _chatTokenUsage.asStateFlow()

    fun analyzeMarketWithAi() {
        viewModelScope.launch {
            val aiConfig = apiConfigProvider.getAiConfig()
            if (!aiConfig.isValid()) {
                _marketAiState.value = AiAnalysisState.NoApiKey
                return@launch
            }
            _marketAiState.value = AiAnalysisState.Loading
            try {
                val kospiData = marketIndicatorRepository.getRecentData("KOSPI", 14)
                val kosdaqData = marketIndicatorRepository.getRecentData("KOSDAQ", 14)
                val deposits = marketIndicatorRepository.getRecentDeposits(10)

                val userMessage = aiPreparer.prepareMarketAnalysis(
                    kospiData = kospiData,
                    kosdaqData = kosdaqData,
                    deposits = deposits
                )
                val systemPrompt = aiPreparer.getSystemPrompt(AiAnalysisType.MARKET_OVERVIEW)
                val result = aiApiClient.analyze(
                    config = aiConfig,
                    systemPrompt = systemPrompt,
                    userMessage = userMessage,
                    analysisType = AiAnalysisType.MARKET_OVERVIEW
                )
                result.fold(
                    onSuccess = { _marketAiState.value = AiAnalysisState.Success(it) },
                    onFailure = { _marketAiState.value = AiAnalysisState.Error(it.message ?: "AI 분석 실패") }
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _marketAiState.value = AiAnalysisState.Error(e.message ?: "AI 분석 실패")
            }
        }
    }

    fun dismissMarketAi() {
        _marketAiState.value = AiAnalysisState.Idle
    }

    /** 시장지표 데이터 준비 (채팅 컨텍스트 로드) */
    fun prepareMarketData() {
        viewModelScope.launch {
            _marketDataLoading.value = true
            try {
                val kospiData = marketIndicatorRepository.getRecentData("KOSPI", 14)
                val kosdaqData = marketIndicatorRepository.getRecentData("KOSDAQ", 14)
                val deposits = marketIndicatorRepository.getRecentDeposits(10)

                val dataSummary = aiPreparer.prepareMarketAnalysis(
                    kospiData = kospiData,
                    kosdaqData = kosdaqData,
                    deposits = deposits
                )
                _marketDataSummary.value = dataSummary
                marketSystemPrompt = aiPreparer.getChatSystemPrompt(AiAnalysisType.MARKET_OVERVIEW, dataSummary)
                _marketDataPrepared.value = true
                _marketChatMessages.value = emptyList()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _marketDataSummary.value = "데이터 로드 실패: ${e.message}"
            } finally {
                _marketDataLoading.value = false
            }
        }
    }

    /** 시장지표 채팅 메시지 전송 */
    fun sendMarketChat(userMessage: String) {
        if (userMessage.isBlank() || !_marketDataPrepared.value) return

        viewModelScope.launch {
            val aiConfig = apiConfigProvider.getAiConfig()
            if (!aiConfig.isValid()) {
                _marketChatMessages.value = _marketChatMessages.value +
                    ChatMessage(ChatRole.ASSISTANT, "AI API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요.")
                return@launch
            }

            val userMsg = ChatMessage(ChatRole.USER, userMessage)
            _marketChatMessages.value = _marketChatMessages.value + userMsg
            _marketChatLoading.value = true
            _streamingReply.value = null

            try {
                aiApiClient.chatStream(
                    config = aiConfig,
                    systemPrompt = marketSystemPrompt,
                    messages = trimChatHistory(_marketChatMessages.value),
                    maxTokens = 1024
                ).collect { event ->
                    when (event) {
                        is AiStreamEvent.Delta ->
                            _streamingReply.value = (_streamingReply.value ?: "") + event.text
                        is AiStreamEvent.Done -> {
                            _marketChatMessages.value = _marketChatMessages.value +
                                ChatMessage(ChatRole.ASSISTANT, event.fullText)
                            _streamingReply.value = null
                            _chatTokenUsage.value = _chatTokenUsage.value + event
                        }
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                val partial = _streamingReply.value
                _streamingReply.value = null
                val text = if (partial.isNullOrBlank()) aiErrorMessage(e)
                else "$partial\n\n(응답 중단) ${aiErrorMessage(e)}"
                _marketChatMessages.value = _marketChatMessages.value + ChatMessage(ChatRole.ASSISTANT, text)
            } finally {
                _marketChatLoading.value = false
            }
        }
    }

    fun clearMarketChat() {
        _marketChatMessages.value = emptyList()
        _streamingReply.value = null
        _chatTokenUsage.value = ChatTokenUsage()
    }
}
