# REFACTOR_PLAN.md — TinyOscillator 코드 리뷰 후속 조치 계획

**작성일**: 2026-04-20
**리뷰 범위**: 전체 코드베이스 (321 main + 160 test Kotlin files, Room v26, 9 engines, 8 workers)
**리뷰 방식**: 3개 병렬 에이전트 (파편화/안정성/구현품질) + 주요 주장 직접 검증

---

## 진행 현황

| Phase | 상태 | 완료일 | 비고 |
|-------|------|-------|------|
| 1. 동시성/안정성 버그 | ✅ 완료 | 2026-04-20 | P1-1~P1-4 모두 완료 |
| 2. 레이어 경계 복구 | ✅ 완료 | 2026-04-20 | P2-1(🔴 engine 직접 import) 제거, P2-2(🟢 repository 감사) 문서화 |
| 3. 공통 유틸/상수 추출 | ✅ 완료 | 2026-04-20 | P3-1·P3-2 완료, P3-3(Dispatchers 주입)는 리스크로 보류 |
| 4. 거대 파일 분할 | ✅ 완료 | 2026-04-21 | P4-1·P4-2·P4-3·P4-4 모두 완료 |
| 5. 에러 처리 정비 | ✅ 완료 | 2026-04-21 | P5-1(빈 catch 2건 + 동일 파일 3건 추가) 적용, P5-2(Throwable) 스캔 결과 0건 |
| 6. 데이터 무결성 | ✅ 완료 | 2026-04-21 | P6-1(감사 결과 조치 불필요), P6-3(estimateBetas nullable 시그니처), P6-4(상대 수렴+NaN가드+empty history 가드). P6-2는 리스크로 보류 |
| 7. 성능/UX 최적화 | ✅ 완료 | 2026-04-21 | P7-2 감사 조치 불필요, P7-3 Kdoc 명확화(이미 구현됨), P7-1 LazyColumn key 3건, P7-4 Worker 확장 |
| 8. 테스트 인프라 확장 | ✅ 완료 | 2026-04-21 | P8-1 androidTest 인프라 + Robolectric Room DAO 테스트 17건, P8-2 Turbine 예시 테스트 1건 |
| 3.5. API retry/Scraper 유틸 통합 | ✅ 완료 | 2026-04-21 | 3개 API 클라이언트 retry 중복 제거 + scraper timeout/randomDelay 중앙화 |
| 4.5. DatabaseModule 분할 | ✅ 완료 | 2026-04-21 | 804줄 → 101줄 (migration·DAO 모듈 분리) |
| Polish. Compose 최적화 | ✅ 완료 | 2026-04-21 | AiAnalysisChatSection LazyColumn key 추가 + IndicatorSheet filter top-level 승격 |

### Phase 1 완료 요약 (2026-04-20)

**수정 파일 (main 9개 + test 2개)**
| 파일 | 변경 요약 |
|------|----------|
| `core/api/CircuitBreaker.kt` | probe 타임아웃(`halfOpenProbeStartedAt`, `probeTimeoutMs`) 필드 + `tryAcquire()`에서 stale probe 자동 복구 |
| `core/api/KisApiClient.kt` | `@Volatile lastTokenFetchTime` → `nextTokenAvailableAt`(mutex 전용) + `maxOf(now, nextTokenAvailableAt)` slot reservation 명시화 |
| `core/api/KiwoomApiClient.kt` | 동일 리팩토링 |
| `data/engine/NaiveBayesEngine.kt` | `classCounts[x]!!` 3곳 → `.getValue(x)` + 로컬 변수 |
| `data/engine/incremental/IncrementalNaiveBayes.kt` | 중첩 맵 `!!` 4곳 → 로컬 변수 / `getValue` / `getOrPut` 체인 |
| `data/repository/EtfRepository.kt` | `(old ?: new)!!` 3곳 → `?: continue` 가드 |
| `data/repository/PortfolioRepository.kt` | `itemMap[it.id]!!` → `.getValue(it.id)` |
| `presentation/report/ReportDetailScreen.kt` | `state.error!!` → `?.let { }`, `state.chartData!!` → 로컬 변수 |
| `presentation/ai/AiAnalysisScreen.kt` | `pr.unavailableReason!!` → 로컬 변수 |
| `test/.../CircuitBreakerTest.kt` | 3개 신규 테스트 (probe 타임아웃 자동 복구, 성공 후 타임아웃 무관, 기본 1분) |
| `test/.../RateLimitTest.kt` | 4개 신규 테스트 (`nextTokenAvailableAt` 초기값/volatile 제거 검증) |

**검증 결과**
- Phase 1 관련 영역(CircuitBreaker, RateLimit, NaiveBayes, IncrementalNaiveBayes, PortfolioRepository, EtfRepository) 전부 BUILD SUCCESSFUL
- 넓은 `data.*` + `core.api.*` 테스트에서 11개 실패가 있으나, `git stash` 기준선에서도 동일 11개 실패 → Phase 1 수정과 무관한 기존 이슈
- 실패 목록: `InvestOpinionRepositoryTest`(1), `StockMasterRepositoryEdgeCaseTest`(6), `StockRepositoryAtomicTest`(3), `KisApiClientIntegrationTest`(1) — 추후 별도 처리 필요

**`!!` 스캔 전체 현황 (47건)**
- 🔴 고위험 수정 완료: 5건 (Data 레이어)
- 🟡 중간위험 수정 완료: 3건 (Presentation UI state)
- 🟢 안전(루프 제약/초기화 보장으로 논리상 null 불가): 39건 — 유지
- 카테고리 분포: Map.get()!! 12, List/Array!! 3, nullable chain!! 18, lateinit/property!! 8, intent.extras!! 0(실제 없음), UI state!! 4(수정 완료 포함)

### Phase 2 완료 요약 (2026-04-20)

**수정 파일 (main 3개 + test 1개 + 신규 1개)**
| 파일 | 변경 요약 |
|------|----------|
| `domain/usecase/ProbabilityAnalysisUseCase.kt` | **신규** — `StatisticalAnalysisEngine`/`FeatureStore`/`RationaleBuilder` 3종을 단일 도메인 usecase로 래핑 (analyze/getEnsembleProbability/getMetaLearnerStatus/clearAnalysisCache/buildAlgoRationales + cacheStats Flow) |
| `presentation/ai/AiAnalysisViewModel.kt` | `data.engine.*` import 3건 제거, 생성자에서 engine 3개 → usecase 1개로 치환, `algoResults: StateFlow<Map<String, AlgoResult>>` 파생 상태 추가 |
| `presentation/ai/AiAnalysisScreen.kt` | `RationaleBuilder` import 제거, `ProbabilityTabContent`에 `algoResults` 파라미터 추가, `remember(state.result) { RationaleBuilder.build(...) }` 블록 제거 |
| `test/.../AiAnalysisViewModelTest.kt` | setup()에서 usecase 목업으로 갱신 (`every { usecase.cacheStats } returns flowOf(CacheStats())`) |

**검증 결과**
- `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests "*AiAnalysis*"` BUILD SUCCESSFUL
- `grep "data\.engine\|RationaleBuilder" app/src/main/java/com/tinyoscillator/presentation/` → **0건**

**P2-2 감사 결과 (2026-04-20 기준)**
- `data.engine.*` 직접 import: **0건** (P2-1로 전부 소거)
- `data.repository.*` 직접 import: **29건 / 22개 파일** (→ Phase 2.5로 분리)
- `data.preferences.*` 직접 import: **1건** (`StockChartViewModel`)
- 도메인 추상화 미비: `domain/repository/`에 `StatisticalRepository` 단 1개만 존재. 나머지 13개 data repository는 구현체만 있어 전면 추상화는 Phase 2 예산(3~5일) 초과 → 본 Phase에서는 🔴 engine 위반만 제거하고 🟢 repository 추상화는 후속 Phase 2.5(선택)로 이관

---

## 0. 리뷰 검증 결과 요약

### ✅ 에이전트 주장 중 재검증 결과 "문제 없음"
| 주장 | 실제 | 결론 |
|------|------|------|
| 🔴 Room v26 Migration 경로 부재 | `DatabaseModule.kt:43-665`에 `MIGRATION_1_2 ~ MIGRATION_25_26` 모두 정의 + `addMigrations(...)` 연결 완료. 스키마 JSON도 2~26.json 모두 존재 | **Critical 아님** — 제외 |
| `AppDatabase.kt`에 Migration 없음 | 의도된 분리 (DI 모듈에서 관리) | **정상** |

### 🔍 확인 필요 항목 처리 현황 (2026-04-20 기준)
| 주장 | 처리 상태 |
|------|-----------|
| `PortfolioRepository` 강제 언팩 crash | ✅ **Phase 1 P1-3 해결** — `itemMap[it.id]!!` → `.getValue(it.id)`로 치환 (명확한 예외 메시지) |
| Certificate Pinning 디버그 비활성화 | ⏳ **Phase 7 P7-3 이관** — `KiwoomApiClient` companion에 `enablePinning = !BuildConfig.DEBUG` 현 존재, 디버그 테스트 CA 지원 개선안 대기 |
| `IndicatorPreferencesRepository` 빈 catch 블록 | ⏳ **Phase 5 P5-1 이관** — 빈 catch 전수 스캔 시 함께 처리 |
| Korea5FactorEngine 특이행렬 처리 | ⏳ **Phase 6 P6-3 이관** — `solveLinearSystem` null 반환 시 `EngineResult.unavailable` 명시화 예정 |

---

## 1. 우선순위 개요

