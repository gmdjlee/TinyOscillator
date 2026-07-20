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
| P3 — 인프라 정확성·동시성 8건 | 미착수 | |
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
