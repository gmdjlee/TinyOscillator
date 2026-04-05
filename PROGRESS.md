# PROGRESS.md — Implementation State

_Last updated: 2026-04-05 | Session: SC-05 — 관심종목 리스트_

---

## SC-05 — 관심종목 리스트 (신호 정렬 · 스와이프 삭제 · 드래그 순서 · 폴더)

### New files
| File | Purpose |
|------|---------|
| `core/database/entity/WatchlistGroupEntity.kt` | 관심종목 그룹 Room 엔티티 (watchlist_groups) |
| `core/database/entity/WatchlistItemEntity.kt` | 관심종목 아이템 Room 엔티티 (watchlist_items) + 캐시 필드 |
| `core/database/dao/WatchlistDao.kt` | 그룹별/신호순 Flow 관찰, CRUD, 캐시 갱신, 순서/그룹 이동 |
| `presentation/watchlist/SwipeToDeleteBox.kt` | Compose 네이티브 SwipeToDismissBox 래퍼 |
| `presentation/watchlist/DraggableWatchlistColumn.kt` | PointerInput 롱프레스 → 드래그 순서 변경 |
| `presentation/watchlist/WatchlistItemRow.kt` | 종목 행 (드래그 핸들 + 신호 바 + 등락률) |
| `presentation/watchlist/WatchlistHeader.kt` | 정렬 FilterChip + 그룹 탭 + AddGroupDialog |
| `presentation/watchlist/WatchlistViewModel.kt` | HiltViewModel (정렬/그룹 필터/undo 삭제/reorder) |
| `presentation/watchlist/WatchlistScreen.kt` | 통합 화면 (Scaffold + Snackbar + Header + List) |

### Modified files
| File | Change |
|------|--------|
| `core/database/AppDatabase.kt` | v23→v24: WatchlistGroupEntity, WatchlistItemEntity 등록 |
| `core/di/DatabaseModule.kt` | MIGRATION_23_24 + WatchlistDao provider |
| `MainActivity.kt` | WATCHLIST BottomNavItem (Star) + WatchlistScreen 라우팅 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `WatchlistSortTest.kt` | 6 | PASS |
| `WatchlistDaoTest.kt` | 6 | PASS (Robolectric) |

### Design decisions
- **Compose 네이티브 우선**: SwipeToDismissBox + PointerInput 드래그 — 외부 드래그 라이브러리 불사용
- **Undo Snackbar**: 삭제 시 엔티티 임시 보관 → SnackbarResult.ActionPerformed 시 재삽입
- **그룹 필터**: flatMapLatest로 그룹 선택 시 observeByGroup ↔ observeAll 전환
- **정렬 키**: SIGNAL_DESC/ASC, CUSTOM_ORDER, CHANGE_DESC — FilterChip UI
- **캐시 필드**: cachedPrice/cachedChange/cachedSignal — WorkManager가 주기적 갱신 (미래 확장점)

---

## SC-04 — 수익률 비교 (종목·섹터·KOSPI 오버레이 차트)

### New files
| File | Purpose |
|------|---------|
| `domain/model/ComparisonData.kt` | ComparisonSeries, ComparisonData, ComparisonPeriod 도메인 모델 |
| `domain/usecase/BuildComparisonUseCase.kt` | 수익률 정규화, OLS 베타 추정, 섹터 평균 계산 UseCase |
| `presentation/comparison/ComparisonViewModel.kt` | 검색+기간+비교 상태 관리 HiltViewModel |
| `presentation/comparison/ComparisonScreen.kt` | 수익률 비교 UI (LineChart 오버레이 + 신호 강도 + α/β 카드) |

### Modified files
| File | Change |
|------|--------|
| `core/database/dao/RegimeDao.kt` | getKospiIndexByDateRange(fromDate, toDate) 쿼리 추가 |
| `MainActivity.kt` | COMPARISON BottomNavItem + ComparisonScreen 라우팅 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `ComparisonCalculationTest.kt` | 8 | PASS (0.092s) |

### Design decisions
- **Room 캐시 전용**: API 호출 없이 analysis_cache, kospi_index, signal_history에서만 데이터 수집 → 즉시 응답
- **OLS 베타 추정**: 순수 Kotlin — 외부 라이브러리 불필요
- **섹터 평균**: 동일 섹터 상위 20개 종목의 공통 날짜 종가 평균
- **MPAndroidChart LineChart 재사용**: 수익률 차트 + 신호 강도 차트 (기존 의존성)
- **ComparisonPeriod**: CUSTOM 포함 4개 기간 (3M/6M/1Y/직접)

---

## SC-03 — 섹터/테마 그룹화 (KRX 섹터 + 사용자 테마)

### New files
| File | Purpose |
|------|---------|
| `domain/model/StockGroup.kt` | StockGroup 도메인 모델, GroupType enum, DEFAULT_THEMES |
| `core/database/entity/UserThemeEntity.kt` | 사용자 테마 Room 엔티티 |
| `core/database/dao/UserThemeDao.kt` | 사용자 테마 CRUD DAO |
| `data/repository/StockGroupRepository.kt` | KRX 섹터/사용자 테마 통합 리포지토리 (신호 점수 집계) |
| `presentation/sector/SectorGroupViewModel.kt` | 섹터/테마 화면 ViewModel |
| `presentation/sector/SectorGroupScreen.kt` | 섹터/테마 메인 화면 + GroupCard |
| `presentation/sector/AddThemeDialog.kt` | 테마 추가 다이얼로그 |
| `presentation/sector/GroupDetailScreen.kt` | 그룹 내 종목 드릴다운 화면 + GroupDetailViewModel |

### Modified files
| File | Change |
|------|--------|
| `core/database/AppDatabase.kt` | v22→v23, UserThemeEntity 추가, userThemeDao() |
| `core/di/DatabaseModule.kt` | MIGRATION_22_23 (user_themes 테이블), provideUserThemeDao() |
| `core/database/dao/StockMasterDao.kt` | observeAllSectors() Flow, getAllTickersBySector() 추가 |
| `MainActivity.kt` | SECTOR_THEME BottomNavItem, group_detail 라우트, onGroupClick 콜백 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `StockGroupAggregationTest.kt` | 11 | PASS |

### Design decisions
- **DB v23**: user_themes 테이블 추가 (id, name, tickers JSON, sort_order, created_at)
- **신호 점수 소스**: CalibrationDao.getLatestAvgScoresByTicker() 재사용 (SC-02 스크리너와 동일)
- **scoreColor 재사용**: presentation/common/SignalRationaleCard.kt의 기존 함수 활용
- **buildGroup companion**: StockGroupRepository.Companion에 정적 메서드 → 테스트에서 직접 검증
- **GroupDetailScreen**: SavedStateHandle로 groupName/tickers 전달 → 종목명 + 신호점수 표시
- **기본 테마**: 앱 최초 설치 시 5개 테마 자동 생성 (K-방산, 2차전지, 조선, 반도체, 바이오)

---

## SC-02 — 종목 스크리너 (필터/정렬/DataStore 저장)

### New files
| File | Purpose |
|------|---------|
| `data/datasource/ScreenerDataSource.kt` | Room DB 기반 스크리너 엔진 (후보 필터링 → 지표 수집 → 필터/정렬) |
| `data/preferences/ScreenerFilterPreferences.kt` | DataStore 기반 필터 조건 저장/복원 |
| `presentation/screener/ScreenerViewModel.kt` | 필터/정렬 상태 관리 + debounce 500ms |
| `presentation/screener/ScreenerScreen.kt` | 스크리�� 메인 화면 (결과 리스트 + 정렬 칩 + SignalBadge) |
| `presentation/screener/ScreenerFilterSheet.kt` | 필터 BottomSheet (신호강도 RangeSlider, 시총 프리셋, PBR/외국인/거래량 슬라���더, 시장/섹터) |

### Modified files
| File | Change |
|------|--------|
| `domain/model/ScreeningModels.kt` | ScreenerFilter data class, ScreenerSortKey enum 추가 |
| `core/database/dao/StockMasterDao.kt` | getFilteredCandidates(), getAllSectors() 쿼리 추가 |
| `core/database/dao/CalibrationDao.kt` | TickerAvgScore POJO, getLatestAvgScoresByTicker() 벌크 쿼리 추가 |
| `MainActivity.kt` | SCREENER BottomNavItem (Icons.Default.Tune) + ScreenerScreen 라우팅 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `ScreenerFilterTest.kt` | 9 | PASS (0.173s) |

