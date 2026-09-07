package com.tinyoscillator.presentation.investorprofile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.toUserMessage
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.data.repository.InvestorFlowRepository
import com.tinyoscillator.domain.model.DailyInvestorFlow
import com.tinyoscillator.domain.model.InvestorProfileQuery
import com.tinyoscillator.domain.model.InvestorType
import com.tinyoscillator.domain.model.InvestorVolumeProfile
import com.tinyoscillator.domain.model.ProfileBasis
import com.tinyoscillator.domain.model.ProfileDisplayMode
import com.tinyoscillator.domain.model.ProfilePeriod
import com.tinyoscillator.domain.usecase.CalcInvestorVolumeProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 주체별 매물대 화면 상태. 명세: `docs/TASK_investor_volume_profile.md` §7.1.
 */
sealed class InvestorProfileState {
    data object NoStock : InvestorProfileState()

    /** [page] 0 = 캐시 확인 중(네트워크 페이지 수집 이전). [page]/[maxPages] > 0이면 수집 진행 중. */
    data class Loading(val page: Int = 0, val maxPages: Int = 0) : InvestorProfileState()

    /** 질의(query)는 [InvestorProfileViewModel.query] StateFlow가 단일 출처다. */
    data class Success(val profile: InvestorVolumeProfile) : InvestorProfileState()
    data class Empty(val message: String) : InvestorProfileState()
    data class Error(val message: String) : InvestorProfileState()
    data class NoApiKey(val message: String) : InvestorProfileState()
}

/**
 * 주체별 매물대 ViewModel. `DemarkTDViewModel`을 미러링한다(명세 §7.1, §12.3).
 *
 * 원천 행([cachedRows])은 이 ViewModel이 보관하며, 표시 필터(주체 선택·표시 방식)는 재계산·네트워크
 * 호출을 유발하지 않는다(§7.1 규칙 ①②④). 산출 기준·구간 수 변경은 재계산만 하고([recompute]),
 * 기간 변경은 리포지토리를 다시 호출한다([load] — 캐시 범위 안이면 리포지토리가 네트워크를 생략한다).
 */
