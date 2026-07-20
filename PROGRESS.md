# PROGRESS — 코드 리뷰 개선 진행 기록

> 근거: `TASK_code_review_improvements.md` v1.0 (2026-07-20 전체 코드베이스 리뷰 ~60건, 기준 커밋 `d78dd4c`). 각 Phase 완료 시 본 문서에 상세 기록 + 명세서 `PROGRESS:` 마커 갱신. Phase 경계 STOP — 사용자 승인 후 다음 Phase 착수.
>
> 구 BearSignal 이식 진행 기록(v1.0~v1.4 전체 완료)은 `PROGRESS.md.bk`로 보존.

## 현황

| Phase | 상태 | 비고 |
|---|---|---|
| P1a — 치명: 데이터/백엔드 4건 | **완료** (2026-07-20, `cfc0f39`) | 하단 「Phase 1 상세」 |
| P1b — 치명: UI 4건 | **완료** (2026-07-20, `cfc0f39`) | 실기 QA(수용 ⑤~⑧) 잔여 |
| P2 — WorkManager flex 백포트 | **완료** (2026-07-20) | 하단 「Phase 2 상세」 |
| P3 — 인프라 정확성·동시성 8건 | **완료** (2026-07-20) | 하단 「Phase 3 상세」 |
| P4 — 엔진 통계 경로 8건 | 미착수 | **착수 전 사용자 재확인**(산출 숫자 변경) |
| P5 — 성능 6건 | 미착수 | |
| P6 — 사용성·접근성 8건 | 미착수 | |
| P7 — 죽은 코드·빌드·문서 정리 | 미착수 | |
| P8 — 테스트 공백 보강 | 미착수 | |

## Phase 1 상세 — 치명 결함 8건 (2026-07-20, 커밋 `cfc0f39` feat/ai-analysis-ux 푸시)

구현: kotlin-implementer 2병렬(P1a/P1b, 파일 겹침 없음) → Advisor가 diff 전건·테스트 직접 재검증.

### P1a — 데이터/백엔드 4건

| # | 대상 | 수정 |
|---|---|---|
| 1-1 | `DartApiClient.fetchRecentDisclosures` | detailType 루프 `typeUrl`에 `&pblntf_detail_ty=$detailType` 삽입(A001/B001/C001 무필터 3중 쿼리 → 쿼터 3배 낭비 해소), 죽은 `url` 변수 삭제. 테스트 주입용 `baseUrl` 생성자 파라미터 추가(기본값 — DI 호출처 무변경) |
| 1-2 | `DatabaseModule.provideAppDatabase` | try 블록에서 `openHelper.writableDatabase` eager 터치 — Room `build()` lazy로 첫 DAO 쿼리에서 터지던 마이그레이션 실패를 catch 경로(포트폴리오 백업→삭제→재생성)로 유도 |
| 1-3 | `StatisticalAnalysisEngine.analyzeInternal` | `timings`/`failedEngines`를 `ConcurrentHashMap`/`Collections.synchronizedList`로 교체 — 11개 병렬 코루틴 동시 기록 손실/손상 방지. `timedExecution` 시그니처 무변경 |
| 1-4 | `AiContextRepositoryImpl.approve` | upsert 전 `dao.getBySectionKey`로 기존 승인 read → `(신규+기존).distinctBy { text }` 병합(신규 우선 = 재승인 멱등) → asOf는 병합 전체 `sourceDate` 최댓값. 같은 섹션 개별 ✓ 승인 시 마지막 1건만 생존하던 덮어쓰기 해소. §4.7 "fetch 무저장·approve만 upsert" 불변 유지 |

### P1b — UI 4건

