package com.tinyoscillator.core.worker

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [BearSignalUpdateWorker] 스케줄링 계약 테스트 (TASK.md §5.4, §6 Phase 5).
 *
 * `CoroutineWorker.doWork()`는 `BaseCollectionWorker.setForeground()`가 실제 WorkManager/Android
 * 런타임을 요구하므로(다른 [BaseCollectionWorker] 하위 워커들 — MacroUpdateWorker/ThemeUpdateWorker
 * 등 — 도 동일한 이유로 JVM `doWork()` 단위테스트가 없다), 이 테스트는 기존 관례(예:
 * [MarketCloseRefreshWorkerTest] "notification ID is unique")를 따라 companion 상수·알림 ID
 * 유일성·[WorkManagerHelper.scheduleBearSignalUpdate] 입력 검증(`require`, WorkManager 호출 전에
 * 즉시 실패)만 커버한다.
 */
class BearSignalUpdateWorkerTest {

    @Test
    fun `worker companion constants are correct`() {
        assertEquals("bear_signal_monthly_update", BearSignalUpdateWorker.WORK_NAME)
        assertEquals("bear_signal_manual_update", BearSignalUpdateWorker.MANUAL_WORK_NAME)
        assertEquals("collection_bear_signal", BearSignalUpdateWorker.TAG)
        assertEquals("BearSignal 지표", BearSignalUpdateWorker.LABEL)
    }

    @Test
    fun `notification ID is unique among all workers`() {
        val ids = listOf(
            CollectionNotificationHelper.ETF_NOTIFICATION_ID,
            CollectionNotificationHelper.OSCILLATOR_NOTIFICATION_ID,
            CollectionNotificationHelper.DEPOSIT_NOTIFICATION_ID,
            CollectionNotificationHelper.INTEGRITY_CHECK_NOTIFICATION_ID,
            CollectionNotificationHelper.MARKET_CLOSE_REFRESH_NOTIFICATION_ID,
            CollectionNotificationHelper.CONSENSUS_NOTIFICATION_ID,
            CollectionNotificationHelper.FEAR_GREED_NOTIFICATION_ID,
            CollectionNotificationHelper.REGIME_NOTIFICATION_ID,
            CollectionNotificationHelper.META_LEARNER_NOTIFICATION_ID,
            CollectionNotificationHelper.INCREMENTAL_MODEL_NOTIFICATION_ID,
            CollectionNotificationHelper.SIGNAL_OUTCOME_NOTIFICATION_ID,
            CollectionNotificationHelper.THEME_NOTIFICATION_ID,
            CollectionNotificationHelper.PROBABILITY_BATCH_NOTIFICATION_ID,
            CollectionNotificationHelper.SIGNAL_ALERT_NOTIFICATION_ID,
            CollectionNotificationHelper.BEAR_SIGNAL_NOTIFICATION_ID
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(1016, CollectionNotificationHelper.BEAR_SIGNAL_NOTIFICATION_ID)
    }

    // ── WorkManagerHelper.scheduleBearSignalUpdate 입력 검증(§6 Phase 5 "월 1회 주기") ──

    private val dummyContext: Context = mockk(relaxed = true)

    @Test
    fun `dayOfMonth 0은 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalUpdate(dummyContext, dayOfMonth = 0)
        }
    }

    @Test
    fun `dayOfMonth 29는 IllegalArgumentException(월별 일수 차이 회피)`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalUpdate(dummyContext, dayOfMonth = 29)
        }
    }

    @Test
    fun `hour 24는 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalUpdate(dummyContext, hour = 24)
        }
    }

    @Test
    fun `minute 60은 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalUpdate(dummyContext, minute = 60)
        }
    }
}
