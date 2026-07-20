# TASK_code_review_improvements.md — 전체 코드 리뷰 개선 작업 명세서 (v1.0)

> 대상: Claude Code (kotlin-implementer / qa-verifier 서브에이전트)
> 근거: 2026-07-20 전체 코드베이스 리뷰 (5영역 병렬 — core 인프라 / data·domain / presentation UI / bearsignal / 횡단 스윕, 실코드 검증 기반 ~60건)
> 기준 커밋: `d78dd4c` (feat/ai-analysis-ux)
> 진행 규칙: 각 Phase 완료 시 본 문서 `PROGRESS:` 마커 갱신 + `PROGRESS.md` 기록. Phase 경계 STOP — 사용자 승인 후 다음 Phase 착수.

## Changelog
- v1.0 (2026-07-20): 최초 작성 — 리뷰 결과 전체를 Phase 1~8로 편성.

---

## 0. 목표와 제약

| 제약 | 반영 방식 |
|---|---|
| BearSignal 스코어링 SSOT 불변 | 스코어링 함수·`bear_thresholds.json` 임계치 수정 금지(골든 테스트 가드). 본 명세의 bearsignal 항목은 전부 데이터 계층·UI만 대상 |
| 산출 숫자 변경은 격리 | Phase 4(엔진 통계 경로)는 분석 결과 숫자가 바뀜 — 다른 Phase와 커밋 분리, 변경 전후 값 비교 증적 + 테스트 갱신 동반 |
| 테스트 전략 | 전체 스위트 금지 — `--tests` 타깃 실행만 (`feedback_test_strategy` 메모리 규칙) |
| 기존 패턴 유지 | MVVM + Clean + Hilt + Compose + StateFlow + Room. 색은 `LocalFinanceColors`/`ChartTheme`/`colorScheme` 토큰만 |
| 커밋 단위 | Phase당 1커밋 원칙(P1은 1a/1b 분리 가능). 자동 커밋 금지 — 완료 보고 후 사용자 지시 |

**우선순위**: P1(치명) → P2(flex 백포트) → P3(인프라 MED) → P4(엔진 통계) → P5(성능) → P6(사용성·접근성) → P7(죽은 코드·정리) → P8(테스트 공백). P2까지는 즉시 권장, P4는 착수 전 사용자 재확인.

경로 표기: `app/src/main/java/com/tinyoscillator/` 기준 상대경로.

---

## Phase 1 — 치명 결함 (HIGH 7건)

### P1a. 데이터/백엔드 4건

| # | 위치 | 문제 | 수정 방침 |
|---|---|---|---|
| 1-1 | `core/api/DartApiClient.kt:104` | detailTypes 루프(A001/B001/C001)가 `typeUrl`에 `pblntf_detail_ty` 미삽입 → 동일 무필터 쿼리 3회(쿼터 3배). line 90 `url` 미사용 | `typeUrl`에 `&pblntf_detail_ty=$detailType` 추가, 죽은 `url` 삭제 |
| 1-2 | `core/di/DatabaseModule.kt:33-41` | 마이그레이션 실패→백업→재생성 안전망 발화 불가(`build()` lazy — 에러는 첫 DAO 쿼리에서 발생) | try 블록 안에서 `db.openHelper.writableDatabase` 터치로 eager 검증 |
| 1-3 | `data/engine/StatisticalAnalysisEngine.kt:125-126,511-519` | `timings`(mutableMapOf)·`failedEngines`(mutableListOf)를 11개 병렬 코루틴이 변경 — 손실/손상 가능 | 각 Deferred가 (결과, 소요시간, 실패여부) 반환 → await 후 단일 스레드 병합. 또는 ConcurrentHashMap + synchronizedList |
| 1-4 | `feature/bearsignal/data/repository/AiContextRepositoryImpl.kt:48-53` | §4.7 클레임 개별 ✓ 승인 시 같은 섹션 이전 승인 덮어씀(PK REPLACE, 전달 클레임만 저장) — 마지막 1건만 생존 | `approve()`에서 기존 엔티티 read → 클레임 병합·중복 제거 → upsert. 승인 순서 무관 멱등 보장 |