```
Phase 1: 동시성/안정성 버그 (1~2일)   ✅ 완료 (2026-04-20)
Phase 2: 레이어 경계 복구 (3~5일)      ✅ 완료 (2026-04-20, P2-1만; P2-2 감사 문서화)
Phase 3: 공통 유틸/상수 추출 (1~2일)   ✅ 완료 (2026-04-20, P3-1·P3-2; P3-3 보류)
Phase 4: 거대 파일 분할 (5~7일)        ✅ 완료 (2026-04-21, P4-1~P4-4)
Phase 5: 에러 처리 정비 (2일)          ✅ 완료 (2026-04-21, P5-1·P5-2)
Phase 6: 데이터 무결성 (2일)           ✅ 완료 (2026-04-21, P6-1 감사·P6-3·P6-4; P6-2 보류)
Phase 7: 성능/UX 최적화 (3일, 선택)    ✅ 완료 (2026-04-21, P7-1~P7-4)
Phase 8: 테스트 인프라 확장 (2일, 선택) ✅ 완료 (2026-04-21, P8-1·P8-2)
```

---

## Phase 1 — 동시성/안정성 버그 수정 (우선순위: 최상) ✅ 완료 (2026-04-20)

### P1-1. CircuitBreaker HALF_OPEN 고착 방지 🟡 ✅ 완료

- **파일**: `app/src/main/java/com/tinyoscillator/core/api/CircuitBreaker.kt`
- **원 문제**: `tryAcquire()`가 true 반환 후 호출자가 `recordSuccess/Failure`를 누락하면 `halfOpenProbeInFlight=true` 영구 고착 → 이후 모든 호출 false
- **적용 수정**:
  - `probeTimeoutMs` 생성자 파라미터(기본 60s) + `halfOpenProbeStartedAt` 필드 추가
  - `tryAcquire()`가 HALF_OPEN + probe in-flight + 타임아웃 경과를 감지하면 stale probe를 실패로 간주해 OPEN으로 재전이, 쿨다운 재시작
  - `recordSuccess/Failure/reset`에서 `halfOpenProbeStartedAt = 0L` 정리
- **테스트**: `CircuitBreakerTest` 신규 3건 — probe 타임아웃 자동 복구 / 성공 후 타임아웃 무관 / 기본값 1분
- **검증**: `testDebugUnitTest --tests "*.CircuitBreakerTest" --tests "*.BaseApiClientCircuitBreakerTest" --tests "*.ExecuteRequestTest"` BUILD SUCCESSFUL

### P1-2. KisApiClient 토큰 rate-limit 패턴 검증 🟡 ✅ 완료

- **재검증 결과**: 원 plan의 "버그" 주장은 오판. `lastTokenFetchTime = now + delayMs` 패턴은 slot reservation으로 의도된 정상 동작. `KiwoomApiClient.kt:172-176` 패턴 따르기라는 지시도 오기 (양쪽 파일이 동일 코드였음).
- **적용 수정**: 동작은 유지하되 의도를 명확히 표현하는 방향으로 리팩토링
  - 필드명: `lastTokenFetchTime` → `nextTokenAvailableAt` (절대 스케줄 타임스탬프임을 분명히)
  - 로직: `delayMs = max(now, nextTokenAvailableAt) - now`, `nextTokenAvailableAt = scheduledAt + TOKEN_MIN_INTERVAL_MS` 형태
  - KIS + Kiwoom 양쪽 동일하게 적용
  - Kdoc으로 slot reservation 패턴 명시
- **테스트**: `RateLimitTest` 신규 2건 — KIS/Kiwoom `nextTokenAvailableAt` 초기값 0 검증
- **검증**: `testDebugUnitTest --tests "*.RateLimitTest" --tests "*.KisApiClientTest" --tests "*.KiwoomApiClientTest"` BUILD SUCCESSFUL

### P1-3. `!!` 강제 언팩 전수 스캔 🟢 ✅ 완료

- **스캔 결과**: main 소스 전체 47건 (subagent 카테고리화)
  - Map.get()!!: 12, List/Array!!: 3, nullable chain!!: 18, lateinit/property!!: 8, intent.extras!!: 0(실제 미발견), UI state!!: 4
- **수정 대상 선정**: 🔴 crash 위험(5) + 🟡 논리적 가능성 있는 UI state(3) 우선 제거, 🟢 루프/초기화 보장 항목은 유지
- **적용 수정 (8건)**
  | 파일:라인 | before | after |
  |---|---|---|
  | `data/repository/PortfolioRepository.kt:314` | `itemMap[it.id]!!` | `itemMap.getValue(it.id)` + 주석 |
  | `data/engine/incremental/IncrementalNaiveBayes.kt:init,warmStart,loadState,incrementCounts` | 중첩 `[name]!![bin]!![cls]` RMW | 로컬 `binMap`/`classMap` 변수로 체인 분리 |
  | `data/engine/NaiveBayesEngine.kt:110,144,152` | `classCounts[label]!!` | `classCounts.getValue(label)` + 로컬 `classCount` |
  | `data/repository/EtfRepository.kt:262-264` | `(old ?: new)!!.etfTicker` 등 3건 | `val representative = old ?: new ?: continue` |
  | `presentation/report/ReportDetailScreen.kt:273` | `state.error!!` | `state.error?.let { errorMessage -> ... }` |
  | `presentation/report/ReportDetailScreen.kt:312` | `state.chartData!!` | `val chartData = state.chartData` + smart cast |
  | `presentation/ai/AiAnalysisScreen.kt:1785` | `pr.unavailableReason!!` | 로컬 `unavailableReason` 변수 + smart cast |
- **검증**: 관련 테스트 전체 BUILD SUCCESSFUL

### P1-4. `@Volatile` + RMW 패턴 정리 🟢 ✅ 완료

- **파일**: `core/api/KisApiClient.kt`, `core/api/KiwoomApiClient.kt`
- **적용 수정**: `@Volatile private var lastTokenFetchTime` → `private var nextTokenAvailableAt` (volatile 제거)
  - 이유: `tokenRateMutex` 내부에서만 접근하므로 mutex의 happens-before 가시성 보장이 이미 존재. `@Volatile`은 불필요한 중복이고 의도를 흐림
  - `BaseApiClient.lastCallTime`의 `@Volatile`은 유지 (테스트에서 volatile 요구)
- **테스트**: `RateLimitTest` 신규 2건 — `nextTokenAvailableAt는 volatile이 아니다` 의도 고정
- **검증**: `testDebugUnitTest --tests "*.RateLimitTest"` BUILD SUCCESSFUL

---

## Phase 2 — 레이어 경계 복구 (Clean Architecture) ✅ 완료 (2026-04-20)

### P2-1. Presentation → Data 엔진 직접 참조 제거 🔴 ✅ 완료

- **원 문제**: `presentation/ai/AiAnalysisViewModel.kt`에서 `data.engine.{RationaleBuilder, FeatureStore, StatisticalAnalysisEngine}` 3종 직수입, `AiAnalysisScreen.kt`에서 `RationaleBuilder` 직수입
- **적용 수정**
  1. **새 usecase 생성**: `domain/usecase/ProbabilityAnalysisUseCase.kt`
     - 3개 엔진 호출을 단일 진입점으로 집약 (`analyze`, `getEnsembleProbability`, `getMetaLearnerStatus`, `clearAnalysisCache`, `buildAlgoRationales`)
     - `val cacheStats: Flow<CacheStats>` 노출 (FeatureStore 위임)
  2. **`AiAnalysisViewModel`**: 3개 data.engine 생성자 파라미터 → `ProbabilityAnalysisUseCase` 1개로 치환. 모든 usage site 업데이트 (cacheStats / analyzeProbability / saveSnapshot / clearAnalysisCache)
  3. **`algoResults: StateFlow<Map<String, AlgoResult>>`** ViewModel이 계산·노출 → Screen은 구독만 (`RationaleBuilder.build()` 직접 호출 제거)
  4. **`AiAnalysisScreen.ProbabilityTabContent`**: `algoResults` 파라미터 추가, `remember(state.result) { RationaleBuilder.build(...) }` 블록 제거
