# PROMPT.md — 확률적 기대값 분석 엔진 마스터 프롬프트

## 실행 지침

이 프로젝트를 구현할 때 반드시 CLAUDE.md를 먼저 읽고, TASK.md의 Phase 순서대로 진행한다.
각 Phase 완료 시 PROGRESS.md를 업데이트한다.

---

## Phase 1 실행 프롬프트

```
CLAUDE.md를 읽고, TASK.md Phase 1을 실행해라.

domain/model/ 패키지에 7개 알고리즘의 결과 데이터 클래스를 생성해라.

핵심 원칙:
1. StatisticalResult가 최상위 wrapper로 7개 알고리즘 결과를 모두 포함
2. 각 결과 클래스는 불변(val only) data class
3. 모든 확률값은 Double (0.0~1.0)
4. 모든 수익률은 Double (소수점 표현, e.g., 0.034 = 3.4%)
5. nullable 필드는 데이터 부족 시 사용 (per: Double?)

기존 Room DAO(StockDao, IndicatorDao, EtfDao)에 필요한
쿼리 메서드를 @Query 어노테이션으로 추가해라.

완료 후 TASK.md에서 해당 항목을 체크하고 PROGRESS.md를 업데이트해라.
```

---

## Phase 2 실행 프롬프트 (7개 알고리즘)

### 2.1 Naive Bayes Engine

```
CLAUDE.md를 읽고, TASK.md Phase 2.1을 실행해라.

data/engine/NaiveBayesEngine.kt를 구현해라.

알고리즘 명세:
- 목적: P(상승|지표조합) = P(지표조합|상승) × P(상승) / P(지표조합)
- 각 지표를 이산 상태로 변환:
  - MACD histogram: POSITIVE / NEGATIVE / NEAR_ZERO
  - 수급 오실레이터: BUY / SELL / NEUTRAL
  - EMA 배열: GOLDEN (20>60) / DEAD (20<60) / CONVERGING
  - Demark setup: SETUP_HIGH (7-9) / SETUP_LOW (1-3) / NONE
  - 거래량: SURGE (>150% avg) / NORMAL / LOW (<70% avg)
  - PBR: UNDERVALUED (<1.0) / FAIR (1.0-2.0) / OVERVALUED (>2.0)

- DB에서 과거 데이터 조회 → 각 상태 조합별 빈도 카운팅
- Laplace smoothing 적용 (alpha=1)으로 zero probability 방지
- 3-class 분류: UP / DOWN / SIDEWAYS
  - UP: 20일 후 수익률 > +2%
  - DOWN: 20일 후 수익률 < -2%
  - SIDEWAYS: 나머지

입력: stockCode: String, indicators: List<IndicatorSet>, prices: List<DailyPrice>
출력: BayesResult(upProbability, downProbability, sidewaysProbability,
       dominantFeatures: List<FeatureContribution>, sampleCount: Int)

FeatureContribution은 각 지표가 확률에 얼마나 기여했는지 표시:
  - featureName: String
  - likelihoodRatio: Double (P(feature|UP) / P(feature|ALL))

외부 라이브러리 없이 순수 Kotlin으로 구현.
unit test도 작성 (mock 데이터로 확률 합=1.0 검증, Laplace smoothing 검증).

완료 후 TASK.md 업데이트.
```

### 2.2 Logistic Scoring Engine

```
CLAUDE.md를 읽고, TASK.md Phase 2.2를 실행해라.

data/engine/LogisticScoringEngine.kt를 구현해라.

알고리즘 명세:
- 목적: 여러 지표를 가중 합산하여 상승 확률 P(up) = σ(Σ wᵢxᵢ + b) 산출
- σ(z) = 1 / (1 + e^(-z)) — sigmoid 함수
- 지표를 0~1로 정규화 (min-max scaling, 과거 N일 기준)
- 계수(weight)는 과거 데이터에서 학습하거나 사전 정의

추론 전용 구현 (학습은 별도 오프라인):
1. trainWeights() — 과거 데이터로 경사하강법 수행, weights를 SharedPreferences에 저장
2. predict() — 저장된 weights로 추론만 수행

features (정규화 대상):
  - macd_histogram (min-max over 60d)
  - oscillator_value (min-max over 60d)
  - ema20_ema60_spread (정규화)
  - volume_ratio (현재/20일평균)
  - demark_buy_setup / 9.0
  - pbr_inverse (1/PBR, 정규화)

학습:
  - learning_rate = 0.01, epochs = 100
  - label: 20일 후 수익률 > 0이면 1, 아니면 0
  - loss: binary cross-entropy

출력: LogisticResult(probability: Double, weights: Map<String, Double>,
       featureValues: Map<String, Double>, score0to100: Int)

unit test: sigmoid 검증, weights 저장/로드 검증, 예측값 범위 0~1 검증.
```

