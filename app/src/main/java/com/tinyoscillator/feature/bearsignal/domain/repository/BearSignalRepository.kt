package com.tinyoscillator.feature.bearsignal.domain.repository

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import kotlinx.coroutines.flow.Flow

/**
 * BearSignal 데이터 계층 인터페이스 — 하이브리드 데이터 아키텍처(자동 수집 ⊕ 수동 오버라이드, §1.2).
 *
 * Phase 1: [A] 등급 자동 지표(신호2 통계 + 코스피 2사 비중) 수집·Room 캐시.
 * Phase 2+에서 확장 예정:
 * - `fun observeInputs(): Flow<BearSignalInputs>` — auto ⊕ manual 병합 스트림
 * - `suspend fun updateManualInput(...)` — [C][D] 등급 수동 반영 (source/updatedAt 부착)
 * - `suspend fun resetToReportBaseline()` — 리포트 기준값 리셋
 */
interface BearSignalRepository {

    /** Room 캐시 자동 수집값 스트림 (오프라인 우선 — 캐시가 없으면 null) */
    fun observeAutoInputs(): Flow<AutoBearSignalInputs?>

    /** Room 캐시 자동 수집값 1회 조회 */
    suspend fun getCachedAutoInputs(): AutoBearSignalInputs?

    /**
     * [A] 등급 자동 지표를 KRX에서 수집해 계산 후 Room 캐시에 저장한다.
     *
     * 수집 실패 시(네트워크·로그인·데이터 부족 등) 기존 캐시가 있으면 그 값을 [Result.success]로
     * 반환(오프라인 우선 폴백), 캐시조차 없으면 [Result.failure]를 반환한다.
     */
    suspend fun refreshAutoInputs(): Result<AutoBearSignalInputs>
}
