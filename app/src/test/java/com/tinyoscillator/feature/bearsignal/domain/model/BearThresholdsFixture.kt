package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * `bear_thresholds.json`(v1.2) 값을 리터럴로 미러링한 테스트 전용 fixture
 * (TASK_bear_signal_console.md §3.0 retrofit).
 *
 * 안드로이드 무의존 스코어링 테스트가 실제 assets I/O 없이 [BearThresholds]/
 * [com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase]를 구성할 수
 * 있도록 값을 그대로 복제한다. 값은 리포지토리 루트 `bear_thresholds.json` (= 앱 사본
 * `app/src/main/assets/bear_thresholds.json`)과 항상 동일하게 유지해야 한다 — 리포트 개정으로
 * JSON 값이 바뀌면 이 fixture와 골든 테스트도 함께 갱신한다(§8 "임계치 임의 변경" 대응).
 */
object BearThresholdsFixture {
    val DEFAULT: BearThresholds = BearThresholds(
        version = "1.2",
        basis = "신영증권 「주도주의 물리학」 2026.6.30",
        s1 = BearThresholds.S1(manyCountries = 7, deepPct = -12.0, deepeningPct = -6.0),
        s2 = BearThresholds.S2(redLine = 1.0, warnLine = 0.95, watchLine = 0.7),
        s3 = BearThresholds.S3(loss1 = 45.0, loss2 = 60.0, loss3 = 80.0),
        gate = BearThresholds.Gate(critical = 4.5, approach = 4.0, creditWarn = 35.0),
        amp = BearThresholds.Amp(
            semiExport = 20.0,
            kospi2 = 50.0,
            wSemi = 0.15,
            wKospi2 = 0.15,
            wNoBuffer = 0.20,
            cap = 1.6
        ),
        phase = BearThresholds.PhaseCfg(leadOrange = 6, leadAmber = 3)
    )
}
