# TinyOscillator — 주도주 붕괴 판단 계기판 (프로젝트 규칙)

이 파일은 모든 세션에 상시 로드된다. 아래 규칙은 매 프롬프트에 반복하지 않아도 항상 적용된다.

## SSOT (불변)
- **임계치**는 `bear_thresholds.json`이 유일 출처. 코드에 하드코딩 금지, 값 변경 금지.
- **명세**는 `TASK_bear_signal_console.md`(v1.2)가 유일 출처. 다른 스펙 파일 참조 금지(구버전은 `archive/`).
- **스코어링 동작**은 React `bear_signal_dashboard.jsx`와 등가 유지. 의미론 변경 금지.
- 리포트(신영 2026.6.30) 외 지표(VIX·수익률곡선 등) 도입 금지.

## 아키텍처 (준수)
- Clean Architecture: `domain` / `data` / `presentation`.
- Hilt DI · Jetpack Compose(Material 3) · MVVM + StateFlow · Room.
- 데이터소스 재사용: `kotlin_krx`, BOK ECOS. 신규 외부 소스는 Retrofit 원격 소스로 추가.
- `domain`은 안드로이드 무의존(순수 Kotlin). **스코어링 엔진·임계치 데이터클래스는 프레임워크 무의존** — JVM 단위테스트가 안드로이드 없이 돌아야 한다.
- v1은 100% Kotlin 네이티브. Python/Chaquopy 불요.

## 테스트 게이트 (Phase마다 필수 통과)
- **골든**: 2026.6.30 기준값 → `phase == AMBER`.
- **경계**: `neg=6/7` · `rate=4.49/4.5` · `ratio=0.94/0.95/1.0` · `loss=44/45/59/60/79/80`.
- 자동 수집(±3σ 카운트 등)은 결정적 샘플 기반 테스트 동반.

## 진행 규율
- **Phase 경계에서 STOP** → 사용자 승인 후 다음 Phase.
- 각 Phase 완료 시 `PROGRESS.md` 마커 갱신(P0 → P1 → … → P3.5 → …). 전부 완료 시 `LOOP_COMPLETE`.
- 승인: `Phase N approved. Continue.` / 수정: `Phase N needs changes: [내용]. Revise and present again.`

## 서브에이전트 호출 (상기)
- 서브에이전트는 대화 맥락을 못 본다. 호출 프롬프트에 **TASK 파일 경로 · 대상 Phase(§) · 위 제약**을 명시.
- 구현은 `kotlin-implementer`, 수용 검증은 `qa-verifier`.