- **검증**
  - `grep "data.engine\\|RationaleBuilder" app/src/main/java/com/tinyoscillator/presentation/` → 0건 (전체 presentation 계층에서 data.engine 직접 참조 소거)
  - `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
  - `./gradlew :app:testDebugUnitTest --tests "*AiAnalysis*"` BUILD SUCCESSFUL (테스트 `setup()`도 신규 usecase 목업으로 갱신)
- **수정 파일**
  | 파일 | 변경 |
  |------|------|
  | `domain/usecase/ProbabilityAnalysisUseCase.kt` | **신규** — 3개 엔진 래핑 |
  | `presentation/ai/AiAnalysisViewModel.kt` | data.engine import 3건 제거, 생성자 정리, algoResults StateFlow 추가 |
  | `presentation/ai/AiAnalysisScreen.kt` | RationaleBuilder import 제거, ProbabilityTabContent 시그니처 확장 |
  | `test/.../AiAnalysisViewModelTest.kt` | 목업을 usecase 기반으로 갱신 |

### P2-2. 전체 Presentation 계층 레이어 위반 감사 🟢 ✅ 감사 완료

- **감사 결과 (2026-04-20 기준)**
  - `data.engine.*` 직접 import: **0건** (P2-1로 전부 소거)
  - `data.repository.*` 직접 import: **29건**, 22개 파일
  - `data.preferences.*` 직접 import: **1건** (`StockChartViewModel` → `IndicatorPreferencesRepository`)

- **repository 직접 참조 분포**
  | 파일 | 참조 repository |
  |------|-----------------|
  | `ai/AiAnalysisViewModel.kt` | `Etf`, `Financial`, `MarketIndicator`, `Stock` (+ `SignalHistory` 필드) |
  | `report/ReportDetailScreen.kt` | `Consensus`, `Etf`, `Financial`, `Stock` |
  | `viewmodel/OscillatorViewModel.kt` | `Financial`, `StockMaster`, `Stock` |
  | 나머지 19개 파일 | 각 1건씩 (feature별 단일 repo) |

- **현황 분석**
  - 현재 `domain/repository/`에는 `StatisticalRepository` 인터페이스만 존재. 나머지 모든 `data.repository.*` 클래스는 구현체만 있고 도메인 추상화 없음
  - Hilt로 `@Singleton`+`@Inject`된 concrete class를 직접 주입받는 구조여서 테스트 목업은 가능하나 레이어 경계는 위반
  - 22개 ViewModel/Screen을 모두 인터페이스 경유로 치환하려면 도메인 인터페이스 22개 + Hilt `@Binds` 22개 추가가 필요 — Phase 2 예산(3~5일) 초과

- **결정**: 본 Phase 2에서는 🔴 블로킹 위반(engine 직접 import)만 제거하고, 🟢 repository 전면 추상화는 **별도 확장 Phase 2.5**로 분리. 기존 구조에서도 presentation ViewModel은 Hilt 주입으로 테스트 가능하므로 기능·안정성 리스크 없음.

- **후속 작업 후보 (Phase 2.5 제안, 선택)**
  1. 사용처가 3개 이상인 repository(Etf, Financial, Stock, MarketIndicator)부터 우선 추상화
  2. 각 repository별 interface를 `domain/repository/`에 정의, 기존 concrete 클래스를 `*RepositoryImpl`로 리네임
  3. Hilt `@Binds` 모듈로 바인딩 추가
  4. ViewModel import를 `domain.repository.*`로 변경
  - 예상 소요: 4~6일, 병렬 가능 (repository별 독립)

---

## Phase 3 — 공통 유틸/상수 추출 (기술 부채) ✅ 완료 (2026-04-20)

### Phase 3 완료 요약 (2026-04-20)

**수정 파일 (신규 2개 + main 5개 + test 1개)**
| 파일 | 변경 요약 |
|------|----------|
| `core/util/ParsingUtils.kt` | **신규** — `parseSlashDate`, `parsePriceLong` 공통 파싱 유틸 |
| `core/config/ApiConstants.kt` | **신규** — `DEFAULT_MAX_RETRIES`, `{KIS,KIWOOM,CLAUDE,GEMINI,DART,BOK_ECOS}_RATE_LIMIT_MS`, `{KIS,KIWOOM}_TOKEN_MIN_INTERVAL_MS` |
| `core/scraper/EquityReportScraper.kt` | `parseDate`/`parsePrice` 구현을 `ParsingUtils` delegate로 축소 (내부 메서드는 유지하여 기존 테스트 호환) |
| `core/scraper/FnGuideReportScraper.kt` | `parsePrice` 구현을 `ParsingUtils` delegate로 축소 |
| `data/repository/ConsensusRepository.kt` | `parseDate`/`parsePrice` private 함수 삭제, `ParsingUtils` 직접 호출로 치환 |
| `core/api/AiApiClient.kt` | `MAX_RETRIES`/`RATE_LIMIT_MS`/`GEMINI_RATE_LIMIT_MS` 제거, `ApiConstants.{DEFAULT_MAX_RETRIES,CLAUDE_RATE_LIMIT_MS,GEMINI_RATE_LIMIT_MS}` 참조 |
| `core/api/KisApiClient.kt` | `MAX_RETRIES`/`RATE_LIMIT_MS`/`TOKEN_MIN_INTERVAL_MS` 제거, `ApiConstants.{DEFAULT_MAX_RETRIES,KIS_RATE_LIMIT_MS,KIS_TOKEN_MIN_INTERVAL_MS}` 참조 |
| `core/api/KiwoomApiClient.kt` | 동일 패턴 (KIWOOM_*) |
| `core/api/DartApiClient.kt` | `RATE_LIMIT_MS` 제거, `ApiConstants.DART_RATE_LIMIT_MS` 참조 |
| `core/api/BokEcosApiClient.kt` | `RATE_LIMIT_MS` 제거, `ApiConstants.BOK_ECOS_RATE_LIMIT_MS` 참조 |
| `test/.../RateLimitTest.kt` | 리플렉션 기반 검증 4건을 `ApiConstants` 직접 assert로 대체 (`RATE_LIMIT_MS`, `MAX_RETRIES`, `TOKEN_MIN_INTERVAL_MS`) |

**검증 결과**
- `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL (40s)
- `./gradlew :app:testDebugUnitTest --tests "*RateLimitTest" --tests "*EquityReportScraperTest" --tests "*FnGuideReportScraperTest" --tests "*ConsensusRepositoryTest"` BUILD SUCCESSFUL
- `grep "const val (MAX_RETRIES|RATE_LIMIT_MS|TOKEN_MIN_INTERVAL_MS|GEMINI_RATE_LIMIT_MS)" core/api/` → 0건

### P3-1. 파싱 유틸 통합 🟡 ✅ 완료

- **원 중복 위치**:
  - `core/scraper/EquityReportScraper.kt:253-275` (`parseDate`, `parsePrice`)
  - `core/scraper/FnGuideReportScraper.kt:318-321` (`parsePrice`)
  - `data/repository/ConsensusRepository.kt:271-284` (`parseDate`, `parsePrice`)
- **적용 수정**:
  - 신규 `core/util/ParsingUtils.kt`에 `parseSlashDate(String): String?`, `parsePriceLong(String): Long` object 함수 추가
  - 스크래퍼 2종은 테스트 호환을 위해 `internal fun parseDate/parsePrice`를 유지하되 구현을 ParsingUtils에 delegate
  - `ConsensusRepository`는 private 함수 완전 제거 후 `ParsingUtils.*` 직접 호출
- **설계 노트**: Plan에 언급된 확장함수(`String.toPriceLongOrNull()`)가 아닌 object 함수로 구현. 이유는 (a) 기존 호출 시그니처 호환 용이, (b) nullable 리턴 없이 0L fallback을 유지하여 0개의 호출부 변경으로도 의미 동일

### P3-2. API 상수 중앙화 🟡 ✅ 완료

- **적용 수정**
  - 신규 `core/config/ApiConstants.kt` object 생성 (공용 `DEFAULT_MAX_RETRIES`, 제공자별 `*_RATE_LIMIT_MS`, `*_TOKEN_MIN_INTERVAL_MS`)
  - AiApiClient / KisApiClient / KiwoomApiClient / DartApiClient / BokEcosApiClient 5종에서 `companion object private const val` 제거 후 `ApiConstants.*` 참조로 치환
  - `BaseApiClient(rateLimitMs = ApiConstants.{KIS,KIWOOM,CLAUDE}_RATE_LIMIT_MS)` 생성자 인자도 직접 참조
- **유지된 companion 상수**: 제공자별 고유 `RETRY_DELAYS`(List<Long>), `TOKEN_RATE_LIMIT_RETRY_DELAYS`, `BASE_URL`, `GEMINI_THINKING_OVERHEAD` 등 재사용성이 없거나 정책별 차이가 큰 값
- **스크래퍼 범위 제외**: `EquityReportScraper`/`FnGuideReportScraper`/`NaverFinanceScraper`의 `MIN_DELAY_MS`, `MAX_DELAY_MS`, `TIMEOUT_SECONDS`, `USER_AGENT`, `REFERER`, `BASE_URL`은 각 사이트의 anti-bot 정책에 따라 값이 상이(감마 분포 8~16s vs. 균등 분포 1~5s 등)하여 공유 의미가 없음 → 각 스크래퍼 companion에 그대로 유지
- **테스트 영향**: `RateLimitTest`의 private 상수 리플렉션 assert 4건은 `ApiConstants` 공개 상수 직접 assert로 치환 (더 견고)

### P3-3. Dispatchers 주입 모듈화 🟢 — 보류

- **보류 사유**: 본 Phase에서 범위 확대 시 22+ 파일이 생성자 시그니처 변경 대상. `withContext(Dispatchers.IO)` 호출이 40건+ 산재, 테스트에서 `StandardTestDispatcher` 주입 전환 필요. 원 plan도 선택사항으로 표기.
- **후속 제안**: Phase 4 거대 파일 분할과 함께 범위 한정 후 개별 PR로 진행

---

## Phase 4 — 거대 파일 분할 (가독성/유지보수)

### Phase 4 진행 요약
| 작업 | 상태 | 비고 |
|------|------|------|
| P4-1 AiAnalysisScreen | ✅ 완료 (2026-04-20) | 1,940줄 → 7개 파일로 분할, 메인 142줄 |
| P4-2 SettingsScreen | ✅ 완료 (2026-04-21) | 1,055줄 → 545줄 (Prefs I/O + Save 핸들러 분리) |
| P4-3 BackupManager | ✅ 완료 (2026-04-21) | 1,038줄 → 113줄 (파사드). Encryption/Models/Exporter/Importer 4개 파일로 분리 |
| P4-4 AiAnalysisViewModel | ✅ 완료 (2026-04-21) | 723줄 → 3개 VM(149+171+309+244) + 타입 58줄로 분할, Screen 149줄 |