@HiltViewModel
class InvestorProfileViewModel @Inject constructor(
    application: Application,
    private val repository: InvestorFlowRepository,
    private val calcProfile: CalcInvestorVolumeProfileUseCase,
    private val apiConfigProvider: ApiConfigProvider
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<InvestorProfileState>(InvestorProfileState.NoStock)
    val state: StateFlow<InvestorProfileState> = _state.asStateFlow()

    private val _query = MutableStateFlow(InvestorProfileQuery())
    val query: StateFlow<InvestorProfileQuery> = _query.asStateFlow()

    private val _selectedInvestors = MutableStateFlow(InvestorType.entries.toSet())
    val selectedInvestors: StateFlow<Set<InvestorType>> = _selectedInvestors.asStateFlow()

    private val _displayMode = MutableStateFlow(ProfileDisplayMode.GROUPED)
    val displayMode: StateFlow<ProfileDisplayMode> = _displayMode.asStateFlow()

    @Volatile
    private var currentTicker: String? = null

    @Volatile
    private var cachedRows: List<DailyInvestorFlow>? = null

    private var loadJob: Job? = null
    private var recomputeJob: Job? = null

    /**
     * 새 종목 진입. 같은 종목을 이미 로딩 중이면 무시한다(`DemarkTDViewModel.loadForStock` 관례).
     * `stockName`은 화면(Content)이 직접 표시하므로 이 ViewModel은 보관하지 않는다.
     */
    fun loadForStock(ticker: String, stockName: String) {
        if (ticker == currentTicker && _state.value is InvestorProfileState.Loading) return
        currentTicker = ticker
        cachedRows = null
        load()
    }

    /** 진행 중인 작업을 모두 취소하고 초기 상태로 되돌린다. */
    fun clearStock() {
        loadJob?.cancel()
        recomputeJob?.cancel()
        currentTicker = null
        cachedRows = null
        _state.value = InvestorProfileState.NoStock
    }

    /** 현재 종목을 다시 조회한다. 로드된 종목이 없으면 아무것도 하지 않는다. */
    fun retry() {
        if (currentTicker != null) load()
    }

    /** 기간 변경 → 재조회(§7.1 규칙 ③). CUSTOM이 아니면 이전에 지정한 사용자 지정 날짜를 지운다. */
    fun setPeriod(period: ProfilePeriod) {
        _query.value = if (period == ProfilePeriod.CUSTOM) {
            _query.value.copy(period = period)
        } else {
            _query.value.copy(period = period, customStart = null, customEnd = null)
        }
        load()
    }

    /** `start <= end <= today`가 아니면 무시한다. 통과하면 기간을 CUSTOM으로 바꾸고 재조회한다. */
    fun setCustomRange(start: LocalDate, end: LocalDate) {
        if (start > end || end > todayKst()) return
        _query.value = _query.value.copy(period = ProfilePeriod.CUSTOM, customStart = start, customEnd = end)
        load()
    }

    /** 재계산만 유발한다. 리포지토리를 호출하지 않는다(§7.1 규칙 ②). */
    fun setBasis(basis: ProfileBasis?) {
        _query.value = _query.value.copy(basis = basis)
        recompute()
    }

    /** 재계산만 유발한다. 리포지토리를 호출하지 않는다(§7.1 규칙 ②). */
    fun setBinCount(binCount: Int) {
        _query.value = _query.value.copy(binCount = binCount)
        recompute()
    }

    /**
     * 표시 필터. 마지막 남은 1개는 해제하지 않는다(§7.1 규칙 ⑥).
     * 재계산·네트워크를 유발하지 않는다(§7.1 규칙 ④).
     */
    fun toggleInvestor(type: InvestorType) {
        val current = _selectedInvestors.value
        _selectedInvestors.value = when {
            type !in current -> current + type
            current.size == 1 -> current
            else -> current - type
        }
    }

    /** 표시 필터. 재계산·네트워크를 유발하지 않는다(§7.1 규칙 ④). */
    fun setDisplayMode(mode: ProfileDisplayMode) {
        _displayMode.value = mode
    }

    /**
     * 원천 행을 리포지토리에서 가져온다(§7.1 마지막 문단). 오프라인이면 `allowNetwork = false`로
     * 호출해 캐시 범위로만 계산한다(규칙 ⑦). 진행 중이던 이전 로드는 취소한다.
     */
    private fun load() {
        val ticker = currentTicker ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                _state.value = InvestorProfileState.Loading()
                val today = todayKst()
                val from = resolveFrom(_query.value, today)

                val online = NetworkUtils.isNetworkAvailable(getApplication())
                val config = apiConfigProvider.getKisConfig()
                if (online && !config.isValid()) {
                    _state.value = InvestorProfileState.NoApiKey(NO_API_KEY_MESSAGE)
                    return@launch
                }
                if (online && config.investmentMode == InvestmentMode.MOCK) {
                    _state.value = InvestorProfileState.NoApiKey(MOCK_MODE_MESSAGE)
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    repository.getFlows(ticker, from, config, allowNetwork = online) { page, maxPages ->
                        _state.value = InvestorProfileState.Loading(page, maxPages)
                    }
                }

                result.fold(
                    onSuccess = { rows ->
                        when {
                            rows.isEmpty() && !online -> _state.value = InvestorProfileState.Error(OFFLINE_MESSAGE)
                            rows.isEmpty() -> _state.value = InvestorProfileState.Empty(EMPTY_MESSAGE)
                            else -> {
                                cachedRows = rows
                                recompute()
                            }
                        }
                    },
                    onFailure = { e ->
                        _state.value = if (e is ApiError.NoApiKeyError) {
                            InvestorProfileState.NoApiKey(NO_API_KEY_MESSAGE)
                        } else {
                            InvestorProfileState.Error(e.toUserMessage())
                        }
                    }
                )
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = InvestorProfileState.Error(e.toUserMessage())
            }
        }
    }

    /**
     * [cachedRows]를 현재 [query]로 재계산한다(§7.1 규칙 ⑤). 이전 재계산 Job은 취소한다.
     * CUSTOM 기간의 날짜 결측·역전은 유스케이스의 `require` 실패로 이어지므로 호출 전에 걸러낸다.
     */
    private fun recompute() {
        val rows = cachedRows ?: return
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val q = _query.value
                if (q.isCustomRangeInvalid()) {
                    _state.value = InvestorProfileState.Error(CUSTOM_RANGE_INVALID_MESSAGE)
                    return@launch
                }
                val profile = calcProfile(rows, q, todayKst())
                _state.value = if (profile == null) {
                    InvestorProfileState.Empty(EMPTY_MESSAGE)
                } else {
                    InvestorProfileState.Success(profile)
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = InvestorProfileState.Error(e.toUserMessage())
            }
        }
    }

    private fun todayKst(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

    /**
     * 기간 → 리포지토리 조회 시작일(§7.1 마지막 문단). 스윙 기간은 1년 창을 요청한 뒤 유스케이스가
     * 좁힌다(§5.6). CUSTOM은 `customStart`를 쓰되, 결측 시 6개월 기본값으로 대체한다.
     */
    private fun resolveFrom(query: InvestorProfileQuery, today: LocalDate): LocalDate {
        query.period.days?.let { return today.minusDays(it) }
        return when (query.period) {
            ProfilePeriod.CUSTOM -> query.customStart ?: today.minusDays(requireNotNull(ProfilePeriod.M6.days))
            else -> today.minusDays(SWING_SEARCH_DAYS) // SINCE_SWING_HIGH / SINCE_SWING_LOW
        }
    }

    private fun InvestorProfileQuery.isCustomRangeInvalid(): Boolean {
        if (period != ProfilePeriod.CUSTOM) return false
        val start = customStart
        val end = customEnd
        return start == null || end == null || start > end
    }

    companion object {
        private const val SWING_SEARCH_DAYS = 365L
        private const val NO_API_KEY_MESSAGE = "API 키가 설정되지 않았습니다.\n설정 화면에서 KIS API 키를 입력해주세요."
        private const val MOCK_MODE_MESSAGE = "모의투자 모드에서는 투자자매매동향을 조회할 수 없습니다. 설정에서 실전투자로 전환해 주세요."
        private const val OFFLINE_MESSAGE = "네트워크에 연결되어 있지 않습니다. 인터넷 연결을 확인해주세요."
        private const val EMPTY_MESSAGE = "해당 기간에 투자자 데이터가 없습니다."
        private const val CUSTOM_RANGE_INVALID_MESSAGE = "사용자 지정 기간이 올바르지 않습니다."
    }
}
