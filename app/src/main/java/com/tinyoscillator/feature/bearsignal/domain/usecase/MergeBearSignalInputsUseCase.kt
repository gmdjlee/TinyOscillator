package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturns
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot

/**
 * 자동(AUTO) ⊕ 수동(MANUAL) 입력 병합 (TASK.md §1.2 하이브리드 데이터 아키텍처, Phase 3).
 *
 * 필드별 우선순위: **MANUAL > AUTO > 리포트 기준값(부록 C, [BearSignalReportBaseline])**. 두 소스
 * 모두 없는 필드는 2026.6.30 리포트 스냅샷으로 프리시드된다 — 신규 설치·전체 리셋 직후에도
 * §3 스코어링이 항상 유효한 입력을 받도록 보장한다(골든 케이스 재현 가능).
 *
 * §3 스코어링 SSOT([ComputeBearSignalUseCase])는 건드리지 않는다 — 이 UseCase는 스코어링 "이전"
 * 단계에서 [BearSignalInputs]를 조립하는 순수 함수다. 안드로이드/IO 의존성 0(JVM 단위테스트 대상).
 *
 * [ManualBearSignalInputs.issueRatio](신주 비중)는 §3 스코어링 파라미터가 아니므로 조립 대상에서
 * 제외한다(모니터링 전용, §1.1 각주3).
 */
class MergeBearSignalInputsUseCase {

    operator fun invoke(
        auto: AutoBearSignalInputs?,
        manual: ManualBearSignalInputs?,
        marketsSnapshot: MarketReturnsSnapshot?,
        manualMarkets: List<ManualMarketReturn> = emptyList(),
        periodIdx: Int = BearSignalReportBaseline.PERIOD_IDX
    ): BearSignalInputs {
        val baseline = BearSignalReportBaseline
        return BearSignalInputs(
            markets = mergeMarkets(marketsSnapshot, manualMarkets),
            periodIdx = periodIdx,
            up = auto?.up3?.value ?: baseline.UP,
            down = auto?.down3?.value ?: baseline.DOWN,
            deepening = baseline.DEEPENING,
            loss = manual?.loss?.value ?: baseline.LOSS,
            etf = auto?.etf?.value ?: baseline.ETF,
            big = manual?.big?.value ?: baseline.BIG,
            rate = auto?.rate?.value ?: baseline.RATE,
            dir = manual?.dir?.value ?: auto?.dir?.value ?: baseline.DIR,
            credit = manual?.credit?.value ?: baseline.CREDIT,
            margin = manual?.margin?.value ?: baseline.MARGIN,
            semi = auto?.semi?.value ?: baseline.SEMI,
            kospi2 = auto?.kospi2?.value ?: baseline.KOSPI2,
            buffer = auto?.buffer?.value ?: baseline.BUFFER
        )
    }

    companion object {

        /**
         * 국가별 지수 수익률(도표48) 병합 — 지수별로, 그리고 지수 내에서도 기간별로
         * MANUAL > AUTO > 리포트 기준값 우선순위를 적용한다.
         *
         * 리포트 기준값(20지수) 이름 순서를 기준으로 하되, AUTO/MANUAL에만 존재하는 지수명이
         * 있으면 뒤에 추가한다(정상 운영에서는 발생하지 않음 — 방어적 처리).
         */
        internal fun mergeMarkets(
            snapshot: MarketReturnsSnapshot?,
            manualMarkets: List<ManualMarketReturn>
        ): List<MarketReturns> {
            val baselineByName = BearSignalReportBaseline.MARKETS.associateBy { it.name }
            val autoByName = (snapshot?.markets ?: emptyList()).associateBy { it.name }
            val manualByName = manualMarkets.associateBy { it.name }

            val allNames = LinkedHashSet<String>().apply {
                addAll(BearSignalReportBaseline.MARKETS.map { it.name })
                addAll(autoByName.keys)
                addAll(manualByName.keys)
            }

            return allNames.map { name ->
                val baseline = baselineByName[name]
                val auto = autoByName[name]
                val manual = manualByName[name]
                val lead = auto?.lead ?: baseline?.lead ?: false
                val r = (0..3).map { idx ->
                    manual?.r?.getOrNull(idx) ?: auto?.r?.getOrNull(idx) ?: baseline?.r?.getOrNull(idx)
                }
                MarketReturns(name = name, r = r, lead = lead)
            }
        }
    }
}