### Design decisions
- **StockMasterEntity 미변경**: 시가총액은 analysis_cache에서, PBR은 fundamental_cache에서 조회 — 마스터 테이블 확장 불필요
- **DB 마이그레이션 불필요**: 기존 테이블/엔티티만 활용, 새 테이블 없음
- **DataStore 분리**: `screener_filter_preferences` 별도 DataStore — 기존 indicator_preferences와 격리
- **meetsFilter companion**: ScreenerDataSource.Companion에 정적 메서드로 노출 → 테스트에서 직접 검증 가능
- **���호 점수 소스**: signal_history 테이블의 최신 날짜 평균 점수 사용 (앙상블 결과 캐시)
- **거래량 비율**: analysis_cache에 거래량 컬럼 없으므로 1.0 기본값 (향후 확장점)

---

## SEARCH-01 — 종목 검색 자동완성 (초성 + 최근 검색)

### 현황 파악 (TASK 0)
종목 입력 진입점:
1. **MainActivity.kt:450** — OutlinedTextField "종목명 또는 종목코드 검색" + autocomplete dropdown
2. **AiAnalysisScreen.kt:304** — StockTabContent OutlinedTextField
3. **AddHoldingDialog.kt:59** — 포트폴리오 종목 추가 다이얼로그
4. **StockAnalysisTab.kt:41** — ETF 통계 종목 검색

모두 `StockMasterDao.searchStocks()` (LIKE query + '%') 를 사용 → 초성 검색 불가, 부분 매칭만.

### New files
| File | Purpose |
|------|---------|
| `core/util/KoreanUtils.kt` | 초성 추출, 초성 쿼리 감지, 통합 매칭 (순수 Kotlin) |
| `data/preferences/RecentSearchPreferences.kt` | DataStore 기반 최근 검색 5개 기록 |
| `presentation/common/StockSearchBar.kt` | 검색 결과 + 최근 검색 드롭다운 콘텐츠 Composable |

### Modified files
| File | Change |
|------|--------|
| `core/database/entity/StockMasterEntity.kt` | `initial_consonants` 컬럼 추가 |
| `core/database/dao/StockMasterDao.kt` | `searchByText()`, `searchByChosung()`, `getByTicker()` 추가 |
| `core/database/AppDatabase.kt` | version 21→22 |
| `core/di/DatabaseModule.kt` | MIGRATION_21_22 (initial_consonants ALTER TABLE) |
| `data/repository/StockMasterRepository.kt` | `searchWithChosung()`, `backfillChosung()`, `getByTicker()` 추가, 종목 삽입 시 초성 자동 계�� |
| `domain/usecase/SearchStocksUseCase.kt` | `searchWithChosung()` suspend 메서드 추가 |
| `presentation/viewmodel/OscillatorViewModel.kt` | 검색 Flow를 초성 통합 검색으로 변경 |
| `presentation/ai/AiAnalysisViewModel.kt` | 검색 Flow를 초성 통합 검색으로 변경 |
| `presentation/portfolio/PortfolioViewModel.kt` | 검색을 초성 통합 검색으로 변경 |
| `TinyOscillatorApp.kt` | 앱 시작 시 기존 데이터 초성 백필 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `KoreanUtilsTest.kt` | 25 | PASS (0.078s) |

### Design decisions
- **StockMasterEntity 확장**: 새 엔티티 생성 대신 기존 `stock_master` 테이블에 `initial_consonants` 컬럼 추가 — 기존 DAO/Repository 재사용
- **Room 백필 전략**: MIGRATION_21_22는 ALTER TABLE만 수행, 앱 시작 시 `backfillChosung()` 으로 기존 종목에 초성 계산
- **DataStore 분리**: `recent_search_preferences` 별도 DataStore — indicator_preferences와 키 충돌 방지
- **ViewModel 미생성**: 기존 ViewModel(OscillatorVM, AiAnalysisVM, PortfolioVM)의 검색 로직을 chosung-aware로 교체
- **SearchBar 미변경**: 기존 OutlinedTextField + dropdown 패턴 유지 — Material 3 SearchBar는 layout 변경이 큼

---

## SIGNAL-T05 — 알고리즘 신호 충돌 감지 + 앰버 경고 배너

### New files
| File | Purpose |
|------|---------|
| `domain/usecase/SignalConflictDetector.kt` | σ 기반 4단계 충돌 감지 (NONE/LOW/HIGH/CRITICAL), 포지션 배수 산출 |
| `presentation/common/ConflictWarningBanner.kt` | 충돌 경고 배너 + 강세/중립/약세 분포 바 + 추천 포지션 배수 |

### Modified files
| File | Change |
|------|--------|
| `presentation/ai/AiAnalysisScreen.kt` | ConflictWarningBanner 통합 (SignalRationaleCard 상단), PositionGuideCard에 conflictMultiplier 파라미터 추가 + "충돌 축소" 칩 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `SignalConflictDetectorTest.kt` | 16 | PASS |
| `ConflictIntegrationTest.kt` | 4 | PASS |

