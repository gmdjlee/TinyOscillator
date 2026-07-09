package com.tinyoscillator.feature.bearsignal.domain.repository

/**
 * BearSignal 데이터 계층 인터페이스 — Phase 0 스캐폴딩 마커.
 *
 * Phase 1+에서 하이브리드 데이터 아키텍처(자동 수집 ⊕ 수동 오버라이드, §1.2)를 채운다:
 * - `fun observeInputs(): Flow<BearSignalInputs>` — auto ⊕ manual 병합 스트림
 * - `suspend fun refreshAutoInputs()` — [A][B] 등급 자동 수집
 * - `suspend fun updateManualInput(...)` — [C][D] 등급 수동 반영 (source/updatedAt 부착)
 * - `suspend fun resetToReportBaseline()` — 리포트 기준값 리셋
 */
interface BearSignalRepository