### P1b. UI 4건

| # | 위치 | 문제 | 수정 방침 |
|---|---|---|---|
| 1-5 | `presentation/market/MarketDepositTab.kt:86` + `MarketDepositViewModel.kt:92-96` | 에러 화면 "다시 시도"가 `clearMessage()` 호출 — Success에서만 동작, 재시도 영구 no-op | 버튼을 실제 재로드(`loadDataByRange` 재실행)에 배선 + Error 상태 해제 |
| 1-6 | `presentation/chart/composable/KoreanCandleChartView.kt:112` | X축 `IndexDateFormatter(dateLabels)`를 factory에서만 설정 — 일봉↔주봉 전환·종목 변경 시 축 날짜 stale(marker만 갱신) | `update` 블록에서 formatter 재할당 |
| 1-7 | `presentation/market/MarketDepositTab.kt:159,181` | 하드코딩 green=증가/red=감소 — 앱 전체 적=상승 관례 역전 | `signColor()`/`LocalFinanceColors`로 교체 |
| 1-8 | `presentation/market/MarketDepositTab.kt:236-240` + `presentation/etf/stats/CashDepositTab.kt:42` | `isSystemInDarkTheme()`+`Color.White/Black` 직접 사용 — 인앱 테마 오버라이드 무시(시스템다크+앱라이트 = 흰 글자 on 한지 배경) | `rememberChartTheme()` 사용(타 차트 관례) |

**수용 기준**: ①DART 로그에 detailType별 3개 상이 쿼리 확인 ②마이그레이션 실패 시뮬레이션 테스트에서 catch 경로 도달 ③오케스트레이터 반복 실행에 timings 11건 완전 수집 단언 ④같은 섹션 클레임 3건 순차 승인 후 3건 모두 잔존 테스트 ⑤~⑧실기: 에러 재시도 동작·주봉 전환 축 날짜·예탁금 적=증가·시스템다크+앱라이트 차트 가독.

---

## Phase 2 — WorkManager flex 백포트 (MED, 룰 위반 잔존)

자체 KDoc(`WorkManagerHelper.kt:110-114`)·CLAUDE.md "주기 워커에 flex 사용 금지" 룰이 legacy 스케줄러에 미백포트 — 신규 설치·재스케줄 시 첫 실행이 일간 워커 ~하루, 주간 워커 ~7일 밀림.

| 위치 | 대상 |
|---|---|
| `core/worker/WorkManagerHelper.kt:79-82` | `scheduleDailyWorker` 15분 flex 제거 — 호출처 12개 일간 워커 전부 영향 |
| `WorkManagerHelper.kt:278-281` | `scheduleRegimeUpdate` 7일+1h flex 제거 |
| `WorkManagerHelper.kt:338-341` | `scheduleMacroUpdate` 동일 |
| `WorkManagerHelper.kt:387-390` | `scheduleMetaLearnerRefit` 동일 |

수정 방식은 `scheduleWeeklyWorker`(line 147, flex 없음)와 동일 패턴. **수용 기준**: main 소스에서 `PeriodicWorkRequestBuilder` flex 오버로드 사용 0건(grep), WorkManagerHelper 기존 테스트 그린.

---

## Phase 3 — 인프라 정확성·동시성 (MED)

