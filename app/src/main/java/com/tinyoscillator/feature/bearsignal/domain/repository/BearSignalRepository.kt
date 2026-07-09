package com.tinyoscillator.feature.bearsignal.domain.repository

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualFieldUpdate
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * BearSignal 데이터 계층 인터페이스 — 하이브리드 데이터 아키텍처(자동 수집 ⊕ 수동 오버라이드, §1.2).
 *
 * Phase 1: [A] 등급 자동 지표(신호2 통계 + 코스피 2사 비중) 수집·Room 캐시.
 * Phase 2: [B] 등급 자동 지표 — 관세청 수출비중([refreshExternalAutoInputs]의 semi/buffer), FRED/ECOS
 * 금리(rate/dir), IPO ETF 방향(etf), 해외 19개 지수([refreshMarketReturns]).
 * Phase 3: [C]/[D] 등급 수동 오버라이드([updateManualInput]) + 리포트 기준값 리셋([resetToReportBaseline]).
 * 실제 병합(MANUAL 우선)은 이 인터페이스가 아니라 순수 함수
 * [com.tinyoscillator.feature.bearsignal.domain.usecase.MergeBearSignalInputsUseCase]가 담당한다.
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

    /**
     * [B] 등급 스칼라 자동 지표(semi/buffer/rate/dir/etf)를 관세청·FRED·ECOS·Stooq에서 수집한다.
     *
     * 소스가 서로 독립적인 4개 외부 API이므로 지표별 best-effort로 동작한다 — 개별 지표 수집이
     * 실패해도 나머지 지표·[refreshAutoInputs]가 채운 [A] 등급 스칼라는 영향받지 않으며, 실패한
     * 지표는 이전 캐시값을 그대로 유지한다. [A] 등급 스칼라(up3~kospi2)를 담은 캐시가 아예 없으면
     * (즉 Phase 1 자동 수집이 한 번도 성공한 적 없으면) [Result.failure]를 반환한다.
     */
    suspend fun refreshExternalAutoInputs(): Result<AutoBearSignalInputs>

    /** Room 캐시 국가별 지수 수익률(도표48) 스트림 (오프라인 우선 — 캐시가 없으면 null) */
    fun observeMarketReturns(): Flow<MarketReturnsSnapshot?>

    /** Room 캐시 국가별 지수 수익률 1회 조회 */
    suspend fun getCachedMarketReturns(): MarketReturnsSnapshot?

    /**
     * 코스피(kotlin_krx) + 해외지수([com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexRegistry]
     * 커버 대상, Stooq)의 4기간(−12M/−6M/−3M/−1M) 누적수익률을 수집해 Room 캐시에 저장한다.
     *
     * 커버 불가 지수는 [com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage.MANUAL_REQUIRED]로
     * 표시되며 r 값은 전부 null이다(§1.1 각주1 "미커버 지수는 수동 입력 폴백"). 코스피 수집이
     * 실패하면 기존 캐시로 폴백(캐시조차 없으면 실패), 해외지수는 지수별 best-effort.
     */
    suspend fun refreshMarketReturns(): Result<MarketReturnsSnapshot>

    // ── Phase 3: 수동 오버라이드([C]/[D] 등급) ──────────────────────────

    /** Room 캐시 수동 오버라이드 스트림 — 미설정 필드는 각 항목이 null인 [ManualBearSignalInputs]로 표현(컨테이너 자체는 null 아님) */
    fun observeManualInputs(): Flow<ManualBearSignalInputs>

    /** Room 캐시 수동 오버라이드 1회 조회 */
    suspend fun getManualInputs(): ManualBearSignalInputs

    /** 스칼라/국가별 수익률 수동 오버라이드 하나를 갱신한다 — `updatedAt=now`를 부착해 즉시 Room에 반영 */
    suspend fun updateManualInput(update: ManualFieldUpdate)

    /** Room 캐시 국가별 지수 수익률 수동 오버라이드 스트림(§4 "미커버 해외지수" 폴백, §5.3 인라인 편집) */
    fun observeManualMarketReturns(): Flow<List<ManualMarketReturn>>

    /** Room 캐시 국가별 지수 수익률 수동 오버라이드 1회 조회 */
    suspend fun getManualMarketReturns(): List<ManualMarketReturn>

    /**
     * 리포트 기준값(부록 C, 2026.6.30)으로 리셋 — 수동 오버라이드 전체(스칼라 + 국가별 수익률)를
     * 삭제한다. [A]/[B] 등급 자동 수집 캐시는 유지한다(근거는
     * [com.tinyoscillator.feature.bearsignal.domain.usecase.ResetToReportBaselineUseCase] KDoc 참고).
     */
    suspend fun resetToReportBaseline()
}
