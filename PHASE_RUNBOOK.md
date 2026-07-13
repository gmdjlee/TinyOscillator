# PHASE RUNBOOK — 주도주 붕괴 판단 계기판 (Phase 0 → 5)

> 대상 명세: `TASK_bear_signal_console.md` (v1.2) · 상시 규칙: `CLAUDE.md` · 임계치: `bear_thresholds.json`
> 원칙: 서브에이전트는 이 대화·이전 작업 맥락을 못 본다. 각 프롬프트는 **에이전트가 스스로 명세를 읽게** 하고 전제·범위(§)·제약·완료 마커를 자족적으로 담는다. `CLAUDE.md`가 프로젝트 메모리로 상시 로드되므로 SSOT·아키텍처·테스트 게이트는 프롬프트에서 축약하고 Phase 고유 범위에 집중한다.

## 사용법
1. **순서 준수** — Phase N은 N-1 완료(PROGRESS 마커 + 빌드 그린)를 전제로 한다.
2. **각 Phase 실행 → 검증 게이트 → 다음 Phase** 순으로 진행.
3. 디스크에서 에이전트 파일을 직접 수정했으면 **세션 재시작**(파일 기반 에이전트는 세션 시작 시 로드).
4. ralph-loop 사용 시 `SubagentStop` 훅이 검증 게이트 문구를 STDOUT으로 출력 → HITL 승인 후 다음 프롬프트 투입.
5. 인터랙티브 권장: `claude --model opusplan`.

## 보고 형식 (각 Phase 완료 시 공통)
```
- 변경/추가 파일: 경로 목록
- 테스트: 골든/경계/신규 pass 수, 실패 항목
- 빌드: 그린/레드
- PROGRESS: 갱신한 마커
- 미해결 이슈 · 다음 단계
```

## PROGRESS.md 마커 순서
```
P0 → P1 → P2 → P3 → P3.5-1 → P3.5 → P4 → P5-1 → LOOP_COMPLETE
```

## 재사용 검증 게이트 (모든 Phase 공통)
```
확인: 빌드/테스트 그린 · PROGRESS 마커 갱신 · §3 임계치/§4.6 스키마 무변경.
승인: "Phase N approved. Continue with Phase M."
수정: "Phase N needs changes: [내용]. Revise and present again."
```

---

## Phase 0 — 스캐폴딩 · 도메인 모델 · 순수 스코어링 + JVM 테스트

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 0을 구현해줘.

전제: 없음(최초 Phase).
범위(§3 스코어링·도메인 모델, §3.0 임계치, §3.8 유형 축):
  1) feature 패키지 스캐폴딩(domain/data/presentation) — 안드로이드 의존 없는 domain 확립.
  2) 도메인 모델·열거형: BearInputs, MarketReturn, CharacterAxes, SignalScores, Phase,
     Depth, IpoEtfTrend, BigDeal, RateDirection, Period 등.
  3) BearThresholds 데이터클래스(§3.0) — bear_thresholds.json과 1:1. 값은 코드 상수 금지,
     주입으로 수령. (로더는 data 계층에 두되 Phase 0에선 테스트용 리터럴/테스트 JSON 사용.)
  4) SignalScoring(scoreS1..scoreGate, amplifier, analyzeMarkets, composite) +
     TypePriorityEngine — 전부 순수 함수. 프레임워크 무의존.
제약:
  - 스코어링 로직·경계는 React bear_signal_dashboard.jsx와 동작 등가(SSOT). 의미론 변경 금지.
  - 임계치는 BearThresholds 주입으로만 참조.
요구(하드 게이트):
  - 골든 테스트: 2026.6.30 기준값(부록 C 시드) → phase == AMBER, 개별 스코어(s1/s2/s3/gate/amp/leadPct) 대조.
  - 경계 테스트: neg=6/7 · rate=4.49/4.5 · ratio=0.94/0.95/1.0 · loss=44/45/59/60/79/80.
  - 전부 안드로이드 없이 도는 JVM 단위테스트. 빌드/테스트 그린(Bash).
완료 시 PROGRESS: P0 갱신. 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 0 approved. Continue with Phase 1."

---

## Phase 1 — [A] 완전 자동 연동 (kotlin_krx)

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 1을 구현해줘.