| # | 위치 | 문제 | 수정 방침 |
|---|---|---|---|
| 3-1 | `core/api/ApiModels.kt:53-58` | 일반 IOException(ConnectException/SSLException/EOFException)이 `ApiCallError(0)` → 재시도·서킷브레이커 모두 미적용 | else 앞에 `is IOException -> NetworkError(...)` 분기 추가 |
| 3-2 | `core/api/KisApiClient.kt:107-137` | 토큰 mutex를 fetchToken 재시도 사다리 전체(최악 4~6분) 보유 — 동시 호출 전부 블록. `KiwoomApiClient.kt:221` 동일(~37s) | 재시도 지연은 mutex 밖에서 + double-check, 락 내 시도는 1회로 제한 |
| 3-3 | Response 미close 일괄: `core/api/DartApiClient.kt:56-59,128-131` · `BokEcosApiClient.kt:89-92` · `feature/bearsignal/data/remote/YahooChartApiClient.kt:74-79` · `StooqCsvClient.kt:50-55` · `FredApiClient.kt:65-70` | `!isSuccessful` 조기 return 경로에서 body 미소비 — 커넥션 풀 누수 | `execute().use { }` 일괄 적용(`CustomsTradeApiClient.kt:69` 관례) |
| 3-4 | `core/api/OkHttpExtensions.kt:23` | `continuation.resume(response)` — 취소 경합 시 Response 미close | `resume(response) { response.close() }` |
| 3-5 | `core/api/KrxApiClient.kt:18-21,89-91` | 클라이언트 필드 mutex 밖 read, `@Volatile` 없음 — happens-before 부재 | 최소 `@Volatile`, 권장 client-level mutex |
| 3-6 | `feature/bearsignal/data/repository/BearSignalRepositoryImpl.kt:202-208,444-450` | 공유 싱글턴 KrxApiClient에 login→use→`close()` — 일간/주간 워커·EtfUpdate·당겨새로고침 겹치면 사용 중 close | 3-5의 mutex로 login-use-close 시퀀스 보호(또는 참조 카운트) |
| 3-7 | `BearSignalRepositoryImpl.kt:259-271` | 전체 엔티티 read-modify-write(30~60s 창) — 수집 중 도착한 §4.5 승인값·워커 기록을 stale 값으로 되덮음 | 실제 수집한 B등급 키만 per-key upsert(기존 `creditEntity` 관례). MANUAL 불패 불변 유지 |
| 3-8 | `core/api/AiApiClient.kt:106` | `fetchGeminiModels`만 API 키를 URL 쿼리로 전달(타 호출은 헤더) — 예외 메시지 유출 경로 | `x-goog-api-key` 헤더로 통일 |

**수용 기준**: 3-1 IOException→NetworkError 매핑 테스트, 3-3/3-4 에러 응답 leak 테스트(MockWebServer), 3-7 수집 중 승인 시나리오 테스트(승인값 생존 단언), 기존 관련 스위트 그린.

---

## Phase 4 — 엔진 "조용히 틀린" 통계 경로 (★ 착수 전 사용자 재확인 — 산출 숫자 변경)

| # | 위치 | 문제 | 수정 방침 |
|---|---|---|---|
| 4-1 | `data/engine/LogisticScoringEngine.kt:67-68` | `PREFS_TRAINED` 영구 — 최초 분석 종목 가중치가 전 종목·재시작 후에도 적용 | 티커별 가중치 또는 재학습 트리거(TTL/워커) |
| 4-2 | `LogisticScoringEngine.kt:129` | 학습 시 `demarkBuySetup=0` 고정, 추론 시 실값 — gradient 0, 죽은 피처 | 학습 행별 historical setup 계산 공급 |
| 4-3 | `data/engine/CorrelationEngine.kt:89` | `volumeChanges.size == supplyMacd.size` 절대 참 불가(n-1 vs n) — 문서화된 수급↔거래량 상관 미계산 | `supplyMacd.drop(1)` 대응 |
| 4-4 | `data/engine/OrderFlowEngine.kt:197-201,222-226` | z-score 분모 분포가 다른 통계량(`foreign.sum()/Σ|foreign|`)으로 구축 — 평균회귀 신호 편향 | historical 시리즈를 `calcOfi` 동일 공식으로 재구축 |
| 4-5 | `data/engine/BayesianUpdateEngine.kt:206-211,254-260` | PBR≤0(결측)이 OVERVALUED 분류 — posterior 하방 왜곡(NaiveBayes는 FAIR — 엔진 간 불일치) | 결측 시 신호 스킵 또는 FAIR |
| 4-6 | `data/engine/network/SectorCorrelationNetwork.kt:116-125` | 날짜 무시 꼬리 truncate 병합 — 휴장 갭 시 다른 날짜끼리 상관 | 공통 날짜 교집합 후 수익률 계산 |
| 4-7 | `domain/usecase/ProbabilityInterpreter.kt:216` | `avgMdd20d < -0.05` 도달 불가(값 항상 ≥0) — MDD 경고 미표시 | `> 0.05`로 수정 + 표기 부호 처리 |
| 4-8 | `data/engine/SignalScoringEngine.kt:74,83,93` | 무발생 패턴 가중치 0.0(의도 DEFAULT 0.5) — 신호 조용히 비활성 | `totalOccurrences == 0`이면 DEFAULT_WEIGHT |

