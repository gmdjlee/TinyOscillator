# TASK.md — 확률적 기대값 분석 엔진 구현

## Phase 1: Domain Models & Interfaces (Foundation)
### 목표: 모든 데이터 클래스와 인터페이스 정의
### 모델 티어: Haiku → Sonnet

- [x] 1.1 domain/model/ 패키지에 결과 데이터 클래스 생성
  - StatisticalResult (7개 알고리즘 결과를 묶는 최상위)
  - BayesResult, LogisticResult, HmmResult
  - PatternMatch, PatternOccurrence, PatternAnalysis
  - CorrelationResult, CorrelationAnalysis
  - ExpectedValueAnalysis, Scenario, HistoricalOutcome
  - DemarkAnalysis, DemarkState, DemarkHistoricalResult
  - EtfContext, SignalConflict
  - StockAnalysis (LLM 최종 출력)
- [x] 1.2 domain/repository/ 인터페이스 정의
  - StatisticalRepository
  - LlmRepository
- [x] 1.3 기존 Room DAO에 필요한 쿼리 메서드 추가
  - StockDao: getDailyPrices(code, limit), getStockName(code)
  - IndicatorDao: getIndicators(code), getDemarkData(code)
  - EtfDao: getSectorEtfForStock(code), getEtfPrices(code, limit)

## Phase 2: Statistical Engines (Core Algorithms)
### 목표: 7개 알고리즘을 각각 독립 클래스로 구현
### 모델 티어: Opus (알고리즘) → Sonnet (테스트)

- [x] 2.1 NaiveBayesEngine — 조건부 확률 분류기
- [x] 2.2 LogisticScoringEngine — 로지스틱 회귀 스코어링
- [x] 2.3 HmmRegimeEngine — Hidden Markov Model 레짐 탐지
- [x] 2.4 PatternScanEngine — 조건부 빈도 분석 (패턴 백테스팅)
- [x] 2.5 SignalScoringEngine — 가중 신호 앙상블 점수
- [x] 2.6 CorrelationEngine — 롤링 상관/선행-후행 분석
- [x] 2.7 BayesianUpdateEngine — 실시간 사전확률 갱신
- [x] 2.8 StatisticalAnalysisEngine — 7개 엔진을 병렬 실행하고 결과 통합
- [x] 2.9 각 엔진별 unit test (mock data)

## Phase 3: LLM Integration (Prompt + Runtime)
### 목표: llama.cpp JNI 래퍼 + 프롬프트 빌더 + 파서
### 모델 티어: Opus

- [x] 3.1 ProbabilisticPromptBuilder — StatisticalResult → LLM prompt 변환
- [x] 3.2 AnalysisResponseParser — LLM JSON 출력 → StockAnalysis 파싱
- [x] 3.3 LlmRepositoryImpl — llama.cpp JNI 래퍼 (스트리밍)
- [x] 3.4 ModelManager — GGUF 모델 다운로드/캐시 관리
- [x] 3.5 프롬프트 테스트 (mock LLM 응답으로 파서 검증)

## Phase 4: UseCase & ViewModel (Integration)
### 목표: Clean Architecture 통합 + UI 연결
### 모델 티어: Sonnet

- [x] 4.1 AnalyzeStockProbabilityUseCase — 전체 파이프라인 오케스트레이션
- [x] 4.2 StockAnalysisViewModel — UI 상태 관리
- [x] 4.3 Hilt DI Module 구성
- [x] 4.4 Compose UI — 분석 리포트 카드 화면

## Phase 5: NDK Build & Model Setup
### 목표: llama.cpp 빌드 + 모델 배포
### 모델 티어: Sonnet

- [x] 5.1 CMakeLists.txt — llama.cpp NDK 빌드 설정
- [x] 5.2 JNI bridge C++ 코드
- [x] 5.3 모델 다운로드 UI flow
- [x] 5.4 통합 테스트 (StatisticalAnalysisEngine 12 tests + UseCase 5 tests)

## 현재 진행 상태
Phase: ALL PHASES COMPLETED
Current Task: DONE
Total Tests: 96 (ALL PASSED)
Blockers: -