### Design decisions
- **ViewModel 미생성**: SignalTransparencyViewModel 없음 — AiAnalysisViewModel에 이미 통합된 구조. `remember(algoResults)` 패턴으로 Screen에서 직접 계산
- **Kelly 연동 방식**: Engine 레벨이 아닌 UI 레벨에서 `conflictMultiplier` 적용 — StatisticalAnalysisEngine 수정 불필요, 관심사 분리 유지
- **충돌 수준 임계값**: σ < 0.12 NONE, 0.12~0.18 LOW(75%), 0.18~0.25 HIGH(50%), > 0.25 CRITICAL(25%)
- **한국 관례 색상**: LOW=앰버(#FAEED0), HIGH=적갈색(#FAECE7), CRITICAL=적색(#FCEBEB)
- **분포 바**: 강세(적색 #D85A30) / 중립(회색 #888780) / 약세(청색 #378ADD) — 히트맵 색상 관례와 일치

### Signal Transparency Architecture Summary (S-T01 ~ S-T05)
```
StatisticalResult
  └→ RationaleBuilder.build() → Map<String, AlgoResult>
      ├→ SignalRationaleCard (점수 바 + 근거)
      ├→ AlgoContributionView (레이더/폭포수 차트)
      ├→ SignalConflictDetector.detect() → ConflictResult
      │   ├→ ConflictWarningBanner (경고 배너 + 분포 바)
      │   └→ PositionGuideCard (conflictMultiplier 적용)
      └→ AlgoAccuracyCard (적중률)

SignalHistoryEntity (Room)
  ├→ T+N 수익률 수집 (SignalOutcomeUpdateWorker)
  ├→ 적중률 집계 (CalibrationDao)
  └→ 히트맵 (BuildHeatmapUseCase → SignalHeatmap)
```

---

## SIGNAL-T04 — 신호 강도 히트맵 (날짜×종목)

### New files
| File | Purpose |
|------|---------|
| `domain/model/HeatmapData.kt` | 히트맵 데이터 모델 (행=종목, 열=날짜, 셀=점수) |
| `domain/usecase/BuildHeatmapUseCase.kt` | 분석 이력 종목의 일별 앙상블 평균 점수 UseCase |
| `presentation/common/SignalHeatmap.kt` | Canvas 기반 히트맵 Composable (색상+탭+스크롤) |
| `presentation/common/HeatmapViewModel.kt` | 기간 선택 + 데이터 로드 ViewModel |
| `presentation/common/HeatmapScreen.kt` | 히트맵 화면 + 기간 선택기 + 색상 범례 |

### Modified files
| File | Change |
|------|--------|
| `core/database/dao/CalibrationDao.kt` | getAverageScoreForDay, getDistinctDates 쿼리 추가 |
| `presentation/ai/AiAnalysisScreen.kt` | onHeatmapClick 콜백 + GridView 아이콘 버튼 추가 |
| `MainActivity.kt` | heatmap NavHost 라우트, onHeatmapClick 콜백 전달 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `HeatmapDataTest.kt` | 6 | PASS |
| `HeatmapColorTest.kt` | 9 | PASS |

### Design decisions
- **WatchlistRepository 없음**: AnalysisHistoryDao.getAll()로 최근 분석 종목을 히트맵 행으로 사용
- **날짜 형식 적응**: SignalHistoryEntity.date가 String "yyyyMMdd" → getAverageScoreForDay(ticker, date) 쿼리
- **Canvas 직접 구현**: Vico/MPAndroidChart 불필요 — 28dp 셀 + nativeCanvas.drawText
- **한국 관례 색상**: 강세=적색(D85A30), 약세=청색(378ADD), 5단계 구분
- **네비게이션**: AiAnalysisScreen TopAppBar에 GridView 아이콘 → "heatmap" NavHost 라우트 → 셀 탭 시 stock_aggregated/{ticker}

---

## SIGNAL-T03 — 신호 이력 저장 + T+N 수익률 수집 + 적중률 UI

### New files
| File | Purpose |
|------|---------|
| `data/repository/SignalHistoryRepository.kt` | 신호 기록·적중률 조회·T+N 업데이트 리포지토리 |
| `core/worker/SignalOutcomeUpdateWorker.kt` | 매일 18:00 T+N 결과 수집 HiltWorker |
| `presentation/common/AlgoAccuracyCard.kt` | 알고리즘 적중률 진행 바 + % + 건수 카드 |

### Modified files
| File | Change |
|------|--------|
| `core/database/entity/SignalHistoryEntity.kt` | outcome_t1/t5/t20 Float? 컬럼 추가 |
| `core/database/dao/CalibrationDao.kt` | T+N 업데이트, 적중률 집계, observeAllHistory, getPendingTickers 쿼리 추가 |
| `domain/model/SignalTransparencyModels.kt` | AlgoAccuracyRow 데이터 클래스 추가 |
| `core/database/AppDatabase.kt` | version 20→21 |
| `core/di/DatabaseModule.kt` | MIGRATION_20_21 (signal_history 3컬럼 추가) |
| `core/worker/WorkManagerHelper.kt` | scheduleSignalOutcomeUpdate/cancel/runNow |
| `core/worker/CollectionNotificationHelper.kt` | SIGNAL_OUTCOME_NOTIFICATION_ID = 1012 |
| `TinyOscillatorApp.kt` | SignalOutcomeUpdate 워커 자동 등록 |
| `presentation/ai/AiAnalysisScreen.kt` | AlgoAccuracyCard 통합 + algoAccuracy 파라미터 전달 |
| `presentation/ai/AiAnalysisViewModel.kt` | SignalHistoryRepository 주입, algoAccuracy StateFlow |
| `test/.../AiAnalysisViewModelTest.kt` | SignalHistoryRepository mock 추가 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `SignalAccuracyCalculationTest.kt` | 9 | PASS |
| `SignalHistoryRepositoryTest.kt` | 7 | PASS |

### Design decisions
- **기존 signal_history 테이블 확장**: 새 테이블 대신 3개 컬럼(outcome_t1/t5/t20) 추가 — CalibrationDao와 공유
- **CalibrationDao 확장**: 별도 DAO 대신 기존 CalibrationDao에 적중률 쿼리 추가 — 동일 테이블 접근
- **적중률 기준**: raw_score > 0.5 AND outcome_t1 > 0 (강세 신호+양수 수익) OR raw_score < 0.5 AND outcome_t1 < 0 (약세 신호+음수 수익)
- **워커 시간**: 18:00 KST — 장 마감(15:30) 후 충분한 여유, 19:00 IncrementalModel 워커 전

---

## SIGNAL-T02 — 알고리즘 기여도 시각화 (레이더 + 폭포수)

### New files
| File | Purpose |
|------|---------|
| `presentation/common/AlgoRadarChartView.kt` | MPAndroidChart RadarChart — 알고리즘 꼭짓점, 신호 강도 면적 채움 |
| `presentation/common/AlgoWaterfallChart.kt` | Compose Canvas 폭포수 — 0.5 기준선, 기여분 누적 바, 앙상블 점수 |
| `presentation/common/AlgoContributionView.kt` | SegmentedButton 토글 래퍼 (레이더 ↔ 폭포수), rememberSaveable 상태 |

### Modified files
| File | Change |
|------|--------|
| `presentation/ai/AiAnalysisScreen.kt` | AlgoContributionView 통합 (SignalRationaleCard 하단) |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `AlgoContributionLogicTest.kt` | 10 | PASS (0.11s) |

### Design decisions
- **레이더 차트**: MPAndroidChart `RadarChart` 재사용 — 별도 라이브러리 추가 없음
- **폭포수 차트**: Compose Canvas 직접 구현 — MPAndroidChart BarChart 대신 경량화
- **기여분 계산**: `(score - 0.5) * weight` — 0.5 기준선 대비 편차 × 가중치
- **정렬**: 폭포수는 가중치 내림차순, 레이더는 알고리즘명 오름차순
- **색상**: 강세 적색(#D85A30), 약세 청색(#378ADD), 앙상블 금색(#BA7517)
- **토글 상태**: `rememberSaveable`로 화면 회전 시 유지
- **@OptIn(ExperimentalMaterial3Api::class)**: SegmentedButton API 사용

---

## SIGNAL-T01 — 신호 투명성: 알고리즘별 근거 카드

### New files
| File | Purpose |
|------|---------|
| `data/engine/RationaleBuilder.kt` | StatisticalResult → Map<String, AlgoResult> 변환, 10개 알고리즘별 한국어 근거 생성 |
| `presentation/common/SignalRationaleCard.kt` | 펼치기/접기 카드 Composable (점수 바, ScoreBadge, AlgoRationaleRow) |

### Modified files
| File | Change |
|------|--------|
| `presentation/ai/AiAnalysisScreen.kt` | SignalRationaleCard 통합 (EnsembleProbabilityCard 하단) |

### Algorithm inventory (근거 생성 대상)
| # | Algorithm | AlgoResult key | 근거 예시 |
|---|-----------|---------------|-----------|
| 1 | Naive Bayes | NaiveBayes | 상승65% RSI_14 기여 — 강세 |
| 2 | Logistic Regression | Logistic | 점수72/100 ema_cross 1.2 — 강세 |
| 3 | HMM Regime | HMM | 저변동상승 신뢰70% — 강세 |
| 4 | Pattern Scan | PatternScan | 1개 활성 최고승률65% — 강세 |
| 5 | Signal Scoring | SignalScoring | 점수68/100 RSI 35% — 강세 |
| 6 | Bayesian Update | BayesianUpdate | 사전50%→사후58% ↑8p — 중립 |
| 7 | Order Flow | OrderFlow | 매수우위65% 강도보통 — 강세 |
| 8 | DART Event | DartEvent | 2건 CAR+1.5% 신호60% — 중립 |
| 9 | Korea 5-Factor | Korea5Factor | 알파z=+1.2 신호70% — 강세 |
| 10 | Sector Correlation | SectorCorrelation | IT 정상 신호50% — 중립 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `RationaleBuilderTest.kt` | 12 | PASS |
| `SignalRationaleDisplayTest.kt` | 11 | PASS |
| **Total** | **23** | **ALL PASS** |

### Design decisions
- **No separate ViewModel**: SignalRationaleCard uses existing `StatisticalResult` from `ProbabilityAnalysisState.Success`, computed via `remember {}` — avoids duplicate analysis execution
- **No Python/Chaquopy**: All rationale generation in pure Kotlin (`RationaleBuilder`)
- **50자 제한**: `.take(50)` enforced on all rationale strings
- **점수 바 색상**: `lerp()` 보간 — 0.0(청색) → 0.5(회색) → 1.0(적색)

---

## CHART-K03 — 거래량 프로파일 오버레이 (POC + Value Area + 버킷 바)

### New files
| File | Purpose |
|------|---------|
| `domain/model/VolumeProfile.kt` | VolumeBucket, VolumeProfile 도메인 모델 |
| `domain/usecase/BuildVolumeProfileUseCase.kt` | 버킷 집계, POC, Value Area 70% 계산 (순수 Kotlin, IO 없음) |
| `presentation/chart/overlay/VolumeProfileOverlay.kt` | Compose Canvas DrawScope 오버레이 (VA 배경 + bull/bear 버킷 바 + POC 라인) |
| `presentation/chart/bridge/ChartAxisBridge.kt` | MPAndroidChart CombinedChart y축 범위 → Compose State 브릿지 |

### Modified files
| File | Change |
|------|--------|
| `presentation/chart/composable/KoreanCandleChartView.kt` | Box 래퍼 추가, volumeProfile 파라미터, VolumeProfileOverlay 통합, axisBridge.update() |
| `presentation/viewmodel/StockChartViewModel.kt` | volumeProfile StateFlow 추가 (VOLUME_PROFILE 선택 시 자동 계산) |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `BuildVolumeProfileUseCaseTest.kt` | 12 | PASS |
| `VolumeProfileOverlayLogicTest.kt` | 5 | PASS |
| `ChartAxisBridgeTest.kt` | 1 | PASS |
| **Total** | **18** | **ALL PASS** |

---

## CHART-K02 — 기술 지표 오버레이 (EMA/볼린저/MACD/RSI/스토캐스틱)

### New files
| File | Purpose |
|------|---------|
| `domain/model/Indicator.kt` | Indicator enum, OverlayType, IndicatorParams 도메인 모델 |
| `domain/indicator/IndicatorCalculator.kt` | EMA, 볼린저밴드, MACD, RSI, 스토캐스틱 순수 Kotlin 계산 |
| `presentation/chart/ext/IndicatorDataSetExt.kt` | FloatArray → LineDataSet, IndicatorData → LineData 변환 |
| `presentation/chart/composable/OscillatorChartView.kt` | MACD (CombinedChart), RSI (LineChart), 스토캐스틱 서브차트 |
| `presentation/chart/composable/IndicatorSheet.kt` | BottomSheet 지표 선택기 (최대 4 가격 / 1 오실레이터) |
| `data/preferences/IndicatorPreferencesRepository.kt` | DataStore 기반 지표 선택 + 파라미터 영속화 |
| `presentation/viewmodel/StockChartViewModel.kt` | 지표 계산 + preferences 연동 ViewModel |

### Modified files
| File | Change |
|------|--------|
| `presentation/chart/composable/KoreanCandleChartView.kt` | CandleStickChart → CombinedChart, indicatorData 파라미터 추가 |
| `presentation/chart/interaction/ChartSyncManager.kt` | CandleStickChart → BarLineChartBase<*> (CombinedChart 호환) |
| `presentation/chart/interaction/InertialScrollHandler.kt` | CandleStickChart → BarLineChartBase<*> |
| `core/di/AppModule.kt` | DataStore + IndicatorPreferencesRepository DI 등록 |
| `build.gradle.kts` | DataStore preferences 의존성 추가 |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `IndicatorCalculatorTest.kt` | 17 | PASS |
| `IndicatorConstraintTest.kt` | 2 | PASS |
| **Total** | **19** | **ALL PASS** |

---

## CHART-K01 — MPAndroidChart 인터랙션 개선

### New files
| File | Purpose |
|------|---------|
| `presentation/chart/marker/OhlcvMarkerView.kt` | OHLCV 크로스헤어 MarkerView (경계 감지, 시가/고가/저가/종가 + 패턴) |
| `presentation/chart/interaction/InertialScrollHandler.kt` | VelocityTracker + OverScroller 관성 스크롤 |
| `presentation/chart/interaction/ChartSyncManager.kt` | 캔들 ↔ 거래량 뷰포트/하이라이트 동기화 |
| `presentation/chart/formatter/KoreanVolumeFormatter.kt` | 거래량 축 한국식 단위 (조/억/만) |
| `presentation/chart/formatter/KoreanPriceFormatter.kt` | 가격 축 천 단위 쉼표 |
| `presentation/chart/formatter/IndexDateFormatter.kt` | X축 인덱스→날짜 포매터 |
| `presentation/chart/ext/FormatExt.kt` | Long/Float.formatKRW() 확장 |
| `presentation/chart/ext/CandleDataExt.kt` | toCandleData() / toVolumeBarData() 확장 |
| `presentation/chart/composable/KoreanCandleChartView.kt` | Compose 래퍼 (캔들 70% + 거래량 30%) |
| `res/layout/view_ohlcv_marker.xml` | 마커 레이아웃 (6 TextViews) |
| `res/drawable/ohlcv_marker_bg.xml` | 마커 배경 (rounded, semi-transparent white) |

### Tests
| Test file | Tests | Status |
|-----------|-------|--------|
| `FormatExtTest.kt` | 5 | PASS |
| `KoreanVolumeFormatterTest.kt` | 6 | PASS |
| `CandleDataExtTest.kt` | 6 | PASS |
| `MarkerOffsetTest.kt` | 5 | PASS |
| **Total** | **22** | **ALL PASS** |

---

## Baseline architecture snapshot
_Captured by PROMPT 00. Do not manually edit this section — it is the reference state
against which all future changes are measured._

### Kotlin source files
134 source files in `app/src/main/java/com/tinyoscillator/`
98 test files in `app/src/test/java/com/tinyoscillator/`
0 androidTest files

### Python analysis layer
| File | Purpose | Status |
|------|---------|--------|
| `concen/equity_report_scraper_450.py` | Standalone analyst report scraper (not in Android build) | External tool |

**No Python files exist in the Android app.** There is no `app/src/main/python/` directory and no Chaquopy integration.

### C++ / JNI layer
| File | Purpose | Status |
|------|---------|--------|
| `app/src/main/cpp/CMakeLists.txt` | CMake build for llama.cpp JNI | Present |
| `app/src/main/cpp/llama_jni.cpp` | JNI bridge to llama.cpp for local LLM inference | Present |
| `app/src/main/cpp/llama_jni_stub.cpp` | Stub implementation for builds without llama.cpp | Present |

### Algorithm registry
| # | Algorithm | Class | File | Output range | Calibrated | Ensemble weight |
|---|-----------|-------|------|-------------|------------|----------------|
| 1 | Naive Bayes | `NaiveBayesEngine` | `data/engine/NaiveBayesEngine.kt` | [0,1] × 3 classes | No | Equal (no weighting) |
| 2 | Logistic Regression | `LogisticScoringEngine` | `data/engine/LogisticScoringEngine.kt` | [0,1] + 0–100 | No | Equal |
| 3 | HMM Regime | `HmmRegimeEngine` | `data/engine/HmmRegimeEngine.kt` | 4 regimes, [0,1]⁴ | No | Equal |
| 4 | Pattern Scan | `PatternScanEngine` | `data/engine/PatternScanEngine.kt` | Win rates [0,1] | No | Equal |
| 5 | Signal Scoring | `SignalScoringEngine` | `data/engine/SignalScoringEngine.kt` | 0–100 + direction | No | Win-rate weighted (internal) |
| 6 | Rolling Correlation | `CorrelationEngine` | `data/engine/CorrelationEngine.kt` | r ∈ [-1,1] | No | Equal |
| 7 | Bayesian Updating | `BayesianUpdateEngine` | `data/engine/BayesianUpdateEngine.kt` | [0.001,0.999] | No | Equal |
| 8 | Order Flow | `OrderFlowEngine` | `data/engine/OrderFlowEngine.kt` | [0,1] buyerDominanceScore | No | Regime-weighted |
| 9 | DART Event Study | `DartEventEngine` | `data/engine/DartEventEngine.kt` | [0,1] signalScore | No | Regime-weighted |
| 10 | Korea 5-Factor | `Korea5FactorEngine` | `data/engine/Korea5FactorEngine.kt` | [0,1] signalScore (sigmoid(alpha_zscore)) | No | Regime-weighted |
| 11 | Sector Correlation | `SectorCorrelationNetwork` | `data/engine/network/SectorCorrelationNetwork.kt` | [0,1] signalScore (outlier=0.6~1.0, normal=0.3~0.5) | No | Regime-weighted |

### Ensemble orchestrator
- **Class**: `StatisticalAnalysisEngine`
- **File**: `data/engine/StatisticalAnalysisEngine.kt`
- **Aggregation**: Regime-aware weighting via `RegimeWeightTable` — all 11 engines run in parallel via coroutineScope/async; results collected into `StatisticalResult` with individual fields + `MarketRegimeResult`. The AI API (via `ProbabilisticPromptBuilder`) or `ProbabilityInterpreter` synthesizes the final interpretation with regime context.
- **Regime integration**: `MarketRegimeClassifier` provides current market regime (BULL_LOW_VOL/BEAR_HIGH_VOL/SIDEWAYS/CRISIS) and per-algorithm weight table. Regime result cached and updated weekly by `RegimeUpdateWorker`.

### Room database entities (v13)
| Entity | DAO | Purpose |
|--------|-----|---------|
| `StockMasterEntity` | `StockMasterDao` | KOSPI/KOSDAQ stock list |
| `AnalysisCacheEntity` | `AnalysisCacheDao` | Per-stock per-date OHLCV + indicators |
| `AnalysisHistoryEntity` | `AnalysisHistoryDao` | Recently analyzed stocks |
| `FinancialCacheEntity` | `FinancialCacheDao` | KIS financial statements (24h TTL) |
| `EtfEntity` | `EtfDao` | ETF master list |
| `EtfHoldingEntity` | `EtfDao` | ETF portfolio composition |
| `MarketOscillatorEntity` | `MarketOscillatorDao` | Market overbought/oversold |
| `MarketDepositEntity` | `MarketDepositDao` | Market deposit/credit |
| `PortfolioEntity` | `PortfolioDao` | User portfolios |
| `PortfolioHoldingEntity` | `PortfolioDao` | Portfolio holdings |
| `PortfolioTransactionEntity` | `PortfolioDao` | Buy/sell transactions |
| `FundamentalCacheEntity` | `FundamentalCacheDao` | KRX fundamental data (730d TTL) |
| `WorkerLogEntity` | `WorkerLogDao` | Worker execution logs |
| `ConsensusReportEntity` | `ConsensusReportDao` | Analyst consensus reports |
| `FearGreedEntity` | `FearGreedDao` | Fear & Greed index |

### Hilt DI modules
| Module | File | Bindings |
|--------|------|----------|
| `AppModule` | `core/di/AppModule.kt` | OkHttpClient, API clients, repositories, use cases |
| `DatabaseModule` | `core/di/DatabaseModule.kt` | AppDatabase (v1→v13 migrations), all DAOs |
| `StatisticalModule` | `core/di/StatisticalModule.kt` | StatisticalRepository, LlmRepository bindings, LogisticPrefs |
| `WorkerModule` | `core/di/WorkerModule.kt` | WorkManager Configuration with HiltWorkerFactory |

### WorkManager jobs
| Worker class | Default schedule | Purpose | Status |
|-------------|-----------------|---------|--------|
| `EtfUpdateWorker` | 00:30 | Sync ETF list + holdings (KRX) | Active |
| `MarketOscillatorUpdateWorker` | 01:00 | KOSPI/KOSDAQ oscillator (KRX) | Active |
| `MarketDepositUpdateWorker` | 02:00 | Deposit/credit (Naver scrape) | Active |
| `ConsensusUpdateWorker` | 03:00 | Analyst reports (Equity + FnGuide) | Active |
| `FearGreedUpdateWorker` | 04:00 | Fear/Greed 7-indicator index | Active |
| `MarketCloseRefreshWorker` | 19:00 | Replace intraday with close-of-day | Active |
| `DataIntegrityCheckWorker` | Manual | Comprehensive data validation | Active |

### API clients
| Client | Base URL | Auth | Rate limit |
|--------|----------|------|------------|
| `KiwoomApiClient` | mockapi/api.kiwoom.com | OAuth2 token | 500ms |
| `KisApiClient` | KIS OpenAPI | OAuth2 token | 500ms |
| `KrxApiClient` | kotlin_krx library | Username/password | None (library-level) |
| `AiApiClient` | Claude/Gemini endpoints | API key header | 1000ms |

### Web scrapers
| Scraper | Source | Rate limit |
|---------|--------|------------|
| `NaverFinanceScraper` | finance.naver.com | 500ms |
| `EquityReportScraper` | equity.co.kr | 8-16s gamma |
| `FnGuideReportScraper` | comp.fnguide.com | 1-5s random |

### KIS API integration
- **Credential storage**: EncryptedSharedPreferences (`api_settings_encrypted`), AES256
- **Token refresh**: `KisApiClient.getToken()` → mutex-protected, 1 min early expiry
- **Rate limiting**: 500ms mutex-based in `BaseApiClient`
- **Active endpoints**: Financial statements (balance sheet, income, profitability, stability, growth)

### Known technical debt
**Blocking**: None

**Non-blocking**:
- No KRX holiday calendar in `TradingHours` (weekday-only check)
- No androidTest infrastructure
- MarketOscillatorCalculator lacks Room caching for KRX OHLCV
- JNI llama.cpp bridge present but unused (AI API used instead)
- No TODO/FIXME/HACK markers found in codebase

---

## Feature expansion log
_Each completed PROMPT session appends one block below._

### [COMPLETE] PROMPT 01 — Signal Calibration (2026-04-02)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python)
- New source files:
  - `data/engine/calibration/SignalCalibrator.kt` — Isotonic (PAVA) + Platt sigmoid calibrators
  - `data/engine/calibration/WalkForwardValidator.kt` — Walk-forward time-series cross-validation
  - `data/engine/calibration/CalibrationMonitor.kt` — Rolling window Brier/ECE monitor with recalibration flag
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts raw 0-1 bullish scores from 6 engines
  - `domain/model/CalibrationModels.kt` — CalibrationMetrics, CalibratedScore, CalibratorState, WalkForwardResult, RawSignalScore, ReliabilityBin
  - `core/database/entity/SignalHistoryEntity.kt` — signal_history table
  - `core/database/entity/CalibrationStateEntity.kt` — calibration_state table
  - `core/database/dao/CalibrationDao.kt` — DAO for both tables
- Modified files:
  - `core/database/AppDatabase.kt` — v13→v14, 2 new entities + calibrationDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_13_14, provideCalibrationDao()
  - `data/engine/StatisticalAnalysisEngine.kt` — records signal history, exposes getCalibratedScores()
- Tests added (4 files):
  - `SignalCalibratorTest.kt` — fit/transform roundtrip, save/load state, all 6 algo names, edge cases
  - `WalkForwardValidatorTest.kt` — no overlap, no future leakage, correct fold count, metrics
  - `CalibrationMonitorTest.kt` — rolling window, recalibration flag trigger, ECE, multi-algo
  - `SignalScoreExtractorTest.kt` — extraction from all engine types, range validation
- Calibrated algorithms: NaiveBayes, Logistic, HMM, PatternScan, SignalScoring, BayesianUpdate (6 of 7; Correlation excluded — no scalar probability output)

### [COMPLETE] PROMPT 02 — Market Regime Detection (2026-04-02)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01
- KOSPI index data: `KrxIndex.getKospi(startDate, endDate)` returns `List<IndexOhlcv>` with OHLCV data
- New source files:
  - `data/engine/regime/GaussianHmm.kt` — 4-state diagonal-covariance Gaussian HMM with Baum-Welch (EM) training, Forward-Backward, Viterbi, serialization
  - `data/engine/regime/MarketRegimeClassifier.kt` — KOSPI regime classifier (4 features: log return, 20d realized vol, 60d momentum, 20d skewness), state labeling, heuristic fallback
  - `data/engine/regime/RegimeWeightTable.kt` — Regime-specific ensemble algorithm weights (BULL_LOW_VOL: momentum, BEAR_HIGH_VOL: correlation/HMM, SIDEWAYS: mean-reversion, CRISIS: HMM/Bayesian), `validateWeights()` assertion
  - `domain/model/RegimeModels.kt` — MarketRegimeResult(regimeId, regimeName, regimeDescription, confidence, probaVec, regimeDurationDays)
  - `core/database/entity/KospiIndexEntity.kt` — KOSPI daily close cache (504d, 1-day TTL)
  - `core/database/entity/RegimeStateEntity.kt` — HMM model state persistence (JSON serialized)
  - `core/database/dao/RegimeDao.kt` — DAO for kospi_index + regime_state
  - `core/worker/RegimeUpdateWorker.kt` — Weekly (Sunday 05:00) retraining WorkManager job
- Modified files:
  - `core/database/AppDatabase.kt` — v14→v15, 2 new entities + regimeDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_14_15, provideRegimeDao()
  - `data/engine/StatisticalAnalysisEngine.kt` — MarketRegimeClassifier injection, cachedRegimeResult, updateRegimeResult(), getRegimeWeights(), regime transition logging
  - `domain/model/StatisticalModels.kt` — Added marketRegimeResult field to StatisticalResult
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretMarketRegime(), regime info in AI prompt
  - `presentation/ai/AiAnalysisScreen.kt` — Regime badge chip (color-coded) + expandable card with probabilities
  - `presentation/ai/AiAnalysisViewModel.kt` — regime interpretation in interpretLocal()
  - `TinyOscillatorApp.kt` — Regime model restore on startup + schedule weekly update
  - `core/worker/WorkManagerHelper.kt` — scheduleRegimeUpdate() (weekly), cancelRegimeUpdate(), runRegimeUpdateNow()
  - `core/worker/CollectionNotificationHelper.kt` — REGIME_NOTIFICATION_ID = 1008
- Tests added (3 files):
  - `GaussianHmmTest.kt` — fit/predict, save/load roundtrip, probability normalization, determinism, transition matrix validation
  - `MarketRegimeClassifierTest.kt` — buildFeatures NaN check, fit/predict validity, save/load roundtrip, duration counter, heuristic fallback
  - `RegimeWeightTableTest.kt` — validateWeights(), weight sums, regime-specific strategy priorities, equal weights fallback
- Existing tests updated: StatisticalAnalysisEngineTest, AnalyzeStockProbabilityUseCaseTest (added MarketRegimeClassifier parameter)

### [COMPLETE] PROMPT 03 — Feature Store (2026-04-02)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01/02
- New source files:
  - `core/database/entity/FeatureCacheEntity.kt` — Room entity (PK=key, ticker index, computed_at index)
  - `core/database/dao/FeatureCacheDao.kt` — get, upsert, evictExpired, evictByTicker, evictAll, count()
  - `domain/model/FeatureStoreModels.kt` — FeatureKey (ticker:feature:date), FeatureTtl (Intraday/Daily/Weekly/Custom), CacheStats
  - `data/engine/FeatureStore.kt` — Singleton with generic `getOrCompute<T>(key, ttl, serializer, compute)`, cache stats Flow
  - `core/worker/FeatureCacheEvictionWorker.kt` — Daily 06:00 KST eviction of expired entries
- Modified files:
  - `core/database/AppDatabase.kt` — v15→v16, FeatureCacheEntity + featureCacheDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_15_16 (feature_cache table), provideFeatureCacheDao()
  - `core/worker/WorkManagerHelper.kt` — scheduleFeatureCacheEviction(), cancelFeatureCacheEviction(), runFeatureCacheEvictionNow()
  - `TinyOscillatorApp.kt` — Schedule feature cache eviction on startup
  - `domain/model/StatisticalModels.kt` — @Serializable on all result types (StatisticalResult, BayesResult, LogisticResult, HmmResult, PatternAnalysis, SignalScoringResult, CorrelationAnalysis, BayesianUpdateResult, etc.)
  - `domain/model/RegimeModels.kt` — @Serializable on MarketRegimeResult
  - `data/engine/StatisticalAnalysisEngine.kt` — FeatureStore injection, analyze() wraps analyzeInternal() via getOrCompute (Daily TTL), clearAnalysisCache()
  - `presentation/ai/AiAnalysisViewModel.kt` — FeatureStore injection, cacheStats StateFlow, clearAnalysisCache()
  - `presentation/ai/AiAnalysisScreen.kt` — Cached/Live SuggestionChip in probability result header
- Tests added (1 file):
  - `FeatureStoreTest.kt` — 12 tests: cache miss/hit, TTL expiry, recompute, invalidate, key format, TTL constants, cache stats
- Existing tests updated: StatisticalAnalysisEngineTest, AnalyzeStockProbabilityUseCaseTest, AiAnalysisViewModelTest (added featureStore parameter)

### [COMPLETE] PROMPT 04 — Order Flow Features (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation — investor data (foreignNetBuy, instNetBuy) already available in DailyTrading via AnalysisCacheEntity
- No new API calls needed — data source: existing KRX/Kiwoom investor trend endpoints cached in analysis_cache table
- New source files:
  - `data/engine/OrderFlowEngine.kt` — 8th engine: OFI (5d/20d), institutional divergence, foreign buy pressure, Z-score sigmoid signal, trend alignment, mean reversion detection
  - `domain/model/StatisticalModels.kt` — OrderFlowResult data class with 12 fields
- Modified files:
  - `data/engine/StatisticalAnalysisEngine.kt` — OrderFlowEngine injection, parallel async execution, result in StatisticalResult
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_ORDER_FLOW constant, ALL_ALGOS updated to 8, weights redistributed (OrderFlow: BULL 0.14, BEAR 0.17, SIDEWAYS 0.15, CRISIS 0.18)
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts buyerDominanceScore for OrderFlow
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretOrderFlow(), updated summarize(), assessOverallDirection(), buildPromptForAi() (8개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — OrderFlow section in AI prompt (8개 알고리즘)
  - `presentation/ai/AiAnalysisScreen.kt` — Order Flow expandable card (direction, OFI, divergence, trend alignment)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes orderflow
- Tests added (1 file):
  - `OrderFlowEngineTest.kt` — 14 tests: bounds checking (OFI [-1,1], signal [0,1], divergence [0,1], fbp [-1,1]), BUY/SELL direction, divergence high/low, trend alignment, zero flow, insufficient data, analysis details keys
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added orderFlowEngine parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added orderFlowEngine parameter
  - `RegimeWeightTableTest.kt` — updated from 7 to 8 algorithms

### [COMPLETE] PROMPT 05 — DART Event Study (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–04
- DART API key stored in EncryptedSharedPreferences via Settings screen
- Corp code mapping: DART corpCode.xml (ZIP) → Room `dart_corp_code` table (30-day cache)
- Event study: OLS beta estimation (120-day window, min 60 obs), CAR with [-5, +20] event window
- Classification: 7 event types (유상증자/자사주/지분변동/경영진변동/실적/배당/기타) via keyword matching
- Signal: time-weighted CAR average → sigmoid → [0,1] signal score
- New source files:
  - `core/api/DartApiClient.kt` — DART REST API client (corp_code XML download/parse, disclosure list fetch, 1000ms rate limit)
  - `data/engine/DartEventEngine.kt` — 9th engine: resolveCorpCode, fetchDisclosures, classify, estimateBeta, computeCar, buildSignal
  - `domain/model/DartModels.kt` — DartDisclosure, CorpCodeEntry, EventStudyResult, DartEventResult, DartEventType (7 types + classify + toKorean)
  - `core/database/entity/DartCorpCodeEntity.kt` — Room entity (PK=ticker, corp_code unique index)
  - `core/database/dao/DartDao.kt` — getCorpCode, insertAll, count, lastUpdatedAt, deleteAll
- Modified files:
  - `core/database/AppDatabase.kt` — v16→v17, DartCorpCodeEntity + dartDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_16_17 (dart_corp_code table), provideDartDao()
  - `core/di/AppModule.kt` — provideDartApiClient()
  - `core/config/ApiConfigProvider.kt` — getDartApiKey() with volatile+mutex cache
  - `presentation/settings/SettingsScreen.kt` — PrefsKeys.DART_API_KEY, loadDartApiKey(), saveDartApiKey(), dartApiKey state
  - `presentation/settings/ApiKeySettingsSection.kt` — DART OpenAPI section (API key field + info)
  - `domain/model/StatisticalModels.kt` — dartEventResult field in StatisticalResult
  - `data/engine/StatisticalAnalysisEngine.kt` — DartEventEngine injection, parallel async execution, apiConfigProvider
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_DART_EVENT constant, ALL_ALGOS updated to 9, weights redistributed (DartEvent: BULL 0.10, BEAR 0.13, SIDEWAYS 0.11, CRISIS 0.14)
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts signalScore for DartEvent (when nEvents > 0)
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretDartEvent(), updated summarize(), assessOverallDirection(), buildPromptForAi() (9개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — DartEvent section in AI prompt (9개 알고리즘)
  - `presentation/ai/AiAnalysisScreen.kt` — DART 공시 이벤트 expandable card (event type, CAR, t-stat, significance ★)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes dartevent
- Tests added (1 file):
  - `DartEventEngineTest.kt` — 20 tests: classify covers all 7 event types, computeCar zero AR, positive CAR, insufficient data, estimateBeta default/correct, analyze null/blank key, corp_code not found, no disclosures, signal bounds [0,1], computeLogReturns, all types have Korean labels, corp_code cache
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added dartEventEngine + apiConfigProvider parameters
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added dartEventEngine + apiConfigProvider parameters
  - `RegimeWeightTableTest.kt` — updated from 8 to 9 algorithms

### [COMPLETE] PROMPT 06 — BOK ECOS Macro (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–05
- BOK ECOS API key stored in EncryptedSharedPreferences via Settings screen
- 5 indicators: base_rate (722Y001), m2 (101Y004), iip (901Y033), usd_krw (731Y001), cpi (901Y009)
- YoY computation: base_rate uses absolute change (pp), others use percentage change
- 4 macro environments: EASING, TIGHTENING, NEUTRAL, STAGFLATION
- Classification priority: STAGFLATION > TIGHTENING/EASING > NEUTRAL
- Weight overlay: adjusts regime weights (momentum ↓/↑, HMM ↑/↓, DartEvent/OrderFlow ↑), normalizes to sum=1.0
- Data lag: ECOS data 1-2 month lag → referenceDate minus 2 months for safety
- Caching: FeatureStore with Weekly TTL + Room DB macro_indicator table
- New source files:
  - `core/api/BokEcosApiClient.kt` — ECOS REST API client (rate limited 1000ms, JSON parsing)
  - `data/engine/macro/BokEcosCollector.kt` — 24-month fetch, YoY computation, ffill (max 3 months)
  - `data/engine/macro/MacroRegimeOverlay.kt` — 4-env classification + ensemble weight adjustment (all rules as constants)
  - `domain/model/MacroModels.kt` — EcosIndicatorSpec, EcosDataPoint, MacroEnvironment, MacroSignalResult
  - `core/database/entity/MacroIndicatorEntity.kt` — Room entity (PK=id, indicator_key+year_month indexes)
  - `core/database/dao/MacroDao.kt` — getByIndicator, getByMonth, insertAll, deleteOlderThan
  - `core/worker/MacroUpdateWorker.kt` — Weekly HiltWorker (fetches, classifies, caches, cleans up 36-month cutoff)
- Modified files:
  - `core/database/AppDatabase.kt` — v17→v18, MacroIndicatorEntity + macroDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_17_18 (macro_indicator table), provideMacroDao()
  - `core/di/AppModule.kt` — provideBokEcosApiClient()
  - `core/config/ApiConfigProvider.kt` — getEcosApiKey() with volatile+mutex cache
  - `data/engine/FeatureStore.kt` — Added put() method for direct cache writes
  - `data/engine/StatisticalAnalysisEngine.kt` — MacroRegimeOverlay injection, cachedMacroSignal, updateMacroSignal(), macro overlay in getRegimeWeights(), macroSignalResult in analyzeInternal()
  - `domain/model/StatisticalModels.kt` — macroSignalResult field in StatisticalResult
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretMacro(), macro section in buildPromptForAi()
  - `data/mapper/ProbabilisticPromptBuilder.kt` — macro environment section in user prompt
  - `presentation/ai/AiAnalysisScreen.kt` — Macro environment chip (color-coded, tappable) + expandable bottom sheet with 5 YoY values + expandable card
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes macro
  - `presentation/settings/SettingsScreen.kt` — PrefsKeys.ECOS_API_KEY, loadEcosApiKey(), saveEcosApiKey(), ecosApiKey state
  - `presentation/settings/ApiKeySettingsSection.kt` — BOK ECOS API section (API key field + info)
  - `core/worker/WorkManagerHelper.kt` — scheduleMacroUpdate() (weekly), cancelMacroUpdate(), runMacroUpdateNow()
  - `TinyOscillatorApp.kt` — Schedule macro update on startup (Sunday 05:30)
- Tests added (2 files):
  - `BokEcosCollectorTest.kt` — 9 tests: blank key, insufficient data, valid result, ffill, ffill gap limit, base_rate absolute change, percentage change, zero division, closest month fallback
  - `MacroRegimeOverlayTest.kt` — 16 tests: all 4 environments classified, STAGFLATION priority, boundary value, NEUTRAL no-change, weight sum=1.0 for all regime×env combinations, TIGHTENING/EASING/STAGFLATION weight adjustments, empty weights, positive weights, normalize, applyClassification, MacroEnvironment round-trip
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added macroRegimeOverlay parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added macroRegimeOverlay parameter

### [COMPLETE] PROMPT 07 — Stacking Ensemble (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–06
- 2-level stacking ensemble: Level-0 = 9 existing calibrated algorithms, Level-1 = L2-regularized LogisticRegression
- TimeSeriesSplit (not KFold) for OOF predictions — no future leakage
- LogisticRegression C=0.5, minimum 60 labeled samples, gradient descent with L2 penalty
- Cold-start fallback: regime-weighted sum when meta-learner not yet fitted
- New source files:
  - `data/engine/ensemble/StackingEnsemble.kt` — Core stacking: TimeSeriesSplit CV, OOF collection, fit/predict_proba, feature importance, save/load state
  - `data/engine/ensemble/RegimeStackingEnsemble.kt` — Regime-conditional subclass: per-regime meta-learner, fallback to global if <60 samples in regime
  - `data/engine/ensemble/SignalHistoryStore.kt` — Room-backed training data store: append, updateOutcome, getHistory, toTrainingData
  - `domain/model/StackingModels.kt` — MetaLearnerStatus, MetaLearnerState, EnsembleHistoryEntry
  - `core/database/entity/EnsembleHistoryEntity.kt` — Room entity (PK=ticker+date, signals_json, actual_outcome, regime_id)
  - `core/database/dao/EnsembleHistoryDao.kt` — DAO (upsert, getCompleted, getPending, updateOutcome, getByRegime)
  - `core/worker/MetaLearnerRefitWorker.kt` — Weekly HiltWorker (Sunday 06:30, extends BaseCollectionWorker)
- Modified files:
  - `core/database/AppDatabase.kt` — v18→v19, EnsembleHistoryEntity + ensembleHistoryDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_18_19 (ensemble_history table), provideEnsembleHistoryDao()
  - `data/engine/StatisticalAnalysisEngine.kt` — StackingEnsemble injection, getEnsembleProbability(), refitMetaLearner(), getMetaLearnerStatus(), recordEnsembleHistory(), cold-start fallback
  - `core/worker/WorkManagerHelper.kt` — scheduleMetaLearnerRefit(), cancelMetaLearnerRefit(), runMetaLearnerRefitNow()
  - `core/worker/CollectionNotificationHelper.kt` — META_LEARNER_NOTIFICATION_ID = 1010
  - `TinyOscillatorApp.kt` — Schedule meta learner refit on startup
  - `presentation/ai/AiAnalysisViewModel.kt` — metaLearnerStatus, ensembleProbability StateFlows
  - `presentation/ai/AiAnalysisScreen.kt` — EnsembleProbabilityCard (Meta-Learner/가중합 badge, probability display, training stats)
  - `domain/usecase/ProbabilityInterpreter.kt` — Updated buildPromptForAi() mention of stacking
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Updated system prompt for stacking
- Tests added (2 files):
  - `StackingEnsembleTest.kt` — 14 tests: TimeSeriesSplit no overlap, train < test ordering, OOF in [0,1], insufficient samples rejection, fit/predict roundtrip, bullish vs bearish differentiation, feature importance sum=1, save/load identical predictions, getStatus fitted/unfitted, min 60 samples enforcement
  - `RegimeStackingEnsembleTest.kt` — 6 tests: regime-specific model creation, insufficient samples skip, regime vs global differentiation, global fallback for unfitted regime, save/load roundtrip, fittedRegimes listing
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added signalHistoryStore parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added signalHistoryStore parameter

### [COMPLETE] PROMPT 08 — Kelly + CVaR (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–07
- Fractional Kelly criterion: f* = (p·b − q)/b × 0.25 (quarter-Kelly), with volatility adjustment
- Cornish-Fisher CVaR: skewness + excess kurtosis correction for tail risk, fallback to historical CVaR
- Position limit: daily loss budget (2%) / |CVaR|, clipped to [0, 1]
- 4 SizeReasonCodes: KELLY_BOUND, CVAR_BOUND, MAX_POSITION, NO_EDGE
- No DB migration needed — recommendation is computed on-the-fly and cached via FeatureStore as part of StatisticalResult
- New source files:
  - `data/engine/risk/KellyPositionSizer.kt` — Fractional Kelly: estimateWinLossRatio, kellyFraction, size(), computeReturns, realizedVolatility
  - `data/engine/risk/CVaRRiskOverlay.kt` — historicalCvar, cornishFisherCvar, positionLimit, riskAdjustedSize, normalPdf/Cdf/Quantile (Abramowitz-Stegun + Beasley-Springer-Moro)
  - `data/engine/risk/PositionRecommendationEngine.kt` — Orchestrates Kelly + CVaR → PositionRecommendation with sizeReasonCode
  - `domain/model/PositionModels.kt` — PositionRecommendation (13 fields), KellyResult, SizeReasonCode enum
- Modified files:
  - `domain/model/StatisticalModels.kt` — Added positionRecommendation field to StatisticalResult
  - `data/engine/StatisticalAnalysisEngine.kt` — PositionRecommendationEngine integration, computes recommendation after ensemble probability
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretPositionRecommendation(), position guide in buildPromptForAi()
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Position Guide section in system + user prompts
  - `presentation/ai/AiAnalysisScreen.kt` — PositionGuideCard (horizontal bar 0%→rec%→max%, CVaR subtitle, details toggle, disclaimer)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes position recommendation
- Tests added (3 files):
  - `KellyPositionSizerTest.kt` — 17 tests: kellyFraction returns 0 when no edge, size bounds [0, maxPosition], WLR clipping, vol adjustment, computeReturns, realizedVolatility
  - `CVaRRiskOverlayTest.kt` — 16 tests: historical CVaR negative, CF fallback for small samples, CF non-positive, stress scenario, positionLimit 0 when CVaR >= 0, riskAdjustedSize ≤ min(kelly, cvar), normalCdf/Quantile/Pdf accuracy
  - `PositionRecommendationEngineTest.kt` — 14 tests: NO_EDGE when prob < 0.5, finite result for Samsung 252d, unavailable for insufficient data, CVaR bound in stress, signalEdge correctness, higher prob → larger position

### [COMPLETE] PROMPT 09 — Incremental Learning (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–08
- Incremental NaiveBayes: 3-bin discretization of calibrated signal scores, Laplace smoothing (alpha=0.5)
- Incremental LogisticRegression: SGD with adaptive learning rate (eta=eta0/(1+eta0*lambda*t)), L2 regularization, class-balanced weights
- Features: 9 calibrated signal scores from base engines (same input space as StackingEnsemble)
- Drift detection: Rolling 30-day Brier score vs 90-day baseline, threshold=0.05
- Ensemble blending: meta-learner 70% + incremental 30% when both fitted
- Cold start: warmStart on last 252 rows from SignalHistoryStore
- New source files:
  - `data/engine/incremental/IncrementalNaiveBayes.kt` — warmStart + update(partial_fit) + predictProba + save/load
  - `data/engine/incremental/IncrementalLogisticRegression.kt` — warmStart + SGD update + predictProba + save/load
  - `data/engine/incremental/IncrementalModelManager.kt` — coldStartIfNeeded, dailyUpdate, drift detection, state management
  - `domain/model/IncrementalModels.kt` — IncrementalNaiveBayesState, IncrementalLogisticRegressionState, IncrementalModelManagerState, BrierEntry, ModelDriftAlert, IncrementalUpdateSummary
  - `core/database/entity/IncrementalModelStateEntity.kt` — Room entity (PK=model_name, state_json, samples_seen)
  - `core/database/entity/ModelDriftAlertEntity.kt` — Room entity (auto PK, model_name, brier/baseline/degradation)
  - `core/database/dao/IncrementalModelDao.kt` — DAO for model state + drift alerts
  - `core/worker/IncrementalModelUpdateWorker.kt` — Daily 19:00 KST HiltWorker
- Modified files:
  - `core/database/AppDatabase.kt` — v19→v20, 2 new entities + incrementalModelDao()
  - `core/di/DatabaseModule.kt` — MIGRATION_19_20, provideIncrementalModelDao()
  - `core/worker/WorkManagerHelper.kt` — scheduleIncrementalModelUpdate(), cancelIncrementalModelUpdate(), runIncrementalModelUpdateNow()
  - `core/worker/CollectionNotificationHelper.kt` — INCREMENTAL_MODEL_NOTIFICATION_ID = 1011
  - `TinyOscillatorApp.kt` — Schedule incremental model update on startup (daily 19:00)
  - `data/engine/StatisticalAnalysisEngine.kt` — IncrementalModelManager integration, 70/30 blending in getEnsembleProbability()
- Tests added (3 files):
  - `IncrementalNaiveBayesTest.kt` — 10 tests: warmStart, update stability, predict bounds, save/load roundtrip, discretize, missing features, error cases
  - `IncrementalLogisticRegressionTest.kt` — 11 tests: warmStart, SGD update stability, predict bounds, save/load roundtrip, adaptive LR, map-based predict, error cases
  - `IncrementalModelManagerTest.kt` — 10 tests: dailyUpdate <200ms, save/load roundtrip, drift detection, constants, both-model update

### [COMPLETE] PROMPT 10 — Korea 5-Factor Model (2026-04-03)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–09
- 5-factor: MKT excess (KOSPI − base rate), SMB (market cap proxy), HML (PBR inverse), RMW (ROE proxy), CMA (asset growth proxy)
- OLS rolling regression: 36-month window, 3-month step, min 24 observations
- Signal: alpha z-score → sigmoid → [0,1]
- Factor data cached via FeatureStore (Weekly TTL) — no new DB table needed
- New source files:
  - `data/engine/Korea5FactorEngine.kt` — 10th engine: OLS regression, rolling alpha, z-score signal
  - `domain/model/FactorModels.kt` — Korea5FactorResult, FactorBetas, MonthlyFactorRow, FactorDataCache
- Modified files:
  - `data/engine/StatisticalAnalysisEngine.kt` — Korea5FactorEngine injection, parallel execution
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_KOREA_5FACTOR constant, ALL_ALGOS updated to 10
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts signalScore for Korea5Factor
  - `data/engine/calibration/SignalCalibrator.kt` — Korea5Factor added to ALGO_NAMES
  - `domain/model/StatisticalModels.kt` — korea5FactorResult field in StatisticalResult
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretKorea5Factor(), updated summarize/AI prompt (10개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Korea5Factor section in AI prompt
  - `presentation/ai/AiAnalysisScreen.kt` — 5팩터 알파 expandable card
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes korea5factor
- Tests added (1 file):
  - `Korea5FactorEngineTest.kt` — 12 tests: OLS alpha recovery, rolling_alpha, signal bounds, guard for < 24 obs

### [COMPLETE] PROMPT 11 — Sector Network + Vectorized Indicators (2026-04-04)
- Status: COMPLETE
- Decision: Pure Kotlin implementation (no Chaquopy/Python) — consistent with PROMPT 01–10
- Part A: Sector Correlation Network (11th engine)
  - Ledoit-Wolf shrinkage covariance estimation (analytical optimal shrinkage formula)
  - Correlation matrix from shrunk covariance with covToCorr transformation
  - Graph-based outlier detection: edge threshold |corr| >= 0.5, mean neighbor correlation
  - Outlier = potential divergence/reversal signal (signal_score → 0.6~1.0 for outliers, 0.3~0.5 for normal)
  - Sector peers via StockMasterDao.getTickersBySector(), price data from AnalysisCacheDao
  - Weekly update cadence (not intraday), cached via FeatureStore
- Part B: Vectorized Indicators
  - DoubleArray-based EMA/MACD/RSI (no autoboxing, pre-allocated arrays)
  - RSI uses Wilder smoothing (not SMA)
  - batchCompute: 100 tickers × 252 days in < 500ms
  - CalcOscillatorUseCase.calcEma() now delegates to VectorizedIndicators.emaList()
- New source files:
  - `data/engine/network/SectorCorrelationNetwork.kt` — 11th engine: Ledoit-Wolf, graph construction, outlier detection
  - `data/engine/VectorizedIndicators.kt` — DoubleArray-based emaArray, macdArray, rsiArray, batchCompute, emaList
  - `domain/model/SectorCorrelationModels.kt` — SectorCorrelationResult (12 fields)
- Modified files:
  - `core/database/dao/StockMasterDao.kt` — getTickersBySector() query added
  - `domain/model/StatisticalModels.kt` — sectorCorrelationResult field in StatisticalResult
  - `data/engine/StatisticalAnalysisEngine.kt` — SectorCorrelationNetwork injection, parallel async execution (11개 엔진)
  - `data/engine/regime/RegimeWeightTable.kt` — ALGO_SECTOR_CORRELATION constant, ALL_ALGOS updated to 11, weights redistributed per regime (sum=1.0)
  - `data/engine/calibration/SignalScoreExtractor.kt` — Extracts signalScore for SectorCorrelation
  - `data/engine/calibration/SignalCalibrator.kt` — SectorCorrelation added to ALGO_NAMES
  - `domain/usecase/ProbabilityInterpreter.kt` — interpretSectorCorrelation(), updated AI prompt (11개 알고리즘)
  - `data/mapper/ProbabilisticPromptBuilder.kt` — Sector correlation section in AI prompt (11개 알고리즘)
  - `presentation/ai/AiAnalysisScreen.kt` — 섹터 상관 expandable card (outlier status, neighbors, avg corr, rank)
  - `presentation/ai/AiAnalysisViewModel.kt` — interpretLocal() includes sectorcorr
  - `domain/usecase/CalcOscillatorUseCase.kt` — calcEma() delegates to VectorizedIndicators.emaList()
- Tests added (2 files):
  - `SectorCorrelationNetworkTest.kt` — 7 tests: valid correlation matrix (diagonal=1, symmetric, [-1,1]), outlier detection (correlated→not outlier, uncorrelated→outlier), unavailable (no sector, too few peers), signal bounds, shrinkage intensity
  - `VectorizedIndicatorsTest.kt` — 13 tests: EMA parity with pandas ewm, period=1, single element, empty throws, list/array match, MACD correctness, RSI bounds [0,100], NaN for first period, monotonic increase/decrease, batch shape, batch timing (<500ms), batch EMA match, CalcOscillatorUseCase compatibility
- Existing tests updated:
  - `StatisticalAnalysisEngineTest.kt` — added sectorCorrelationNetwork parameter
  - `AnalyzeStockProbabilityUseCaseTest.kt` — added sectorCorrelationNetwork parameter
