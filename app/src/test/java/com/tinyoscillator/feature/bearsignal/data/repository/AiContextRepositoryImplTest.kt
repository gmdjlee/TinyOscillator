package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextDao
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextEntity
import com.tinyoscillator.feature.bearsignal.data.mapper.AiContextClaimMapper
import com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextGroupOutcome
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ClaimType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [AiContextRepositoryImpl] 테스트 (TASK_bear_signal_console.md §4.7, Phase 7-2) —
 * ①키 미설정 시 네트워크 호출 없이 failure ②성공 시 [LlmMarketDataSource] 위임 ③[fetchUpdates]는
 * 어떤 저장도 하지 않는다(§4.7 "승인 없이는 표시 콘텐츠 불변") ④[approve]는 section_key별로 묶어
 * upsert하고 as_of는 최신 source_date를 사용한다 ⑤[getApproved]는 entity를 도메인으로 역직렬화한다.
 */
class AiContextRepositoryImplTest {

    private val llmMarketDataSource = mockk<LlmMarketDataSource>()
    private val apiConfigProvider = mockk<ApiConfigProvider>()
    private val dao = mockk<BearSignalAiContextDao>()
    private val repository = AiContextRepositoryImpl(llmMarketDataSource, apiConfigProvider, dao)

    private val today = LocalDate.of(2026, 7, 17)

    private fun emptyOutcome() = AiContextGroupOutcome(emptyList(), emptyMap(), null, null, "claude")

    private fun emptyFetchResult() = AiContextFetchResult(
        monitor = emptyOutcome(),
        cases = emptyOutcome(),
        historyCurrent = emptyOutcome()
    )

    private fun claim(
        sectionKey: AiContextSectionKey = AiContextSectionKey.TYPE0_MONITOR,
        text: String = "체크리스트 항목",
        sourceDate: LocalDate = today
    ) = AiContextClaim(
        sectionKey = sectionKey,
        text = text,
        type = ClaimType.FACT,
        sourceUrl = "https://example.com/report",
        sourceTitle = "제목",
        sourceDate = sourceDate,
        quote = "원문 인용"
    )

    // ── fetchUpdates ─────────────────────────────────────────────────

    @Test
    fun `API 키 미설정이면 네트워크 호출 없이 failure를 반환한다`() = runTest {
        coEvery { apiConfigProvider.getAiConfig() } returns AiApiKeyConfig(AiProvider.CLAUDE, apiKey = "", modelId = "")

        val result = repository.fetchUpdates(today)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiError.NoApiKeyError)
        coVerify(exactly = 0) { llmMarketDataSource.fetchAiContextUpdates(any(), any()) }
    }

    @Test
    fun `키가 유효하면 LlmMarketDataSource에 위임하고 어떤 것도 저장하지 않는다`() = runTest {
        val config = AiApiKeyConfig(AiProvider.CLAUDE, apiKey = "sk-ant-test", modelId = "claude-3-5-haiku-latest")
        coEvery { apiConfigProvider.getAiConfig() } returns config
        coEvery { llmMarketDataSource.fetchAiContextUpdates(config, today) } returns emptyFetchResult()

        val result = repository.fetchUpdates(today)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { llmMarketDataSource.fetchAiContextUpdates(config, today) }
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `LlmMarketDataSource가 예외를 던지면 ApiError로 매핑된 failure를 반환한다`() = runTest {
        val config = AiApiKeyConfig(AiProvider.CLAUDE, apiKey = "sk-ant-test", modelId = "claude-3-5-haiku-latest")
        coEvery { apiConfigProvider.getAiConfig() } returns config
        coEvery { llmMarketDataSource.fetchAiContextUpdates(config, today) } throws RuntimeException("boom")

        val result = repository.fetchUpdates(today)

        assertTrue(result.isFailure)
    }

    // ── approve ──────────────────────────────────────────────────────

    @Test
    fun `approve는 section_key별로 묶어 upsert하고 직렬화 왕복이 성립한다`() = runTest {
        val entitySlot = slot<BearSignalAiContextEntity>()
        coEvery { dao.upsert(capture(entitySlot)) } returns Unit

        val claims = listOf(
            claim(sectionKey = AiContextSectionKey.TYPE0_MONITOR, text = "항목1"),
            claim(sectionKey = AiContextSectionKey.TYPE1_MONITOR, text = "항목2")
        )

        repository.approve(claims, provider = "claude", now = 12345L)

        coVerify(exactly = 2) { dao.upsert(any()) }
    }

    @Test
    fun `approve는 한 섹션의 여러 클레임을 하나의 content_json 배열로 직렬화하고 as_of는 최신 source_date를 쓴다`() = runTest {
        val entitySlot = slot<BearSignalAiContextEntity>()
        coEvery { dao.upsert(capture(entitySlot)) } returns Unit

        val claims = listOf(
            claim(sourceDate = LocalDate.of(2026, 7, 10), text = "오래된 항목"),
            claim(sourceDate = LocalDate.of(2026, 7, 17), text = "최신 항목")
        )

        repository.approve(claims, provider = "gemini", now = 99L)

        val entity = entitySlot.captured
        assertEquals("type0_monitor", entity.sectionKey)
        assertEquals("2026-07-17", entity.asOf)
        assertEquals("gemini", entity.provider)
        assertEquals(99L, entity.approvedAt)

        // 직렬화 왕복 — content_json을 다시 도메인으로 역직렬화하면 두 클레임이 모두 복원된다.
        val roundTrip = AiContextClaimMapper.toDomain(entity)
        assertEquals(2, roundTrip.size)
        assertEquals(setOf("오래된 항목", "최신 항목"), roundTrip.map { it.text }.toSet())
    }

    // ── getApproved ──────────────────────────────────────────────────

    @Test
    fun `getApproved는 entity를 도메인 클레임 목록으로 역직렬화한다`() = runTest {
        val entity = AiContextClaimMapper.toEntity(
            sectionKey = AiContextSectionKey.HISTORY_CURRENT,
            claims = listOf(claim(sectionKey = AiContextSectionKey.HISTORY_CURRENT, text = "현재 비교")),
            provider = "claude",
            asOf = today,
            approvedAt = 1L
        )
        coEvery { dao.getAll() } returns listOf(entity)

        val result = repository.getApproved()

        assertEquals(1, result.size)
        assertEquals("현재 비교", result[AiContextSectionKey.HISTORY_CURRENT]?.first()?.text)
    }

    @Test
    fun `getApproved는 승인 캐시가 없으면 빈 맵을 반환한다`() = runTest {
        coEvery { dao.getAll() } returns emptyList()

        val result = repository.getApproved()

        assertTrue(result.isEmpty())
    }
}
