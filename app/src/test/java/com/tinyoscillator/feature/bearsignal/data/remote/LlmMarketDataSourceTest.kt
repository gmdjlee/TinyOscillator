package com.tinyoscillator.feature.bearsignal.data.remote

import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimRejection
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionField
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * [LlmMarketDataSource] 통합 테스트(MockWebServer) — 성공/열거형 위반(필드 폐기)/HTTP 오류/타임아웃/
 * pause_turn 재개/급변 재확인/부분 실패 격리 (TASK_bear_signal_console.md §4.5).
 */
class LlmMarketDataSourceTest {

    private lateinit var server: MockWebServer
    private val config = AiApiKeyConfig(AiProvider.CLAUDE, apiKey = "test-key", modelId = "claude-3-5-haiku-latest")
    private val geminiConfig = AiApiKeyConfig(AiProvider.GEMINI, apiKey = "gemini-test-key", modelId = "gemini-2.0-flash")

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun dataSource(
        httpClient: OkHttpClient = OkHttpClient(),
        retryBackoffMs: Long = 10L,
        geminiRateLimitMs: Long = 0L
    ) = LlmMarketDataSource(
        httpClient = httpClient,
        baseUrl = baseUrl(),
        geminiBaseUrl = baseUrl(),
        retryBackoffMs = retryBackoffMs,
        geminiRateLimitMs = geminiRateLimitMs
    )