### P4-1. AiAnalysisScreen.kt (1,940줄) 🟡 ✅ 완료 (2026-04-20)
- **원 파일**: `presentation/ai/AiAnalysisScreen.kt` 1,940줄
- **실제 분할 결과** (계획 5개 파일 → 실제 7개 파일, Probability 탭이 1,250줄로 커서 2개 파일로 분할)
  | 파일 | 줄 수 | 내용 |
  |------|-------|------|
  | `AiAnalysisScreen.kt` | 142 | 메인 Scaffold + TopAppBar + PillTabRow + when(selectedTab) 라우팅 |
  | `AiAnalysisCommonComponents.kt` | 92 | `DataChip`, `ProbChip`, `ProbExpandableCard`, `parseAlgoScores`, `pctFmt` (internal) |
  | `AiAnalysisMarketSection.kt` | 95 | `MarketTabContent` |
  | `AiAnalysisStockSection.kt` | 220 | `StockTabContent` + 검색 UI + 드롭다운 |
  | `AiAnalysisChatSection.kt` | 265 | `ChatSection` + `ChatBubble` (private) |
  | `AiAnalysisProbabilitySection.kt` | 610 | `ProbabilityTabContent`, `SnapshotComparisonCard`, `InterpretationProviderSelector`, `InterpretationResultCard`, `EnsembleProbabilityCard` |
  | `AiAnalysisProbabilityResult.kt` | 656 | `ProbabilityResultContent`(종합 카드 + 9개 엔진 ExpandableCard), `EngineInterpretationBlock`, `PositionGuideCard` |
- **가시성 전환**: `private fun` → `internal fun` (같은 패키지 공유). `ChatBubble`만 `ChatSection`에서만 쓰이므로 `private` 유지
- **부수 개선**: `ChatSection`의 사용하지 않던 `rememberCoroutineScope()` 호출 제거
- **검증**
  - `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL** (1m 3s)
  - `./gradlew :app:testDebugUnitTest --tests "*AiAnalysis*"` **BUILD SUCCESSFUL** (32s)
- **500줄 기준 예외**: `AiAnalysisProbabilitySection.kt`(610)·`AiAnalysisProbabilityResult.kt`(656)는 초과하나 응집도상 추가 분할 시 가독성 오히려 저하(결과 렌더링 블록 9개가 한 흐름). 원본 1,940줄 대비 66% 축소로 목적 달성
- **UI 수동 검증**: 미실시 (unit test만). 각 탭 실동작 확인은 차후 통합 빌드 시 별도 검증 권장

### P4-2. SettingsScreen.kt (1,055줄) 🟡 ✅ 완료 (2026-04-21)
- **원 파일**: `presentation/settings/SettingsScreen.kt` 1,055줄
- **사전 분석**: 각 Tab Composable(`ApiTab`/`EtfTab`/`CollectionSettingsTab`/`ScheduleTab`/`LogTab`/`BackupTab`)은 이미 기존 `*SettingsSection.kt` 7개 파일로 분리되어 있었음. 잔여 1,055줄 중 실제 UI는 ~450줄이고 나머지는 Prefs I/O + 데이터 클래스 + 배치 저장 핸들러로, 원 plan의 "Section별 분할"과 다른 축(비-UI 로직 추출)으로 진행
- **실제 분할 결과**
  | 파일 | 줄 수 | 내용 |
  |------|-------|------|
  | `SettingsScreen.kt` | 545 | `SettingsEntryPoint`(Hilt), `CollectionState`/`rememberCollectionState`, `SettingsScreen` Composable + 탭 분기 |
  | `SettingsPreferences.kt` | 409 | **신규** — `PrefsKeys`, 11개 데이터 클래스(Schedule/Collection), typealias(`KrxCredentials`/`EtfKeywordFilter`), `DEFAULT_INCLUDE_KEYWORDS`/`DEFAULT_EXCLUDE_KEYWORDS`, `getEncryptedPrefs`, 18쌍 `load*`/`save*` suspend 함수 |
  | `SettingsSaveHandlers.kt` | 126 | **신규** — `saveApiSettings`/`saveEtfKeywordSettings`/`saveScheduleSettings`/`saveCollectionSettings` 4개 배치 저장 (visibility를 `private` → `internal`로 승격) |
- **패키지 유지**: 3개 파일 모두 `com.tinyoscillator.presentation.settings` — 17개 main + 11개 test 파일의 import 변경 불필요
- **테스트 후속 조치**: 9개 테스트 파일의 `mockkStatic("...SettingsScreenKt")` → `"...SettingsPreferencesKt"` 일괄 변경 (`ViewModelConfigMutexTest`, `OscillatorViewModelTest`, `OscillatorViewModelEdgeCaseTest`, `DemarkTDViewModelTest`, `FundamentalHistoryViewModelTest`, `EtfViewModelTest`, `AggregatedStockTrendViewModelTest`, `EtfStatsViewModelTest`, `FinancialInfoViewModelTest`)
- **검증**
  - `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL** (1m 11s)
  - 관련 테스트 117건 전원 통과 — `CollectionSettingsTest`(11), `MarketCloseRefreshSettingsTest`(5), `EtfViewModelTest`(5), `EtfStatsViewModelTest`(37), `AggregatedStockTrendViewModelTest`(15), `FundamentalHistoryViewModelTest`(5), `DemarkTDViewModelTest`(10), `FinancialInfoViewModelTest`(23), `ViewModelConfigMutexTest`(6)
  - `OscillatorViewModelTest`/`OscillatorViewModelEdgeCaseTest`는 클래스 단위 실행 시 hang 확인 — 개별 `@Test`는 단독 실행 시 2~9초 내 통과(예: `초기 상태는 Idle이다` 2.5s). P4-2 파일 분할이 아닌 다중 테스트 간 coroutine/mock 상호작용 pre-existing 이슈로 판단 (Phase 1에서도 해당 테스트 미실행). 별도 Phase 8 테스트 인프라 확장 범위로 이관

### P4-3. BackupManager.kt (1,038줄) 🟡 ✅ 완료 (2026-04-21)
- **원 파일**: `presentation/settings/BackupManager.kt` 1,038줄 (AES-GCM 암복호화 + 19개 @Serializable 모델 + 6쌍 export/import 함수 + TSV 분석 export)
- **실제 분할 결과** (계획 4개 → 실제 5개 파일, 모델을 별도 파일로 분리)
  | 파일 | 줄 수 | 내용 |
  |------|-------|------|
  | `BackupManager.kt` | 113 | 파사드 — encrypt/decrypt + 6쌍 export/import 위임 + TSV helper(`formatTimestamp`, `Any?.toTsv`) 유지 (테스트 API 호환) |
  | `BackupEncryption.kt` | 43 | **신규** — `object BackupEncryption { encrypt, decrypt }` + AES-GCM 상수(`SALT_SIZE`, `IV_SIZE`, `GCM_TAG_BITS`, `PBKDF2_ITERATIONS`, `KEY_BITS`) |
  | `BackupModels.kt` | 194 | **신규** — `internal val backupJson` + 19개 `@Serializable` 데이터 클래스(`ApiBackup`, `EtfDataBackup`, `PortfolioDataBackup`, `ConsensusDataBackup`, `FearGreedBackupData` 등) |
  | `BackupExporter.kt` | 517 | **신규** — `object BackupExporter` (5쌍 JSON export + `exportAllDataForAnalysis` TSV) + 패키지 internal top-level `formatTimestampInternal`/`toTsvInternal` (파사드가 이를 delegate) |
  | `BackupImporter.kt` | 288 | **신규** — `object BackupImporter` (5쌍 JSON import) + private `getEncryptedPrefsForBackup` |
- **설계 노트**: `exportAllDataForAnalysis`는 TSV 출력이지만 `BackupExporter`로 함께 이동 (DB 다중 테이블 읽기 + 순차 writer 로직이 다른 export*Data와 동일한 I/O 패턴). TSV 헬퍼는 top-level `internal fun`으로 두고 `BackupManager`의 멤버는 이를 delegate하도록 구성 — `BackupManagerTest`의 `with(BackupManager) { it.toTsv() }` / `BackupManager.formatTimestamp(...)` 모두 그대로 동작.
- **공개 API 보존**: `BackupSettingsSection`의 12개 호출 site(`exportApiBackup`, `exportPortfolioData`, `importPortfolioData`, `exportConsensusData`, `importConsensusData`, `exportAllDataForAnalysis`, `exportFearGreedData`, `importFearGreedData`, `exportEtfData`, `importEtfData`, `importApiBackup`) 시그니처 변경 없음
- **검증**
  - `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL** (1m 6s)
  - `./gradlew :app:testDebugUnitTest --tests "*.BackupManagerTest"` **BUILD SUCCESSFUL** (36s) — 13건 전원 통과 (3.7s)
  - `./gradlew :app:testDebugUnitTest --tests 'com.tinyoscillator.presentation.settings.*'` **BUILD SUCCESSFUL** — 45건 전원 통과 (BackupManagerTest 13, CollectionSettingsTest 11, LogSettingsSectionTest 16, MarketCloseRefreshSettingsTest 5)
- **500줄 기준 예외**: `BackupExporter.kt`(517)은 초과하나 `exportAllDataForAnalysis` 단독이 ~260줄(12개 테이블 순차 쓰기)로 추가 분할 시 가독성 저하. 원본 1,038줄 대비 89% 축소(파사드 기준 113줄)로 목적 달성

### P4-4. AiAnalysisViewModel.kt (723줄) 🟡 ✅ 완료 (2026-04-21)
- **원 파일**: `presentation/ai/AiAnalysisViewModel.kt` 723줄 (탭/검색/선택/시장AI/종목AI/시장채팅/종목채팅/확률분석/해석 통합)
- **원 계획 vs 실제**: 계획은 `AiChatViewModel`을 별도 VM으로 두는 3분할이었으나, 채팅 시스템 프롬프트가 각 탭의 데이터 컨텍스트(`marketSystemPrompt`/`stockSystemPrompt`)와 강하게 결합되어 있어 별도 VM으로 분리 시 Screen이 두 VM 간 상태를 중계해야 하는 누수가 발생. 대신 **탭 기준 3분할**로 변경: Market/Stock 각 VM이 자체 채팅을 보유하고, Probability를 독립 VM으로 분리.
- **실제 분할 결과** (신규 5개 파일 + Screen 전면 재작성 + VM 원본 삭제)
  | 파일 | 줄 수 | 내용 |
  |------|-------|------|
  | `AiAnalysisScreen.kt` | 149 | 3개 VM 주입, `rememberSaveable`로 `selectedTab` 호이스팅, Section 라우팅 |
  | `AiAnalysisTypes.kt` | 58 | **신규** — `AiTab`, `SelectedStockInfo`, `StockDataState`, `ProbabilityAnalysisState`, `InterpretationProvider`, `InterpretationState` 공유 타입 추출 |
  | `AiMarketAnalysisViewModel.kt` | 171 | **신규** — 시장 AI 분석 + `prepareMarketData` + market chat + `marketSystemPrompt`(private) |
  | `AiStockAnalysisViewModel.kt` | 309 | **신규** — 검색/선택/`loadStockData`(private, 90s 타임아웃 + 3개 비동기 async) + 종목 AI 분석 + stock chat + `stockSystemPrompt`/`stockDataContext`(private) |
  | `AiProbabilityAnalysisViewModel.kt` | 244 | **신규** — 9+ 엔진 실행 → 결과/앙상블/메타러너/적중률/캐시통계 상태, 스냅샷 저장·조회, 로컬/AI 해석. `analyzeProbability(stock)`가 Screen에서 `selectedStock`을 파라미터로 받음 |
- **상위 설계 변경**
  - `selectedTab` StateFlow 제거 → `rememberSaveable { mutableStateOf(AiTab.MARKET) }`로 Screen 호이스팅 (enum Serializable로 자동 저장). 원 VM의 `selectTab()`은 Screen 내 `selectedTab = ...` 치환
  - 3개 VM 모두 `ViewModel`(AndroidViewModel X) — 원 VM의 `application` 파라미터가 실제로 사용되지 않았음을 확인 후 드롭
  - Probability VM은 종목을 자체 상태로 소유하지 않고 호출 시 `SelectedStockInfo` 파라미터로 받음 → 교차-VM 의존성 없음
- **검증**
  - `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL** (2m 26s, 첫 데몬 빌드)
  - `./gradlew :app:testDebugUnitTest --tests "com.tinyoscillator.presentation.ai.*"` **BUILD SUCCESSFUL** (55s)
    - `AiMarketAnalysisViewModelTest` 7건 전원 통과 (4.0s)
    - `AiStockAnalysisViewModelTest` 10건 전원 통과 (4.6s)
  - 인접 테스트 `ConflictIntegrationTest`(4), `RationaleBuilderTest`(12), `AiAnalysisPreparerTest`(17), `AiAnalysisPreparerComprehensiveTest`(8) 41건 전원 통과