**수용 기준**: 항목별 전용 단위테스트(버그 재현 → 수정 후 기대값), 변경 전후 대표 종목 1개 산출 비교표를 PROGRESS.md에 증적, 엔진 스위트 타깃 그린.

---

## Phase 5 — 성능 (MED)

| # | 위치 | 문제 | 수정 방침 |
|---|---|---|---|
| 5-1 | `data/repository/StatisticalRepositoryImpl.kt:55-73` + `StatisticalAnalysisEngine.kt:132-134` | 동일 365행 가격 테이블 3회 로드 | 로드된 `List<DailyTrading>` 전달 오버로드 |
| 5-2 | `feature/bearsignal` 당겨새로고침 (`BearSignalViewModel.kt:388-393`) | TTL 게이트 없이 풀 파이프라인(KRX 로그인 2회+관세청 XML 3×2.2MB+지수 20종) ~30-60s | `updatedAt` 동일일/N시간 freshness 창이면 skip — §3 임계치와 무관한 UI 파라미터 |
| 5-3 | `presentation/stock/OscillatorScreen.kt:561-563` | `ParkSignalDetector.detect`(1년 캔들)를 composition 중 메인스레드 실행 | ViewModel에서 `Dispatchers.Default` |
| 5-4 | `data/engine/FeatureStore.kt:57-95` | per-key mutex 없음 — 동일 종목 동시 분석 시 11-엔진 2회 실행 | keyed Mutex |
| 5-5 | `core/worker/DataIntegrityCheckWorker.kt:377-388` | market-agnostic FearGreed 업데이트를 시장별 2회 호출(+5s delay), fixed 카운트 하드코딩 0 | 1회 호출 + 실제 diff 계산 |
| 5-6 | `data/engine/calibration/SignalCalibrator.kt:23` + `ensemble/StackingEnsemble.kt:32-35` | 워커 write / 분석 코루틴 read 동기화 없음 | ConcurrentHashMap + 맵 atomic swap, `@Volatile` |

---

## Phase 6 — 사용성·접근성

| # | 위치 | 문제 | 수정 방침 |
|---|---|---|---|
| 6-1 | 터치 타깃 <48dp: `presentation/portfolio/PortfolioContent.kt:577`(32dp) · `stock/OscillatorScreen.kt:387,418`(24dp),`:525`(28dp) · `settings/EtfSettingsSection.kt:122`(32dp) · `ai/AiAnalysisChatSection.kt:185,219`(40dp) | 최소 인터랙션 영역 미달 | 외곽 size 제약 제거(내부 Icon만 축소) |
| 6-2 | 하드코딩 Material 색(다크 미적응): `financial/DuPontContent.kt:381-385` · `financial/FinancialCharts.kt:378-379` · `ai/AiStructuredInterpretationCard.kt:273-287` · `ai/AiAnalysisProbabilityResult.kt:194-196,578` · `etf/stats/MarketBadge.kt:18-20` | Jade Terminal 토큰화 잔존분. `:578`은 다크에서 라이트 핑크 칩+회색 라벨 가독 실패 | `LocalFinanceColors`/`colorScheme`/`LocalExtendedColors.warn` 이행 |
| 6-3 | `feature/bearsignal/presentation/ui/BearSignalScreen.kt:585-589` + `AiContextUpdatePanel.kt:90-97` | 정세 업데이트 성공+클레임 0건 시 패널 무피드백 소멸(Gemini 경로는 fetch 전 안내문 오표시) | "새 업데이트 없음" 명시 상태 |
| 6-4 | `feature/bearsignal/presentation/ui/BearSignalCountryTableSection.kt:212,221-227,233` | 로케일 포맷("%.1f") ↔ dot-only 파싱(`toDoubleOrNull`) — 콤마 로케일에서 미변경 저장도 null화. decimal 키보드·검증 피드백 없음 | `Locale.US` 포맷 + `KeyboardType.Decimal` + 무효 입력 에러 표시 |
| 6-5 | `presentation/portfolio/PortfolioContent.kt:89-106` | Error 상태에 재시도 액션 없음 | "다시 시도" 버튼 추가 |
| 6-6 | `presentation/ai/AiAnalysisChatSection.kt:145` | LazyColumn key=timestamp — 같은 ms 2건 시 duplicate-key 크래시 | 고유 id 키 |
| 6-7 | `feature/bearsignal/presentation/ui/BearSignalGraphics.kt:51,126` | 레이더/신호등 Canvas semantics 없음 — TalkBack 무음 | `semantics { contentDescription }`에 축값 4종 포함(또는 decorative 마킹+헤더 설명 확장) |
| 6-8 | `feature/bearsignal/presentation/ui/BearSignalTypesHistorySection.kt:159,178` | 체크 상태 `rememberSaveable` 위치 키만 — AI 승인으로 내용 교체 시 체크 오귀속 | 키에 content hash/`approvedAt` 포함 |

