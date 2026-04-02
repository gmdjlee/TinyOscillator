package com.tinyoscillator.core.worker

import android.content.Context
import androidx.work.*
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerHelper {

    // ===== Generic scheduling helpers =====

    private inline fun <reified W : ListenableWorker> scheduleDailyWorker(
        context: Context,
        workName: String,
        tag: String,
        label: String,
        hour: Int,
        minute: Int
    ) {
        require(hour in 0..23) { "hour must be 0-23, got $hour" }
        require(minute in 0..59) { "minute must be 0-59, got $minute" }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<W>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

        Timber.d("$label 일일 업데이트 스케줄 등록: %02d:%02d (초기 딜레이: %d분)", hour, minute, initialDelay / 60000)
    }

    private fun cancelWorker(context: Context, workName: String, label: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName)
        Timber.d("$label 일일 업데이트 스케줄 취소")
    }

    private inline fun <reified W : ListenableWorker> runWorkerNow(
        context: Context,
        workName: String,
        tag: String,
        label: String
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<W>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        Timber.d("$label 즉시 업데이트 요청")
    }

    // ===== ETF =====

    fun scheduleEtfUpdate(context: Context, hour: Int = 0, minute: Int = 30) =
        scheduleDailyWorker<EtfUpdateWorker>(context, EtfUpdateWorker.WORK_NAME, EtfUpdateWorker.TAG, "ETF", hour, minute)

    fun cancelEtfUpdate(context: Context) =
        cancelWorker(context, EtfUpdateWorker.WORK_NAME, "ETF")

    fun runEtfUpdateNow(context: Context) =
        runWorkerNow<EtfUpdateWorker>(context, EtfUpdateWorker.MANUAL_WORK_NAME, EtfUpdateWorker.TAG, "ETF")

    // ===== 시장지표(과매수/과매도) =====

    fun scheduleOscillatorUpdate(context: Context, hour: Int = 1, minute: Int = 0) =
        scheduleDailyWorker<MarketOscillatorUpdateWorker>(context, MarketOscillatorUpdateWorker.WORK_NAME, MarketOscillatorUpdateWorker.TAG, "시장지표", hour, minute)

    fun cancelOscillatorUpdate(context: Context) =
        cancelWorker(context, MarketOscillatorUpdateWorker.WORK_NAME, "시장지표")

    fun runOscillatorUpdateNow(context: Context) =
        runWorkerNow<MarketOscillatorUpdateWorker>(context, MarketOscillatorUpdateWorker.MANUAL_WORK_NAME, MarketOscillatorUpdateWorker.TAG, "시장지표")

    // ===== 자금 동향(deposit) =====

    fun scheduleDepositUpdate(context: Context, hour: Int = 2, minute: Int = 0) =
        scheduleDailyWorker<MarketDepositUpdateWorker>(context, MarketDepositUpdateWorker.WORK_NAME, MarketDepositUpdateWorker.TAG, "자금 동향", hour, minute)

    fun cancelDepositUpdate(context: Context) =
        cancelWorker(context, MarketDepositUpdateWorker.WORK_NAME, "자금 동향")

    fun runDepositUpdateNow(context: Context) =
        runWorkerNow<MarketDepositUpdateWorker>(context, MarketDepositUpdateWorker.MANUAL_WORK_NAME, MarketDepositUpdateWorker.TAG, "자금 동향")

    // ===== 장 마감 데이터 교체 =====

    fun scheduleMarketCloseRefresh(context: Context, hour: Int = 19, minute: Int = 0) =
        scheduleDailyWorker<MarketCloseRefreshWorker>(context, MarketCloseRefreshWorker.WORK_NAME, MarketCloseRefreshWorker.TAG, "장 마감 교체", hour, minute)

    fun cancelMarketCloseRefresh(context: Context) =
        cancelWorker(context, MarketCloseRefreshWorker.WORK_NAME, "장 마감 교체")

    fun runMarketCloseRefreshNow(context: Context) =
        runWorkerNow<MarketCloseRefreshWorker>(context, MarketCloseRefreshWorker.MANUAL_WORK_NAME, MarketCloseRefreshWorker.TAG, "장 마감 교체")

    // ===== 리포트(컨센서스) =====

    fun scheduleConsensusUpdate(context: Context, hour: Int = 3, minute: Int = 0) =
        scheduleDailyWorker<ConsensusUpdateWorker>(context, ConsensusUpdateWorker.WORK_NAME, ConsensusUpdateWorker.TAG, "리포트", hour, minute)

    fun cancelConsensusUpdate(context: Context) =
        cancelWorker(context, ConsensusUpdateWorker.WORK_NAME, "리포트")

    fun runConsensusUpdateNow(context: Context) =
        runWorkerNow<ConsensusUpdateWorker>(context, ConsensusUpdateWorker.MANUAL_WORK_NAME, ConsensusUpdateWorker.TAG, "리포트")

    // ===== Fear & Greed =====

    fun scheduleFearGreedUpdate(context: Context, hour: Int = 4, minute: Int = 0) =
        scheduleDailyWorker<FearGreedUpdateWorker>(context, FearGreedUpdateWorker.WORK_NAME, FearGreedUpdateWorker.TAG, "Fear & Greed", hour, minute)

    fun cancelFearGreedUpdate(context: Context) =
        cancelWorker(context, FearGreedUpdateWorker.WORK_NAME, "Fear & Greed")

    fun runFearGreedUpdateNow(context: Context) =
        runWorkerNow<FearGreedUpdateWorker>(context, FearGreedUpdateWorker.MANUAL_WORK_NAME, FearGreedUpdateWorker.TAG, "Fear & Greed")

    // ===== 시장 레짐 =====

    fun scheduleRegimeUpdate(context: Context, hour: Int = 5, minute: Int = 0) {
        require(hour in 0..23) { "hour must be 0-23, got $hour" }
        require(minute in 0..59) { "minute must be 0-59, got $minute" }

        // Weekly schedule (every 7 days instead of daily)
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            if (before(now)) add(java.util.Calendar.WEEK_OF_YEAR, 1)
        }

        val initialDelay = target.timeInMillis - now.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<RegimeUpdateWorker>(
            7, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(RegimeUpdateWorker.TAG)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                RegimeUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

        Timber.d("시장 레짐 주간 업데이트 스케줄 등록: 매주 일요일 %02d:%02d (초기 딜레이: %d분)", hour, minute, initialDelay / 60000)
    }

    fun cancelRegimeUpdate(context: Context) =
        cancelWorker(context, RegimeUpdateWorker.WORK_NAME, "시장 레짐")

    fun runRegimeUpdateNow(context: Context) =
        runWorkerNow<RegimeUpdateWorker>(context, RegimeUpdateWorker.MANUAL_WORK_NAME, RegimeUpdateWorker.TAG, "시장 레짐")

    // ===== 데이터 무결성 검사 =====

    fun runIntegrityCheckNow(context: Context) =
        runWorkerNow<DataIntegrityCheckWorker>(context, DataIntegrityCheckWorker.WORK_NAME, DataIntegrityCheckWorker.TAG, "데이터 무결성 검사")

}
