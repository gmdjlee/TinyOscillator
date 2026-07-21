package com.tinyoscillator.presentation.keyword

import android.content.Context
import app.cash.turbine.test
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.domain.model.KeywordGroup
import com.tinyoscillator.domain.model.KeywordSortMode
import com.tinyoscillator.presentation.settings.EtfKeywordFilter
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [KeywordViewModel] 단위 테스트.
 *
 * - `combine(getAllEtfs, includeKeywords, query, sortMode)` → `groupEtfsByKeyword` 결합을 검증한다.
 * - 그룹핑 규칙 자체는 `GroupEtfsByKeywordTest`(Phase 0)에서 커버 — 여기서는 ViewModel 배선(방출·query·sort·파생)에 집중.
 * - `refresh()`는 `WorkManagerHelper.runEtfUpdateNow`(object)에 의존하므로 단위 테스트 범위에서 제외 (Phase 4 실기 검증).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeywordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var etfRepository: EtfRepository
    private lateinit var context: Context

    private fun etf(
        ticker: String,
        name: String,
        changeRate: Double? = null,
        updatedAt: Long = 0L,
    ) = EtfEntity(
        ticker = ticker,
        name = name,
        isinCode = "ISIN_$ticker",
        changeRate = changeRate,
        updatedAt = updatedAt,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        etfRepository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        mockkStatic("com.tinyoscillator.presentation.settings.SettingsPreferencesKt")
        // 기본: 포함 키워드 없음, ETF 없음
        coEvery { com.tinyoscillator.presentation.settings.loadEtfKeywordFilter(any()) } returns
            EtfKeywordFilter(includeKeywords = emptyList(), excludeKeywords = emptyList())
        every { etfRepository.getAllEtfs() } returns flowOf(emptyList())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun withKeywords(vararg keywords: String) {
        coEvery { com.tinyoscillator.presentation.settings.loadEtfKeywordFilter(any()) } returns
            EtfKeywordFilter(includeKeywords = keywords.toList(), excludeKeywords = emptyList())
    }

    // ============================================================================
    // 초기 상태
    // ============================================================================

    @Test
    fun `init - 초기 query는 빈 문자열, sortMode는 ETF_COUNT`() = runTest {
        val vm = KeywordViewModel(etfRepository, context)
        advanceUntilIdle()

        assertEquals("", vm.query.value)
        assertEquals(KeywordSortMode.ETF_COUNT, vm.sortMode.value)
    }

    @Test
    fun `includeKeywords - loadEtfKeywordFilter의 includeKeywords로 로드된다`() = runTest {
        withKeywords("반도체", "2차전지")

        val vm = KeywordViewModel(etfRepository, context)
        advanceUntilIdle()

        assertEquals(listOf("반도체", "2차전지"), vm.includeKeywords.value)
    }

    // ============================================================================
    // groups 방출: 키워드 + ETF 결합
    // ============================================================================

    @Test
    fun `groups - 키워드별 멤버 ETF로 그룹핑된다`() = runTest {
        withKeywords("반도체", "2차전지")
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(
                etf("A", "KODEX 반도체 액티브", changeRate = 1.0),
                etf("B", "TIGER 반도체 액티브", changeRate = 3.0),
                etf("C", "KODEX 2차전지 액티브", changeRate = 2.0),
            )
        )

        val vm = KeywordViewModel(etfRepository, context)

        vm.groups.test {
            assertEquals(emptyList<KeywordGroup>(), awaitItem())
            val groups = awaitItem()
            // ETF_COUNT 기본 정렬 → 반도체(2) 먼저, 2차전지(1)
            assertEquals(listOf("반도체", "2차전지"), groups.map { it.keyword })
            assertEquals(listOf(2, 1), groups.map { it.etfCount })
            assertEquals(2.0, groups[0].avgChangeRate, 1e-9) // (1.0+3.0)/2
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `groups - 멤버 0개 키워드는 목록에서 제외된다`() = runTest {
        withKeywords("반도체", "바이오")
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(etf("A", "KODEX 반도체 액티브", changeRate = 1.0))
        )

        val vm = KeywordViewModel(etfRepository, context)
        val job = launch { vm.groups.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("반도체"), vm.groups.value.map { it.keyword })
        job.cancel()
    }

    @Test
    fun `groups - 포함 키워드 없으면 빈 목록`() = runTest {
        // 기본 setup: includeKeywords 없음
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(etf("A", "KODEX 반도체 액티브", changeRate = 1.0))
        )

        val vm = KeywordViewModel(etfRepository, context)
        val job = launch { vm.groups.collect {} }
        advanceUntilIdle()

        assertEquals(emptyList<KeywordGroup>(), vm.groups.value)
        job.cancel()
    }

    // ============================================================================
    // query 변경
    // ============================================================================

    @Test
    fun `onQueryChange - 키워드명 필터가 적용된다`() = runTest {
        withKeywords("반도체", "2차전지")
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(
                etf("A", "KODEX 반도체 액티브"),
                etf("C", "KODEX 2차전지 액티브"),
            )
        )

        val vm = KeywordViewModel(etfRepository, context)
        val job = launch { vm.groups.collect {} }
        advanceUntilIdle()

        vm.onQueryChange("2차")
        advanceUntilIdle()

        assertEquals("2차", vm.query.value)
        assertEquals(listOf("2차전지"), vm.groups.value.map { it.keyword })
        job.cancel()
    }

    // ============================================================================
    // sortMode 변경
    // ============================================================================

    @Test
    fun `onSortModeChange - NAME 정렬 시 키워드 오름차순`() = runTest {
        withKeywords("반도체", "2차전지")
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(
                etf("A", "KODEX 반도체 액티브"),
                etf("B", "TIGER 반도체 액티브"),
                etf("C", "KODEX 2차전지 액티브"),
            )
        )

        val vm = KeywordViewModel(etfRepository, context)
        val job = launch { vm.groups.collect {} }
        advanceUntilIdle()

        vm.onSortModeChange(KeywordSortMode.NAME)
        advanceUntilIdle()

        assertEquals(KeywordSortMode.NAME, vm.sortMode.value)
        // "2차전지" < "반도체" (유니코드 오름차순)
        assertEquals(listOf("2차전지", "반도체"), vm.groups.value.map { it.keyword })
        job.cancel()
    }

    @Test
    fun `onSortModeChange - AVG_RETURN 정렬 시 평균 등락률 내림차순`() = runTest {
        withKeywords("반도체", "2차전지")
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(
                etf("A", "KODEX 반도체 액티브", changeRate = 1.0),   // 반도체 avg 1.0
                etf("C", "KODEX 2차전지 액티브", changeRate = 5.0),  // 2차전지 avg 5.0
            )
        )

        val vm = KeywordViewModel(etfRepository, context)
        val job = launch { vm.groups.collect {} }
        advanceUntilIdle()

        vm.onSortModeChange(KeywordSortMode.AVG_RETURN)
        advanceUntilIdle()

        assertEquals(listOf("2차전지", "반도체"), vm.groups.value.map { it.keyword })
        job.cancel()
    }

    // ============================================================================
    // 파생 StateFlow
    // ============================================================================

    @Test
    fun `groupCount - groups의 size를 반영`() = runTest {
        withKeywords("반도체", "2차전지")
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(
                etf("A", "KODEX 반도체 액티브"),
                etf("C", "KODEX 2차전지 액티브"),
            )
        )

        val vm = KeywordViewModel(etfRepository, context)

        vm.groupCount.test {
            assertEquals(0, awaitItem())
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastUpdatedAt - 멤버 updatedAt 최댓값`() = runTest {
        withKeywords("반도체")
        every { etfRepository.getAllEtfs() } returns flowOf(
            listOf(
                etf("A", "KODEX 반도체 액티브", updatedAt = 1_000L),
                etf("B", "TIGER 반도체 액티브", updatedAt = 7_000L),
            )
        )

        val vm = KeywordViewModel(etfRepository, context)

        vm.lastUpdatedAt.test {
            assertEquals(null, awaitItem())
            assertEquals(7_000L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastUpdatedAt - 빈 groups면 null`() = runTest {
        // 기본 setup: 키워드/ETF 없음 → 빈 groups
        val vm = KeywordViewModel(etfRepository, context)
        advanceUntilIdle()

        assertNull(vm.lastUpdatedAt.value)
        assertEquals(0, vm.groupCount.value)
    }

    // ============================================================================
    // 동적 source Flow: repository가 새 emit하면 즉시 반영
    // ============================================================================

    @Test
    fun `groups - repository Flow가 새 emit하면 ViewModel도 즉시 반영`() = runTest {
        withKeywords("반도체")
        val source = MutableStateFlow(listOf(etf("A", "KODEX 반도체 액티브")))
        every { etfRepository.getAllEtfs() } returns source

        val vm = KeywordViewModel(etfRepository, context)

        vm.groups.test {
            assertEquals(emptyList<KeywordGroup>(), awaitItem())
            assertEquals(1, awaitItem().first().etfCount)

            source.value = listOf(
                etf("A", "KODEX 반도체 액티브"),
                etf("B", "TIGER 반도체 액티브"),
            )
            assertEquals(2, awaitItem().first().etfCount)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