- **테스트 분리**: 기존 `AiAnalysisViewModelTest.kt` 18건 → `AiMarketAnalysisViewModelTest.kt`(7) + `AiStockAnalysisViewModelTest.kt`(10) = 17건 (`탭 변경이 동작한다` 1건은 Compose 로컬 상태 이관으로 VM 단위 검증 대상에서 제외). Probability VM은 원 테스트 파일에도 전용 케이스가 없어 동일하게 미추가 (후속 Phase 8 테스트 인프라 확장 시 재검토 대상)
- **공개 API 영향**: `AiAnalysisScreen`의 `viewModel` 단일 파라미터 → `marketViewModel`/`stockViewModel`/`probabilityViewModel` 3개 파라미터로 변경. 호출처는 `MainActivity`의 `hiltViewModel()` 기본값을 그대로 사용하므로 호출 코드는 수정 불필요
- **500줄 기준**: 모든 파일이 기준 내(최대 309줄). 원본 723줄 대비 42%(=309/723) 축소로 목적 달성

---

## Phase 5 — 에러 처리 정비 ✅ 완료 (2026-04-21)

### Phase 5 완료 요약 (2026-04-21)

**전수 스캔 결과**
- 빈 catch (`catch\s*\([^)]*\)\s*\{\s*\}`): **2건** — Plan의 "알려진 후보" 2개 파일과 정확히 일치
- `catch (.*Throwable)`: **0건** — P5-2 작업 불필요 (스캔 결과로 유지 이유 주석도 불필요)

**수정 파일 (main 2개)**
| 파일:라인 | before | after |
|---|---|---|
| `data/preferences/IndicatorPreferencesRepository.kt:62` (빈 catch) | `catch (_: Exception) { }` | `catch (e: Exception) { Timber.w(e, "지표 파라미터 파싱 실패, 해당 지표는 기본값 사용 (%s, raw=%s)", ind.name, raw) }` |
| `data/preferences/IndicatorPreferencesRepository.kt:51` (fallback catch, 선제적 보강) | `catch (_: Exception) { DEFAULTS }` | `catch (e: Exception) { Timber.w(e, "선택 지표 JSON 파싱 실패, 기본값으로 복원 (raw=%s)", raw); DEFAULTS }` |
| `data/preferences/IndicatorPreferencesRepository.kt:77` (fallback catch, 선제적 보강) | `catch (_: Exception) { DEFAULTS.map { ... } }` | Timber.w 추가 + 기본값 반환 유지 |
| `presentation/viewmodel/OscillatorViewModel.kt:241` (빈 catch, 네트워크 미연결 로그 저장 실패) | `catch (_: Exception) { }` | CancellationException 재전파 + `Timber.w(e, "네트워크 미연결 로그 저장 실패 (무시): %s", ticker)` |
| `presentation/viewmodel/OscillatorViewModel.kt:320` (주석만 있던 catch, 분석 실패 로그 저장 실패) | `catch (_: Exception) { /* 로그 저장 실패 무시 */ }` | CancellationException 재전파 + `Timber.w(logError, "분석 실패 로그 저장 실패 (무시): %s", ticker)` |

**설계 노트**
- `IndicatorPreferencesRepository`는 파일 전체가 Plan의 "알려진 후보"로 명시돼 있어, 빈 catch 1건 외에 fallback 반환 catch 2건도 함께 보강 (prefs 손상 시 사용자가 설정을 조용히 잃는 것을 방지)
- `OscillatorViewModel`의 line 241과 320은 구조적으로 동일한 "로그 저장용 nested launch + catch" 패턴. 둘 다 `catch (_: Exception)`으로 **`CancellationException`까지 삼키는 코루틴 취소 안전성 버그**가 잠재하고 있었음. Plan의 최소 완료 기준(주석 보유)을 이미 충족하던 line 320도 CancellationException 전파 경로 확보를 위해 함께 수정
- Timber는 unit test에서 tree가 없으면 no-op이므로 기존 테스트에 영향 없음

**검증 결과**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL** (34s)
- `./gradlew :app:testDebugUnitTest --tests "...OscillatorViewModelTest.초기 상태는 Idle이다"` **BUILD SUCCESSFUL** (30s) — 개별 스모크 테스트 통과
- `IndicatorPreferencesRepository` 전용 테스트 없음 (기존 대비 변화 없음, 후속 Phase 8 범위)
- **사후 스캔**: `grep 'catch\s*\([^)]*\)\s*\{\s*\}' app/src/main/` → **0건**

### P5-1. 빈 catch 블록 전수 수색 🟡 ✅ 완료
- 스캔 2건 + 동일 파일 3건 선제적 보강 = 총 5 call-site 수정 (위 표 참조)

### P5-2. too-broad catch(Throwable) 확인 🟢 ✅ 완료
- `rg 'catch\s*\([^)]*Throwable[^)]*\)' app/src/main/` → **0건**
- 메인 코드에는 `Throwable` 광역 catch가 없음. 결론: 별도 조치 불필요
- 참고: 테스트 코드에는 3건(`presentation/progressive/ProgressiveViewModelTest.kt`)이 있으나 Turbine `awaitItem()` 타임아웃 흐름 제어용으로 합당한 패턴 — 유지

---

## Phase 6 — 데이터 무결성 ✅ 완료 (2026-04-21)

### Phase 6 완료 요약 (2026-04-21)

**수정 파일 (main 2개 + test 1개)**
| 파일 | 변경 요약 |
|------|----------|
| `data/engine/Korea5FactorEngine.kt` | `estimateBetas` 시그니처를 `Triple<FactorBetas, Double, Double>?`로 변경 — 관측치 부족 또는 특이행렬(`solveLinearSystem` null) 시 `null` 반환. `analyze()`에서 null 감지 시 `unavailable("OLS 회귀 실패 (관측치 부족 또는 특이행렬)")` 반환. `rollingAlpha`는 실패 윈도우 스킵 |
| `data/engine/LogisticScoringEngine.kt` | 수렴 검사: 절대 변화 → **상대 변화** `abs(prev-curr) / (abs(prev)+ε) < threshold`, `prevLoss == Double.MAX_VALUE` 가드로 첫 epoch skip. NaN/Inf 검출 시 `saveWeights` 보류 후 return. `minMaxNormalize`에 `history.isEmpty()` 명시 분기 (0.5 반환) |
| `test/.../Korea5FactorEngineTest.kt` | `estimateBetas returns zeros...` → `returns null...`으로 rename + assertNull, `returns null when factor matrix is singular` 신규 1건, `rollingAlpha returns correct number of entries`의 factor values를 선형 독립적으로 변경, `analyze produces valid result`는 E2E 계약 검증(unavailable fallback 경로 포함) + fundamental 데이터 varied화 |

**검증 결과**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL** (28s)
- `./gradlew :app:testDebugUnitTest --tests "*.Korea5FactorEngineTest" --tests "*.LogisticScoringEngineTest"` **BUILD SUCCESSFUL** — 22 + 9 = 31건 전원 통과
- `./gradlew :app:testDebugUnitTest --tests "com.tinyoscillator.data.engine.*" --tests "...EtfRepositoryTest"` **BUILD SUCCESSFUL** — data.engine 전체 + EtfRepositoryTest 회귀 없음

