package com.tinyoscillator.presentation.theme

import android.content.Context
import app.cash.turbine.test
import com.tinyoscillator.data.repository.ThemeRepository
import com.tinyoscillator.domain.model.ThemeGroup
import com.tinyoscillator.domain.model.ThemeSortMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
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
 * [ThemeViewModel] 단위 테스트.
 *
 * - `combine(query, sortMode)` → `flatMapLatest` → `themeRepository.observeThemes(q, sort)`
 *   라우팅을 검증한다.
 * - `themeCount` / `lastUpdatedAt` 파생 StateFlow도 함께 검증.
 * - `refresh()`는 `WorkManagerHelper.runThemeUpdateNow`에 의존하므로 단위 테스트 범위에서 제외 (Step 8 실기 검증).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: ThemeRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun theme(
        code: String,
        name: String = code,
        period: Double = 0.0,
        flu: Double = 0.0,
        stockCount: Int = 0,
        lastUpdated: Long = 0L,
    ) = ThemeGroup(
        themeCode = code,
        themeName = name,
        stockCount = stockCount,
        fluRate = flu,
        periodReturnRate = period,
        riseCount = 0,
        fallCount = 0,
        mainStocks = "",
        lastUpdated = lastUpdated,
    )

    // ============================================================================
    // 초기 상태 + observeThemes("", TOP_RETURN) 라우팅
    // ============================================================================

    @Test
    fun `init - 초기 query는 빈 문자열, sortMode는 TOP_RETURN`() = runTest {
        every { repository.observeThemes(any(), any()) } returns flowOf(emptyList())

        val vm = ThemeViewModel(repository, context)
        advanceUntilIdle()

        assertEquals("", vm.query.value)
        assertEquals(ThemeSortMode.TOP_RETURN, vm.sortMode.value)
    }

    @Test
    fun `themes - 빈 query + TOP_RETURN으로 repository 호출`() = runTest {
        val list = listOf(theme("T1", "AI", period = 5.0))
        every { repository.observeThemes("", ThemeSortMode.TOP_RETURN) } returns flowOf(list)

        val vm = ThemeViewModel(repository, context)

        vm.themes.test {
            // initial empty
            assertEquals(emptyList<ThemeGroup>(), awaitItem())
            // observeThemes 결과
            assertEquals(list, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        verify(atLeast = 1) { repository.observeThemes("", ThemeSortMode.TOP_RETURN) }
    }

    // ============================================================================
    // query 변경 → flatMapLatest로 새 Flow 전환
    // ============================================================================

    @Test
    fun `onQueryChange - 새 query로 repository를 다시 호출`() = runTest {
        every { repository.observeThemes("", ThemeSortMode.TOP_RETURN) } returns flowOf(emptyList())
        every { repository.observeThemes("AI", ThemeSortMode.TOP_RETURN) } returns
            flowOf(listOf(theme("T1", "AI인공지능")))

        val vm = ThemeViewModel(repository, context)
        val job = backgroundScope.launch { vm.themes.collect { } }
        advanceUntilIdle()

        vm.onQueryChange("AI")
        advanceUntilIdle()
        job.cancel()

        assertEquals("AI", vm.query.value)
        assertEquals(listOf(theme("T1", "AI인공지능")), vm.themes.value)
        verify(atLeast = 1) { repository.observeThemes("AI", ThemeSortMode.TOP_RETURN) }
    }

    // ============================================================================
    // sortMode 변경 → flatMapLatest로 새 Flow 전환
    // ============================================================================

    @Test
    fun `onSortModeChange - 새 sortMode로 repository를 다시 호출`() = runTest {
        every { repository.observeThemes(any(), any()) } returns flowOf(emptyList())

        val vm = ThemeViewModel(repository, context)
        val job = backgroundScope.launch { vm.themes.collect { } }
        advanceUntilIdle()

        ThemeSortMode.entries.forEach { mode ->
            vm.onSortModeChange(mode)
            advanceUntilIdle()
        }
        job.cancel()

        assertEquals(ThemeSortMode.entries.last(), vm.sortMode.value)
        ThemeSortMode.entries.forEach { mode ->
            verify(atLeast = 1) { repository.observeThemes("", mode) }
        }
    }

    // ============================================================================
    // 동적 Flow: 검색어가 변하면 다른 List 반환
    // ============================================================================

    @Test
    fun `themes - query 토글 시 새 결과로 갱신`() = runTest {
        val initial = listOf(theme("T1", "AI"), theme("T2", "반도체"))
        val filtered = listOf(theme("T1", "AI"))

        every { repository.observeThemes("", ThemeSortMode.TOP_RETURN) } returns flowOf(initial)
        every { repository.observeThemes("AI", ThemeSortMode.TOP_RETURN) } returns flowOf(filtered)

        val vm = ThemeViewModel(repository, context)

        vm.themes.test {
            assertEquals(emptyList<ThemeGroup>(), awaitItem())
            assertEquals(initial, awaitItem())

            vm.onQueryChange("AI")

            assertEquals(filtered, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ============================================================================
    // 파생 StateFlow: themeCount / lastUpdatedAt
    // ============================================================================

    @Test
    fun `themeCount - themes의 size를 그대로 반영`() = runTest {
        val list = listOf(theme("T1"), theme("T2"), theme("T3"))
        every { repository.observeThemes(any(), any()) } returns flowOf(list)

        val vm = ThemeViewModel(repository, context)

        vm.themeCount.test {
            assertEquals(0, awaitItem())
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastUpdatedAt - themes 중 lastUpdated 최댓값`() = runTest {
        val list = listOf(
            theme("T1", lastUpdated = 1_000L),
            theme("T2", lastUpdated = 5_000L),
            theme("T3", lastUpdated = 3_000L),
        )
        every { repository.observeThemes(any(), any()) } returns flowOf(list)

        val vm = ThemeViewModel(repository, context)

        vm.lastUpdatedAt.test {
            assertEquals(null, awaitItem())
            assertEquals(5_000L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lastUpdatedAt - 빈 리스트면 null`() = runTest {
        every { repository.observeThemes(any(), any()) } returns flowOf(emptyList())

        val vm = ThemeViewModel(repository, context)
        advanceUntilIdle()

        assertNull(vm.lastUpdatedAt.value)
        assertEquals(0, vm.themeCount.value)
    }

    // ============================================================================
    // 동적 source Flow (MutableStateFlow): repository가 emit 갱신
    // ============================================================================

    @Test
    fun `themes - repository Flow가 새 emit하면 ViewModel도 즉시 반영`() = runTest {
        val source = MutableStateFlow(listOf(theme("T1", "AI")))
        every { repository.observeThemes("", ThemeSortMode.TOP_RETURN) } returns source

        val vm = ThemeViewModel(repository, context)

        vm.themes.test {
            assertEquals(emptyList<ThemeGroup>(), awaitItem())
            assertEquals(listOf(theme("T1", "AI")), awaitItem())

            source.value = listOf(theme("T1", "AI"), theme("T2", "반도체"))
            assertEquals(listOf(theme("T1", "AI"), theme("T2", "반도체")), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ============================================================================
    // query + sortMode 결합 (combine)
    // ============================================================================

    @Test
    fun `query 와 sortMode 동시 변경 시 마지막 조합으로 호출`() = runTest {
        every { repository.observeThemes(any(), any()) } returns flowOf(emptyList())

        val vm = ThemeViewModel(repository, context)
        val job = backgroundScope.launch { vm.themes.collect { } }
        advanceUntilIdle()

        vm.onQueryChange("반도체")
        vm.onSortModeChange(ThemeSortMode.NAME)
        advanceUntilIdle()
        job.cancel()

        assertEquals("반도체", vm.query.value)
        assertEquals(ThemeSortMode.NAME, vm.sortMode.value)
        coVerify(atLeast = 1) { repository.observeThemes("반도체", ThemeSortMode.NAME) }
    }
}