---

## Phase 7 — 죽은 코드 삭제 + 빌드/문서 정리

**삭제 대상(전부 참조 0 검증됨 — 삭제 전 재grep 필수)**:
- 클러스터: `presentation/viewmodel/StockChartViewModel.kt` + `presentation/chart/composable/OscillatorChartView.kt` + `IndicatorSheet.kt` (지표 기능 살릴 계획 없으면 삭제 — **사용자 선택**)
- 제로 참조 클래스: `data/preferences/RecentSearchPreferences.kt` · `data/engine/calibration/CalibrationMonitor.kt` · `data/engine/ensemble/RegimeStackingEnsemble.kt` · `domain/model/AnalysisBridge.kt` (+각 고아 테스트)
- 미사용 선언: `ExpectedValueAnalysis`/`DemarkAnalysis`/`EtfContext`(`domain/model/StatisticalModels.kt:291,316,343`) · `CalibratedStatisticalResult`(`CalibrationModels.kt:60`) · `FactorDataCache`(`FactorModels.kt:73`) · `MonitorItem`(`feature/bearsignal/domain/model/BearSignalModels.kt:140`) · `RealtimeSupplyResponse`(`data/dto/StockApiModels.kt:85`)
- 함수: `core/scraper/EquityReportScraper.kt:51` `scrapeReports` · `presentation/market/MarketDepositViewModel.kt:87` `refreshData` · bearsignal `BearSignalViewModel.kt:563-568` 스칼라 수동입력 6함수 · `ManualInputViewModel.updateMarketReturn` · `CorrelationEngine`/`PatternScanEngine`/`StackingEnsemble` 내 미사용 로컬(`data/engine/CorrelationEngine.kt:60,65,79-81` 등)
- 기타: `MainActivity.kt:21-23` 미사용 WindowSizeClass import · `presentation/ai/AiAnalysisProbabilitySection.kt:358-368` algoNameMap 키 불일치(PascalCase vs camelCase — 한글 라벨 죽음, **삭제가 아니라 키 통일로 살릴 것**) · `feature/bearsignal` `dismissAllAiContextClaims` — "전체 무시" 버튼으로 배선 권장(삭제보다 유용)

**빌드/문서**:
- `app/build.gradle.kts:52-54` — `lint { checkReleaseBuilds = false }` 전면 off → 타깃 disable/baseline
- `app/build.gradle.kts:97 vs 157` — coroutines main 1.7.3 / test 1.8.0 런타임 불일치 → 1.8.x 정렬
- `CLAUDE.md` — Room v37 표기 → 실제 v38(`AppDatabase.kt:102`, MIGRATION_37_38, schemas/38.json) 반영
- `design_handoff_jade_terminal_polish/` — 미추적 256K. 잔여 차트 테마 작업이 참조 중 → `docs/design/`으로 커밋(잔여 작업 완료 시 삭제 재검토)

