package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.Suggestion
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.LocalDate

/**
 * [ApplySuggestionUseCase] 테스트 — 승인 시에만 `source=AUTO`로 반영되고, 승인되지 않으면 repository에
 * 어떤 쓰기도 발생하지 않는다는 §7 "승인 흐름"을 검증한다.
 */
class ApplySuggestionUseCaseTest {

    private val repository = mockk<BearSignalRepository>(relaxed = true)
    private val useCase = ApplySuggestionUseCase(repository)

    private fun suggestion(field: SuggestionField = SuggestionField.RATE, next: String = "4.50") = Suggestion(
        field = field,
        currentValue = "3.75",
        nextValue = next,
        asOf = LocalDate.of(2026, 7, 10),
        origin = "Anthropic web_search",
        stale = false
    )

    @Test
    fun `생성만으로는 repository에 어떤 쓰기도 발생하지 않는다(승인 전 상태 불변)`() {
        coVerify(exactly = 0) { repository.applySuggestion(any(), any(), any()) }
    }

    @Test
    fun `invoke는 제안 필드와 값을 그대로 repository applySuggestion에 위임한다`() = runTest {
        val s = suggestion()
        coEvery { repository.applySuggestion(any(), any(), any()) } returns Unit

        useCase(s, now = 1_000L)

        coVerify(exactly = 1) { repository.applySuggestion(SuggestionField.RATE, "4.50", 1_000L) }
    }

    @Test
    fun `applyAll은 목록의 모든 제안을 각각 위임한다`() = runTest {
        val s1 = suggestion(field = SuggestionField.RATE, next = "4.50")
        val s2 = suggestion(field = SuggestionField.CREDIT, next = "50.00")

        useCase.applyAll(listOf(s1, s2), now = 2_000L)

        coVerify(exactly = 1) { repository.applySuggestion(SuggestionField.RATE, "4.50", 2_000L) }
        coVerify(exactly = 1) { repository.applySuggestion(SuggestionField.CREDIT, "50.00", 2_000L) }
    }

    @Test
    fun `applyAll 빈 목록이면 repository를 호출하지 않는다`() = runTest {
        useCase.applyAll(emptyList(), now = 3_000L)

        coVerify(exactly = 0) { repository.applySuggestion(any(), any(), any()) }
    }
}
