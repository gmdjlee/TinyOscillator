# PROGRESS.md — 진행 상태 추적

## 현재 상태
- Phase: ALL PHASES COMPLETED (Phase 1~5)
- Last Updated: 2026-03-28
- Total Tests: 96 (ALL PASSED)

## Phase 1: Domain Models & Interfaces — COMPLETED
- `StatisticalModels.kt` (30+ data classes), `StatisticalRepository.kt`, `LlmRepository.kt`
- DAO 6개 쿼리 추가

## Phase 2: Statistical Engines — COMPLETED (8/8, 73 tests)
| 엔진 | 테스트 | 핵심 |
|------|--------|------|
| NaiveBayesEngine | 7 | Laplace smoothing, 6 이산 피처, 3-class |
| LogisticScoringEngine | 9 | sigmoid, 경사하강법, SharedPreferences |
| HmmRegimeEngine | 11 | Forward/Viterbi, 4-state HMM |
| PatternScanEngine | 8 | 8개 패턴, 5/10/20일 수익률+MDD |
| SignalScoringEngine | 7 | 6개 신호 가중 앙상블, 충돌 탐지 |
| CorrelationEngine | 10 | Pearson r, cross-correlation(lag -5~+5) |
| BayesianUpdateEngine | 9 | 순차적 posterior 갱신 |
| StatisticalAnalysisEngine | 12 | 7개 엔진 coroutine 병렬, 개별 에러 핸들링 |

## Phase 3: LLM Integration — COMPLETED (18 tests)
- `ProbabilisticPromptBuilder` (7 tests) — ChatML, null 스킵, 토큰 추정
- `AnalysisResponseParser` (11 tests) — JSON 추출, fallback, confidence 검증
- `LlmRepositoryImpl` — JNI 래퍼, Flow<String> 스트리밍
- `ModelManager` — GGUF 모델 관리, RAM 감지, 다운로드 Flow

## Phase 4: UseCase & ViewModel — COMPLETED (5 tests)
- `StatisticalRepositoryImpl` — Room DAO + CalcUseCase 통합
- `AnalyzeStockProbabilityUseCase` (5 tests) — 4단계 파이프라인, fallback 처리
- `StockAnalysisViewModel` — UI 상태 관리
- `StatisticalModule` — Hilt DI (@Binds + @Provides + @LogisticPrefs)
- `StockAnalysisScreen` — Compose UI, expandable 리포트 카드

## Phase 5: NDK Build — COMPLETED
- `CMakeLists.txt` — llama.cpp NDK 빌드 (stub 모드 지원)
- `llama_jni.cpp` — JNI 브릿지 (모델 로드/토큰 생성/해제)
- `llama_jni_stub.cpp` — llama.cpp 없이 빌드 가능한 스텁
- `ModelDownloadScreen.kt` — 모델 다운로드/관리 Compose UI
- `build.gradle.kts` — NDK/CMake 설정 (arm64-v8a, armeabi-v7a)

## 파일 목록 (총 36개)
### 소스 (25개)
- `domain/model/StatisticalModels.kt`
- `domain/repository/StatisticalRepository.kt`
- `domain/repository/LlmRepository.kt`
- `domain/usecase/AnalyzeStockProbabilityUseCase.kt`
- `data/engine/NaiveBayesEngine.kt`
- `data/engine/LogisticScoringEngine.kt`
- `data/engine/HmmRegimeEngine.kt`
- `data/engine/PatternScanEngine.kt`
- `data/engine/SignalScoringEngine.kt`
- `data/engine/CorrelationEngine.kt`
- `data/engine/BayesianUpdateEngine.kt`
- `data/engine/StatisticalAnalysisEngine.kt`
- `data/mapper/ProbabilisticPromptBuilder.kt`
- `data/mapper/AnalysisResponseParser.kt`
- `data/local/llm/LlmRepositoryImpl.kt`
- `data/local/llm/ModelManager.kt`
- `data/repository/StatisticalRepositoryImpl.kt`
- `core/di/StatisticalModule.kt`
- `presentation/viewmodel/StockAnalysisViewModel.kt`
- `presentation/ai/StockAnalysisScreen.kt`
- `presentation/ai/ModelDownloadScreen.kt`
- `cpp/CMakeLists.txt`
- `cpp/llama_jni.cpp`
- `cpp/llama_jni_stub.cpp`

### 수정 (4개)
- `core/database/dao/AnalysisCacheDao.kt` (+getRecentByTicker)
- `core/database/dao/StockMasterDao.kt` (+getStockName, +getSector)
- `core/database/dao/FundamentalCacheDao.kt` (+getRecentByTicker, +getLatestByTicker)
- `core/database/dao/EtfDao.kt` (+getEtfCountForStock)
- `app/build.gradle.kts` (+NDK/CMake 설정)

### 테스트 (11개, 96 tests)
- `NaiveBayesEngineTest` (7), `LogisticScoringEngineTest` (9), `HmmRegimeEngineTest` (11)
- `PatternScanEngineTest` (8), `SignalScoringEngineTest` (7), `CorrelationEngineTest` (10)
- `BayesianUpdateEngineTest` (9), `StatisticalAnalysisEngineTest` (12)
- `ProbabilisticPromptBuilderTest` (7), `AnalysisResponseParserTest` (11)
- `AnalyzeStockProbabilityUseCaseTest` (5)

## llama.cpp 설치 방법
```bash
cd app/src/main/cpp/
git clone https://github.com/ggerganov/llama.cpp.git
```
설치 후 프로젝트 재빌드하면 stub 대신 실제 llama.cpp가 링크됨.