### 2.3 HMM Regime Engine

```
CLAUDE.md를 읽고, TASK.md Phase 2.3을 실행해라.

data/engine/HmmRegimeEngine.kt를 구현해라.

알고리즘 명세:
- 목적: 시장 레짐(숨은 상태) 탐지 — 강세/약세/고변동/저변동
- Heuristic (Static) HMM 구현 — 사전 정의된 파라미터 사용
  (완전한 Baum-Welch 학습은 모바일에서 불필요하게 복잡)

상태 정의 (4개):
  - REGIME_0: 저변동 상승 (Low vol, positive returns)
  - REGIME_1: 저변동 횡보 (Low vol, near-zero returns)
  - REGIME_2: 고변동 상승 (High vol, positive returns)
  - REGIME_3: 고변동 하락 (High vol, negative returns)

구현:
1. 관측값 생성:
   - 일별 수익률을 z-score 정규화 (60일 롤링 mean/std)
   - 일별 변동성 = |high-low|/close를 z-score 정규화
   - observation = [normalized_return, normalized_volatility]

2. 방출 확률 (Emission) — 각 상태별 가우시안 파라미터 사전 정의:
   - REGIME_0: mean=[+0.5, -0.5], std=[0.8, 0.6]
   - REGIME_1: mean=[0.0, -0.3], std=[0.7, 0.5]
   - REGIME_2: mean=[+0.3, +1.0], std=[1.2, 1.0]
   - REGIME_3: mean=[-0.5, +1.2], std=[1.0, 0.8]

3. 전환 행렬 (Transition) — 사전 정의:
   레짐 지속성 높게 (대각선 0.90~0.95):
   [[0.93, 0.04, 0.02, 0.01],
    [0.05, 0.90, 0.03, 0.02],
    [0.02, 0.03, 0.92, 0.03],
    [0.01, 0.02, 0.04, 0.93]]

4. Forward Algorithm으로 현재 시점의 상태 확률 분포 계산
   - α(t, j) = [Σᵢ α(t-1, i) × aᵢⱼ] × bⱼ(oₜ)
   - 정규화: α(t) = α(t) / Σⱼ α(t, j)

5. Viterbi Algorithm으로 최근 N일의 최적 상태 경로 추정

출력: HmmResult(
  currentRegime: Int,
  regimeProbabilities: DoubleArray (size 4),
  transitionProbabilities: Map<String, Double> (현재→다른상태),
  recentRegimePath: List<Int> (최근 20일),
  regimeDescription: String (사전 정의된 설명)
)

순수 Kotlin, 외부 라이브러리 없음. DoubleArray 연산만.
unit test: Forward algorithm 정규화 검증 (확률합=1.0),
         Viterbi 경로 검증 (명확한 레짐 전환 데이터).
```

### 2.4 Pattern Scan Engine

```
CLAUDE.md를 읽고, TASK.md Phase 2.4를 실행해라.

data/engine/PatternScanEngine.kt를 구현해라.

이전 대화에서 설계한 scanPattern() 로직을 정식 엔진으로 구현.
핵심: 사전 정의된 패턴 조건을 DB에서 스캔 → 후속 수익률 통계 집계.

최소 8개 패턴 정의:
1. MACD_GOLDEN_CROSS_WITH_SUPPLY_BUY
2. DEMARK_SETUP9_WITH_VOLUME_SURGE
3. EMA_GOLDEN_CROSS_WITH_LOW_PBR
4. OSCILLATOR_3DAY_RISING_MACD_POSITIVE
5. TRIPLE_BULLISH (EMA정배열 + MACD+ + 수급BUY)
6. VOLUME_BREAKOUT_WITH_EMA_SUPPORT (거래량200%+ & 가격>EMA20)
7. DEMARK_COUNTDOWN13_COMPLETION
8. BEARISH_DIVERGENCE (가격 신고가 but 수급 하락)

각 패턴에 대해:
- 과거 발생 횟수
- 5일/10일/20일 승률 및 평균수익률
- 평균 MDD (20일 내)
- 최근 3회 결과
- 현재 활성 여부

출력: PatternAnalysis(allPatterns, activePatterns, totalHistoricalDays)

unit test 포함.
```

### 2.5 Signal Scoring Engine