전제: Phase 0 완료(PROGRESS: P0) — 순수 스코어링·도메인 모델·단위테스트 존재.
범위(§1 등급 [A], §3.2, §3.5): 기존 kotlin_krx 데이터소스를 재사용해 두 자동 지표를 수집·산출.
  1) 신호2 변동성 무게중심(§3.2) — 코스피 지수 일별 종가(직전 6개월)로 μ·σ 산출 후
     ±3σ/±4σ 초과 상승일·하락일 수를 Kotlin으로 계산(up/down 생성). σ 기준창 정의는
     명세 도표50 규정을 따르고, 모호하면 스냅샷 field_meta에 기준창을 노출.
  2) 코스피 2사 비중(§3.5) — (삼성전자+SK하이닉스 시총)/(KOSPI 총시총) → kospi2.
제약:
  - 기존 패턴 유지: domain(repository/usecase) ↔ data(remote 재사용·Room 캐시·mapper),
    결과는 스코어링 UseCase로 주입, Hilt 모듈 바인딩.
  - 각 자동값에 source=AUTO(§4.6 ValueSource)·as_of 부착. 수집 실패 시 Room 캐시 폴백.
요구:
  - ±3σ/±4σ 카운트에 결정적 샘플 기반 JVM 단위테스트 추가·통과.
  - 빌드/테스트 그린(Bash).
완료 시 PROGRESS: P1 갱신. 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 1 approved. Continue with Phase 2."

---

## Phase 2 — [B] 자동 연동 (외부 무료 API)

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 2를 구현해줘.

전제: Phase 1 완료(PROGRESS: P1).
범위(§1 등급 [B], §3.3/§3.4/§3.5): Retrofit 원격 소스를 추가해 아래 자동 지표를 수집.
  1) 수출 비중·완충 산업(§3.5) — 관세청 무역통계 Open API(월). HS→15대 품목(MTI) 매핑으로
     반도체 비중(semi)·완충 산업(자동차/일반기계/석유) 건재 여부(buffer) 산출. K-stat 대체 허용.
  2) 금리(§3.4) — 한은 기준금리는 기존 ECOS 재사용, 미 연준 상단은 FRED(DFEDTARU) 추가 → rate.
  3) IPO ETF 방향(§3.3) — Renaissance IPO ETF(티커 IPO) 가격으로 up/flat/down 판정 → etf.
제약:
  - Retrofit 원격 소스 + Room 캐시 + mapper, Hilt 바인딩. domain UseCase로 주입.
  - 각 값 source=AUTO·as_of·origin(예: FRED:DFEDTARU) 부착. 실패 시 캐시 폴백·수동 대체 경로.
  - API 키/엔드포인트는 기존 설정 패턴 준수.
요구:
  - HS→MTI 매핑·방향 판정 로직 단위테스트. 원격 소스는 캔드 응답 테스트.
  - 빌드/테스트 그린(Bash).
완료 시 PROGRESS: P2 갱신. 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 2 approved. Continue with Phase 3."

---

## Phase 3 — 수동/반자동 입력 계층 + 핵심 프레젠테이션 화면

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 3을 구현해줘.

전제: Phase 2 완료(PROGRESS: P2) — 자동 지표([A][B]) 수집 존재.
범위(§1 등급 [C]/[D], §4.6 스냅샷 계약, §3.8): 나머지 입력을 수동/반자동으로 채우고
             자동+수동을 병합해 첫 완전 동작 콘솔 화면을 구성.
  A. 입력 계층
     1) 신용잔고([C]) — 반자동(가능 시 소스, 아니면 수동). marginCall·dir·bigDeal·신호1 국가표
        (도표48, 20지수×4기간)는 수동 입력([D]).
     2) BuildSnapshotUseCase — 자동값(AUTO)+수동값(MANUAL)+기준값(BASELINE)을 §4.6 스키마로
        병합, FieldSource 부여, 우선순위(MANUAL 〉 SNAPSHOT 〉 AUTO 〉 BASELINE) 해소.
  B. 핵심 화면 (React 파리티 — 부록 B)
     3) VerdictBand(신호등+선행점수 바+방아쇠/증폭/경고), PhaseTrafficLight, RadarChart(4축, Canvas),
        Gauge, SignalPanel×4(입력 컨트롤+근거), TypeCards(유형 우선순위 재정렬), LeaderProfile 칩.
     4) LeaderCollapseViewModel + UiState: 입력 편집 → EvaluatePhaseUseCase 재계산(StateFlow).
        NavGraph에 메뉴 진입점 1개 추가.
