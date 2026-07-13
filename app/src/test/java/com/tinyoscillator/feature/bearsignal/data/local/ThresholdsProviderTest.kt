package com.tinyoscillator.feature.bearsignal.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ThresholdsProvider] JSON 디코딩 검증 (TASK_bear_signal_console.md §3.0 v1.2 임계치 외부화).
 *
 * 순수 디코딩 함수([ThresholdsProvider.decode])만 검증하므로 Context/에셋 I/O 없이 JVM에서
 * 실행된다. [fixtureJson]은 리포지토리 루트 `bear_thresholds.json`(= 앱 사본
 * `app/src/main/assets/bear_thresholds.json`)의 v1.2 내용과 완전히 동일하다 — 실 파일이 바뀌면
 * 이 fixture도 함께 갱신해야 한다.
 */
class ThresholdsProviderTest {

    // 리포지토리 루트 bear_thresholds.json v1.2 내용 그대로 (§3.0 SSOT, "note" 필드 포함).
    private val fixtureJson = """
        {
          "version": "1.2",
          "basis": "신영증권 「주도주의 물리학」 2026.6.30",
          "note": "임계치 단일 출처(SSOT). React(bear_signal_dashboard.jsx)와 Kotlin 엔진이 이 파일을 공유한다. 리포트 개정 시 이 파일만 교체하고 version을 올린다. 값 변경은 스코어링 의미론 변경이므로 근거(리포트 도표) 명시 후에만 수행.",

          "s1": {
            "manyCountries": 7,
            "deepPct": -12.0,
            "deepeningPct": -6.0
          },
          "s2": {
            "redLine": 1.0,
            "warnLine": 0.95,
            "watchLine": 0.7
          },
          "s3": {
            "loss1": 45.0,
            "loss2": 60.0,
            "loss3": 80.0
          },
          "gate": {
            "critical": 4.5,
            "approach": 4.0,
            "creditWarn": 35.0
          },
          "amp": {
            "semiExport": 20.0,
            "kospi2": 50.0,
            "wSemi": 0.15,
            "wKospi2": 0.15,
            "wNoBuffer": 0.20,
            "cap": 1.6
          },
          "phase": {
            "leadOrange": 6,
            "leadAmber": 3
          }
        }
    """.trimIndent()

    @Test
    fun `실 JSON과 동일한 fixture를 디코딩하면 모든 필드값이 일치한다`() {
        val result = ThresholdsProvider.decode(fixtureJson)

        assertEquals("1.2", result.version)
        assertEquals("신영증권 「주도주의 물리학」 2026.6.30", result.basis)

        assertEquals(7, result.s1.manyCountries)
        assertEquals(-12.0, result.s1.deepPct, 0.0)
        assertEquals(-6.0, result.s1.deepeningPct, 0.0)

        assertEquals(1.0, result.s2.redLine, 0.0)
        assertEquals(0.95, result.s2.warnLine, 0.0)
        assertEquals(0.7, result.s2.watchLine, 0.0)

        assertEquals(45.0, result.s3.loss1, 0.0)
        assertEquals(60.0, result.s3.loss2, 0.0)
        assertEquals(80.0, result.s3.loss3, 0.0)

        assertEquals(4.5, result.gate.critical, 0.0)
        assertEquals(4.0, result.gate.approach, 0.0)
        assertEquals(35.0, result.gate.creditWarn, 0.0)

        assertEquals(20.0, result.amp.semiExport, 0.0)
        assertEquals(50.0, result.amp.kospi2, 0.0)
        assertEquals(0.15, result.amp.wSemi, 0.0)
        assertEquals(0.15, result.amp.wKospi2, 0.0)
        assertEquals(0.20, result.amp.wNoBuffer, 0.0)
        assertEquals(1.6, result.amp.cap, 0.0)

        assertEquals(6, result.phase.leadOrange)
        assertEquals(3, result.phase.leadAmber)
    }

    @Test
    fun `note 등 스코어링과 무관한 미정의 필드는 ignoreUnknownKeys로 무시된다`() {
        // fixtureJson에 "note" 필드가 있음에도 예외 없이 디코딩된다는 사실 자체가 검증 대상이다.
        val result = ThresholdsProvider.decode(fixtureJson)
        assertEquals("1.2", result.version)
    }

    @Test
    fun `필수 필드가 누락되면 디코딩이 실패한다`() {
        val invalidJson = """{ "version": "1.2", "basis": "x" }"""
        assertThrowsSerializationFailure { ThresholdsProvider.decode(invalidJson) }
    }

    private fun assertThrowsSerializationFailure(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (e: Exception) {
            threw = true
        }
        assertEquals(true, threw)
    }
}
