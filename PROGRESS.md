# PROGRESS — BearSignal 이식 진행 기록

> 근거: `TASK.md` (「주도주 붕괴 판단 계기판」 이식 명세서 v1.0). 각 Phase 완료 시 `PROGRESS:` 마커 갱신.

PROGRESS: P0 — 완료 (스캐폴딩·도메인 모델·순수 스코어링·JVM 테스트 41건, 2026-07-09; 시드 데이터 정정 2026-07-09 점검)

## P0 상세
- `feature/bearsignal/domain/model/BearSignalModels.kt` — SignalLevel·GateState·BearPhase·Depth·InputSource·MarketReturns·MarketAnalysis·BearSignalInputs·BearSignalResult + 플레이스홀더(BearType·MonitorItem)
- `feature/bearsignal/domain/model/BearSignalReportBaseline.kt` — 2026.6.30 리포트 기준값 스칼라 + 도표48 전체 20지수 시드(`MARKETS`, 프로토타입 jsx `MARKETS` 그대로 이관)
- `feature/bearsignal/domain/repository/BearSignalRepository.kt` — Phase 1+ 확장용 마커 인터페이스
- `feature/bearsignal/domain/usecase/ComputeBearSignalUseCase.kt` — §3/부록 A 1:1 순수 스코어링(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite), 안드로이드 의존성 0
- 테스트: `ComputeBearSignalUseCaseTest.kt` 41건 전부 통과 (골든 케이스 2026.6.30 → AMBER 재현, 도표48 실데이터 20지수 사용 + 전 임계 경계)

## 점검 이력 (2026-07-09)
- kotlin-implementer 셀프리뷰(qa 점검) 결과: 스코어링 5개 함수(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite) 전부 프로토타입 `bear_signal_dashboard.jsx`(작업 디렉터리 루트에서 재확보, git 미추적)와 문자 단위 일치 확인.
- **수정**: "도표48 전체 시드 미이관(18행 결손)" 편차 해소 — 확보된 `bear_signal_dashboard.jsx`의 `MARKETS` 상수(20지수) 전체를 `BearSignalReportBaseline.MARKETS`로 이관. 골든 케이스 테스트를 합성 픽스처 대신 실데이터로 교체(neg=11, worstNew=-5.1(나스닥), depth=SHALLOW → s1=1 재검증). 시드 검증 테스트 확장(20행 카운트 + 6개 지수 스팟체크).
- 재실행: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.*"` → 41 tests, 0 failures.
