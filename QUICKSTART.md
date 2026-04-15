# QUICKSTART.md — Claude Code 실행 가이드

## 사전 준비

### 1. 프로젝트 루트에 파일 배치
```powershell
# 프로젝트 루트에 복사
Copy-Item CLAUDE.md, TASK.md, PROMPT.md, PROGRESS.md -Destination "C:\YourProject\"
Copy-Item -Recurse .claude -Destination "C:\YourProject\"
```

### 2. 기존 CLAUDE.md가 있다면 병합
기존 CLAUDE.md 하단에 이 프로젝트의 내용을 추가하거나,
별도 섹션으로 구분:
```markdown
# --- Probabilistic Analysis Engine ---
(이 프로젝트 CLAUDE.md 내용)
```

### 3. 기존 Room Entity/DAO 확인
Claude Code가 기존 코드를 정확히 참조하려면:
```powershell
# Claude Code에서 기존 구조 확인
claude "프로젝트의 Room Entity와 DAO 파일들을 찾아서 목록을 보여줘"
```

---

## Phase별 실행 명령어

### Phase 1: 도메인 모델 (약 10분)
```powershell
claude "CLAUDE.md를 읽고, TASK.md Phase 1을 실행해라. `
domain/model/ 패키지에 7개 알고리즘의 결과 데이터 클래스를 모두 생성해라. `
StatisticalResult가 최상위 wrapper. 모든 확률값은 Double(0.0~1.0). `
기존 Room DAO에 필요한 쿼리 메서드도 추가해라. `
완료 후 PROGRESS.md 업데이트."
```

### Phase 2: 통계 엔진 (가장 핵심, 엔진별 개별 실행 권장)
```powershell
# 2.1 Naive Bayes — 가장 단순, 먼저 구현하여 패턴 확립
claude --model opus "PROMPT.md의 Phase 2.1 프롬프트를 읽고 실행해라. `
data/engine/NaiveBayesEngine.kt를 구현해라. `
agent: .claude/agents/algorithm-engine.md를 참고해라."

# 2.2 Logistic Regression
claude --model opus "PROMPT.md의 Phase 2.2 프롬프트를 읽고 실행해라."

# 2.3 HMM — 가장 복잡, Opus 필수
claude --model opus "PROMPT.md의 Phase 2.3 프롬프트를 읽고 실행해라. `
Forward Algorithm과 Viterbi를 순수 Kotlin으로 구현. `
외부 라이브러리 절대 사용 금지."

# 2.4 Pattern Scan
claude "PROMPT.md의 Phase 2.4 프롬프트를 읽고 실행해라."

# 2.5 Signal Scoring
claude "PROMPT.md의 Phase 2.5 프롬프트를 읽고 실행해라."

# 2.6 Correlation Engine
claude "PROMPT.md의 Phase 2.6 프롬프트를 읽고 실행해라."

# 2.7 Bayesian Update
claude "PROMPT.md의 Phase 2.7 프롬프트를 읽고 실행해라."

# 2.8 Orchestrator — 모든 엔진 통합
claude "PROMPT.md의 Phase 2.8 프롬프트를 읽고 실행해라. `
7개 엔진을 coroutineScope에서 async 병렬 실행. `
각 엔진의 실행 시간을 로깅."

# 2.9 테스트 일괄 생성
claude "Phase 2에서 생성한 모든 엔진의 unit test를 작성해라. `
각 엔진에 대해 최소 3개 테스트 케이스: `
1) 정상 데이터 → 올바른 결과 범위 검증 `
2) 빈 데이터 → graceful empty result `
3) 엣지 케이스 (모든 값 동일, 극단값 등)"
```

