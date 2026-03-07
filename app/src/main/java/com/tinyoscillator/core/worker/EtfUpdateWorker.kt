package com.tinyoscillator.core.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.EtfDataProgress
import com.tinyoscillator.presentation.settings.loadEtfCollectionPeriod
import com.tinyoscillator.presentation.settings.loadEtfKeywordFilter
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class EtfUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val etfRepository: EtfRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("ETF 업데이트 워커 시작 (attempt: $runAttemptCount)")

        val creds = loadKrxCredentials(applicationContext)
        if (creds.id.isBlank() || creds.password.isBlank()) {
            Timber.w("KRX 자격증명 미설정 → 워커 건너뜀")
            return Result.success()
        }

        val keywords = loadEtfKeywordFilter(applicationContext)
        val period = loadEtfCollectionPeriod(applicationContext)

        var lastResult: Result = Result.success()
        etfRepository.updateData(creds, keywords, daysBack = period.daysBack).collect { progress ->
            when (progress) {
                is EtfDataProgress.Success -> {
                    Timber.d("ETF 업데이트 완료: ${progress.etfCount} ETFs, ${progress.holdingCount} holdings")
                    lastResult = Result.success()
                }
                is EtfDataProgress.Error -> {
                    Timber.e("ETF 업데이트 실패: ${progress.message}")
                    lastResult = if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
                is EtfDataProgress.Loading -> {
                    Timber.d("ETF 업데이트 진행: ${progress.message}")
                }
            }
        }

        return lastResult
    }

    companion object {
        const val WORK_NAME = "etf_daily_update"
    }
}