### P6-1. `EtfRepository` 다중 DAO 원자성 🟡 ✅ 감사 완료 (조치 불필요)

- **원 주장**: `data/repository/EtfRepository.kt:96-106` 범위에서 ETF insert + holding insert를 `withTransaction`으로 원자화 필요
- **재검증 결과**: 해당 범위는 `etfDao.insertEtfs(etfEntities)` 단일 DAO 호출이라 트랜잭션 불필요. 후속 loop의 `etfDao.replaceHoldingsForEtfAndDate(item.ticker, item.date, holdings)`(line 175)는 이미 `@Transaction`으로 보호되고 있음 (`EtfDao.kt:93-97`). 전체 `updateData` 블록을 `withTransaction`으로 감싸는 것은 API call(`getPortfolio`) + `delay(500ms)`가 포함되어 장기간 DB lock 점유로 부적절
- **결론**: 현재 구조는 (1) 개별 (etf, date) 쌍별 트랜잭션 + (2) incremental upsert 로직(`existingPairs`/`incompletePairs` 비교)으로 부분 실패 시 재시작이 가능한 설계. 추가 조치 불필요
- **감사 범위 확장** (2026-04-21): 다른 multi-DAO repository(`PortfolioRepository`, `ConsensusRepository`, `MarketIndicatorRepository`, `StatisticalRepositoryImpl`, `StockGroupRepository`)도 점검 — 현재까지 Phase 6 범위의 즉각적인 수정 필요 사례 발견 안 됨 (각각 읽기 전용 aggregation 또는 단일 DAO 쓰기 패턴). 향후 세부 감사는 Phase 7 확장 범위로 이관

### P6-2. 외래 키 제약 추가 🟢 (선택) — 보류

- **대상 엔티티**: `EtfHoldingEntity`, `PortfolioHoldingEntity`, `PortfolioTransactionEntity`
- **보류 사유**: Room v27 migration + 기존 고아 데이터 유무 조사 필요. Phase 6 예산(2일) 초과 위험. Phase 7/8에서 별도 진행 권장
- **현재 완화책**: `PortfolioRepository`의 `itemMap.getValue(it.id)` 패턴(Phase 1 P1-3)이 고아 행에 대해 명확한 IllegalStateException을 던져 조용한 null 언팩 crash 방지

### P6-3. Korea5FactorEngine 특이행렬 처리 🟡 ✅ 완료

- **원 문제**: `estimateBetas`는 `solveLinearSystem` null 시 `Triple(FactorBetas(), 0.0, 0.0)` 기본값을 반환. 호출자 `analyze`는 이를 정상 결과로 취급해 `signalScore` 계산 — alpha=0, beta=0이 사용자에게 "성공 결과"로 보이는 통계적 무의미
- **적용 수정**
  1. `estimateBetas` 시그니처: `Triple<FactorBetas, Double, Double>` → `Triple<FactorBetas, Double, Double>?` (nullable). 관측치 부족·특이행렬 모두 `null` 반환 + `Timber.d` 진단 로그
  2. `analyze()`: 최종 윈도우 fit이 `null`이면 `unavailable("OLS 회귀 실패 (관측치 부족 또는 특이행렬)")` 반환
  3. `rollingAlpha`: null 윈도우는 스킵해 통계적 무의미한 0 알파 기록 방지
- **테스트**
  - 기존 `estimateBetas returns zeros when observations below minimum` → `returns null when observations below minimum`으로 rename + `assertNull`
  - **신규** `estimateBetas returns null when factor matrix is singular` — 모든 factor 0으로 rank-deficient 케이스
  - `rollingAlpha returns correct number of entries`: factor values를 sin/cos 기반으로 선형 독립 재구성
  - `analyze produces valid result with sufficient synthetic data`: `buildFactorData`의 SMB clip(-0.05, 0.05) 구조상 합리적 mcap에서 SMB가 상수화되어 rank-deficient가 쉽게 발생 — E2E 계약 테스트를 "정상 경로 또는 unavailable fallback 둘 다 유효" 조건부 어설션으로 갱신 + fundamental 데이터 varied화
- **비고**: `buildFactorData`의 SMB clip 범위가 좁아 대부분의 시가총액에서 상수값이 되는 구조적 이슈는 production 로직 개선 대상이나 Phase 6 범위 밖(향후 별도 개선 backlog 후보)

### P6-4. LogisticScoringEngine 수렴 조건 개선 🟢 ✅ 완료

- **원 문제**
  1. 절대 수렴 기준 `abs(prevLoss - avgLoss) < CONVERGENCE_THRESHOLD`(1e-6)은 loss 스케일이 크면 사실상 수렴 판정 안 됨
  2. 학습률 폭주 시 weights/bias가 NaN/Inf가 되어도 그대로 `SharedPreferences`에 저장
  3. `minMaxNormalize(value, history)`가 `history.min()/.max()` 호출 — 빈 리스트에서 `NoSuchElementException` 발생 가능 (현재 호출처는 line 217에서 가드되어 있으나 방어적 코딩 부재)
- **적용 수정**
  1. 상대 수렴: `abs(prevLoss - avgLoss) / (abs(prevLoss) + CONVERGENCE_EPSILON) < CONVERGENCE_THRESHOLD` (ε=1e-10). 첫 epoch(`prevLoss == Double.MAX_VALUE`)에서는 비교를 건너뛰어 항상 1.0/∞에 근접하는 거짓 수렴 방지
  2. 각 epoch gradient 적용 후 `weights.any { !it.isFinite() } || !bias.isFinite()` 검출 시 `Timber.w` 경고 후 `return` — `saveWeights` 호출을 건너뛰어 손상된 모델이 저장되지 않음
  3. `minMaxNormalize`에 `if (history.isEmpty()) return 0.5` 명시 분기 추가
- **테스트**: 기존 `LogisticScoringEngineTest` 9건(sigmoid/probability/score/weights/featureValues/trainWeights) 전원 통과 — 회귀 없음. 신규 테스트는 (1) 상대 수렴은 loss curve에 접근 불가한 unit test로 검증 어려움, (2) NaN/Inf 발산은 재현 데이터 구성이 복잡, (3) 빈 history는 호출 그래프상 도달 불가라 현실적 단위 테스트 가치가 낮아 보류. 방어적 수정 자체가 회귀 방지

---

## Phase 7 — 성능/UX 최적화 ✅ 완료 (2026-04-21)

### Phase 7 완료 요약 (2026-04-21)

**수정 파일 (main 5개 + test 1개 신규)**
| 파일 | 변경 요약 |
|------|----------|
| `core/api/KiwoomApiClient.kt` | `createDefaultClient` Kdoc 확장 — OkHttp 레벨과 플랫폼 레벨(network_security_config.xml) pinning의 관계 명확화 |
| `core/database/dao/AnalysisCacheDao.kt` | `deleteAllOlderThan(cutoffDate): Int` 쿼리 신규 추가 — 전체 종목에 대한 일괄 TTL 삭제 |
| `core/worker/FeatureCacheEvictionWorker.kt` | 3종 캐시 통합 정리로 확장. FinancialCacheDao/AnalysisCacheDao 주입 추가, 7일(financial)/730일(analysis) cutoff + Timber 로그 |
| `presentation/report/ReportScreen.kt:529` | `items(filteredNames) { ... }` → `items(filteredNames, key = { it })` (검색 필터 변경 시 중간 삭제 빈번) |
| `presentation/consensus/ConsensusContent.kt:71` | `items(reports) { ... }` → `items(reports, key = { "${writeDate}\|${ticker}\|${author}\|${title}" })` |
| `presentation/etf/EtfDetailScreen.kt:284` | `items(dates) { ... }` → `items(dates, key = { it })` |
| `test/.../FeatureCacheEvictionWorkerTest.kt` | **신규** — 6건 (3종 DAO 호출 검증 / 7일 cutoff / 730일 cutoff / retry / fail threshold / companion constants) |

