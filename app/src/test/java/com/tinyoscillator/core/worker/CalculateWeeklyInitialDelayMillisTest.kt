package com.tinyoscillator.core.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.TimeZone

/**
 * [calculateWeeklyInitialDelayMillis] 순수 함수 테스트 (§6 Phase 5-1 — 주간(KST) 스케줄링 분리 검증).
 *
 * `Calendar.getInstance()`(현재 시각) 의존을 제거한 순수 함수이므로, 고정된 [nowMillis]를 주입해
 * 결정적으로 검증한다. 기준 시각은 2026-07-15(수) 12:00 KST — [ZonedDateTime]으로 실제 요일을
 * 재확인해 하드코딩 오류를 방지한다.
 */
class CalculateWeeklyInitialDelayMillisTest {

    private val seoul: TimeZone = TimeZone.getTimeZone("Asia/Seoul")
    private val seoulZoneId: ZoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun `수요일 12시에 월요일 06시를 예약하면 다음주 월요일까지 딜레이`() {
        val wednesdayNoon = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, seoulZoneId)
        assertEquals("전제 조건: 기준일이 수요일이어야 함", DayOfWeek.WEDNESDAY, wednesdayNoon.dayOfWeek)

        val delay = calculateWeeklyInitialDelayMillis(
            nowMillis = wednesdayNoon.toInstant().toEpochMilli(),
            zone = seoul,
            dayOfWeek = Calendar.MONDAY,
            hour = 6,
            minute = 0
        )

        val expectedMonday = ZonedDateTime.of(2026, 7, 20, 6, 0, 0, 0, seoulZoneId)
        assertEquals(DayOfWeek.MONDAY, expectedMonday.dayOfWeek)
        val expectedDelay = expectedMonday.toInstant().toEpochMilli() - wednesdayNoon.toInstant().toEpochMilli()

        assertEquals(expectedDelay, delay)
        // 114시간(4일 18시간) — 회귀 방지용 명시적 값
        assertEquals(114L * 60 * 60 * 1000, delay)
    }

    @Test
    fun `월요일 05시에 월요일 06시를 예약하면 당일까지 1시간 딜레이`() {
        val mondayEarly = ZonedDateTime.of(2026, 7, 13, 5, 0, 0, 0, seoulZoneId)
        assertEquals(DayOfWeek.MONDAY, mondayEarly.dayOfWeek)

        val delay = calculateWeeklyInitialDelayMillis(
            nowMillis = mondayEarly.toInstant().toEpochMilli(),
            zone = seoul,
            dayOfWeek = Calendar.MONDAY,
            hour = 6,
            minute = 0
        )

        assertEquals(60L * 60 * 1000, delay)
    }

    @Test
    fun `월요일 07시에 월요일 06시를 예약하면 다음주까지 딜레이(이미 지남)`() {
        val mondayLate = ZonedDateTime.of(2026, 7, 13, 7, 0, 0, 0, seoulZoneId)
        assertEquals(DayOfWeek.MONDAY, mondayLate.dayOfWeek)

        val delay = calculateWeeklyInitialDelayMillis(
            nowMillis = mondayLate.toInstant().toEpochMilli(),
            zone = seoul,
            dayOfWeek = Calendar.MONDAY,
            hour = 6,
            minute = 0
        )

        val expectedNextMonday = ZonedDateTime.of(2026, 7, 20, 6, 0, 0, 0, seoulZoneId)
        val expectedDelay = expectedNextMonday.toInstant().toEpochMilli() - mondayLate.toInstant().toEpochMilli()
        assertEquals(expectedDelay, delay)
        assertTrue("6일 넘게(거의 7일) 남아야 함", delay > 6L * 24 * 60 * 60 * 1000)
    }

    @Test
    fun `일요일 저녁에 일요일 새벽 예약은 이미 지나 다음주로 이월`() {
        val sundayEvening = ZonedDateTime.of(2026, 7, 12, 20, 0, 0, 0, seoulZoneId)
        assertEquals(DayOfWeek.SUNDAY, sundayEvening.dayOfWeek)

        val delay = calculateWeeklyInitialDelayMillis(
            nowMillis = sundayEvening.toInstant().toEpochMilli(),
            zone = seoul,
            dayOfWeek = Calendar.SUNDAY,
            hour = 5,
            minute = 30
        )

        val expectedNextSunday = ZonedDateTime.of(2026, 7, 19, 5, 30, 0, 0, seoulZoneId)
        val expectedDelay = expectedNextSunday.toInstant().toEpochMilli() - sundayEvening.toInstant().toEpochMilli()
        assertEquals(expectedDelay, delay)
    }
}
