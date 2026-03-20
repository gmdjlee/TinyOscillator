package com.tinyoscillator.presentation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * BackupManager 암호화/복호화 테스트.
 *
 * PBKDF2 210K iterations이므로 테스트 수를 최소화 (핵심 시나리오만).
 * 각 encrypt/decrypt 호출에 ~3-5초 소요.
 */
class BackupManagerTest {

    @Test
    fun `encrypt then decrypt roundtrip - 다양한 입력`() {
        // 일반 텍스트, 한국어, 빈 문자열, JSON을 한 번에 검증
        val cases = listOf(
            "Hello, World!" to "testPassword123",
            "안녕하세요, 한국어 테스트" to "비밀번호123",
            "" to "emptyPlaintext",
            """{"appKey":"abc","secret":"xyz"}""" to "jsonPw"
        )
        for ((plain, pw) in cases) {
            val decrypted = BackupManager.decrypt(BackupManager.encrypt(plain, pw), pw)
            assertEquals("Roundtrip failed for: $plain", plain, decrypted)
        }
    }

    @Test
    fun `encrypted output structure - salt IV ciphertext GCM tag`() {
        val encrypted = BackupManager.encrypt("Hello", "password")
        // salt(16) + iv(12) + plaintext(5) + GCM_tag(16) = 49
        assertEquals(49, encrypted.size)
        assertTrue(encrypted.size > 28) // header alone is 28
    }

    @Test
    fun `IV and salt differ between encryptions`() {
        val e1 = BackupManager.encrypt("data", "pw")
        val e2 = BackupManager.encrypt("data", "pw")
        assertNotEquals(e1.copyOfRange(0, 16).toList(), e2.copyOfRange(0, 16).toList()) // salt
        assertNotEquals(e1.copyOfRange(16, 28).toList(), e2.copyOfRange(16, 28).toList()) // IV
        // Both still decrypt
        assertEquals("data", BackupManager.decrypt(e1, "pw"))
        assertEquals("data", BackupManager.decrypt(e2, "pw"))
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong password throws AEADBadTagException`() {
        val encrypted = BackupManager.encrypt("secret", "correct")
        BackupManager.decrypt(encrypted, "wrong")
    }

    @Test(expected = AEADBadTagException::class)
    fun `corrupted ciphertext throws AEADBadTagException`() {
        val encrypted = BackupManager.encrypt("integrity", "pw")
        val corrupted = encrypted.copyOf()
        corrupted[30] = (corrupted[30].toInt() xor 0xFF).toByte()
        BackupManager.decrypt(corrupted, "pw")
    }

    @Test(expected = Exception::class)
    fun `truncated data throws exception`() {
        BackupManager.decrypt(ByteArray(20) { it.toByte() }, "pw")
    }

    // region TSV helper tests

    @Test
    fun `toTsv - null returns empty`() {
        assertEquals("", null.toTsv())
    }

    @Test
    fun `toTsv - zero number returns empty`() {
        assertEquals("", 0.toTsv())
        assertEquals("", 0L.toTsv())
        assertEquals("", 0.0.toTsv())
    }

    @Test
    fun `toTsv - non-zero values return string`() {
        assertEquals("42", 42.toTsv())
        assertEquals("3.14", 3.14.toTsv())
        assertEquals("-100", (-100L).toTsv())
        assertEquals("hello", "hello".toTsv())
    }

    @Test
    fun `toTsv - empty string returns empty`() {
        assertEquals("", "".toTsv())
    }

    @Test
    fun `formatTimestamp - zero returns empty`() {
        assertEquals("", BackupManager.formatTimestamp(0L))
    }

    @Test
    fun `formatTimestamp - valid millis returns formatted date`() {
        // 2026-01-15 09:30 KST = specific epoch millis
        val result = BackupManager.formatTimestamp(1736904600000L)
        assertTrue("Expected date format yyyy-MM-dd HH:mm, got: $result", result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
    }

    @Test
    fun `TSV line format - tab separated values`() {
        val ticker = "005930"
        val date = "20260315"
        val marketCap = 400000000000L
        val line = "$ticker\t$date\t${marketCap.toTsv()}"
        val parts = line.split("\t")
        assertEquals(3, parts.size)
        assertEquals("005930", parts[0])
        assertEquals("20260315", parts[1])
        assertEquals("400000000000", parts[2])
    }

    private fun Any?.toTsv(): String = with(BackupManager) { this@toTsv.toTsv() }

    // endregion
}
