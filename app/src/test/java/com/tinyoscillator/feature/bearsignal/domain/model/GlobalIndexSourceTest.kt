package com.tinyoscillator.feature.bearsignal.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** [GlobalIndexSource] 설정 저장/복원 규약 검증 — 미지 값은 기본 소스(Yahoo)로 폴백. */
class GlobalIndexSourceTest {

    @Test
    fun `기본 소스는 YAHOO`() {
        assertEquals(GlobalIndexSource.YAHOO, GlobalIndexSource.DEFAULT)
    }

    @Test
    fun `fromName 저장된 이름을 복원한다`() {
        assertEquals(GlobalIndexSource.STOOQ, GlobalIndexSource.fromName("STOOQ"))
        assertEquals(GlobalIndexSource.YAHOO, GlobalIndexSource.fromName("YAHOO"))
    }

    @Test
    fun `fromName null·미지 값은 기본 소스로 폴백`() {
        assertEquals(GlobalIndexSource.DEFAULT, GlobalIndexSource.fromName(null))
        assertEquals(GlobalIndexSource.DEFAULT, GlobalIndexSource.fromName("GOOGLE_FINANCE"))
    }
}
