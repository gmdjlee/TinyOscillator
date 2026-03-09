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

}