    /** Gemini `generateContent` 응답 형태 — `candidates[0].content.parts[].text` 단일 블록. */
    private fun geminiResponseBody(text: String, renderedContent: String? = null): String = buildJsonObject {
        put("candidates", buildJsonArray {
            add(buildJsonObject {
                put("content", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", text) })
                    })
                })
                if (renderedContent != null) {
                    put("groundingMetadata", buildJsonObject {
                        put("searchEntryPoint", buildJsonObject {
                            put("renderedContent", renderedContent)
                        })
                    })
                }
            })
        })
    }.toString()

    /** Anthropic `/v1/messages` 응답 형태 — `content`에 단일 text 블록만 담는다. */
    private fun claudeResponseBody(text: String, stopReason: String = "end_turn"): String = buildJsonObject {
        put("id", "msg_1")
        put("type", "message")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", text)
            })
        })
        put("stop_reason", stopReason)
    }.toString()

    /** `pause_turn` — 아직 text 블록이 없고 서버 도구 사용 블록만 있는 응답. */
    private fun pauseTurnResponseBody(): String = buildJsonObject {
        put("id", "msg_1")
        put("type", "message")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "server_tool_use")
                put("id", "srvtool_1")
                put("name", "web_search")
            })
        })
        put("stop_reason", "pause_turn")
    }.toString()

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setBody(body).setResponseCode(code))
    }

    // ── 그룹① rate/dir — 성공 ────────────────────────────────────────

    @Test
    fun `fetchRateDirGroup 성공 — rate dir 모두 유효하면 제안 2건 생성`() = runTest {
        enqueue(claudeResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-10","rate_origin":"FOMC","dir":"hike","dir_as_of":"2026-07-10","dir_origin":"BOK"}"""))

        val outcome = dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        assertNull(outcome.error)
        assertEquals(2, outcome.suggestions.size)
        val rate = outcome.suggestions.first { it.field == SuggestionField.RATE }
        assertEquals("4.00", rate.nextValue)
        assertEquals("3.75", rate.currentValue)
        assertEquals("FOMC", rate.origin)
        assertTrue(!rate.stale)
    }

    // ── 그룹① — 열거형 위반(필드 폐기, 그룹 전체 폐기 아님) ──────────────────

    @Test
    fun `fetchRateDirGroup dir 열거형 위반이면 dir만 폐기되고 rate는 유지된다`() = runTest {
        enqueue(claudeResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-10","dir":"tightening"}"""))

        val outcome = dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        assertNull(outcome.error)
        assertEquals(1, outcome.suggestions.size)
        assertEquals(SuggestionField.RATE, outcome.suggestions.first().field)
    }

    // ── 그룹① — HTTP 오류(재시도 1회 후에도 실패) ─────────────────────────

    @Test
    fun `fetchRateDirGroup HTTP 500이 재시도 후에도 실패하면 그룹 에러를 반환한다`() = runTest {
        enqueue("", code = 500)
        enqueue("", code = 500)

        val outcome = dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        assertTrue(outcome.suggestions.isEmpty())
        assertTrue(outcome.error != null)
        assertEquals(2, server.requestCount)
    }

    // ── 그룹① — 타임아웃(재시도 1회 후에도 실패) ──────────────────────────

    @Test
    fun `fetchRateDirGroup 응답 지연이 타임아웃을 초과하면 그룹 에러를 반환한다`() = runTest {
        val slowResponse = MockResponse()
            .setBody(claudeResponseBody("""{"rate":4.00}"""))
            .setBodyDelay(2, TimeUnit.SECONDS)
        server.enqueue(slowResponse)
        server.enqueue(slowResponse)

        val shortTimeoutClient = OkHttpClient.Builder()
            .readTimeout(150, TimeUnit.MILLISECONDS)
            .build()

        val outcome = dataSource(httpClient = shortTimeoutClient).fetchRateDirGroup(config, currentRate = 3.75)

        assertTrue(outcome.suggestions.isEmpty())
        assertTrue(outcome.error != null)
    }

    // ── 그룹① — pause_turn 재개 ───────────────────────────────────────

    @Test
    fun `fetchRateDirGroup pause_turn 응답 후 재요청해 최종 답을 파싱한다`() = runTest {
        enqueue(pauseTurnResponseBody())
        enqueue(claudeResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-10"}"""))

        val outcome = dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        assertEquals(2, server.requestCount)
        assertEquals(1, outcome.suggestions.size)
        assertEquals("4.00", outcome.suggestions.first().nextValue)

        // 두 번째 요청은 assistant 메시지가 append된 messages 배열을 담아야 한다(추가 user 메시지 없이).
        server.takeRequest() // 첫 요청 소비
        val secondRequestBody = server.takeRequest().body.readUtf8()
        assertTrue(secondRequestBody.contains("\"role\":\"assistant\""))
    }

    // ── 그룹① — 급변 재확인(금리 ±0.5%p) ──────────────────────────────

    @Test
    fun `fetchRateDirGroup 급변(0점5 초과) 시 재확인 값이 일치하면 제안에 포함된다`() = runTest {
        enqueue(claudeResponseBody("""{"rate":5.00,"rate_as_of":"2026-07-10"}""")) // 최초: 3.75 → 5.00 (급변)
        enqueue(claudeResponseBody("""{"rate":5.00,"rate_as_of":"2026-07-10"}""")) // 재확인: 동일값

        val outcome = dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        assertEquals(2, server.requestCount)
        assertEquals(1, outcome.suggestions.size)
        assertEquals("5.00", outcome.suggestions.first().nextValue)
    }

    @Test
    fun `fetchRateDirGroup 급변 시 재확인 값이 불일치하면 폐기된다`() = runTest {
        enqueue(claudeResponseBody("""{"rate":5.00,"rate_as_of":"2026-07-10"}""")) // 최초: 급변
        enqueue(claudeResponseBody("""{"rate":4.20,"rate_as_of":"2026-07-10"}""")) // 재확인: 다른 값

        val outcome = dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        assertEquals(2, server.requestCount)
        assertTrue(outcome.suggestions.isEmpty())
        assertNull(outcome.error)
    }

    // ── 그룹② bigDeal/lossRatio ───────────────────────────────────────

    @Test
    fun `fetchBigDealLossRatioGroup bigDeal 열거형 위반이면 lossRatio만 남는다`() = runTest {
        enqueue(claudeResponseBody("""{"big_deal":"withdrawn","loss_ratio":55.0,"loss_ratio_as_of":"2026-07-10"}"""))

        val outcome = dataSource().fetchBigDealLossRatioGroup(config, currentBigDeal = "pending", currentLossRatio = 45.0)

        assertNull(outcome.error)
        assertEquals(1, outcome.suggestions.size)
        assertEquals(SuggestionField.LOSS_RATIO, outcome.suggestions.first().field)
        assertEquals("55.00", outcome.suggestions.first().nextValue)
    }

    @Test
    fun `fetchBigDealLossRatioGroup 정상 응답은 2건 제안을 생성한다`() = runTest {
        enqueue(
            claudeResponseBody(
                """{"big_deal":"failed","big_deal_as_of":"2026-07-01","loss_ratio":60.0,"loss_ratio_as_of":"2026-07-01"}"""
            )
        )

        val outcome = dataSource().fetchBigDealLossRatioGroup(config, currentBigDeal = "pending", currentLossRatio = 45.0)

        assertEquals(2, outcome.suggestions.size)
    }

    // ── 그룹③ credit — 급변 재확인(±30%) ───────────────────────────────

    @Test
    fun `fetchCreditGroup 급변(30퍼센트 초과) 시 재확인 일치하면 포함된다`() = runTest {
        enqueue(claudeResponseBody("""{"credit":60.0,"credit_as_of":"2026-07-10"}""")) // 38 → 60 (급변)
        enqueue(claudeResponseBody("""{"credit":60.0,"credit_as_of":"2026-07-10"}"""))

        val outcome = dataSource().fetchCreditGroup(config, currentCredit = 38.0)

        assertEquals(2, server.requestCount)
        assertEquals(1, outcome.suggestions.size)
        assertEquals("60.00", outcome.suggestions.first().nextValue)
    }

    @Test
    fun `fetchCreditGroup 급변 시 재확인 불일치면 폐기된다`() = runTest {
        enqueue(claudeResponseBody("""{"credit":60.0,"credit_as_of":"2026-07-10"}"""))
        enqueue(claudeResponseBody("""{"credit":45.0,"credit_as_of":"2026-07-10"}"""))

        val outcome = dataSource().fetchCreditGroup(config, currentCredit = 38.0)

        assertTrue(outcome.suggestions.isEmpty())
    }

    @Test
    fun `fetchCreditGroup 급변 아니면(30퍼센트 이하) 재확인 없이 1회 호출로 완료된다`() = runTest {
        enqueue(claudeResponseBody("""{"credit":45.0,"credit_as_of":"2026-07-10","credit_origin":"KOFIA"}"""))

        val outcome = dataSource().fetchCreditGroup(config, currentCredit = 38.0)

        assertEquals(1, server.requestCount)
        assertEquals(1, outcome.suggestions.size)
        assertEquals("KOFIA", outcome.suggestions.first().origin)
    }

    // ── 응답 파싱 실패 ────────────────────────────────────────────────

    @Test
    fun `fetchRateDirGroup 최종 텍스트가 JSON이 아니면 그룹 에러를 반환한다`() = runTest {
        enqueue(claudeResponseBody("죄송합니다, 검색 결과를 찾지 못했습니다."))

        val outcome = dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        assertTrue(outcome.suggestions.isEmpty())
        assertTrue(outcome.error != null)
    }

    // ── fetchSuggestions() 오케스트레이션 — 부분 실패 격리 ────────────────

    @Test
    fun `fetchSuggestions는 한 그룹이 실패해도 나머지 그룹 제안을 그대로 반환한다`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    "미국 연방준비제도" in body -> MockResponse().setResponseCode(500)
                    "대어급" in body -> MockResponse().setBody(
                        claudeResponseBody("""{"big_deal":"pending","big_deal_as_of":"2026-07-10","loss_ratio":50.0,"loss_ratio_as_of":"2026-07-10"}""")
                    )
                    "KOFIA" in body -> MockResponse().setBody(
                        claudeResponseBody("""{"credit":40.0,"credit_as_of":"2026-07-10"}""")
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val result = dataSource().fetchSuggestions(
            config,
            current = BearSignalReportBaseline.toInputs()
        )

        assertTrue(result.rateDir.error != null)
        assertTrue(result.rateDir.suggestions.isEmpty())
        assertEquals(2, result.bigDealLossRatio.suggestions.size)
        assertEquals(1, result.credit.suggestions.size)
        assertEquals(1, result.failedGroupMessages.size)
        assertEquals(3, result.all.size)
    }

    // ── §4.5 v1.3 제공자 이원화 — Gemini 경로 ──────────────────────────────

    @Test
    fun `Gemini fetchRateDirGroup은 generateContent 엔드포인트로 x-goog-api-key 헤더와 google_search 도구를 담아 요청하고 responseMimeType은 포함하지 않는다`() = runTest {
        enqueue(geminiResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-10","dir":"hike","dir_as_of":"2026-07-10"}"""))

        dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        val request = server.takeRequest()
        assertEquals("/v1beta/models/${geminiConfig.modelId}:generateContent", request.path)
        assertEquals(geminiConfig.apiKey, request.getHeader("x-goog-api-key"))
        val body = request.body.readUtf8()
        // 문자열 contains가 아니라 구조 단언 — tools 배열에 빈 객체 google_search 하나만 선언돼야 한다.
        val bodyJson = Json.parseToJsonElement(body).jsonObject
        val tools = bodyJson["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        assertTrue(tools[0].jsonObject["google_search"] is JsonObject)
        // 구조화 출력 관련 필드는 google_search 병용 시 HTTP 400 — 어느 쪽도 있어선 안 된다.
        assertTrue(!body.contains("responseMimeType"))
        assertTrue(!body.contains("responseSchema"))
    }

    @Test
    fun `Claude fetchRateDirGroup은 v1 messages 엔드포인트로 x-api-key 헤더와 web_search_20250305 도구를 담아 요청한다`() = runTest {
        enqueue(claudeResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-10"}"""))

        dataSource().fetchRateDirGroup(config, currentRate = 3.75)

        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals(config.apiKey, request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val tools = Json.parseToJsonElement(request.body.readUtf8()).jsonObject["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        assertEquals("web_search_20250305", tools[0].jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Gemini fetchRateDirGroup 성공 응답은 제안을 생성하고 origin 미지정 시 Gemini google_search로 폴백한다`() = runTest {
        enqueue(geminiResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-10"}"""))

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertNull(outcome.error)
        assertEquals(1, outcome.suggestions.size)
        assertEquals("4.00", outcome.suggestions.first().nextValue)
        assertEquals("Gemini google_search", outcome.suggestions.first().origin)
    }

    @Test
    fun `Gemini renderedContent가 있으면 SuggestionGroupOutcome searchWidgetHtml에 전달된다`() = runTest {
        enqueue(
            geminiResponseBody(
                """{"credit":40.0,"credit_as_of":"2026-07-10"}""",
                renderedContent = "<div>검색 제안 위젯</div>"
            )
        )

        val outcome = dataSource().fetchCreditGroup(geminiConfig, currentCredit = 38.0)

        assertEquals("<div>검색 제안 위젯</div>", outcome.searchWidgetHtml)
    }

    @Test
    fun `Gemini fetchSuggestions는 검색 제안 위젯 HTML을 SuggestionFetchResult searchWidgetsHtml에 노출한다`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    "미국 연방준비제도" in body -> MockResponse().setBody(
                        geminiResponseBody(
                            """{"rate":4.00,"rate_as_of":"2026-07-10"}""",
                            renderedContent = "<div>rate 위젯</div>"
                        )
                    )
                    "대어급" in body -> MockResponse().setBody(
                        geminiResponseBody("""{"big_deal":"pending","big_deal_as_of":"2026-07-10"}""")
                    )
                    "KOFIA" in body -> MockResponse().setBody(
                        geminiResponseBody("""{"credit":40.0,"credit_as_of":"2026-07-10"}""")
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val result = dataSource().fetchSuggestions(geminiConfig, current = BearSignalReportBaseline.toInputs())

        assertEquals(listOf("<div>rate 위젯</div>"), result.searchWidgetsHtml)
    }

    @Test
    fun `Gemini fetchRateDirGroup HTTP 400은 재시도 없이 Gemini 전용 안내 메시지로 실패한다`() = runTest {
        enqueue("", code = 400)

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertTrue(outcome.suggestions.isEmpty())
        assertTrue(outcome.error != null)
        assertTrue(outcome.error!!.contains("Gemini"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `Gemini HTTP 400에 API_KEY_INVALID 본문이 오면 인증 오류로 안내한다 — 재시도 없음`() = runTest {
        // Gemini는 무효 키도 401이 아닌 400(API_KEY_INVALID)으로 반환한다.
        enqueue(
            """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""",
            code = 400
        )

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertTrue(outcome.suggestions.isEmpty())
        assertTrue(outcome.error!!.contains("키가 유효하지 않습니다"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `Gemini HTTP 429 후 재시도가 성공하면 제안이 생성된다`() = runTest {
        enqueue("", code = 429)
        enqueue(geminiResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-10"}"""))

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertEquals(2, server.requestCount)
        assertNull(outcome.error)
        assertEquals(1, outcome.suggestions.size)
        assertEquals("4.00", outcome.suggestions.first().nextValue)
    }

    @Test
    fun `Gemini 응답이 잘린 JSON이면 그룹 파싱 실패로 처리된다 — MAX_TOKENS 잘림 상당`() = runTest {
        enqueue(geminiResponseBody("""{"rate":4.00,"rate_as_of":"2026-07-1""")) // 닫는 중괄호 없음

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertTrue(outcome.suggestions.isEmpty())
        assertTrue(outcome.error!!.contains("파싱 실패"))
    }

    @Test
    fun `Gemini 파싱 실패에도 검색 제안 위젯 HTML은 보존된다 — ToS 표시 의무`() = runTest {
        enqueue(geminiResponseBody("JSON이 아닌 설명 텍스트", renderedContent = "<div>위젯</div>"))

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertTrue(outcome.error != null)
        assertTrue(outcome.suggestions.isEmpty())
        assertEquals("<div>위젯</div>", outcome.searchWidgetHtml)
    }

    @Test
    fun `Gemini fetchRateDirGroup HTTP 429는 1회 재시도 후에도 실패하면 그룹 에러를 반환한다`() = runTest {
        enqueue("", code = 429)
        enqueue("", code = 429)

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertTrue(outcome.suggestions.isEmpty())
        assertTrue(outcome.error != null)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `Gemini HTTP 오류는 실패한 그룹만 에러가 되고 다른 그룹 제안은 그대로 반환된다`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    "미국 연방준비제도" in body -> MockResponse().setResponseCode(400)
                    "대어급" in body -> MockResponse().setBody(
                        geminiResponseBody("""{"big_deal":"pending","big_deal_as_of":"2026-07-10"}""")
                    )
                    "KOFIA" in body -> MockResponse().setBody(
                        geminiResponseBody("""{"credit":40.0,"credit_as_of":"2026-07-10"}""")
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val result = dataSource().fetchSuggestions(geminiConfig, current = BearSignalReportBaseline.toInputs())

        assertTrue(result.rateDir.error != null)
        assertTrue(result.rateDir.suggestions.isEmpty())
        assertEquals(1, result.bigDealLossRatio.suggestions.size)
        assertEquals(1, result.credit.suggestions.size)
    }

    @Test
    fun `Gemini 경로에서도 급변 재확인이 동작한다 — 일치하면 포함된다`() = runTest {
        enqueue(geminiResponseBody("""{"rate":5.00,"rate_as_of":"2026-07-10"}""")) // 최초: 3.75 → 5.00 (급변)
        enqueue(geminiResponseBody("""{"rate":5.00,"rate_as_of":"2026-07-10"}""")) // 재확인: 동일값

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertEquals(2, server.requestCount)
        assertEquals(1, outcome.suggestions.size)
        assertEquals("5.00", outcome.suggestions.first().nextValue)
    }

    @Test
    fun `Gemini 경로 급변 재확인 불일치면 폐기된다`() = runTest {
        enqueue(geminiResponseBody("""{"rate":5.00,"rate_as_of":"2026-07-10"}""")) // 최초: 급변
        enqueue(geminiResponseBody("""{"rate":4.20,"rate_as_of":"2026-07-10"}""")) // 재확인: 다른 값

        val outcome = dataSource().fetchRateDirGroup(geminiConfig, currentRate = 3.75)

        assertEquals(2, server.requestCount)
        assertTrue(outcome.suggestions.isEmpty())
    }

    // ══════════════════════════════════════════════════════════════════
    // §4.7 그룹④⑤⑥ 정세 업데이트(모니터·사례·역사 현재비교, Phase 7-2)
    // ══════════════════════════════════════════════════════════════════

    private val today = LocalDate.of(2026, 7, 17)

    /** Claude `web_search_tool_result`(검색결과 URL 목록) + `claims` JSON text 블록을 담은 응답. */
    private fun claudeResponseBodyWithSearch(
        claimsJson: String,
        urls: List<String>,
        stopReason: String = "end_turn"
    ): String = buildJsonObject {
        put("id", "msg_1")
        put("type", "message")
        put("content", buildJsonArray {
            add(buildJsonObject {
                put("type", "web_search_tool_result")
                put("tool_use_id", "srvtool_1")
                put("content", buildJsonArray {
                    urls.forEach { url ->
                        add(buildJsonObject {
                            put("type", "web_search_result")
                            put("url", url)
                            put("title", "결과")
                        })
                    }
                })
            })
            add(buildJsonObject {
                put("type", "text")
                put("text", claimsJson)
            })
        })
        put("stop_reason", stopReason)
    }.toString()

    /** Gemini `groundingChunks`(검색결과 URL 목록) + 선택적 검색 제안 위젯을 담은 응답. */
    private fun geminiResponseBodyWithGrounding(
        text: String,
        urls: List<String>,
        renderedContent: String? = null
    ): String = buildJsonObject {
        put("candidates", buildJsonArray {
            add(buildJsonObject {
                put("content", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) })
                })
                put("groundingMetadata", buildJsonObject {
                    put("groundingChunks", buildJsonArray {
                        urls.forEach { url ->
                            add(buildJsonObject { put("web", buildJsonObject { put("uri", url) }) })
                        }
                    })
                    if (renderedContent != null) {
                        put("searchEntryPoint", buildJsonObject { put("renderedContent", renderedContent) })
                    }
                })
            })
        })
    }.toString()

    // ── 그룹④ monitor — 정상 클레임 수용 ────────────────────────────────

    @Test
    fun `fetchMonitorGroup 정상 클레임은 검증 통과해 pending에 담긴다`() = runTest {
        val claims = """{"claims":[{"section_key":"type0_monitor","text":"체크리스트 항목",
            "type":"fact","source_url":"https://example.com/report","source_title":"제목",
            "source_date":"2026-07-15","quote":"원문 인용"}]}"""
        enqueue(claudeResponseBodyWithSearch(claims, listOf("https://example.com/report")))

        val outcome = dataSource().fetchMonitorGroup(config, today)

        assertNull(outcome.error)
        assertEquals(1, outcome.pending.size)
        assertEquals(AiContextSectionKey.TYPE0_MONITOR, outcome.pending.first().claim.sectionKey)
        assertFalse(outcome.pending.first().stale)
        assertTrue(outcome.rejectedCounts.isEmpty())
        assertEquals("claude", outcome.provider)
    }

    // ── 그룹⑤ cases — 환각 URL 폐기 ─────────────────────────────────────

    @Test
    fun `fetchCasesGroup 환각 URL은 URL_NOT_VERIFIED로 폐기된다`() = runTest {
        val claims = """{"claims":[{"section_key":"type1_cases","text":"사례 텍스트",
            "type":"fact","source_url":"https://fake.example.com","source_title":"제목",
            "source_date":"2026-07-10","quote":"원문"}]}"""
        enqueue(claudeResponseBodyWithSearch(claims, listOf("https://real.example.com")))

        val outcome = dataSource().fetchCasesGroup(config, today)

        assertTrue(outcome.pending.isEmpty())
        assertEquals(1, outcome.rejectedCounts[AiContextClaimRejection.URL_NOT_VERIFIED])
    }

    // ── 그룹④ monitor — interpretation 클레임 폐기(fact만 허용) ───────────────

    @Test
    fun `fetchMonitorGroup interpretation 클레임은 INTERPRETATION_NOT_ALLOWED로 폐기된다`() = runTest {
        val claims = """{"claims":[{"section_key":"type2_monitor","text":"전망",
            "type":"interpretation","source_url":"https://example.com/report","source_title":"제목",
            "source_date":"2026-07-10"}]}"""
        enqueue(claudeResponseBodyWithSearch(claims, listOf("https://example.com/report")))

        val outcome = dataSource().fetchMonitorGroup(config, today)

        assertTrue(outcome.pending.isEmpty())
        assertEquals(1, outcome.rejectedCounts[AiContextClaimRejection.INTERPRETATION_NOT_ALLOWED])
    }

    // ── 그룹⑤ cases — fact quote 부재 폐기 ──────────────────────────────

    @Test
    fun `fetchCasesGroup fact 클레임 quote 부재는 FACT_QUOTE_MISSING으로 폐기된다`() = runTest {
        val claims = """{"claims":[{"section_key":"type0_cases","text":"사례",
            "type":"fact","source_url":"https://example.com/report","source_title":"제목",
            "source_date":"2026-07-10"}]}"""
        enqueue(claudeResponseBodyWithSearch(claims, listOf("https://example.com/report")))

        val outcome = dataSource().fetchCasesGroup(config, today)

        assertTrue(outcome.pending.isEmpty())
        assertEquals(1, outcome.rejectedCounts[AiContextClaimRejection.FACT_QUOTE_MISSING])
    }

    // ── 그룹④ monitor — 알 수 없는 section_key 스킵 ─────────────────────────

    @Test
    fun `알 수 없는 section_key 클레임은 스킵되고 다른 클레임은 정상 처리된다`() = runTest {
        val claims = """{"claims":[
            {"section_key":"type9_unknown","text":"모름","type":"fact",
             "source_url":"https://example.com/report","source_title":"제목",
             "source_date":"2026-07-10","quote":"인용"},
            {"section_key":"type0_monitor","text":"정상","type":"fact",
             "source_url":"https://example.com/report","source_title":"제목",
             "source_date":"2026-07-10","quote":"인용"}
        ]}"""
        enqueue(claudeResponseBodyWithSearch(claims, listOf("https://example.com/report")))

        val outcome = dataSource().fetchMonitorGroup(config, today)

        assertEquals(1, outcome.pending.size)
        assertEquals(AiContextSectionKey.TYPE0_MONITOR, outcome.pending.first().claim.sectionKey)
        assertTrue(outcome.rejectedCounts.isEmpty())
    }

    // ── 그룹⑥ history_current — STALE 플래그(폐기 아님) ─────────────────────

    @Test
    fun `fetchHistoryCurrentGroup 30일 초과 클레임은 STALE로 표시되되 폐기되지 않는다`() = runTest {
        val claims = """{"claims":[{"section_key":"history_current","text":"현재 비교",
            "type":"fact","source_url":"https://example.com/report","source_title":"제목",
            "source_date":"2026-06-01","quote":"인용"}]}"""
        enqueue(claudeResponseBodyWithSearch(claims, listOf("https://example.com/report")))

        val outcome = dataSource().fetchHistoryCurrentGroup(config, today)

        assertEquals(1, outcome.pending.size)
        assertTrue(outcome.pending.first().stale)
    }

    // ── 그룹④ monitor — Gemini 경로 검색 제안 위젯 HTML 전달 ─────────────────

    @Test
    fun `Gemini fetchMonitorGroup은 검색 제안 위젯 HTML을 전달하고 provider는 gemini다`() = runTest {
        val claims = """{"claims":[]}"""
        enqueue(geminiResponseBodyWithGrounding(claims, urls = emptyList(), renderedContent = "<div>위젯</div>"))

        val outcome = dataSource().fetchMonitorGroup(geminiConfig, today)

        assertNull(outcome.error)
        assertTrue(outcome.pending.isEmpty())
        assertEquals("<div>위젯</div>", outcome.searchWidgetHtml)
        assertEquals("gemini", outcome.provider)
    }

    @Test
    fun `Gemini fetchCasesGroup 정상 클레임은 groundingChunks URL로 검증 통과한다`() = runTest {
        val claims = """{"claims":[{"section_key":"type1_cases","text":"사례",
            "type":"fact","source_url":"https://example.com/report","source_title":"제목",
            "source_date":"2026-07-10","quote":"인용"}]}"""
        enqueue(geminiResponseBodyWithGrounding(claims, urls = listOf("https://example.com/report")))

        val outcome = dataSource().fetchCasesGroup(geminiConfig, today)

        assertEquals(1, outcome.pending.size)
        assertTrue(outcome.rejectedCounts.isEmpty())
    }

    // ── 그룹④ monitor — pause_turn 재개 시 URL 누적 ─────────────────────────

    @Test
    fun `fetchMonitorGroup pause_turn 재개 시 이전 응답의 검색결과 URL을 누적해 이후 클레임 검증에 사용한다`() = runTest {
        val pauseBody = buildJsonObject {
            put("id", "msg_1")
            put("type", "message")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "web_search_tool_result")
                    put("tool_use_id", "srvtool_1")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "web_search_result")
                            put("url", "https://a.example.com")
                        })
                    })
                })
            })
            put("stop_reason", "pause_turn")
        }.toString()

        // 최종 응답에는 web_search_tool_result 블록이 없다 — 누적이 없으면 URL 검증이 실패해야 한다.
        val claims = """{"claims":[{"section_key":"type0_monitor","text":"항목",
            "type":"fact","source_url":"https://a.example.com","source_title":"제목",
            "source_date":"2026-07-10","quote":"인용"}]}"""
        val finalBody = claudeResponseBody(claims)

        enqueue(pauseBody)
        enqueue(finalBody)

        val outcome = dataSource().fetchMonitorGroup(config, today)

        assertEquals(2, server.requestCount)
        assertEquals(1, outcome.pending.size)
        assertTrue(outcome.rejectedCounts.isEmpty())
    }

    // ── fetchAiContextUpdates() 오케스트레이션 — 부분 실패 격리 ────────────────

    @Test
    fun `fetchAiContextUpdates는 한 그룹이 실패해도 나머지 그룹은 정상 처리된다`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return when {
                    "모니터링 체크리스트" in body -> MockResponse().setResponseCode(500)
                    "사례" in body -> MockResponse().setBody(
                        claudeResponseBodyWithSearch(
                            """{"claims":[{"section_key":"type0_cases","text":"사례","type":"fact",
                                "source_url":"https://example.com/report","source_title":"제목",
                                "source_date":"2026-07-10","quote":"인용"}]}""",
                            listOf("https://example.com/report")
                        )
                    )
                    "1980년대 일본" in body -> MockResponse().setBody(
                        claudeResponseBodyWithSearch("""{"claims":[]}""", emptyList())
                    )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val result = dataSource().fetchAiContextUpdates(config, today)

        assertTrue(result.monitor.error != null)
        assertTrue(result.monitor.pending.isEmpty())
        assertEquals(1, result.cases.pending.size)
        assertNull(result.historyCurrent.error)
        assertEquals(1, result.allPending.size)
        assertEquals(1, result.failedGroupMessages.size)
    }
}
