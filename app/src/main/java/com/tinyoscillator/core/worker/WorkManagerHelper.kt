package com.tinyoscillator.core.worker

import android.content.Context
import androidx.work.*
import timber.log.Timber
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * [WorkManagerHelper]의 주간(KST) 스케줄링에서 "다음 실행 시각까지 초기 딜레이"만 순수 함수로
 * 분리한 것 — `Calendar.getInstance()`가 참조하는 "현재 시각" 부작용을 격리해 JVM 테스트로
 * 결정적 검증이 가능하도록 한다(§6 Phase 5-1, "주간 initialDelay 계산" 분리 요구).
 *
 * Calendar의 `DAY_OF_WEEK` 세터는 [nowMillis]가 속한 주(일요일 시작) 안에서 [dayOfWeek]에 해당하는
 * 날짜로 이동한다(과거로 이동할 수도 있음). 그 결과가 [nowMillis] 이전이면 한 주를 더한다 —
 * [WorkManagerHelper]의 기존 `scheduleRegimeUpdate`/`scheduleMacroUpdate` 등과 동일한 계산 규약.
 * `Locale.US`(일요일 시작 주)를 명시 고정해, 기기 로케일이 월요일 시작 주(예: 유럽권)로 설정돼도
 * "다음 [dayOfWeek]"의 의미가 흔들리지 않도록 한다 — 시간대(Asia/Seoul)를 고정한 것과 같은 취지.
 *
 * @return 목표 시각(다음 [dayOfWeek] [hour]:[minute], [zone] 기준)까지의 밀리초 델타(항상 ≥ 0)
 */
