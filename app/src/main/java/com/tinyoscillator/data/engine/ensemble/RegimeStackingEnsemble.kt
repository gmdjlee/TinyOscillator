package com.tinyoscillator.data.engine.ensemble

import com.tinyoscillator.domain.model.MetaLearnerState
import timber.log.Timber

/**
 * 레짐별 메타 학습기 — 시장 레짐에 따라 별도 로지스틱 회귀 학습.
 *
 * 각 레짐에 대해 별도의 StackingEnsemble을 유지.
 * 레짐 내 샘플이 MIN_SAMPLES(60) 미만이면 글로벌 메타 학습기로 폴백.
 */
class RegimeStackingEnsemble(
    private val baseModelNames: List<String>,
    private val metaC: Double = 0.5
) : StackingEnsemble(baseModelNames, metaC) {

    /** 레짐별 메타 학습기 (regimeId → StackingEnsemble) */
    private val regimeModels = mutableMapOf<String, StackingEnsemble>()

    /**
     * 특정 레짐의 데이터로 레짐 전용 메타 학습기 학습.
     *
     * @param regimeId 레짐 이름 (e.g., "BULL_LOW_VOL")
     * @param signals [n_samples][n_algos] — 해당 레짐 구간의 신호
     * @param labels [n_samples] — 실제 결과
     */
    fun fitRegime(regimeId: String, signals: Array<DoubleArray>, labels: IntArray) {
        if (signals.size < MIN_SAMPLES) {
            Timber.d("레짐 '%s' 샘플 부족 (%d < %d) — 글로벌 폴백 사용",
                regimeId, signals.size, MIN_SAMPLES)
            regimeModels.remove(regimeId)
            return
        }

        val model = StackingEnsemble(baseModelNames, metaC)
        model.fit(signals, labels)
        regimeModels[regimeId] = model
        Timber.i("레짐 '%s' 메타 학습기 학습 완료: n=%d", regimeId, signals.size)
    }

    /**
     * 레짐 인식 예측.
     *
     * @param currentSignals {algo_name: calibrated_prob}
     * @param regimeId 현재 시장 레짐
     * @return 확률 [0, 1]
     */
    fun predictProba(currentSignals: Map<String, Double>, regimeId: String?): Double {
        // 레짐별 모델이 있으면 사용
        if (regimeId != null) {
            regimeModels[regimeId]?.let { model ->
                return model.predictProba(currentSignals)
            }
        }

        // 글로벌 폴백
        return super.predictProba(currentSignals)
    }

    /** 레짐별 상태 저장 */
    fun saveRegimeStates(): Map<String, MetaLearnerState> {
        return regimeModels.mapValues { it.value.saveState() }
    }

    /** 레짐별 상태 복원 */
    fun loadRegimeStates(states: Map<String, MetaLearnerState>) {
        regimeModels.clear()
        for ((regimeId, state) in states) {
            val model = StackingEnsemble(baseModelNames, metaC)
            model.loadState(state)
            if (model.isFitted) {
                regimeModels[regimeId] = model
            }
        }
        Timber.d("레짐별 메타 학습기 복원: %d개 레짐", regimeModels.size)
    }

    /** 학습된 레짐 모델 목록 */
    fun fittedRegimes(): Set<String> = regimeModels.keys.toSet()

    /** 특정 레짐의 학습 여부 */
    fun isRegimeFitted(regimeId: String): Boolean = regimeModels[regimeId]?.isFitted == true
}
