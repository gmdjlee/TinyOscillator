package com.tinyoscillator.feature.bearsignal.data.local

import android.content.Context
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholds
import kotlinx.serialization.json.Json

/**
 * `assets/bear_thresholds.json` 로드 (TASK_bear_signal_console.md §3.0 v1.2 임계치 외부화).
 *
 * 리포지토리 루트 `bear_thresholds.json`(SSOT)의 사본을 `app/src/main/assets/bear_thresholds.json`에
 * 배치해 앱 기동 시(Hilt `@Singleton`) 1회 로드한다. 리포트 개정 시 이 파일만 교체하면 코드 무수정으로
 * [ComputeBearSignalUseCase][com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase]의
 * 판정이 바뀐다(§7 "config 구동" 수용 기준).
 *
 * 디코딩 로직은 [decode]로 분리해 `Context` 없이 JVM 단위테스트가 가능하다.
 */
class ThresholdsProvider(
    private val context: Context,
    private val json: Json = defaultJson
) {

    /** assets에서 [ASSET_PATH]를 읽어 [BearThresholds]로 디코딩한다. */
    fun load(): BearThresholds = decode(readAssetText(), json)

    private fun readAssetText(): String =
        context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }

    companion object {
        const val ASSET_PATH = "bear_thresholds.json"

        private val defaultJson = Json { ignoreUnknownKeys = true }

        /**
         * 문자열 → [BearThresholds] 순수 디코딩 함수 (Context 비의존, JVM 단위테스트 대상).
         *
         * `bear_thresholds.json`은 `note` 등 임계치 스코어링과 무관한 문서용 필드를 포함하므로
         * `ignoreUnknownKeys = true`(기존 앱 전역 Json 파싱 관례, [com.tinyoscillator.core.api.KiwoomApiClient.createDefaultJson] 등과 동일)로 흡수한다.
         */
        fun decode(content: String, json: Json = defaultJson): BearThresholds =
            json.decodeFromString(BearThresholds.serializer(), content)
    }
}
