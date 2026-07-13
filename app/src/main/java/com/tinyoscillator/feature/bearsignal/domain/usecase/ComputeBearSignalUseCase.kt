package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalResult
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholds
import com.tinyoscillator.feature.bearsignal.domain.model.Depth
import com.tinyoscillator.feature.bearsignal.domain.model.MarketAnalysis
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturns

/**
 * 약세장 전환 신호 종합 판정 UseCase (TASK_bear_signal_console.md §3 · 부록 A).
 *
 * 스코어링 로직·비교 연산자·반올림은 프로토타입 bear_signal_dashboard.jsx와 1:1 — **임의 변경 금지 (SSOT)**.
 * 숫자 임계치는 [BearThresholds] 생성자 주입(§3.0 v1.2 임계치 외부화) — `bear_thresholds.json`이 값의
 * 단일 진실 공급원이며, 리포트 개정 시 그 파일만 교체하면 코드 무수정으로 판정이 바뀐다.
 *
 * 다음 구조 상수는 로직 구조 자체이므로 주입 대상이 아니다(§3.0 제약):
 * - `leadPct` 산출의 분모 `9.0`(선행 신호 합의 이론적 최댓값 s1+s2+s3=3+3+3)
 * - `scoreS2`의 `up==0` 폴백 값 `9.0`(0으로 나눔 방지 sentinel)
 * - 레벨 값 0~3 자체(안전/주의/경고/위험 4단계는 로직의 형태)
 *
 * 순수 산술만 수행하며 안드로이드/IO 의존성 0 (JVM 단위테스트 대상).
 *
 * @param t 스코어링 임계치(§3.0 `BearThresholds`).
 */
class ComputeBearSignalUseCase(private val t: BearThresholds) {

    /** §3.6 종합 국면 판정 [상태 기계] */
    operator fun invoke(inputs: BearSignalInputs): BearSignalResult {
        val ma = analyzeMarkets(inputs.markets, inputs.periodIdx)
        val s1 = scoreS1(ma.neg, ma.depth)
        val s2 = scoreS2(inputs.up, inputs.down, inputs.deepening)
        val s3 = scoreS3(inputs.loss, inputs.etf, inputs.big)
        val gate = scoreGate(inputs.rate, inputs.dir, inputs.credit, inputs.margin)
        val amp = amplifier(inputs.semi, inputs.kospi2, inputs.buffer)
        val lead = s1 + s2 + s3
        val warn = listOf(s1, s2, s3).count { it >= 2 }
        val phase = when {
            gate >= 3 && warn >= 1 -> BearPhase.RED // 긴축 돌입 + 선행 경고 = 톱니바퀴 격발
            gate >= 2 || (lead >= t.phase.leadOrange && gate >= 1) -> BearPhase.ORANGE // 방아쇠 임박
            lead >= t.phase.leadAmber || gate >= 1 -> BearPhase.AMBER // 신호 점등 · 방아쇠 대기
            else -> BearPhase.GREEN // 안정
        }
        return BearSignalResult(
            s1 = s1,
            s2 = s2,
            s3 = s3,
            gate = gate,
            amp = amp,
            lead = lead,
            leadPct = Math.round(lead / 9.0 * 100).toInt(),
            warn = warn,
            phase = phase,
            ma = ma
        )
    }

    /**
     * §3.1 신호1 입력 분석 — 주변부 압착 (도표 46~48).
     *
     * worstNew는 12M 수익률 > 0 이고 해당 기간 < 0 인 지수만 집계(신규 이탈).
     * 만성 약세국(12M도 마이너스)은 neg에는 포함되나 낙폭 심도에서 제외.
     */
    fun analyzeMarkets(markets: List<MarketReturns>, periodIdx: Int): MarketAnalysis {
        var neg = 0
        var worstNew = 0.0
        markets.forEach { m ->
            val v = m.r.getOrNull(periodIdx)
            val v12 = m.r.getOrNull(0)
            if (v != null && v < 0) {
                neg++
                if (v12 != null && v12 > 0) worstNew = minOf(worstNew, v)
            }
        }
        val depth = when {
            worstNew <= t.s1.deepPct -> Depth.DEEP
            worstNew <= t.s1.deepeningPct -> Depth.DEEPENING
            else -> Depth.SHALLOW
        }
        return MarketAnalysis(neg, worstNew, depth)
    }

    /** §3.1 신호1 스코어 — 이탈 수·낙폭이 동시에 확대될 때만 위험 상향 */
    fun scoreS1(neg: Int, depth: Depth): Int {
        val many = neg >= t.s1.manyCountries // 닷컴 정점 직전 = 7개국
        if (!many) return if (depth == Depth.DEEP) 1 else 0
        return when (depth) {
            Depth.SHALLOW -> 1
            Depth.DEEPENING -> 2
            Depth.DEEP -> 3
        }
    }

    /** §3.2 신호2 — 변동성 무게중심 (up=±3σ 초과 상승일 수, down=하락일 수) */
    fun scoreS2(up: Int, down: Int, deepening: Boolean): Int {
        val r = if (up == 0) 9.0 else down.toDouble() / up
        return when {
            r > t.s2.redLine -> 3 // 큰 하락일이 큰 상승일 추월 = 천장
            r >= t.s2.warnLine -> 2
            r >= t.s2.watchLine -> if (deepening) 1 else 0
            else -> 0
        }
    }

    /** §3.3 신호3 — IPO 질 (loss=적자상장비중%, etf∈{up,flat,down}, big∈{smooth,pending,failed}) */
    fun scoreS3(loss: Double, etf: String, big: String): Int {
        var lv = when {
            loss >= t.s3.loss3 -> 3
            loss >= t.s3.loss2 -> 2
            loss >= t.s3.loss1 -> 1 // 평상 20~40, 버블 ~80
            else -> 0
        }
        if (etf == "down") lv = maxOf(lv, 2)
        lv = if (big == "failed") maxOf(lv, 3) else if (big == "pending") maxOf(lv, 1) else lv
        return lv
    }

    /** §3.4 신호4 — 금리 방아쇠 [GATE] (rate=기준금리상단%, dir∈{ease,hold,hike}, credit=신용잔고 조원) */
    fun scoreGate(rate: Double, dir: String, credit: Double, margin: Boolean): Int {
        var lv = when {
            rate >= t.gate.critical -> 3 // 임계 4.5% = 진짜 긴축
            rate >= t.gate.approach -> 2
            else -> if (dir == "hike") 1 else 0
        }
        lv = if (margin) maxOf(lv, 2) else if (credit >= t.gate.creditWarn) maxOf(lv, 1) else lv // 2023말 17.5조 → 2배 이상
        return lv
    }

    /** §3.5 증폭·집중 [AMP] — semi=반도체수출비중%, kospi2=삼성+SK 코스피비중%, buffer=완충산업건재 */
    fun amplifier(semi: Double, kospi2: Double, buffer: Boolean): Double {
        val a = 1.0 +
            (if (semi >= t.amp.semiExport) t.amp.wSemi else 0.0) +
            (if (kospi2 >= t.amp.kospi2) t.amp.wKospi2 else 0.0) +
            (if (!buffer) t.amp.wNoBuffer else 0.0)
        return minOf(a, t.amp.cap)
    }
}
