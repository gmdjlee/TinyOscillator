package com.tinyoscillator.presentation.fundamental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.network.NetworkUtils
import com.tinyoscillator.data.repository.FundamentalHistoryRepository
import com.tinyoscillator.domain.model.FundamentalHistoryState
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class FundamentalHistoryViewModel @Inject constructor(
    application: Application,
    private val fundamentalHistoryRepository: FundamentalHistoryRepository,
    private val krxApiClient: KrxApiClient
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<FundamentalHistoryState>(FundamentalHistoryState.NoStock)
    val state: StateFlow<FundamentalHistoryState> = _state.asStateFlow()

    @Volatile
    private var currentTicker: String? = null
    @Volatile
    private var currentName: String? = null
    private val loginMutex = Mutex()
    @Volatile
    private var loginAttempted = false

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun loadForStock(ticker: String, name: String) {
        if (ticker == currentTicker && _state.value is FundamentalHistoryState.Loading) return
        currentTicker = ticker
        currentName = name
        loadData(ticker, name)
    }

    fun retry() {
        val ticker = currentTicker ?: return
        val name = currentName ?: return
        loginAttempted = false
        loadData(ticker, name)
    }

    fun clearStock() {
        currentTicker = null
        currentName = null
        _state.value = FundamentalHistoryState.NoStock
    }

    private fun loadData(ticker: String, name: String) {
        viewModelScope.launch {
            try {
                _state.value = FundamentalHistoryState.Loading

                if (!NetworkUtils.isNetworkAvailable(getApplication())) {
                    _state.value = FundamentalHistoryState.Error(
                        "네트워크에 연결되어 있지 않습니다."
                    )
                    return@launch
                }

                // KRX 로그인 확인
                val loginResult = ensureKrxLogin()
                Timber.d("FundamentalVM: ensureKrxLogin=$loginResult, krxStock=${krxApiClient.getKrxStock() != null}")
                if (!loginResult) {
                    _state.value = FundamentalHistoryState.NoKrxLogin
                    return@launch
                }

                val endDate = LocalDate.now().format(fmt)
                val startDate = LocalDate.now().minusYears(2).format(fmt)
                Timber.d("FundamentalVM: loading ticker=$ticker, range=$startDate~$endDate")

                val data = fundamentalHistoryRepository.getFundamentalHistory(
                    ticker, startDate, endDate
                )

                Timber.d("FundamentalVM: received ${data.size} items")
                if (data.isEmpty()) {
                    _state.value = FundamentalHistoryState.Error(
                        "투자지표 데이터를 찾을 수 없습니다."
                    )
                } else {
                    _state.value = FundamentalHistoryState.Success(
                        ticker = ticker,
                        stockName = name,
                        data = data
                    )
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Fundamental 데이터 로드 실패")
                _state.value = FundamentalHistoryState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다."
                )
            }
        }
    }

    private suspend fun ensureKrxLogin(): Boolean {
        // 이미 로그인 되어있으면 true
        if (krxApiClient.getKrxStock() != null) return true

        return loginMutex.withLock {
            // Double-check
            if (krxApiClient.getKrxStock() != null) return@withLock true
            if (loginAttempted) return@withLock false

            loginAttempted = true
            val creds = loadKrxCredentials(getApplication())
            if (creds.id.isBlank() || creds.password.isBlank()) {
                Timber.w("KRX credentials 미설정")
                return@withLock false
            }

            val success = krxApiClient.login(creds.id, creds.password)
            Timber.d("KRX 로그인 결과: $success")
            success
        }
    }
}
