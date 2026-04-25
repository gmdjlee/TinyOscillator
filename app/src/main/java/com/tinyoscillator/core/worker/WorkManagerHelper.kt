package com.tinyoscillator.core.worker

import android.content.Context
import androidx.work.*
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

object WorkManagerHelper {

    // ===== Generic scheduling helpers =====

    /**
     * 일일 Worker 스케줄 등록.
     * @param forceUpdate true이면 기존 스케줄을 교체 (사용자가 시간 변경 시),
     *                    false이면 기존 스케줄이 있으면 유지 (앱 재시작 시)
     */
    private inline fun <reified W : ListenableWorker> scheduleDailyWorker(
        context: Context,
        workName: String,
        tag: String,
        label: String,
        hour: Int,
        minute: Int,
        forceUpdate: Boolean = false
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
            24, TimeUnit.HOURS,
            15, TimeUnit.MINUTES  // flex interval: 설정 시각 전후 15분 이내 실행
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(tag)
            .build()

        val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                workName,
                policy,
                request
            )

        Timber.d("$label 일일 업데이트 스케줄 등록: %02d:%02d (초기 딜레이: %d분, policy=%s)", hour, minute, initialDelay / 60000, policy)
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

    fun scheduleEtfUpdate(context: Context, hour: Int = 0, minute: Int = 30, forceUpdate: Boolean = false) =
        scheduleDailyWorker<EtfUpdateWorker>(context, EtfUpdateWorker.WORK_NAME, EtfUpdateWorker.TAG, "ETF", hour, minute, forceUpdate)

    fun cancelEtfUpdate(context: Context) =
        cancelWorker(context, EtfUpdateWorker.WORK_NAME, "ETF")

    fun runEtfUpdateNow(context: Context) =
        runWorkerNow<EtfUpdateWorker>(context, EtfUpdateWorker.MANUAL_WORK_NAME, EtfUpdateWorker.TAG, "ETF")

    // ===== 시장지표(과매수/과매도) =====

