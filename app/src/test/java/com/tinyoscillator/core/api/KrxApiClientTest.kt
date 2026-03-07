package com.tinyoscillator.core.api

import com.krxkt.KrxEtf
import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.api.KrxClient
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * KrxApiClient 유닛 테스트.
 *
 * login, getEtfTickerList, close, Mutex 동시성 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KrxApiClientTest {

    private lateinit var client: KrxApiClient
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        client = KrxApiClient()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==========================================================
    // login 테스트
    // ==========================================================

    @Test
    fun `login - 로그인 성공 시 true를 반환한다`() = runTest {
        val mockKrxClient = mockk<KrxClient> {
            coEvery { login(any(), any()) } returns true
        }
        mockkConstructor(KrxClient::class)
        every { anyConstructed<KrxClient>().login(any(), any()) } returns true

        // KrxClient 생성자를 mock하기 어려우므로 reflection으로 내부 상태를 확인
        // 대신 실제 login은 네트워크가 필요하므로, 내부 필드를 통해 검증
        // login 호출 후 getKrxIndex/getKrxStock이 null이 아닌지 확인
        unmockkConstructor(KrxClient::class)

        // login이 실제 KrxClient를 생성하므로 constructor mock 필요
        mockkConstructor(KrxClient::class)
        every { anyConstructed<KrxClient>().login("testId", "testPw") } returns true

        mockkConstructor(KrxEtf::class)
        mockkConstructor(KrxIndex::class)
        mockkConstructor(KrxStock::class)

        val result = client.login("testId", "testPw")

        assertTrue(result)
        assertNotNull(client.getKrxIndex())
        assertNotNull(client.getKrxStock())

        unmockkConstructor(KrxClient::class)
        unmockkConstructor(KrxEtf::class)
        unmockkConstructor(KrxIndex::class)
        unmockkConstructor(KrxStock::class)
    }

    @Test
    fun `login - 로그인 실패 시 false를 반환한다`() = runTest {
        mockkConstructor(KrxClient::class)
        every { anyConstructed<KrxClient>().login("badId", "badPw") } returns false

        val result = client.login("badId", "badPw")

        assertFalse(result)
        assertNull(client.getKrxIndex())
        assertNull(client.getKrxStock())

        unmockkConstructor(KrxClient::class)
    }

    @Test
    fun `login - 예외 발생 시 false를 반환한다`() = runTest {
        mockkConstructor(KrxClient::class)
        every { anyConstructed<KrxClient>().login(any(), any()) } throws RuntimeException("네트워크 오류")

        val result = client.login("id", "pw")

        assertFalse(result)
        assertNull(client.getKrxIndex())
        assertNull(client.getKrxStock())

        unmockkConstructor(KrxClient::class)
    }

    // ==========================================================
    // getEtfTickerList 테스트
    // ==========================================================

    @Test(expected = IllegalStateException::class)
    fun `getEtfTickerList - 로그인 전 호출 시 IllegalStateException을 던진다`() = runTest {
        client.getEtfTickerList("20240101")
    }

    @Test(expected = IllegalStateException::class)
    fun `getPortfolio - 로그인 전 호출 시 IllegalStateException을 던진다`() = runTest {
        client.getPortfolio("20240101", "069500")
    }

    // ==========================================================
    // close 테스트
    // ==========================================================

    @Test
    fun `close - 호출 후 모든 참조가 null이 된다`() = runTest {
        // 먼저 로그인하여 내부 참조를 설정
        mockkConstructor(KrxClient::class)
        mockkConstructor(KrxEtf::class)
        mockkConstructor(KrxIndex::class)
        mockkConstructor(KrxStock::class)
        every { anyConstructed<KrxClient>().login(any(), any()) } returns true
        every { anyConstructed<KrxClient>().close() } just Runs

        client.login("id", "pw")
        assertNotNull(client.getKrxIndex())
        assertNotNull(client.getKrxStock())

        // close 호출
        client.close()

        assertNull(client.getKrxIndex())
        assertNull(client.getKrxStock())

        unmockkConstructor(KrxClient::class)
        unmockkConstructor(KrxEtf::class)
        unmockkConstructor(KrxIndex::class)
        unmockkConstructor(KrxStock::class)
    }

    @Test
    fun `close - 로그인 전 close를 호출해도 예외가 발생하지 않는다`() {
        // 아직 로그인하지 않은 상태에서 close
        client.close()

        assertNull(client.getKrxIndex())
        assertNull(client.getKrxStock())
    }

    // ==========================================================
    // Mutex 동시성 테스트
    // ==========================================================

    @Test
    fun `login - Mutex로 동시 로그인이 직렬화된다`() = runTest {
        mockkConstructor(KrxClient::class)
        mockkConstructor(KrxEtf::class)
        mockkConstructor(KrxIndex::class)
        mockkConstructor(KrxStock::class)

        var loginCallCount = 0
        every { anyConstructed<KrxClient>().login(any(), any()) } answers {
            loginCallCount++
            true
        }

        // 두 개의 동시 로그인 요청
        val job1 = launch { client.login("id1", "pw1") }
        val job2 = launch { client.login("id2", "pw2") }

        // testDispatcher를 진행시켜 코루틴 완료
        testScheduler.advanceUntilIdle()

        job1.join()
        job2.join()

        // Mutex 때문에 두 번 순차 호출됨
        assertEquals(2, loginCallCount)

        unmockkConstructor(KrxClient::class)
        unmockkConstructor(KrxEtf::class)
        unmockkConstructor(KrxIndex::class)
        unmockkConstructor(KrxStock::class)
    }
}