### Phase 3: LLM 통합 (Opus 필수)
```powershell
claude --model opus "PROMPT.md의 Phase 3.1~3.2 프롬프트를 읽고 실행해라. `
ProbabilisticPromptBuilder와 AnalysisResponseParser를 구현해라. `
agent: .claude/agents/llm-integration.md를 참고해라."

claude --model opus "PROMPT.md의 Phase 3.3~3.4 프롬프트를 읽고 실행해라. `
LlmRepositoryImpl(JNI 래퍼)과 ModelManager를 구현해라."
```

### Phase 4: 통합 (Sonnet OK)
```powershell
claude "PROMPT.md의 Phase 4 프롬프트를 읽고 실행해라. `
AnalyzeStockProbabilityUseCase, ViewModel, Hilt Module, 기본 Compose UI."
```

---

## Ralph Loop 방식 실행 (권장)

전체를 한 번에 실행하려면 Ralph Loop 사용:

```powershell
# PowerShell Ralph Loop
$phases = @(
    "TASK.md Phase 1을 실행해라. 완료 후 PROGRESS.md 업데이트.",
    "TASK.md Phase 2.1~2.4를 실행해라. PROMPT.md에서 각 프롬프트를 읽어라.",
    "TASK.md Phase 2.5~2.8을 실행해라. PROMPT.md에서 각 프롬프트를 읽어라.",
    "TASK.md Phase 2.9를 실행해라. 모든 엔진의 unit test 작성.",
    "TASK.md Phase 3을 실행해라. PROMPT.md에서 프롬프트를 읽어라.",
    "TASK.md Phase 4를 실행해라. PROMPT.md에서 프롬프트를 읽어라."
)

foreach ($phase in $phases) {
    $prompt = "CLAUDE.md를 읽고, $phase `
    이전 Phase의 코드를 참조하여 일관된 구조를 유지해라. `
    에러가 있으면 수정하고 PROGRESS.md에 기록해라."

    claude --model opus $prompt

    # 중간 확인
    Write-Host "Phase completed. Review before continuing? (y/n)"
    $review = Read-Host
    if ($review -eq 'n') { break }
}
```

---

## 검증 명령어

```powershell
# 전체 빌드 확인
claude "프로젝트를 빌드하고 컴파일 에러를 수정해라."

# 테스트 실행
claude "모든 unit test를 실행하고 실패하는 테스트를 수정해라."

# 코드 리뷰
claude "Phase 2의 모든 엔진 코드를 리뷰해라. `
특히 확인할 것: `
1) NaN/Infinity 방어 `
2) 빈 리스트 처리 `
3) 확률값 합 = 1.0 검증 `
4) 메모리 효율 (대량 데이터 시 GC 압박)"

# 프롬프트 토큰 추정
claude "ProbabilisticPromptBuilder가 생성하는 프롬프트의 `
예상 토큰 수를 계산해라. 목표: < 4,000 tokens. `
초과 시 어떤 섹션을 줄일지 제안해라."
```

---

## 트러블슈팅

### Claude Code가 기존 코드 구조를 못 찾을 때
```powershell
claude "프로젝트에서 다음 파일들을 찾아서 경로를 알려줘: `
StockDao.kt, IndicatorDao.kt, DailyPrice.kt, IndicatorSet.kt `
찾은 경로를 CLAUDE.md에 기록해라."
```

### 엔진 간 의존성 문제
Phase 2.8 (Orchestrator)은 Phase 2.1~2.7 모두 완료 후 실행.
중간에 인터페이스가 변경되면:
```powershell
claude "StatisticalResult data class가 변경되었다. `
모든 엔진의 출력이 새 StatisticalResult와 호환되는지 확인하고 수정해라."
```

### HMM 구현이 복잡할 때
```powershell
claude --model opus "HmmRegimeEngine의 Forward Algorithm 구현을 `
단계별로 설명하면서 작성해라. `
1단계: 초기화 α(0,j) = π(j) × b(j, o₀) `
2단계: 재귀 α(t,j) = [Σᵢ α(t-1,i) × aᵢⱼ] × bⱼ(oₜ) `
3단계: 정규화 α(t) = α(t) / Σⱼ α(t,j) `
각 단계를 Kotlin 함수로 분리하고, 중간값을 로깅해라."
```