---

## Phase 8 — 테스트 공백 보강

우선순위 순:
1. `data/repository/FearGreedRepository.kt` — 361줄 멀티소스 병합, 무테스트
2. `core/worker/ProbabilityBatchWorker.kt` — 임계 돌파 알림(사용자 대면) 로직
3. `domain/usecase/ProbabilityAnalysisUseCase.kt:59-64` — 수제 JSON(`"` 만 escape — 역슬래시/제어문자/NaN에 invalid JSON 영속) → **kotlinx.serialization 전환 + 테스트** (P8에서 유일한 프로덕션 코드 변경)
4. `presentation/ai/AiProbabilityAnalysisViewModel.kt` — 286줄 스트리밍 오케스트레이션
5. `presentation/heatmap` `HeatmapViewModel` + `BuildHeatmapUseCase`
6. 비-bearsignal 워커 계층(Consensus/Etf/Macro/MarketDeposit/MarketOscillator/Regime/SignalOutcome/Theme/IncrementalModel/MetaLearnerRefit/DataIntegrityCheck) — 대표 2~3개부터

---

## 리뷰에서 클린 확인된 항목 (재작업 불필요)

보안: EncryptedSharedPreferences 전면·비밀 로깅 0·`allowBackup=false`+전체 exclude·인증서 피닝·ProGuard 로그 strip 실재·MainActivity만 exported. Room 마이그레이션 1_2~37_38 완결 등록. main에 `runBlocking`/`GlobalScope`/미가드 `!!` 0건. BearSignal 설계 제약(자동 fetch 금지·MANUAL 불패·hsCode="-" 제외·주간 Asia/Seoul 고정·fetch 무저장/approve만 upsert) 전부 준수.

---

## PROGRESS

- [x] P1a — 치명: 데이터/백엔드 4건 (2026-07-20 완료 — 타깃 테스트 32/32 그린: Dart 6·DatabaseModuleRecovery 1·Engine 14·AiContext 11. 신규 테스트 `DartApiClientDisclosureUrlTest`·`DatabaseModuleRecoveryTest`)
- [x] P1b — 치명: UI 4건 (2026-07-20 완료 — MarketDepositViewModelTest 12/12 그린. 수용 기준 ⑤~⑧ 실기 QA 잔여)
- [x] P2 — flex 백포트 (2026-07-20 완료 — 4곳 flex 오버로드 제거: scheduleDailyWorker(24h,15m→24h)·scheduleRegimeUpdate·scheduleMacroUpdate·scheduleMetaLearnerRefit(7d,1h→7d). grep flex 오버로드 0건, CalculateWeeklyInitialDelayMillisTest 그린 + compileDebugKotlin 성공)
- [x] P3 — 인프라 정확성·동시성 8건 (2026-07-20 완료 — 타깃 테스트 그린: ApiErrorClassification 23·OkHttpExtensions 9·BearSignalRepositoryImpl 45·Krx 8·Kis 26·Kiwoom 21 + Dart/Fred/Stooq/Yahoo parse 그린. 신규 테스트: mapException IOException 분기 7건, 커넥션풀 누수 회귀 1건, per-key upsert 승인값 보존 2건)
  - 3-1 `ApiModels.mapException`: 일반 IOException(Connect/SSL/EOF)→`NetworkError`(재시도·CB 적용). UnknownHost/SocketTimeout/Serialization 선분류 유지
  - 3-2 KIS/Kiwoom `getToken`: 재시도 지연을 `tokenMutex` **밖**으로, 락 내부는 캐시 double-check + 단일 발급 시도만 — 동시 호출자가 재시도 사다리(KIS ~2min) 전체 블록되지 않음. `fetchToken` 인라인·제거
  - 3-3 Response 미close 6클라이언트(DART 2·Bok·Yahoo·Stooq·Fred): `execute().use { }`로 조기 return 경로 body close
  - 3-4 `OkHttpExtensions.await`: `resume(response) { response.close() }` — 취소 경합 시 Response close 보장
  - 3-5 `KrxApiClient`: 4필드 `@Volatile` + client-level `sessionMutex` 추가(login-use-close 직렬화용)
  - 3-6 `BearSignalRepositoryImpl` 2경로: login→use→close를 `krxApiClient.sessionMutex.withLock`로 감싸 공유 싱글턴 동시 close 방지. **잔여**: EtfUpdate/FundamentalHistory 등 미이관 호출처와의 교차 세션 경합은 sessionMutex 미사용이라 잔존(그쪽은 장기 세션 재사용 패턴이라 일괄 이관은 별도 리팩터) — @Volatile로 가시성만 보장
  - 3-7 `refreshExternalAutoInputs`: 전체 엔티티 RMW → 수집 성공한 B등급 키만 per-key upsert(`Mapper.externalEntities`). collectX 실패 시 null 반환. 수집 중 도착한 §4.5 승인값·워커 기록 보존, MANUAL 불패 불변
  - 3-8 `AiApiClient.fetchGeminiModels`: API 키 URL 쿼리 → `x-goog-api-key` 헤더(타 Gemini 호출과 통일, 유출 경로 제거)