제약:
  - MVVM + StateFlow, Compose(Material 3), 기존 팔레트 토큰. 프레젠테이션은 domain UseCase만 소비.
  - 입력 편집 시 해당 필드 source=MANUAL 마킹(자동 갱신을 이김).
  - 스코어링·§4.6 스키마 불변.
요구:
  - BuildSnapshotUseCase 병합·우선순위 단위테스트. VM 재계산 매핑 테스트.
  - 빌드 그린(Bash). 주요 컴포저블 @Preview.
완료 시 PROGRESS: P3 갱신. 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 3 approved. Continue with Phase 3.5-1."

---

## Phase 3.5-1 — Room 이력 영속 + 국면/방아쇠 전이 감지

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 3.5-1을 구현해줘.

전제: Phase 3 완료(PROGRESS: P3) — 스냅샷 병합·핵심 화면 존재.
범위(§4.6 스냅샷 계약 + Phase 3.5 상세):
  1) BearSnapshotEntity(day=PK, phase/lead/gate/s1/s2/s3/amp, configBasis,
     inputsJson/fieldMetaJson, createdAt) + BearSnapshotDao(upsert·observeLatest·observeRange·latest).
     inputsJson/fieldMetaJson은 §4.6 bear-snapshot/1 스키마 그대로 직렬화(별도 규약 금지).
  2) SnapshotRepository(upsertToday·observeLatest·observeRange·latestOrNull) + Impl + Hilt 바인딩.
     기존 AppDatabase에 DAO 추가, 마이그레이션 버전 +1(기존 데이터 무손실).
  3) DetectTransitionsUseCase — 연속 스냅샷에서 국면 변화·방아쇠(gate) 상승 전이 산출.
  4) 세션 진입 복원용 state:latest 저장/로드(WEB/MANUAL 출처·수동값 유지). 최신 as_of가
     로컬보다 오래되면 갱신 "제안"만 표면화(자동 반영 금지 — 승인 원칙).
제약: Clean Architecture(domain↔data), Room, Hilt, StateFlow. 직렬화는 §4.6 스키마 단일 사용
      (kotlinx.serialization JSON↔domain↔entity 왕복). 스코어링 미변경.
요구(하드 게이트):
  - in-memory Room 테스트: upsert(동일 day 덮어쓰기)·observeRange·latest 통과.
  - DetectTransitionsUseCase 결정적 테스트: 국면 변화 / gate 상승 / 무변화 시 빈 결과.
  - 마이그레이션 테스트(기존→신 스키마 데이터 보존). 빌드/테스트 그린(Bash).
완료 시 PROGRESS: P3.5-1 갱신. 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 3.5-1 approved. Continue with Phase 3.5-2."

---

## Phase 3.5-2 — 프레젠테이션 (스파크라인 · 전이 로그)

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 3.5-2를 구현해줘.

전제: Phase 3.5-1 완료(PROGRESS: P3.5-1) — SnapshotRepository·DetectTransitionsUseCase 존재.
범위(Phase 3.5 상세 — 프레젠테이션):
  1) ObserveHistoryUseCase로 최근 최대 90일 스냅샷 스트림을 UiState에 노출(lead·gate 시계열).
  2) Sparkline 컴포저블 — lead·gate 추세를 Compose Canvas로 경량 렌더(MPAndroidChart 불요).
  3) TransitionLog 컴포저블 — 전이를 "6/30 GREEN→AMBER · 방아쇠 경계 접근" 형식 최신순 표시.
  4) VERDICT BAND(Phase 3 산출) 아래에 두 컴포넌트 배치. VM이 이력·전이를 StateFlow로 결합.
제약: MVVM + StateFlow, Compose(Material 3), 기존 팔레트. domain UseCase만 소비. 스키마·스코어링 불변.
요구:
  - 이력 상태 3종 처리: empty(첫 실행 빈 상태 UI)·단일·다수.
  - VM 단위테스트: 이력 스트림 → UiState 매핑, 전이 목록 노출. 빌드 그린(Bash). @Preview 1개+.
완료 시 PROGRESS: P3.5 갱신(전체 3.5 완료). 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 3.5 approved. Continue with Phase 4."

---

## Phase 4 — 웹/LLM 갱신 + 승인 흐름

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 4를 구현해줘.

