package com.tinyoscillator.core.worker

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [BearSignalDailyUpdateWorker] 스케줄링 계약 테스트 (TASK.md §2 111행, §4, §6 Phase 5-1 신설).
 *
 * [BearSignalUpdateWorkerTest]와 동일한 사유(`BaseCollectionWorker.setForeground()`가 실제
 * WorkManager/Android 런타임을 요구)로 companion 상수·알림 ID 유일성·
 * [WorkManagerHelper.scheduleBearSignalDailyUpdate] 입력 검증(`require`, WorkManager 호출 전에
 * 즉시 실패)만 커버한다.
 */
class BearSignalDailyUpdateWorkerTest {

    @Test
    fun `worker companion constants are correct`() {
        assertEquals("bear_signal_daily_update", BearSignalDailyUpdateWorker.WORK_NAME)
        assertEquals("bear_signal_daily_manual_update", BearSignalDailyUpdateWorker.MANUAL_WORK_NAME)
        assertEquals("collection_bear_signal_daily", BearSignalDailyUpdateWorker.TAG)
        assertEquals("BearSignal 지표(일간)", BearSignalDailyUpdateWorker.LABEL)
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
            CollectionNotificationHelper.BEAR_SIGNAL_NOTIFICATION_ID,
            CollectionNotificationHelper.BEAR_SIGNAL_DAILY_NOTIFICATION_ID
        )
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(1017, CollectionNotificationHelper.BEAR_SIGNAL_DAILY_NOTIFICATION_ID)
    }

    // ── WorkManagerHelper.scheduleBearSignalDailyUpdate 입력 검증(§4 "일" 주기) ──

    private val dummyContext: Context = mockk(relaxed = true)

    @Test
    fun `hour 24는 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalDailyUpdate(dummyContext, hour = 24)
        }
    }

    @Test
    fun `hour -1은 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalDailyUpdate(dummyContext, hour = -1)
        }
    }

    @Test
    fun `minute 60은 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalDailyUpdate(dummyContext, minute = 60)
        }
    }

    @Test
    fun `minute -1은 IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkManagerHelper.scheduleBearSignalDailyUpdate(dummyContext, minute = -1)
        }
    }
}