- [x] P4 — 엔진 통계 경로 8건 (2026-07-20 완료 — 사용자 재확인 후 all 8 + 4-1 종목별 가중치 승인. 타깃 테스트 107/107 그린: Logistic 13·Correlation 13·OrderFlow 15·Bayesian 13·NaiveBayes 12·SignalScoring 15·Interpreter 18·SectorCorr 8 + 오케스트레이터 통합 그린. 신규 회귀 9건. 커밋 대기)
  - 4-1 `LogisticScoringEngine`: prefs 키를 **종목별**(`logistic_weight_<ticker>_i`·`_bias_<ticker>`·`_trained_<ticker>`)로 분리 — 첫 분석 종목 가중치가 전 종목·재시작 후 재사용되던 전역 단일 모델 제거. `analyze`/`trainWeights`에 `stockCode` 파라미터 추가
  - 4-2 `LogisticScoringEngine.trainWeights`: 학습 행별 `demarkByDate[date].tdBuyCount` 공급(기존 0 고정) — demark_buy_setup 피처 gradient 살아남. `demarkRows` 파라미터 추가, 호출처 `StatisticalAnalysisEngine:156` 배선
  - 4-3 `CorrelationEngine`: `supplyMacd.drop(1)`로 volumeChanges(n-1)와 길이·날짜 정렬 — `size==size`(n-1 vs n) 절대 거짓으로 스킵되던 수급↔거래량 상관 계산 복원
  - 4-4 `OrderFlowEngine`: `calcMeanReversionSignal`/`ofiToSignal`의 과거 z-score 분포를 `calcOfi` **동일 공식**(전체 분모, retail/inst 포함)의 롤링 시리즈로 재구축 — 외국인-only 분포와의 통계량 불일치 편향 제거. 두 함수에 `inst`/`retail` 인자 추가
  - 4-5 `BayesianUpdateEngine`(2곳)+`NaiveBayesEngine`(sibling): PBR≤0(결측)을 `FAIR`로 처리 — BayesianUpdate는 결측→OVERVALUED 하방 왜곡, NaiveBayes는 `pbr<1.0`이 0까지 포함해 결측→UNDERVALUED로 **정반대** 분류(엔진 불일치)였음. 양쪽 FAIR로 일관화
  - 4-6 `SectorCorrelationNetwork`: 꼬리 truncate 정렬(minLen) → **공통 거래일 교집합** 후 수익률 계산 — 휴장 갭 시 서로 다른 날짜끼리 상관되던 결함 제거. 날짜→종가 맵 기반 재작성, `nPeers` 소스 갱신
  - 4-7 `ProbabilityInterpreter`: MDD 경고 조건 `< -0.05`(항상 ≥0이라 도달 불가) → `> 0.05`, 표기 `pctSigned(-avgMdd20d)`로 하방 부호 처리
  - 4-8 `SignalScoringEngine`: patternWinRates 맵 구성 시 `totalOccurrences==0`이면 `DEFAULT_WEIGHT` — 무발생 패턴 winRate 0.0이 `?: DEFAULT_WEIGHT`를 우회해 가중치 0(신호 조용히 비활성)되던 결함 제거