전제: Phase 3.5 완료(PROGRESS: P3.5) — 이력·화면 존재.
범위(§4.5 3-tier 웹 수집): [C]/[D] 판단성 필드를 LLM/web 제안 → 승인 반영.
  1) LlmMarketDataSource — Anthropic API + web_search. 그룹 분할 호출(Promise.allSettled 상당):
     ① rate/dir(공식 발표·날짜 명시) ② bigDeal/lossRatio ③ credit(KOFIA 주간). 열거형 화이트리스트 검증.
  2) Suggestion(field/current/next/as_of/origin/stale) — 필드별 허용 연령 초과 시 STALE.
     급변 값(금리 ±0.5%p·신용잔고 ±30% 초과)은 자동 1회 재확인, 일치 시에만 목록화.
  3) SuggestionPanel + ApplySuggestionUseCase — 제안은 상태 직접 변경 금지. 개별/일괄 승인 시에만
     source=AUTO로 반영(우선순위 준수, MANUAL 불패).
제약: 승인 없이 상태 변경 금지. fetch에 타임아웃(30s)+백오프 1회. §4.6 스키마·스코어링 불변.
요구:
  - 화이트리스트 검증·STALE 판정·급변 재확인 단위테스트(캔드 응답). 승인 반영 테스트.
  - 빌드/테스트 그린(Bash).
완료 시 PROGRESS: P4 갱신. 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 4 approved. Continue with Phase 5-1."

---

## Phase 5-1 — 통합 폴리시 (접근성 · 엣지케이스 · 내비 마무리)

```
kotlin-implementer 서브에이전트를 사용해서 TASK_bear_signal_console.md를 읽고 Phase 5-1을 구현해줘.

전제: Phase 4 완료(PROGRESS: P4) — 전 기능 배선 완료.
범위: 출시 품질 마감.
  1) 접근성 — 콘텐츠 설명·터치 타깃·대비(WCAG AA), 신호등/게이지에 텍스트 라벨 병기.
  2) 엣지케이스 — 데이터 수집 실패·부분 수집·이력 empty·오프라인 시 그레이스풀 폴백.
  3) 내비게이션·설정 마무리, WorkManager 스케줄(일 Tier A / 주 Tier B) 연결 및 진입 시 신선도 검사.
  4) 로딩/에러 상태 UI(기존 Shimmer 패턴 재사용).
제약: 기존 패턴·팔레트 유지. 스코어링·스키마 불변.
요구: 폴백·상태 전이 테스트. 빌드 그린(Bash).
완료 시 PROGRESS: P5-1 갱신. 보고 형식으로 요약.
```
### 검증 게이트 → "Phase 5-1 approved. Continue with Phase 5-2."

---

## Phase 5-2 — QA (수용 검증)

```
qa-verifier 서브에이전트를 사용해서 TASK_bear_signal_console.md §7 수용 기준을 검증해줘.

전제: Phase 5-1 완료(PROGRESS: P5-1).
검증 범위:
  1) 스코어링 동치 — 골든(2026.6.30 → AMBER)·경계 테스트 전부 통과 확인.
  2) 부록 B 9개 기능 블록 존재·동작 확인(React 파리티).
  3) 신규 능력 — 이력 영속(재시작 후 유지)·전이 감지·승인 흐름(MANUAL 불패)·config 구동
     (bear_thresholds.json 교체로 판정 변화, 코드 무수정) 확인.
  4) SSOT 위반 없음 — 리포트 외 지표 미도입, §3 임계치 코드 하드코딩 없음.
보고: 항목별 pass/fail, 실패 시 재현 경로. 전부 통과면 결론 명시.
```
### 최종 게이트
```
전부 통과 확인 후: PROGRESS.md에 LOOP_COMPLETE 기록.
실패 항목 있으면 해당 Phase로 회귀: "Phase N needs changes: [내용]. Revise and present again."
```

---

## 부록 · 배치 실행 (ralph-loop)
```powershell
# ralph-loop-bear.ps1 (요지)
for ($i = 1; $i -le $Max; $i++) {
  if (Test-Path "PROGRESS.md") {
    if ((Get-Content "PROGRESS.md" -Raw) -match "LOOP_COMPLETE") { Write-Host "Done."; break }
  }
  claude --model $Model --continue -p $Prompt   # SubagentStop 훅이 다음 게이트 문구 출력 → HITL
  Start-Sleep -Seconds 2
}
```
> 각 Phase 경계 STOP은 TASK.md 마커로 강제. 단계별 확인이 필요하면 인터랙티브(`opusplan`)를 권장.