| # | 대상 | 수정 |
|---|---|---|
| 1-5 | `MarketDepositViewModel` + `MarketDepositTab` | 에러 "다시 시도"가 `clearMessage()`(Error에서 no-op) → `_refreshTrigger` + `combine(_selectedRange, _refreshTrigger)` 실재로드 배선, Error→`Loading("다시 시도 중")` 즉시 전환. 죽은 `refreshData()` 재활 |
| 1-6 | `KoreanCandleChartView` | 캔들 차트 `update` 블록에서 `xAxis.valueFormatter = IndexDateFormatter(dateLabels)` 재할당 — 일봉↔주봉 전환·종목 변경 시 축 날짜 stale 해소 |
| 1-7 | `MarketDepositTab.DepositSummarySection` | 하드코딩 green=증가/red=감소 → `signColor()` (한국식 적=상승, 다크 변형은 LocalFinanceColors) |
| 1-8 | `MarketDepositTab.ChartSection` + `CashDepositTab` | `isSystemInDarkTheme()`+`Color.White/Black` → `rememberChartTheme()`의 `axisText`/`grid`. MarketDeposit은 update 블록 재대입으로 테마 전환 반영. 시리즈색(블루/오렌지/#6750A4)은 스펙대로 P6-2 이관 |

### 테스트 (전부 그린)

- P1a 타깃 32/32: `DartApiClientDisclosureUrlTest` 1(신규 — MockWebServer로 3개 상이 `pblntf_detail_ty` 쿼리 단언) + `DartApiClientParseTest` 5 + `DatabaseModuleRecoveryTest` 1(신규 — Robolectric, v999 다운그레이드 불가 DB 배치 → catch 경로 실발화·재생성 단언) + `StatisticalAnalysisEngineTest` 14(신규 1 — 3회 반복 실행 매회 timings 11/11 수집) + `AiContextRepositoryImplTest` 11(신규 3 — stateful dao로 순차 3건 승인 전부 잔존·동일 text 멱등·asOf 최댓값)
- P1b: `MarketDepositViewModelTest` 12/12(신규 3 — Error→재시도→Success·Error→Loading 즉시 전환·repository 재호출 verify)
- `compileDebugKotlin` 통과. 전체 스위트 미실행(타깃 `--tests` 규칙).

### 잔여

- P1b 수용 기준 ⑤~⑧ 실기 QA(에뮬레이터): 에러 재시도 동작 · 주봉 전환 축 날짜 · 예탁금 적=증가 · 시스템다크+앱라이트 차트 가독.

## Phase 2 상세 — WorkManager flex 백포트 (2026-07-20)

`WorkManagerHelper.kt` KDoc·CLAUDE.md "주기 워커에 flex 사용 금지" 룰이 legacy 스케줄러 4곳에 미백포트 — flex 있으면 첫 실행이 `initialDelay + (interval − flex)`로 밀려 신규 설치·재스케줄 시 첫 실행이 일간 ~하루/주간 ~7일 지연. `scheduleWeeklyWorker`(flex 없음) 패턴으로 통일.

| 위치 | 변경 |
|---|---|
| `scheduleDailyWorker` | `PeriodicWorkRequestBuilder<W>(24h, 15m flex)` → `(24h)`. 호출처 12개 일간 워커 전부 영향 |
| `scheduleRegimeUpdate` | `(7d, 1h flex)` → `(7d)` |
| `scheduleMacroUpdate` | `(7d, 1h flex)` → `(7d)` |
| `scheduleMetaLearnerRefit` | `(7d, 1h flex)` → `(7d)` |

각 위치에 flex 미사용 사유 주석(`scheduleWeeklyWorker` KDoc 참조) 추가. `initialDelay`/constraints/backoff/policy 등 나머지 로직 무변경.

### 수용 기준 (충족)

- main 소스 `PeriodicWorkRequestBuilder` flex(4-arg) 오버로드 grep **0건**.
- `CalculateWeeklyInitialDelayMillisTest` 그린(순수함수 — flex와 무관하나 WorkManagerHelper 기존 테스트 회귀 확인) + `compileDebugKotlin` 성공.

## Phase 3 상세 — 인프라 정확성·동시성 8건 (2026-07-20)

리뷰 MED 8건. BearSignal 스코어링 SSOT·임계치 무변경(데이터/네트워크 계층만). `compileDebugKotlin` 성공.

| # | 위치 | 변경 |
|---|---|---|
| 3-1 | `core/api/ApiModels.kt` `mapException` | 일반 IOException(Connect/SSL/EOF 등)→`NetworkError` 분기 추가 → 재시도·서킷브레이커 적용. UnknownHost/SocketTimeout/Serialization은 위에서 선분류 유지, ApiCallError(0) 폴백은 비-IO 예외만 |
| 3-2 | `core/api/KisApiClient.kt`·`KiwoomApiClient.kt` `getToken` | 재시도 지연(KIS 61~64s×N, Kiwoom 11~14s×N)을 `tokenMutex` **밖**으로 이동. 락 내부는 캐시 double-check + 단일 `fetchTokenOnce`만. 동시 호출자가 한 코루틴의 재시도 사다리(KIS 최악 ~2min) 전체를 블록하던 문제 해소. `fetchToken` 인라인·삭제 |
| 3-3 | DART(2)·Bok·Yahoo·Stooq·Fred | `execute().use { }`로 감싸 `!isSuccessful` 조기 return 경로에서도 body close(커넥션 풀 누수 방지). `CustomsTradeApiClient` 관례 준수 |
| 3-4 | `core/api/OkHttpExtensions.kt` `await` | `continuation.resume(response) { response.close() }` — 취소 경합 시 Response close 보장(coroutines 1.7.3 1-arg onCancellation) |
| 3-5 | `core/api/KrxApiClient.kt` | 4클라이언트 필드 `@Volatile`(mutex 밖 read/close의 happens-before) + client-level `sessionMutex`(공개 val, login-use-close 직렬화용) 추가 |
| 3-6 | `feature/bearsignal/.../BearSignalRepositoryImpl.kt` (`refreshAutoInputs`·`refreshMarketReturns`) | login→use→close 시퀀스를 `krxApiClient.sessionMutex.withLock`로 감싸 공유 싱글턴 동시 close 방지(inline withLock — 기존 `return@withContext`/폴백 흐름 무변경) |
| 3-7 | `BearSignalRepositoryImpl.refreshExternalAutoInputs` + `BearSignalAutoCacheMapper` | 전체 엔티티 read-modify-write → 수집 성공한 B등급 키만 per-key upsert(`externalEntities` 헬퍼). collectX(customs/rate/dir/etf)는 실패·키 미설정 시 `null` 반환(기존 캐시 유지). 수집 창(30~60s) 중 도착한 §4.5 승인값·워커 기록을 stale base로 되덮지 않음. A등급·credit·MANUAL 불변 |
| 3-8 | `core/api/AiApiClient.kt` `fetchGeminiModels` | API 키 URL 쿼리(`?key=`) → `x-goog-api-key` 헤더 — 타 Gemini 호출과 통일, 예외/로그 URL 유출 경로 제거 |

### 잔여(범위 밖 명시)

- **3-6 교차 세션**: `sessionMutex`는 이를 사용하는 호출처(현재 BearSignal 2경로)만 상호 배타. `EtfRepository`/`FundamentalHistoryViewModel`/`FearGreedRepository`/`MarketIndicatorRepository`/`RegimeUpdateWorker` 등 미이관 호출처는 login-use-close를 분리 호출(일부는 장기 세션 재사용 패턴)하므로 여전히 교차 close 경합 가능. 전면 해소는 세션 소유권 리팩터(참조카운트/세션 API 일괄 이관)로 별건 — 명세 3-6 스코프(BearSignal 2경로)는 충족. `@Volatile`(3-5)로 가시성만 보장.

### 수용 기준 (충족)

- 3-1 IOException→NetworkError 매핑: `ApiErrorClassificationTest` +7(Connect/SSL/EOF→Network+retriable, UnknownHost/SocketTimeout/Serialization 선분류, 비-IO→ApiCallError0) → **23/23**.
- 3-3/3-4 에러 응답 leak(MockWebServer): `OkHttpExtensionsTest` +1 — 500 응답 3회를 `await().use`로 소비 후 `connectionPool.connectionCount()==1`(재사용=body close 증거) → **9/9**.
- 3-7 수집 중 승인 생존: `BearSignalRepositoryImplTest` +2 — FRED 실패 시 upsert에 `GATE_RATE` 미포함(승인값 보존)·A등급 키 미포함 단언, 전무 시 upsert 0회 → **45/45**.
- 회귀 그린: Krx 8·Kis 26·Kiwoom 21·Dart(parse/disclosureUrl)·Fred·Stooq·Yahoo parse 전부 통과.

---

## 코드리뷰 개선 P4 — 엔진 "조용히 틀린" 통계 경로 8건 (2026-07-20)

명세 `TASK_code_review_improvements.md` §Phase 4. **산출 숫자 변경** — 사용자 재확인 후 all 8 + 4-1 종목별 가중치 방식 승인. 다른 Phase와 커밋 분리(§0 제약).

### 변경 전후 (엔진 단위 동작 — 회귀 테스트가 concrete 값으로 인코딩)

| # | 위치 | 변경 전 (버그) | 변경 후 |
|---|---|---|---|
| 4-1 | `LogisticScoringEngine` | prefs 전역 단일 키(`logistic_trained`) → 첫 분석 종목 가중치가 **전 종목·재시작 후 재사용** | 종목별 키(`logistic_trained_<ticker>` 등) → 종목마다 자체 학습 |
| 4-2 | `LogisticScoringEngine.trainWeights` | 학습 시 `demarkBuySetup=0` 고정 → demark 피처 gradient 항상 0(죽은 피처, weight 정확히 0f) | 행별 `tdBuyCount` 공급 → weight≠0(학습됨) |
| 4-3 | `CorrelationEngine` | `volumeChanges.size(n-1)==supplyMacd.size(n)` 절대 거짓 → 수급↔거래량 상관 **미출력** | `supplyMacd.drop(1)` 정렬 → 상관 행 출력 |
| 4-4 | `OrderFlowEngine` | z-score 과거 분포 = 외국인-only(`Σf/Σ\|f\|`), currentOfi = 전체 공식 → 통계량 불일치 편향 | 과거 분포도 `calcOfi` 동일 공식 롤링 → z 일관. (외국인 상수+기관 추세 시 버그면 signalScore=0.5 고정, 수정 후 >0.5) |
| 4-5 | `BayesianUpdateEngine`·`NaiveBayesEngine` | 결측 PBR(≤0): Bayesian→OVERVALUED, NaiveBayes→UNDERVALUED (**정반대**, 엔진 불일치) | 양쪽 FAIR — 결측 PBR posterior가 FAIR값과 동일 |
| 4-6 | `SectorCorrelationNetwork` | 꼬리 minLen truncate → 휴장 갭 시 다른 날짜끼리 상관 | 공통 거래일 교집합 후 계산 — gap 피어도 reference와 동일 corr |
| 4-7 | `ProbabilityInterpreter` | MDD 경고 `avgMdd20d < -0.05`(값 항상 ≥0 → 도달 불가) | `> 0.05` + `pctSigned(-avgMdd20d)` 하방 부호 (8% 낙폭 → "-8.0%" 경고 표시) |
| 4-8 | `SignalScoringEngine` | 무발생 패턴 winRate 0.0 → `?: DEFAULT_WEIGHT` 우회 → weight 0(신호 조용히 비활성) | `totalOccurrences==0` → DEFAULT_WEIGHT(0.5) |

### 수용 기준 (충족)

- 항목별 전용 회귀 테스트 9건 신규(버그 재현 → 수정 후 기대값):
  - Logistic +2(종목 분리 격리·demark 피처 gradient≠0), Correlation +1(거래량변화율 행 존재), OrderFlow +1(signalScore≠0.5·>0.5), Bayesian +1(결측 PBR=FAIR posterior 동일), Interpreter +2(고낙폭 경고+음부호/저낙폭 무경고), SignalScoring +1(무발생→DEFAULT_WEIGHT), SectorCorr +1(gap 피어 공통날짜 정렬 = reference 동일).
- 타깃 스위트 **107/107 그린**: Logistic 13·Correlation 13·OrderFlow 15·Bayesian 13·NaiveBayes 12·SignalScoring 15·Interpreter 18·SectorCorr 8 + `StatisticalAnalysisEngineTest`(오케스트레이터 통합) 그린.
- 시그니처 변경(Logistic `analyze`/`trainWeights` +stockCode/demarkRows) 호출처: `StatisticalAnalysisEngine:156` 1곳 + 테스트 — 전수 배선 확인.
