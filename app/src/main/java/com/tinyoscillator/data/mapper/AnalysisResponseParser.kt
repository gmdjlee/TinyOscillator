package com.tinyoscillator.data.mapper

import com.tinyoscillator.domain.model.AlgorithmInsight
import com.tinyoscillator.domain.model.StockAnalysis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * LLM JSON 출력 → StockAnalysis 파싱
 *
 * - 응답에서 첫 번째 '{' ~ 마지막 '}' 사이를 추출
 * - markdown code fence 제거
 * - 파싱 실패 시 fallback: 원본 텍스트를 summary에 넣어 반환
 */
class AnalysisResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * LLM 응답 텍스트를 StockAnalysis로 파싱
     */
    fun parse(response: String): StockAnalysis {
        if (response.isBlank()) return fallback("응답이 비어있습니다.")

        val jsonStr = extractJson(response) ?: return fallback(response)

        return try {
            val raw = json.decodeFromString<RawAnalysisResponse>(jsonStr)
            validate(raw)
        } catch (e: Exception) {
            fallback(response)
        }
    }

    /** [parse]와 동일하되 파싱 실패 시 null 반환 — 호출부가 원문 표시로 대체할 수 있게 한다. */
    fun parseOrNull(response: String): StockAnalysis? {
        if (response.isBlank()) return null
        val jsonStr = extractJson(response) ?: return null
        return try {
            val raw = json.decodeFromString<RawAnalysisResponse>(jsonStr)
            if (raw.overallAssessment.isBlank() && raw.action.isBlank()) null
            else validate(raw)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * JSON 문자열 추출
     * - markdown code fence (```json ... ```) 제거
     * - 첫 번째 '{' ~ 마지막 '}' 사이 추출
     */
    internal fun extractJson(text: String): String? {
        // markdown code fence 제거
        var cleaned = text
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')

        if (firstBrace < 0 || lastBrace <= firstBrace) return null

        return cleaned.substring(firstBrace, lastBrace + 1)
    }

    /**
     * 파싱 결과 검증 및 기본값 채움
     */
    private fun validate(raw: RawAnalysisResponse): StockAnalysis {
        return StockAnalysis(
            overallAssessment = raw.overallAssessment.ifBlank { "분석 불가" },
            confidence = raw.confidence.coerceIn(0.0, 1.0),
            insights = raw.insights.map { insight ->
                AlgorithmInsight(
                    algorithmName = insight.algorithm.ifBlank { "Unknown" },
                    interpretation = insight.interpretation.ifBlank { "-" },
                    significance = insight.significance.ifBlank { "보통" }
                )
            },
            conflicts = raw.conflicts,
            risks = raw.risks,
            action = raw.action.ifBlank { "추가 분석 필요" },
            summary = raw.overallAssessment
        )
    }

    /**
     * 파싱 실패 시 fallback — 원본 텍스트를 summary에 삽입
     */
    private fun fallback(text: String): StockAnalysis {
        return StockAnalysis(
            overallAssessment = "LLM 응답 파싱 실패",
            confidence = 0.0,
            insights = emptyList(),
            conflicts = emptyList(),
            risks = listOf("LLM 응답을 JSON으로 파싱할 수 없습니다."),
            action = "수동 확인 필요",
            summary = text.take(500)
        )
    }

    // ─── 내부 직렬화 모델 ───

    @Serializable
    internal data class RawAnalysisResponse(
        @SerialName("overall_assessment") val overallAssessment: String = "",
        val confidence: Double = 0.0,
        val insights: List<RawInsight> = emptyList(),
        val conflicts: List<String> = emptyList(),
        val risks: List<String> = emptyList(),
        val action: String = ""
    )

    @Serializable
    internal data class RawInsight(
        val algorithm: String = "",
        val interpretation: String = "",
        val significance: String = ""
    )

    companion object {
        /**
         * [RawAnalysisResponse]에 대응하는 JSON Schema — Claude tool_choice 강제 출력용.
         * 이 파서가 소비할 수 있는 형식의 단일 진실 공급원.
         */
        val STOCK_ANALYSIS_SCHEMA: JsonObject = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("overall_assessment", buildJsonObject {
                    put("type", "string")
                    put("description", "매수/매도/관망 판단 + 한 줄 근거")
                })
                put("confidence", buildJsonObject {
                    put("type", "number")
                    put("description", "판단 신뢰도 0.0~1.0")
                })
                put("insights", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("algorithm", buildJsonObject { put("type", "string") })
                            put("interpretation", buildJsonObject { put("type", "string") })
                            put("significance", buildJsonObject {
                                put("type", "string")
                                put("description", "높음|보통|낮음")
                            })
                        })
                        put("required", buildJsonArray { add("algorithm"); add("interpretation") })
                    })
                })
                put("conflicts", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                    put("description", "알고리즘 간 상충 신호")
                })
                put("risks", buildJsonObject {
                    put("type", "array")
                    put("items", buildJsonObject { put("type", "string") })
                })
                put("action", buildJsonObject {
                    put("type", "string")
                    put("description", "구체적 행동 권고")
                })
            })
            put("required", buildJsonArray {
                add("overall_assessment"); add("confidence"); add("action")
            })
        }
    }
}