- [x] P5 — 성능 6건 (2026-07-20 완료 — 타깃 테스트 131/131 그린: StatisticalRepositoryImpl 24·StatisticalAnalysisEngine 15·FeatureStore 14·SignalCalibrator 15·StackingEnsemble 17·BearSignalViewModel 46. compileDebugKotlin·compileDebugUnitTestKotlin 성공. 커밋 대기)
  - 5-1 `StatisticalRepository`: `getOscillatorData(prices)`/`getDemarkData(prices)` **로드된 리스트 오버로드** 추가 → 오케스트레이터가 365행 가격 테이블을 3회→**1회** 로드(엔진에서 `getDailyPrices` 1회 후 두 오버로드에 전달). 신규 회귀 3건(DAO 재조회 0 단언 + 엔진 로드 1회 단언)
  - 5-2 `BearSignalViewModel.refresh`: **신선도 게이트**(`REFRESH_FRESHNESS_WINDOW_MS=1h`) — 최근 자동 수집(핵심 일간 up3/down3/up4/down4/kospi2 max updatedAt)이 창 이내면 풀 파이프라인(KRX 로그인 2회+관세청 XML+지수 20종) skip. `launchRefresh(force)` 분리 — 신선도 제안 "수락"은 force로 우회. §3 임계치 무접촉. 회귀 3건(창 이내 skip/창 밖 실행/force 우회)
  - 5-3 `OscillatorScreen`: `ParkSignalDetector.detect`(≤1년 캔들) composition 중 메인스레드 실행 → **`produceState`+`withContext(Dispatchers.Default)`**로 오프메인(키=일별데이터·봉단위 변경 시에만 재계산). ※ 리뷰 방침 "ViewModel에서 Default"은 VM `stateIn`+`flowOn(Default)`/파이어앤포겟 `launch(Default)`가 기존 시간의존 VM 테스트의 `advanceUntilIdle`를 무한 대기시켜 부적합 — 성능 의도(오프메인) 동일 충족하는 Compose 관용 패턴으로 대체
  - 5-4 `FeatureStore.getOrCompute`: **per-key `Mutex`**(`ConcurrentHashMap<String,Mutex>`) + fast-path 캐시 조회 + 락 내 double-check — 동일 종목 동시 분석 시 11-엔진 compute 중복 실행 방지. 회귀 1건(동시 5호출 compute 1회)
  - 5-5 `DataIntegrityCheckWorker.checkFearGreedIntegrity`: market-agnostic `updateFearGreed`를 시장별 2회(+5s delay)→**1회** 호출 + 하드코딩 0 대신 **실제 diff**(전후 count) 반환. 미사용 `loadFearGreedCollectionPeriod` import 제거
  - 5-6 `SignalCalibrator`/`StackingEnsemble` 동기화: 워커 write ↔ 분석 read 가시성. Calibrator → `@Volatile ConcurrentHashMap` + `loadState` 새 맵 원자 스왑(clear() 빈맵 노출 창 제거). Ensemble → 계수·절편·메타를 불변 `FittedModel` 홀더로 묶어 `@Volatile` 참조 원자 스왑(찢어진 읽기 방지)
  - ⚠ 미검증(P5 무관): `OscillatorViewModelTest`/`EdgeCase`/`ConfigMutex`는 `analyze()`→`startAutoRefresh` 무한 `delay(60s)` 루프가 **장중(KST 09:00–15:30)에 실행 시** `advanceUntilIdle`를 무한 대기 → **기존 시간의존 결함**(baseline stash 재현으로 확인). P5 VM diff는 미사용 `CandleChartUi` data class +15줄뿐이라 analyze 경로 무변경. 장 마감 후 재실행 필요(또는 별건으로 `TradingHours` 주입 리팩터)
- [ ] P6 — 사용성·접근성 8건
- [ ] P7 — 죽은 코드·빌드·문서 정리
- [ ] P8 — 테스트 공백 보강
