package com.tinyoscillator.core.worker

import android.content.Context
import androidx.work.*
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerHelper {

    fun scheduleEtfUpdate(context: Context, hour: Int = 0, minute: Int = 30) {
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

        val request = PeriodicWorkRequestBuilder<EtfUpdateWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                EtfUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

        Timber.d("ETF 일일 업데이트 스케줄 등록: %02d:%02d (초기 딜레이: %d분)", hour, minute, initialDelay / 60000)
    }

    fun cancelEtfUpdate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(EtfUpdateWorker.WORK_NAME)
        Timber.d("ETF 일일 업데이트 스케줄 취소")
    }

    fun runEtfUpdateNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<EtfUpdateWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Timber.d("ETF 즉시 업데이트 요청")
    }

    // ===== 시장지표(과매수/과매도) 스케줄링 =====

    fun scheduleOscillatorUpdate(context: Context, hour: Int = 1, minute: Int = 0) {
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

        val request = PeriodicWorkRequestBuilder<MarketOscillatorUpdateWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                MarketOscillatorUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

        Timber.d("시장지표 일일 업데이트 스케줄 등록: %02d:%02d (초기 딜레이: %d분)", hour, minute, initialDelay / 60000)
    }

    fun cancelOscillatorUpdate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MarketOscillatorUpdateWorker.WORK_NAME)
        Timber.d("시장지표 일일 업데이트 스케줄 취소")
    }

    fun runOscillatorUpdateNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<MarketOscillatorUpdateWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Timber.d("시장지표 즉시 업데이트 요청")
    }

    // ===== 자금 동향(deposit) 스케줄링 =====

    fun scheduleDepositUpdate(context: Context, hour: Int = 2, minute: Int = 0) {
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

        val request = PeriodicWorkRequestBuilder<MarketDepositUpdateWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                MarketDepositUpdateWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

        Timber.d("자금 동향 일일 업데이트 스케줄 등록: %02d:%02d (초기 딜레이: %d분)", hour, minute, initialDelay / 60000)
    }

    fun cancelDepositUpdate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MarketDepositUpdateWorker.WORK_NAME)
        Timber.d("자금 동향 일일 업데이트 스케줄 취소")
    }

    fun runDepositUpdateNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<MarketDepositUpdateWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Timber.d("자금 동향 즉시 업데이트 요청")
    }

}
