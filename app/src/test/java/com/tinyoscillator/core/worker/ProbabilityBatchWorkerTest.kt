package com.tinyoscillator.core.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.domain.usecase.ProbabilityAnalysisUseCase
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ProbabilityBatchWorker] 임계 돌파 알림 로직 검증 (P8-2).
 *
 * 사용자 대면 알림을 만드는 `buildAlertLine`은 private + 순수(인스턴스 상태 미사용)이며,
 * 워커 본체는 [BaseCollectionWorker.setForeground] 때문에 순수 JVM에서 실행할 수 없다.
 * 따라서 work-testing으로 워커 인스턴스만 실제 생성한 뒤 리플렉션으로 알림 분기만 커버한다.
 * (프로덕션 코드 무변경 — P8의 유일한 프로덕션 변경은 ProbabilityAnalysisUseCase JSON 직렬화)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = Application::class)
class ProbabilityBatchWorkerTest {

    private lateinit var worker: ProbabilityBatchWorker

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val useCase = mockk<ProbabilityAnalysisUseCase>(relaxed = true)
        val portfolioDao = mockk<PortfolioDao>(relaxed = true)
        val snapshotDao = mockk<AnalysisSnapshotDao>(relaxed = true)

        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker =
                ProbabilityBatchWorker(appContext, workerParameters, useCase, portfolioDao, snapshotDao)
        }

        worker = TestListenableWorkerBuilder<ProbabilityBatchWorker>(context)
            .setWorkerFactory(factory)
            .build()
    }

    /** private `buildAlertLine(name, previousScore: Double?, currentScore: Double): String?` 호출 */
    private fun alertLine(previous: Double?, current: Double, name: String = "삼성전자"): String? {
        val method = ProbabilityBatchWorker::class.java.getDeclaredMethod(
            "buildAlertLine",
            String::class.java,
            java.lang.Double::class.java,      // Double? (nullable → boxed)
            java.lang.Double.TYPE              // Double (primitive)
        )
        method.isAccessible = true
        return method.invoke(worker, name, previous, current) as String?
    }

    // ── 첫 분석(이전 스냅샷 없음)은 알리지 않음 ──

    @Test
    fun `이전 점수 null이면 알림 없음 — 첫 배치 대량 알림 방지`() {
        assertNull(alertLine(previous = null, current = 0.9))
    }

    // ── 매수 임계(0_65) 상향 돌파 ──

    @Test
    fun `상승 신호 진입 — 0_65 상향 돌파`() {
        val line = alertLine(previous = 0.60, current = 0.66)
        assertTrue("실제: $line", line != null && line.contains("상승 신호 진입"))
    }

    @Test
    fun `이미 임계 위면 상승 신호 진입 아님`() {
        // prev 0.70, cur 0.72 — 둘 다 0.65 초과 + delta 0.02 < 0.15 → 무알림
        assertNull(alertLine(previous = 0.70, current = 0.72))
    }

    // ── 매도 임계(0_35) 하향 돌파 ──

    @Test
    fun `하락 신호 진입 — 0_35 하향 돌파`() {
        val line = alertLine(previous = 0.40, current = 0.34)
        assertTrue("실제: $line", line != null && line.contains("하락 신호 진입"))
    }

    // ── 급변(|delta| >= 0_15) — 임계 미돌파 ──

    @Test
    fun `점수 급변 — 임계 미돌파이나 delta 0_15 이상`() {
        // prev 0.40 → cur 0.58: 매수 미돌파(0.58<0.65)·매도 무관, delta 0.18 ≥ 0.15
        val line = alertLine(previous = 0.40, current = 0.58)
        assertTrue("실제: $line", line != null && line.contains("점수 급변"))
    }

    @Test
    fun `미세 변화는 무알림`() {
        assertNull(alertLine(previous = 0.50, current = 0.55))
    }

    // ── 알림 문구에 이전·현재 퍼센트 포함 ──

    @Test
    fun `알림 문구에 이전·현재 퍼센트 포함`() {
        val line = alertLine(previous = 0.60, current = 0.66, name = "카카오")!!
        assertTrue(line, line.contains("카카오"))
        assertTrue(line, line.contains("60%"))
        assertTrue(line, line.contains("66%"))
    }

    // ── companion 상수 계약 ──

    @Test
    fun `companion 상수 계약`() {
        assertEquals("probability_batch_daily", ProbabilityBatchWorker.WORK_NAME)
        assertEquals("probability_batch_manual", ProbabilityBatchWorker.MANUAL_WORK_NAME)
        assertEquals("collection_probability_batch", ProbabilityBatchWorker.TAG)
        assertEquals("확률분석 배치", ProbabilityBatchWorker.LABEL)
    }
}