```
CLAUDE.md를 읽고, TASK.md Phase 2.5를 실행해라.

data/engine/SignalScoringEngine.kt를 구현해라.

알고리즘: 각 지표 신호에 과거 승률 기반 가중치를 부여하여 0~100 종합 점수 산출.

Score = Σ(wᵢ × signalᵢ × directionᵢ) / Σ(wᵢ) × 100

- wᵢ = 해당 지표의 과거 20일 승률 (PatternScanEngine 결과에서 가져옴)
- signalᵢ = 1 (활성) or 0 (비활성)
- directionᵢ = +1 (매수) or -1 (매도)

지표별 신호 판단:
- MACD: histogram > 0 → 매수, < 0 → 매도
- 수급 오실레이터: BUY → 매수, SELL → 매도
- EMA: 20 > 60 → 매수, 20 < 60 → 매도
- Demark: buy setup ≥ 7 → 매수 근접, sell setup ≥ 7 → 매도 근접
- 거래량: > 150% 평균 → 신호 강화 (별도 가중치 부여)
- PBR: < 1.0 → 저평가 매수

출력: SignalScoringResult(
  totalScore: Int (0-100),
  contributions: List<SignalContribution>(name, weight, signal, contribution%),
  conflictingSignals: List<SignalConflict>,
  dominantDirection: String ("BULLISH"/"BEARISH"/"MIXED")
)

unit test: 점수 범위 0~100 검증, 기여도 합 100% 검증.
```

### 2.6 Correlation Engine

```
CLAUDE.md를 읽고, TASK.md Phase 2.6을 실행해라.

data/engine/CorrelationEngine.kt를 구현해라.

두 가지 분석:
A) 지표 간 상관 분석 (Pearson r, 60일 롤링)
B) 선행-후행 분석 (cross-correlation, lag -5 ~ +5)

분석 쌍:
- 수급 오실레이터 ↔ MACD histogram
- 수급 오실레이터 ↔ 거래량 변화율
- MACD ↔ EMA spread
- Demark count ↔ 수급 오실레이터
- 개별종목 수익률 ↔ 섹터 ETF 수익률

Pearson r 공식: 직접 구현 (이전 대화의 pearsonCorrelation 함수)
Cross-correlation: lag별로 한 시리즈를 shift한 후 Pearson r 계산,
                   최대 |r| 을 갖는 lag가 최적 선행/후행

출력: CorrelationAnalysis(
  correlations: List<CorrelationResult>(ind1, ind2, pearsonR, strength),
  leadLagResults: List<LeadLagResult>(ind1, ind2, optimalLag, rAtOptimalLag,
                                      interpretation: "ind1이 ind2를 N일 선행")
)

unit test: 완전 상관(r=1.0), 무상관(r≈0), 역상관(r=-1.0) 검증.
```

### 2.7 Bayesian Update Engine

```
CLAUDE.md를 읽고, TASK.md Phase 2.7을 실행해라.

data/engine/BayesianUpdateEngine.kt를 구현해라.

알고리즘: 새 신호 도착 시 Bayes' theorem으로 상승 확률 갱신.

Posterior = (Prior × Likelihood) / Evidence

구현:
1. 초기 Prior: 과거 전체 데이터의 상승 비율 (base rate)
2. 각 지표 신호별 Likelihood 테이블 (DB에서 사전 집계):
   - P(MACD+ | UP), P(MACD+ | DOWN), P(MACD+ | SIDEWAYS)
   - P(수급BUY | UP), P(수급BUY | DOWN), ...
   - (같은 방식으로 모든 지표)
3. 신호가 순차적으로 도착할 때마다 posterior 갱신
4. 갱신 히스토리 추적 (어떤 신호가 확률을 얼마나 변화시켰는지)

출력: BayesianUpdateResult(
  finalPosterior: Double (P(UP) after all updates),
  priorProbability: Double (base rate),
  updateHistory: List<ProbabilityUpdate>(
    signalName, beforeProb, afterProb, deltaProb, likelihoodRatio
  )
)

unit test: posterior가 0~1 범위, 순차 갱신 순서 불변성 검증
          (나이브 베이즈 가정 하에 순서 무관해야 함).
```

### 2.8 Statistical Analysis Engine (Orchestrator)

```
CLAUDE.md를 읽고, TASK.md Phase 2.8을 실행해라.

data/engine/StatisticalAnalysisEngine.kt를 구현해라.

역할: 7개 엔진을 coroutine으로 병렬 실행하고 결과를 StatisticalResult로 통합.

@Inject constructor로 7개 엔진을 주입받음.
suspend fun analyze(stockCode: String): StatisticalResult

구현:
- coroutineScope { } 안에서 7개 async { } 병렬 실행
- 각 엔진의 실패는 개별 처리 (하나가 실패해도 나머지 결과는 반환)
- 실패한 엔진은 null 또는 empty result로 표시
- 타이밍 로그: 각 엔진의 실행 시간 측정

출력: StatisticalResult (7개 결과 + 실행 메타데이터)
```

---

## Phase 3 실행 프롬프트

### 3.1 Prompt Builder