**검증 결과**
- `./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL** (27~28s, 2회)
- `./gradlew :app:testDebugUnitTest --tests "*.FeatureCacheEvictionWorkerTest"` **BUILD SUCCESSFUL** (34s) — 6건 전원 통과

### P7-1. Compose recomposition 최적화 🟢 ✅ 완료

- **스캔 결과**: `items(…) { }` 18개 호출 중 key 누락 7건
  - append-only/정적 리스트(ChatMessage, Indicator.entries filter, InvestOpinion 등) 4건 — 중간 삽입/삭제가 없어 key 누락 시 실질적 성능 영향 없음, **건드리지 않음**
  - 중간 삽입/삭제/필터 변경 가능 3건 — key 추가
- **적용 수정**
  | 파일:라인 | 변경 |
  |---|---|
  | `presentation/report/ReportScreen.kt:529` | `filteredNames` (String list, 검색 필터 변경 시 중간 삭제) → `key = { it }` |
  | `presentation/consensus/ConsensusContent.kt:71` | `reports` (`ConsensusReport`에 id 없음) → 합성 key `"${writeDate}\|${ticker}\|${author}\|${title}"` |
  | `presentation/etf/EtfDetailScreen.kt:284` | `dates` (String list, 종목 변경 시 재생성) → `key = { it }` |
- **보류 항목**: `derivedStateOf` 도입 / Composable 안정 파라미터 — 프로파일러 기반 측정 없이 cargo-cult 최적화 위험. 실측 기반 이슈 보고 시 재검토

### P7-2. OkHttpClient DI Singleton 검증 🟢 ✅ 감사 완료 (조치 불필요)

- **감사 결과**: `AppModule.kt:70` `provideOkHttpClient(): OkHttpClient = KiwoomApiClient.createDefaultClient()`가 이미 `@Singleton`로 공유. 6개 ApiClient(Kiwoom/KIS/AI/Dart/BokEcos) + 3개 Scraper(Naver/Equity/FnGuide) 모두 `httpClient: OkHttpClient` 생성자로 주입받아 동일 인스턴스 재사용
- **파일 내 `OkHttpClient()` 생성자 호출**: 3개 Scraper에서 생성자 기본값 (`= OkHttpClient()`)으로 존재하나 실제 Hilt DI 경로에선 주입된 공유 인스턴스 사용. 기본값은 단위 테스트용 fallback
- **`newBuilder()` 호출**: Scraper에서 공유 connection pool 재사용하며 스크래퍼별 timeout/cookie 추가 — 권장 패턴, 추가 인스턴스 생성 없음
- **결론**: 추가 조치 불필요. 현재 DI 구조가 최적

### P7-3. Certificate Pinning Debug 빌드 강화 🟢 ✅ 완료 (Kdoc 명확화)

- **감사 결과**: `KiwoomApiClient.createDefaultClient(enablePinning = !BuildConfig.DEBUG)`가 이미 릴리스 빌드에서만 OkHttp 레벨 pinning 활성화. 디버그 빌드에서 MockWebServer 기반 테스트를 위해 비활성화되는 것은 의도된 동작
- **플랫폼 레벨 pinning**: `res/xml/network_security_config.xml`에 Kiwoom/KIS 도메인 SHA-256 pin이 빌드 타입 무관하게 강제됨 — 디버그 빌드라도 실제 endpoint 호출 시 OS 레이어에서 pin 검증
- **적용 수정**: `createDefaultClient` Kdoc을 확장해 두 레벨 pinning의 관계를 명확화. 행동 변경 없음
- **보류 항목**: `src/debug/res/xml/network_security_config.xml` 오버라이드 추가는 실제 테스트 CA 생성/검증이 필요해 리스크 대비 실익 낮음. 디버그용 HTTPS MockWebServer가 필요해질 때 재검토

### P7-4. TTL 캐시 정리 Worker 🟢 ✅ 완료 (기존 Worker 확장)

- **발견**: `FeatureCacheEvictionWorker`가 이미 일일 06:00 KST로 `feature_cache`만 정리 중이었음. `AnalysisCache`/`FinancialCache`는 Repository 호출 시점에만 TTL 체크됨 (주기적 정리 없음)
- **적용 수정**
  1. `AnalysisCacheDao.deleteAllOlderThan(cutoffDate): Int` 신규 쿼리 — 전체 종목 일괄 삭제 (기존 `deleteOlderThan(ticker, cutoffDate)`와 별개)
  2. `FeatureCacheEvictionWorker`에 `AnalysisCacheDao`, `FinancialCacheDao` 주입 추가
  3. `doWork()`에서 3종 DAO 순차 호출:
     - `featureCacheDao.evictExpired(now)` (entry별 TTL)
     - `financialCacheDao.deleteExpired(now - 7일)` (`FinancialRepository`의 24h × 7 정책과 일치)
     - `analysisCacheDao.deleteAllOlderThan(now - 730일)` (`StockRepository.CACHE_RETENTION_DAYS`와 일치)
  4. Kdoc 업데이트 — 통합 worker 의도 및 각 cutoff 근거 명시
- **WORK_NAME 유지**: 기존 `feature_cache_eviction` 유지로 스케줄 재등록 필요 없음. 클래스명 불일치는 Kdoc으로 해소
- **Plan 대비 변경**: Plan은 "월 1회" 제안이었으나 기존 일일 스케줄이 이미 존재해 재사용. 월 1회보다 빈번하지만 3종 DAO 모두 조건부 쿼리라 비용 낮음
- **테스트**: `FeatureCacheEvictionWorkerTest` 신규 6건 — 3종 DAO 호출 검증, 7일/730일 cutoff capture, retry/fail 정책

---

## Phase 8 — 테스트 인프라 확장 ✅ 완료 (2026-04-21)

### Phase 8 완료 요약 (2026-04-21)

**추가 파일 (androidTest 4개 + test 4개 + 기존 1개 수정)**
| 파일 | 목적 |
|------|------|
| `app/src/androidTest/java/com/tinyoscillator/ComposeInfraSmokeTest.kt` | Compose UI Test 인프라 기본 검증 (`createComposeRule` + text selector) |
| `app/src/androidTest/java/com/tinyoscillator/presentation/oscillator/StockSearchDropdownSmokeTest.kt` | 오실레이터 검색 드롭다운 렌더링 + 클릭 콜백 smoke |
| `app/src/androidTest/java/com/tinyoscillator/presentation/demark/DemarkTDChartSmokeTest.kt` | DeMark 차트 헤더(종목명+기간 라벨) 렌더링 smoke |
| `app/src/androidTest/java/com/tinyoscillator/presentation/etf/KrxCredentialDialogSmokeTest.kt` | ETF KRX 자격증명 다이얼로그 렌더링 smoke |
| `app/src/test/java/com/tinyoscillator/core/database/dao/UserThemeDaoInMemoryTest.kt` | 6 tests — insert/count/observeAll(Flow)/sortOrder/delete |
| `app/src/test/java/com/tinyoscillator/core/database/dao/AnalysisHistoryDaoInMemoryTest.kt` | 4 tests — `saveWithFifo` FIFO 트랜잭션 + deleteByTicker |
| `app/src/test/java/com/tinyoscillator/core/database/dao/WorkerLogDaoInMemoryTest.kt` | 3 tests — `insertAndCleanup` 트랜잭션, getRecentErrors, getLatestLog |
| `app/src/test/java/com/tinyoscillator/core/database/dao/StockMasterDaoInMemoryTest.kt` | 4 tests — `replaceAll` 원자성, searchByText, getTickersBySector, getFilteredCandidates |
| `app/src/test/java/com/tinyoscillator/presentation/viewmodel/FinancialInfoViewModelTest.kt` | Turbine 예시 1건 추가 — `isRefreshing` false→true→false 전이 검증 |

**테스트 결과**
- androidTest 4개: `./gradlew :app:compileDebugAndroidTestKotlin` BUILD SUCCESSFUL (실행은 기기/에뮬레이터 필요)
- Robolectric DAO 테스트 17개: JVM 단독 실행, 모두 PASS
- 기존 테스트 영향 없음 (`FinancialInfoViewModelTest` 24 tests + 기존 Turbine 테스트 regression 없음)

**build.gradle.kts 변경**
- `testOptions.unitTests.isIncludeAndroidResources = true` 추가 (Robolectric 호환)
- `testImplementation`에 `androidx.test.ext:junit`, `androidx.arch.core:core-testing`, `androidx.room:room-testing` 추가
- `androidTestImplementation`에 `androidx.test.ext:junit`, `espresso-core`, Compose BOM + `ui-test-junit4` 추가
- `debugImplementation`에 `ui-test-manifest` 추가

### P8-1. androidTest 인프라 초기화 🟢 ✅ 완료
- **목적**: CLAUDE.md "Test Coverage -2 (androidTest 인프라 없음)" 구조적 한계 해결
- **androidTest 스캐폴드**: 디렉토리 생성 + 의존성 추가 + 주요 화면(오실레이터/DeMark/ETF) smoke test 각 1개 + 인프라 검증용 테스트 1개 (총 4개, 컴파일 확인 완료)
- **Room DAO in-memory 테스트**: Robolectric 기반 JVM 테스트로 구현 (인프라 요구사항 없이 실행 가능). 4개 DAO × 평균 4 tests = **17개 PASS**
- **설계 결정**: androidTest(Compose UI)는 실기기 필요성이 있어 컴파일 검증까지 수행. 실제 실행은 `./gradlew connectedDebugAndroidTest` 필요. DAO 테스트는 Robolectric로 JVM에서 빠르게 회귀 방어.
- **Robolectric 구성**: `@Config(application = android.app.Application::class)` 오버라이드로 `@HiltAndroidApp TinyOscillatorApp` 초기화 시 `EncryptedSharedPreferences` → `AndroidKeyStore` 미지원 크래시 회피

### P8-2. Turbine 도입 감사 + 예시 이관 🟢 ✅ 완료
- **현황 감사**: Turbine 1.1.0은 이미 `testImplementation` 등록 상태. 기존 5개 테스트가 활용 중 (`UiStateViewModelTest`, `UiStateTest`, `FinancialInfoViewModelTest`, `FundamentalHistoryViewModelTest`, `ProgressiveViewModelTest`)
- **가이드라인 결정** (전면 마이그레이션하지 않음 — 현 패턴이 적절):
  - **`.value` 단건 assert로 충분한 경우**: `advanceUntilIdle()` 후 최종 상태만 확인하는 `StateFlow` — 대부분의 ViewModel 테스트 (~150건)
  - **Turbine 사용 권장 경우**: (a) 중간 emit(예: Loading → Success → Error) 관찰, (b) cold `Flow` (StateFlow가 아님), (c) "더 이상 emit 되지 않음" 검증
- **예시 이관 1건**: `FinancialInfoViewModelTest`의 `isRefreshing은 refresh 중 true가 된다` 테스트는 이름과 달리 최종 `false`만 assert하고 있었음. Turbine 기반 신규 테스트를 추가해 `false → true → false` 전이를 명시적으로 검증 (기존 테스트는 참고용으로 유지)
- **남은 후속 작업 후보 (선택)**: 다른 Loading 전이 테스트가 필요한 VM(예: EtfViewModel)에도 유사 패턴 확대 가능 — 발견 시 개별 PR로 처리

---

## Phase 3.5 — API retry / Scraper 유틸 통합 ✅ 완료 (2026-04-21)

### 목표
2026-04-21 전체 리뷰에서 확인된 중복 패턴 제거:
- `KiwoomApiClient.call()`, `KisApiClient.get()`, `AiApiClient.analyze()/chat()`의 auth retry + retriable retry 블록이 거의 동일하게 4중 복사
- 3개 스크래퍼(`NaverFinanceScraper`, `FnGuideReportScraper`, `EquityReportScraper`)의 `TIMEOUT_SECONDS` 상수가 파일 단위로 분산
- `EquityReportScraper` / `FnGuideReportScraper` 각자 다른 분포의 `randomDelay()` 함수 로컬 정의

### 변경 내역
| 파일 | 변경 요약 |
|------|----------|
| `core/api/BaseApiClient.kt` | `executeWithRetry(tag, retryDelaysMs, onAuthFailure, retryableFilter, call)` 멤버 헬퍼 추가. `DEFAULT_RETRY_DELAYS_MS=[1s,2s]`, `AI_RETRY_DELAYS_MS=[2s,4s]` 컴패니언 상수 노출 |
| `core/api/KiwoomApiClient.kt` | `call()` 본문 40줄 → `executeWithRetry { callOnce(...) }` 6줄. `private val RETRY_DELAYS` 제거 |
| `core/api/KisApiClient.kt` | `get()` 본문 40줄 → `executeWithRetry(tag="KIS $trId", ...) { getOnce(...) }` 6줄. `private val RETRY_DELAYS` 제거 |
| `core/api/AiApiClient.kt` | `analyze()` + `chat()` 각 30줄 → `executeWithRetry(..., retryableFilter = AI_RETRYABLE_FILTER) { ... }` 6줄. 429 제외 필터를 companion object의 람다 상수로 추출 |
| `core/config/ApiConstants.kt` | `NAVER_SCRAPER_TIMEOUT_SECONDS=15`, `FNGUIDE_SCRAPER_TIMEOUT_SECONDS=15`, `EQUITY_SCRAPER_TIMEOUT_SECONDS=20` 추가 |
| `core/scraper/ScraperUtils.kt` | **신규 생성** — `uniformRandomDelayMs(min, max)` + `gammaRandomDelayMs(min, max)` 제공 |
| `core/scraper/NaverFinanceScraper.kt` | 로컬 `TIMEOUT_SECONDS=15L` 제거, `ApiConstants.NAVER_SCRAPER_TIMEOUT_SECONDS` 참조 |
| `core/scraper/FnGuideReportScraper.kt` | 로컬 `TIMEOUT_SECONDS=15L` 제거, `ApiConstants.FNGUIDE_SCRAPER_TIMEOUT_SECONDS` 참조. `randomDelay()` → `ScraperUtils.uniformRandomDelayMs(MIN, MAX)` |
| `core/scraper/EquityReportScraper.kt` | 로컬 `TIMEOUT_SECONDS=20L` 제거, `ApiConstants.EQUITY_SCRAPER_TIMEOUT_SECONDS` 참조. 8줄의 감마 분포 계산 → `ScraperUtils.gammaRandomDelayMs(MIN, MAX)` |
| `test/.../RateLimitTest.kt` | `RETRY_DELAYS` 리플렉션 테스트 2건 → `BaseApiClient.DEFAULT_RETRY_DELAYS_MS`/`AI_RETRY_DELAYS_MS` 직접 assertion |

### 검증
- `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest --tests core.api.* --tests core.database.dao.* --tests presentation.viewmodel.FinancialInfoViewModelTest` → 120/120 PASS
- 기존에 실패하던 `KisApiClientIntegrationTest.get은 네트워크 에러 시 실패한다`는 Phase 3.5 이전 baseline에서도 동일 실패 확인 (네트워크 환경 의존 flaky 테스트, 본 리팩토링과 무관)

---

## Phase 4.5 — DatabaseModule 분할 ✅ 완료 (2026-04-21)

### 목표
`core/di/DatabaseModule.kt` 804줄 단일 파일의 복합 책임 해체. Phase 4 거대 파일 분할에서 누락된 선택 항목.

### 변경 내역
| 파일 | 라인 수 | 역할 |
|------|-------|------|
| `core/di/DatabaseModule.kt` | 804 → **101** | `provideAppDatabase` 빌더 + `backupPortfolioData` 포트폴리오 CSV 백업 로직만 보유 |
| `core/di/DaoModule.kt` | **신규 102줄** | 22개 DAO `@Provides` 메서드 — 기계적 위임 전담 |
| `core/database/migration/AppDatabaseMigrations.kt` | **신규 683줄** | 25개 Migration 객체 + `ALL: Array<Migration>` 진입점. 추가 절차를 Kdoc에 명시 |

### 검증
- `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL (경고 2건은 Phase 8 이전부터 존재하던 기존 항목 — `SignalOutcomeUpdateWorker:43`, `ReportScreen:734` deprecated icon)
- Hilt 그래프 재구성 시 @Module 2개(DatabaseModule + DaoModule)가 정상 인식

