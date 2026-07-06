package com.tinyoscillator.presentation.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity
import com.tinyoscillator.data.mapper.AnalysisResponseParser
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.domain.model.ConsensusReport
import com.tinyoscillator.domain.model.StockAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 종목분석 "종합" 탭 부가 데이터.
 * 전부 로컬 캐시(Room)만 조회 — 신규 API 호출 없음. 없는 소스는 null로 두고 UI가 안내 문구 표시.
 */
data class StockSummaryExtras(
    val fundamental: FundamentalCacheEntity?,
    val latestReport: ConsensusReport?,
    val ensembleScore: Double?,
    val analyzedAt: Long?,
    val aiAnalysis: StockAnalysis?
)

@HiltViewModel
class StockSummaryViewModel @Inject constructor(
    private val fundamentalCacheDao: FundamentalCacheDao,
    private val consensusRepository: ConsensusRepository,
    private val analysisSnapshotDao: AnalysisSnapshotDao,
    private val analysisResponseParser: AnalysisResponseParser
) : ViewModel() {

    private val _extras = MutableStateFlow<StockSummaryExtras?>(null)
    val extras: StateFlow<StockSummaryExtras?> = _extras.asStateFlow()

    private var currentTicker: String? = null

    fun load(ticker: String) {
        if (ticker == currentTicker) return
        currentTicker = ticker
        _extras.value = null
        viewModelScope.launch {
            val fundamental = runCatching { fundamentalCacheDao.getLatestByTicker(ticker) }
                .onFailure { Timber.w(it, "종합 탭 재무 캐시 조회 실패: %s", ticker) }
                .getOrNull()
            val report = runCatching { consensusRepository.getReportsByTicker(ticker).firstOrNull() }
                .onFailure { Timber.w(it, "종합 탭 컨센서스 조회 실패: %s", ticker) }
                .getOrNull()
            val snapshot = runCatching { analysisSnapshotDao.getRecentByTicker(ticker, 1).firstOrNull() }
                .onFailure { Timber.w(it, "종합 탭 분석 스냅샷 조회 실패: %s", ticker) }
                .getOrNull()
            _extras.value = StockSummaryExtras(
                fundamental = fundamental,
                latestReport = report,
                ensembleScore = snapshot?.ensembleScore,
                analyzedAt = snapshot?.analyzedAt,
                aiAnalysis = snapshot?.aiInterpretation?.let { analysisResponseParser.parseOrNull(it) }
            )
        }
    }
}