    fun scheduleOscillatorUpdate(context: Context, hour: Int = 1, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<MarketOscillatorUpdateWorker>(context, MarketOscillatorUpdateWorker.WORK_NAME, MarketOscillatorUpdateWorker.TAG, "시장지표", hour, minute, forceUpdate)

    fun cancelOscillatorUpdate(context: Context) =
        cancelWorker(context, MarketOscillatorUpdateWorker.WORK_NAME, "시장지표")

    fun runOscillatorUpdateNow(context: Context) =
        runWorkerNow<MarketOscillatorUpdateWorker>(context, MarketOscillatorUpdateWorker.MANUAL_WORK_NAME, MarketOscillatorUpdateWorker.TAG, "시장지표")

    // ===== 자금 동향(deposit) =====

    fun scheduleDepositUpdate(context: Context, hour: Int = 2, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<MarketDepositUpdateWorker>(context, MarketDepositUpdateWorker.WORK_NAME, MarketDepositUpdateWorker.TAG, "자금 동향", hour, minute, forceUpdate)

    fun cancelDepositUpdate(context: Context) =
        cancelWorker(context, MarketDepositUpdateWorker.WORK_NAME, "자금 동향")

    fun runDepositUpdateNow(context: Context) =
        runWorkerNow<MarketDepositUpdateWorker>(context, MarketDepositUpdateWorker.MANUAL_WORK_NAME, MarketDepositUpdateWorker.TAG, "자금 동향")

    // ===== 장 마감 데이터 교체 =====

    fun scheduleMarketCloseRefresh(context: Context, hour: Int = 19, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<MarketCloseRefreshWorker>(context, MarketCloseRefreshWorker.WORK_NAME, MarketCloseRefreshWorker.TAG, "장 마감 교체", hour, minute, forceUpdate)

    fun cancelMarketCloseRefresh(context: Context) =
        cancelWorker(context, MarketCloseRefreshWorker.WORK_NAME, "장 마감 교체")

    fun runMarketCloseRefreshNow(context: Context) =
        runWorkerNow<MarketCloseRefreshWorker>(context, MarketCloseRefreshWorker.MANUAL_WORK_NAME, MarketCloseRefreshWorker.TAG, "장 마감 교체")

    // ===== 리포트(컨센서스) =====

    fun scheduleConsensusUpdate(context: Context, hour: Int = 3, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<ConsensusUpdateWorker>(context, ConsensusUpdateWorker.WORK_NAME, ConsensusUpdateWorker.TAG, "리포트", hour, minute, forceUpdate)

    fun cancelConsensusUpdate(context: Context) =
        cancelWorker(context, ConsensusUpdateWorker.WORK_NAME, "리포트")

    fun runConsensusUpdateNow(context: Context) =
        runWorkerNow<ConsensusUpdateWorker>(context, ConsensusUpdateWorker.MANUAL_WORK_NAME, ConsensusUpdateWorker.TAG, "리포트")

    // ===== Fear & Greed =====

    fun scheduleFearGreedUpdate(context: Context, hour: Int = 4, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<FearGreedUpdateWorker>(context, FearGreedUpdateWorker.WORK_NAME, FearGreedUpdateWorker.TAG, "Fear & Greed", hour, minute, forceUpdate)

    fun cancelFearGreedUpdate(context: Context) =
        cancelWorker(context, FearGreedUpdateWorker.WORK_NAME, "Fear & Greed")

    fun runFearGreedUpdateNow(context: Context) =
        runWorkerNow<FearGreedUpdateWorker>(context, FearGreedUpdateWorker.MANUAL_WORK_NAME, FearGreedUpdateWorker.TAG, "Fear & Greed")

    // ===== 시장 레짐 =====

    fun scheduleRegimeUpdate(context: Context, hour: Int = 5, minute: Int = 0, forceUpdate: Boolean = false) {
        require(hour in 0..23) { "hour must be 0-23, got $hour" }
        require(minute in 0..59) { "minute must be 0-59, got $minute" }

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
            7, TimeUnit.DAYS,
            1, TimeUnit.HOURS  // flex interval: 설정 시각 전후 1시간 이내 실행
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(RegimeUpdateWorker.TAG)
            .build()

        val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                RegimeUpdateWorker.WORK_NAME,
                policy,
                request
            )

        Timber.d("시장 레짐 주간 업데이트 스케줄 등록: 매주 일요일 %02d:%02d (초기 딜레이: %d분, policy=%s)", hour, minute, initialDelay / 60000, policy)
    }

    fun cancelRegimeUpdate(context: Context) =
        cancelWorker(context, RegimeUpdateWorker.WORK_NAME, "시장 레짐")

    fun runRegimeUpdateNow(context: Context) =
        runWorkerNow<RegimeUpdateWorker>(context, RegimeUpdateWorker.MANUAL_WORK_NAME, RegimeUpdateWorker.TAG, "시장 레짐")

    // ===== Feature 캐시 정리 =====

    fun scheduleFeatureCacheEviction(context: Context, hour: Int = 6, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<FeatureCacheEvictionWorker>(context, FeatureCacheEvictionWorker.WORK_NAME, FeatureCacheEvictionWorker.TAG, "Feature 캐시 정리", hour, minute, forceUpdate)

    fun cancelFeatureCacheEviction(context: Context) =
        cancelWorker(context, FeatureCacheEvictionWorker.WORK_NAME, "Feature 캐시 정리")

    fun runFeatureCacheEvictionNow(context: Context) =
        runWorkerNow<FeatureCacheEvictionWorker>(context, FeatureCacheEvictionWorker.MANUAL_WORK_NAME, FeatureCacheEvictionWorker.TAG, "Feature 캐시 정리")

    // ===== 매크로 지표 =====

    fun scheduleMacroUpdate(context: Context, hour: Int = 5, minute: Int = 30, forceUpdate: Boolean = false) {
        require(hour in 0..23) { "hour must be 0-23, got $hour" }
        require(minute in 0..59) { "minute must be 0-59, got $minute" }

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

        val request = PeriodicWorkRequestBuilder<MacroUpdateWorker>(
            7, TimeUnit.DAYS,
            1, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(MacroUpdateWorker.TAG)
            .build()

        val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                MacroUpdateWorker.WORK_NAME,
                policy,
                request
            )

        Timber.d("매크로 지표 주간 업데이트 스케줄 등록: 매주 일요일 %02d:%02d (초기 딜레이: %d분, policy=%s)", hour, minute, initialDelay / 60000, policy)
    }

    fun cancelMacroUpdate(context: Context) =
        cancelWorker(context, MacroUpdateWorker.WORK_NAME, "매크로 지표")

    fun runMacroUpdateNow(context: Context) =
        runWorkerNow<MacroUpdateWorker>(context, MacroUpdateWorker.MANUAL_WORK_NAME, MacroUpdateWorker.TAG, "매크로 지표")

    // ===== 메타 학습기 재학습 =====

    fun scheduleMetaLearnerRefit(context: Context, hour: Int = 6, minute: Int = 30, forceUpdate: Boolean = false) {
        require(hour in 0..23) { "hour must be 0-23, got $hour" }
        require(minute in 0..59) { "minute must be 0-59, got $minute" }

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
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<MetaLearnerRefitWorker>(
            7, TimeUnit.DAYS,
            1, TimeUnit.HOURS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(MetaLearnerRefitWorker.TAG)
            .build()

        val policy = if (forceUpdate) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                MetaLearnerRefitWorker.WORK_NAME,
                policy,
                request
            )

        Timber.d("메타 학습기 주간 재학습 스케줄 등록: 매주 일요일 %02d:%02d (초기 딜레이: %d분, policy=%s)", hour, minute, initialDelay / 60000, policy)
    }

    fun cancelMetaLearnerRefit(context: Context) =
        cancelWorker(context, MetaLearnerRefitWorker.WORK_NAME, "메타 학습기")

    fun runMetaLearnerRefitNow(context: Context) =
        runWorkerNow<MetaLearnerRefitWorker>(context, MetaLearnerRefitWorker.MANUAL_WORK_NAME, MetaLearnerRefitWorker.TAG, "메타 학습기")

    // ===== 점진적 모델 업데이트 =====

    fun scheduleIncrementalModelUpdate(context: Context, hour: Int = 19, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<IncrementalModelUpdateWorker>(context, IncrementalModelUpdateWorker.WORK_NAME, IncrementalModelUpdateWorker.TAG, "점진적 모델", hour, minute, forceUpdate)

    fun cancelIncrementalModelUpdate(context: Context) =
        cancelWorker(context, IncrementalModelUpdateWorker.WORK_NAME, "점진적 모델")

    fun runIncrementalModelUpdateNow(context: Context) =
        runWorkerNow<IncrementalModelUpdateWorker>(context, IncrementalModelUpdateWorker.MANUAL_WORK_NAME, IncrementalModelUpdateWorker.TAG, "점진적 모델")

    // ===== 신호 결과 수집 =====

    fun scheduleSignalOutcomeUpdate(context: Context, hour: Int = 18, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<SignalOutcomeUpdateWorker>(context, SignalOutcomeUpdateWorker.WORK_NAME, SignalOutcomeUpdateWorker.TAG, "신호 결과", hour, minute, forceUpdate)

    fun cancelSignalOutcomeUpdate(context: Context) =
        cancelWorker(context, SignalOutcomeUpdateWorker.WORK_NAME, "신호 결과")

    fun runSignalOutcomeUpdateNow(context: Context) =
        runWorkerNow<SignalOutcomeUpdateWorker>(context, SignalOutcomeUpdateWorker.MANUAL_WORK_NAME, SignalOutcomeUpdateWorker.TAG, "신호 결과")

    // ===== 데이터 무결성 검사 =====

    fun runIntegrityCheckNow(context: Context) =
        runWorkerNow<DataIntegrityCheckWorker>(context, DataIntegrityCheckWorker.WORK_NAME, DataIntegrityCheckWorker.TAG, "데이터 무결성 검사")

    // ===== 테마 (Kiwoom ka90001/ka90002) =====

    fun scheduleThemeUpdate(context: Context, hour: Int = 2, minute: Int = 30, forceUpdate: Boolean = false) =
        scheduleDailyWorker<ThemeUpdateWorker>(context, ThemeUpdateWorker.WORK_NAME, ThemeUpdateWorker.TAG, "테마", hour, minute, forceUpdate)

    fun cancelThemeUpdate(context: Context) =
        cancelWorker(context, ThemeUpdateWorker.WORK_NAME, "테마")

    fun runThemeUpdateNow(context: Context) =
        runWorkerNow<ThemeUpdateWorker>(context, ThemeUpdateWorker.MANUAL_WORK_NAME, ThemeUpdateWorker.TAG, "테마")

}