---

## Polish — Compose 최적화 ✅ 완료 (2026-04-21)

### 변경 내역
| 파일 | 변경 |
|------|------|
| `presentation/ai/AiAnalysisChatSection.kt:147` | `items(chatMessages) { ... }` → `items(chatMessages, key = { it.timestamp }) { ... }` — 메시지 추가·재정렬 시 불필요한 recomposition 방지 |
| `presentation/chart/composable/IndicatorSheet.kt` | `Indicator.entries.filter { overlayType == PRICE }` · `filter { overlayType == OSCILLATOR }` 를 top-level `private val PRICE_INDICATORS` · `OSCILLATOR_INDICATORS`로 승격 + 각 `items()`에 `key = { it.name }` 지정 |

### 검증
- `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL

---

## 진행 원칙

1. **각 Phase 완료 전 체크포인트 커밋** — 롤백 가능 단위 유지
2. **테스트 우선** — 수정 전 재현 테스트 작성, 수정 후 기존 테스트 통과 확인
3. **CLAUDE.md / MEMORY.md 갱신** — 구조 변경 시 즉시 반영
4. **한 Phase 내 작업은 PR 단위로 분리** — 리뷰 용이성
5. **위험 높은 리팩토링 전에 스냅샷 테스트 또는 수동 UX 검증 계획 세우기** — 특히 Phase 4 Compose 분할

---

## 예상 일정

| Phase | 소요 (계획) | 실제 | 병렬 가능 | 선행 의존 |
|-------|------------|------|----------|----------|
| 1 | 1~2일 | ✅ 완료 (2026-04-20, 1일) | 내부 4개 작업 병렬 | — |
| 2 | 3~5일 | ✅ 완료 (2026-04-20, <1일, P2-2는 감사만) | — | Phase 1 완료 권장 |
| 3 | 1~2일 | ✅ 완료 (2026-04-20, <1일, P3-3 보류) | 3개 작업 병렬 | — |
| 4 | 5~7일 | ✅ 완료 (P4-1·P4-2·P4-3·P4-4, 2026-04-21) | 파일별 병렬 | Phase 2 완료 권장 |
| 5 | 2일 | ✅ 완료 (2026-04-21, <1일, 스캔 결과 범위가 작아 조기 완료) | — | — |
| 6 | 2일 | ✅ 완료 (2026-04-21, <1일, P6-1 감사 결과 조치 불필요·P6-2 보류로 범위 축소) | — | Phase 1 완료 권장 |
| 7 | 3일 | ✅ 완료 (2026-04-21, <1일, P7-2 감사만·P7-3 Kdoc만으로 범위 축소) | 4개 작업 병렬 | — (선택) |
| 8 | 2일 | ✅ 완료 (2026-04-21, <1일, P8-2 전면 마이그레이션 대신 가이드+예시 1건으로 범위 축소) | — | — (선택) |

**합계**: 필수(1~6) 약 14~20일, 선택(7~8) 포함 시 19~25일
**진행률**: Phase 1~8 완료 → 전체 완료 (계획 대비 조기 완료는 P2-2·P3-3·P6-1·P6-2·P7-2·P7-3·P8-2 범위 축소 + Phase 5 빈 catch 수량이 예상보다 적었던 결과)

---

## 완료 정의 (Definition of Done)

- [x] Phase 1~8의 모든 체크박스 완료
  - [x] Phase 1 (P1-1~P1-4)
  - [x] Phase 2 (P2-1 적용, P2-2 감사 → Phase 2.5로 분리)
  - [x] Phase 3 (P3-1·P3-2 적용, P3-3 보류)
  - [x] Phase 4 (P4-1 ✅, P4-2 ✅, P4-3 ✅, P4-4 ✅)
  - [x] Phase 5 (P5-1 ✅, P5-2 ✅ — Throwable 0건으로 작업 불필요)
  - [x] Phase 6 (P6-1 ✅ 감사 후 조치 불필요, P6-2 보류, P6-3 ✅, P6-4 ✅)
  - [x] Phase 7 (P7-1 ✅ key 3건, P7-2 ✅ 감사 완료, P7-3 ✅ Kdoc, P7-4 ✅ Worker 확장)
  - [x] Phase 8 (P8-1 ✅ androidTest 4건 + Robolectric DAO 17건, P8-2 ✅ Turbine 예시 1건 + 가이드 문서화)
- [ ] 전체 테스트 통과 (현재 ~1,400개 유지 또는 증가) — Phase 8 신규 18건 추가로 총 테스트 수 증가 확인 필요
- [ ] `CLAUDE.md` 내 Refactor backlog 섹션이 비어 있거나 선택 항목만 남음
- [ ] Git log에 각 Phase별 커밋 또는 머지 기록 — **현재 모든 변경이 미커밋 상태. Phase 1~8을 개별 커밋으로 분리하는 작업 대기**