```
CLAUDE.md를 읽고, TASK.md Phase 3.1을 실행해라.

data/mapper/ProbabilisticPromptBuilder.kt를 구현해라.

역할: StatisticalResult → LLM 프롬프트 변환

핵심 원칙:
1. 시스템 프롬프트에 분석 프레임워크를 명시적으로 정의
2. 모든 숫자는 "이미 계산됨"을 강조 — LLM이 재계산하지 않도록
3. 출력 JSON 스키마를 명확히 지정
4. 토큰 예산 관리: 전체 입력 < 4,000 tokens 목표

시스템 프롬프트 내용:
- "You are a Korean stock analyst. All numbers are PRE-COMPUTED. NEVER recalculate."
- 7개 알고리즘 결과를 해석하는 가이드라인
- 상충 신호 해결 방법 (승률 기반 가중)
- JSON 출력 스키마 (overall_assessment, confidence, insights per algorithm, conflicts, risks, action)
- 언어: 한국어
- 최대 300 단어

사용자 프롬프트 구조:
[현재 상태] → [Bayes 결과] → [Logistic 결과] → [HMM 결과] →
[패턴 분석] → [신호 점수] → [상관 분석] → [Bayesian 갱신] →
[신호 충돌] → "위 데이터를 종합하여 JSON으로 응답하세요."

각 섹션은 결과가 null이면 스킵 (토큰 절약).
ChatML 포맷 (<|im_start|>) 지원.

unit test: 빈 결과에서도 유효한 프롬프트 생성, 토큰 수 추정 검증.
```

### 3.2 Response Parser

```
CLAUDE.md를 읽고, TASK.md Phase 3.2를 실행해라.

data/mapper/AnalysisResponseParser.kt를 구현해라.

역할: LLM의 JSON 출력을 StockAnalysis data class로 파싱.

구현:
- kotlinx.serialization 또는 Gson으로 JSON 파싱
- LLM이 JSON 외 텍스트를 포함할 수 있으므로 JSON 추출 로직 필요:
  - 응답에서 첫 번째 '{' ~ 마지막 '}' 사이를 추출
  - markdown code fence (```json ... ```) 제거
- 파싱 실패 시 fallback: 원본 텍스트를 summary 필드에 넣어 반환
- confidence 값 범위 검증 (0.0~1.0)
- 필수 필드 누락 시 기본값 채움

unit test: 정상 JSON, 마크다운 래핑된 JSON, 비정상 출력 각각 테스트.
```

### 3.3~3.4 LLM Repository + Model Manager

```
CLAUDE.md를 읽고, TASK.md Phase 3.3~3.4를 실행해라.

data/local/llm/LlmRepositoryImpl.kt와 ModelManager.kt를 구현해라.

LlmRepositoryImpl:
- llama.cpp JNI 래퍼
- native 메서드 선언: nativeLoadModel, nativeGenerate, nativeUnload
- ChatML 포맷으로 프롬프트 포매팅
- Flow<String>으로 토큰 단위 스트리밍
- 인퍼런스 스레드 수: min(availableProcessors-1, 4)
- System.loadLibrary("llama_jni")

ModelManager:
- 추천 모델 목록 (ModelInfo: name, url, sizeBytes, minRamGb)
- 디바이스 RAM 감지 → 적합 모델 추천
- 모델 다운로드 진행률 Flow
- 내부 저장소 캐시 관리

JNI C++ 코드는 별도 Phase 5에서 구현.
여기서는 Kotlin 인터페이스와 JNI 선언까지만.
```

---

## Phase 4 실행 프롬프트

```
CLAUDE.md를 읽고, TASK.md Phase 4를 실행해라.

UseCase, ViewModel, DI Module, Compose UI를 구현해라.

AnalyzeStockProbabilityUseCase:
- StatisticalAnalysisEngine.analyze() → ProbabilisticPromptBuilder.build()
  → LlmRepository.generate() → AnalysisResponseParser.parse()
- Flow<AnalysisState> 반환 (Loading, Computing, LlmProcessing, Streaming, Success, Error)
- 각 단계별 진행 메시지 포함

StockAnalysisViewModel:
- uiState: StateFlow<AnalysisUiState>
- streamingText: StateFlow<String>
- analyzeStock(stockCode: String) 함수
- 모델 로드 상태 관리

Hilt Module:
- @Binds로 Repository 바인딩
- @Provides로 Engine 생성
- @Singleton 스코프

Compose UI (간단한 v1):
- "AI 분석" 버튼
- 로딩 상태 표시 (단계별 메시지)
- 스트리밍 텍스트 표시
- 최종 리포트 카드 (Scaffold + LazyColumn)
- 각 알고리즘 결과의 expandable 섹션
```