internal fun calculateWeeklyInitialDelayMillis(
    nowMillis: Long,
    zone: TimeZone,
    dayOfWeek: Int,
    hour: Int,
    minute: Int
): Long {
    val now = Calendar.getInstance(zone, Locale.US).apply { timeInMillis = nowMillis }
    val target = Calendar.getInstance(zone, Locale.US).apply {
        timeInMillis = nowMillis
        set(Calendar.DAY_OF_WEEK, dayOfWeek)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
    }
    return target.timeInMillis - now.timeInMillis
}

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

    /**
     * 주간 Worker 스케줄 등록(KST 고정). §6 Phase 5-1에서 BearSignal 워커를 월간→주간으로 전환하며
     * 신설(과거 `scheduleMonthlyWorker`는 호출자가 0이 되어 제거 — git 이력 참조).
     *
     * flex interval은 의도적으로 쓰지 않는다: flex가 있으면 첫 실행 시각이
     * `initialDelay + (interval − flex)`로 계산되어(WorkSpec.calculateNextRunTime — flex는 각 인터벌의
     * "끝" 구간에서 실행됨) 목표일보다 한 주기 가까이 늦게 시작된다. flex 없이는 첫 실행이 정확히
     * initialDelay 시점(다음 dayOfWeek hour:minute)이다. (기존 `scheduleMonthlyWorker`의 동일 사유를
     * 그대로 계승 — 30일→7일 간격만 다름.)
     *
     * 타임존은 기기 로컬이 아닌 **Asia/Seoul(KST)로 고정**한다: 이 스케줄이 갱신하는 지표(관세청
     * 무역통계·한국은행 기준금리·FRED/ECOS·KOFIA 신용잔고 등)는 전부 한국 시각 기준 발표 주기에
     * 앵커링돼 있어, 해외 로밍 등으로 기기 TZ가 바뀌어도 발표 주기와 어긋나지 않아야 한다(2026-07-13
     * QA MINOR "monthly 스케줄 TZ 미고정" 해소). 기존 [scheduleDailyWorker]류는 사용자 개인 루틴에
     * 맞춘 시각이라 기기 TZ 관례를 유지한다 — 앱 전역 정책 변경이 아니라 본 헬퍼에 한정된 결정이다.
     */
    private inline fun <reified W : ListenableWorker> scheduleWeeklyWorker(
        context: Context,
        workName: String,
        tag: String,
        label: String,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        forceUpdate: Boolean = false
    ) {
        require(dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY) {
            "dayOfWeek must be Calendar.SUNDAY(1)-Calendar.SATURDAY(7), got $dayOfWeek"
        }
        require(hour in 0..23) { "hour must be 0-23, got $hour" }
        require(minute in 0..59) { "minute must be 0-59, got $minute" }

        val seoul = TimeZone.getTimeZone("Asia/Seoul")
        val initialDelay = calculateWeeklyInitialDelayMillis(
            nowMillis = System.currentTimeMillis(), zone = seoul, dayOfWeek = dayOfWeek, hour = hour, minute = minute
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<W>(7, TimeUnit.DAYS)
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

        Timber.d(
            "$label 주간(KST) 업데이트 스케줄 등록: dayOfWeek=%d %02d:%02d (초기 딜레이: %d분, policy=%s)",
            dayOfWeek, hour, minute, initialDelay / 60000, policy
        )
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

    // ===== 확률분석 배치 (포트폴리오 종목) =====

    fun scheduleProbabilityBatch(context: Context, hour: Int = 5, minute: Int = 0, forceUpdate: Boolean = false) =
        scheduleDailyWorker<ProbabilityBatchWorker>(context, ProbabilityBatchWorker.WORK_NAME, ProbabilityBatchWorker.TAG, "확률분석 배치", hour, minute, forceUpdate)

    fun cancelProbabilityBatch(context: Context) =
        cancelWorker(context, ProbabilityBatchWorker.WORK_NAME, "확률분석 배치")

    fun runProbabilityBatchNow(context: Context) =
        runWorkerNow<ProbabilityBatchWorker>(context, ProbabilityBatchWorker.MANUAL_WORK_NAME, ProbabilityBatchWorker.TAG, "확률분석 배치")

    // ===== 테마 (Kiwoom ka90001/ka90002) =====

    fun scheduleThemeUpdate(context: Context, hour: Int = 2, minute: Int = 30, forceUpdate: Boolean = false) =
        scheduleDailyWorker<ThemeUpdateWorker>(context, ThemeUpdateWorker.WORK_NAME, ThemeUpdateWorker.TAG, "테마", hour, minute, forceUpdate)

    fun cancelThemeUpdate(context: Context) =
        cancelWorker(context, ThemeUpdateWorker.WORK_NAME, "테마")

    fun runThemeUpdateNow(context: Context) =
        runWorkerNow<ThemeUpdateWorker>(context, ThemeUpdateWorker.MANUAL_WORK_NAME, ThemeUpdateWorker.TAG, "테마")

    // ===== BearSignal(주도주 붕괴 판단 계기판) 지표 =====

    /**
     * [B] 등급 자동 지표(관세청·FRED·ECOS·IPO ETF) + 국가별 지수 수익률 주간 업데이트(§6 Phase 5-1).
     * 기본값: 매주 월요일([dayOfWeek] = [Calendar.MONDAY]) 06:00 KST — 주말 미국 마감 데이터 반영 후
     * 새 주의 첫 영업일 아침에 갱신한다. [dayOfWeek]는 `Calendar.SUNDAY`(1)~`Calendar.SATURDAY`(7).
     */
    fun scheduleBearSignalUpdate(
        context: Context,
        dayOfWeek: Int = Calendar.MONDAY,
        hour: Int = 6,
        minute: Int = 0,
        forceUpdate: Boolean = false
    ) = scheduleWeeklyWorker<BearSignalUpdateWorker>(
        context, BearSignalUpdateWorker.WORK_NAME, BearSignalUpdateWorker.TAG, "BearSignal 지표(주간)",
        dayOfWeek, hour, minute, forceUpdate
    )

    fun cancelBearSignalUpdate(context: Context) =
        cancelWorker(context, BearSignalUpdateWorker.WORK_NAME, "BearSignal 지표(주간)")

    fun runBearSignalUpdateNow(context: Context) =
        runWorkerNow<BearSignalUpdateWorker>(context, BearSignalUpdateWorker.MANUAL_WORK_NAME, BearSignalUpdateWorker.TAG, "BearSignal 지표(주간)")

    /**
     * [A] 등급 자동 지표(신호2 통계·코스피 2사 비중, `kotlin_krx` 일별 시세 기반) 일간 업데이트
     * (§6 Phase 5-1 신설). 기본값: 매일 06:30 — 06:00 [scheduleFeatureCacheEviction]과 겹치지 않도록
     * 분산 배치.
     */
    fun scheduleBearSignalDailyUpdate(context: Context, hour: Int = 6, minute: Int = 30, forceUpdate: Boolean = false) =
        scheduleDailyWorker<BearSignalDailyUpdateWorker>(context, BearSignalDailyUpdateWorker.WORK_NAME, BearSignalDailyUpdateWorker.TAG, "BearSignal 지표(일간)", hour, minute, forceUpdate)

    fun cancelBearSignalDailyUpdate(context: Context) =
        cancelWorker(context, BearSignalDailyUpdateWorker.WORK_NAME, "BearSignal 지표(일간)")

    fun runBearSignalDailyUpdateNow(context: Context) =
        runWorkerNow<BearSignalDailyUpdateWorker>(context, BearSignalDailyUpdateWorker.MANUAL_WORK_NAME, BearSignalDailyUpdateWorker.TAG, "BearSignal 지표(일간)")

}
