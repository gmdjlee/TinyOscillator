# TASK — 탐색 메뉴 "키워드" 탭 추가

> 명세 SSOT. 구현은 이 문서의 Phase 순서를 따른다. 진행 기록은 각 Phase 완료 시 하단 **진행 로그**에 추가.
> 버전: v1.0 (2026-07-21)

## 1. 목적

탐색(Explore) 하단 탭의 **통계**와 **테마** 사이에 **키워드** 탭을 신설한다.
수집된 ETF를 사용자가 설정에 등록한 **필터 포함 키워드**(`includeKeywords`)로 분류해, 테마 탭과 유사한 카드 목록으로 보여준다.

- **키워드 카드** = 하나의 포함 키워드 그룹 (예: `반도체`, `2차전지`)
- 카드 탭 → 상세: **해당 키워드에 매칭된 멤버 ETF 목록** (사용자 결정, 2026-07-21)
- 멤버 ETF 탭 → 기존 ETF 상세(`EtfDetailContent`) 재사용

## 2. 범위

### In
- `ExploreTab`에 `KEYWORD("키워드")` 추가 (통계↔테마 사이)
- 키워드 그룹핑 도메인 모델 + 순수함수 + 단위테스트
- `KeywordViewModel` — prefs 포함 키워드 + ETF 목록 결합, 검색·정렬
- `KeywordListContent` / `KeywordDetailPane` Compose UI (COMPACT + 2-Pane)
- 상세의 멤버 ETF 탭 → 기존 ETF 상세 네비게이션 재사용

### Out (비범위)
- Room 마이그레이션 **없음** (기존 `etfs` 테이블만 in-memory 재분류)
- 신규 API·수집 로직 **없음** (수집은 기존 `EtfUpdateWorker` 그대로)
- 집계 종목 목록(테마 상세형) — 이번엔 안 함. 멤버 ETF 목록만.
- 키워드 편집 UI 변경 없음 (설정의 기존 `EtfSettingsSection` 그대로 사용)

## 3. 현행 재료 (이미 존재 — 재탐색 불필요)

| 재료 | 위치 | 비고 |
|---|---|---|
| 포함/제외 키워드 저장 | `presentation/settings/SettingsPreferences.kt` `loadEtfKeywordFilter()` | `includeKeywords`(기본 28개, 콤마 문자열) |
| ETF 목록 | `core/database/dao/EtfDao.kt` `getAllEtfs(): Flow<List<EtfEntity>>` | `EtfEntity.name`·`changeRate`·`ticker`·`indexName`·`totalFee` |
| 키워드 매칭 로직 | `presentation/etf/EtfAnalysisContent.kt:123` | `includeKeywords.filter { etf.name.contains(it) }` — 이미 배지 표시에 사용 중 |
| ETF ViewModel | `presentation/etf/EtfViewModel.kt` | `etfList`·`includeKeywords`·`sortMode` StateFlow 노출 — prefs 로드 방식 동일 참조 |
| 탐색 탭 셸 | `presentation/explore/ExploreScreen.kt` | `ExploreTab` enum + when 분기 + `TwoPaneLayout` |
| 미러 대상(목록) | `presentation/theme/ThemeListScreen.kt` `ThemeListContent` | 카드 리스트·검색·정렬칩·빈뷰 구조 |
| 미러 대상(상세) | `presentation/theme/ThemeDetailScreen.kt` `ThemeDetailPane`/`ThemeDetailContent` | 헤더 요약카드 + LazyColumn |
| ETF 상세 네비 | `ExploreScreen` `onEtfDetailClick`(single) / `selectedEtfTicker`+`EtfDetailContent`(2-Pane) | 그대로 재사용 |

## 4. 설계

### 4.1 도메인 모델 (`domain/model/EtfModels.kt`에 추가)
```kotlin
data class KeywordGroup(
    val keyword: String,
    val etfCount: Int,
    val avgChangeRate: Double,   // 멤버 changeRate 평균 (null 제외; 전부 null이면 0.0)
    val lastUpdated: Long,       // 멤버 updatedAt 최대값
    val members: List<EtfEntity> // 상세 pane용
)

enum class KeywordSortMode { ETF_COUNT, AVG_RETURN, NAME }
```

### 4.2 그룹핑 순수함수 (`domain/usecase/GroupEtfsByKeywordUseCase.kt` 또는 model 파일 내 함수)
```kotlin
fun groupEtfsByKeyword(
    etfs: List<EtfEntity>,
    includeKeywords: List<String>,
    query: String,
    sort: KeywordSortMode,
): List<KeywordGroup>
```
규칙:
- 각 키워드마다 `etfs.filter { it.name.contains(keyword) }` → 멤버.
- **멤버 0개 키워드는 목록에서 제외** (등록됐지만 매칭 ETF 없는 키워드).
- `avgChangeRate = members.mapNotNull { it.changeRate }.average()` (빈 경우 0.0).
- `query` 비었으면 전체, 아니면 `keyword.contains(query, ignoreCase=true)` 필터.
- 정렬: `ETF_COUNT`=etfCount 내림차순(기본), `AVG_RETURN`=avgChangeRate 내림차순, `NAME`=keyword 오름차순.
- 한 ETF가 여러 키워드 매칭 시 각 그룹에 중복 등장 (정상 — 테마 종목 중복과 동일).

### 4.3 ViewModel (`presentation/keyword/KeywordViewModel.kt`, `@HiltViewModel`)
- `includeKeywords: StateFlow<List<String>>` — `EtfViewModel`과 동일하게 `loadEtfKeywordFilter(context)` 로드.
- `etfList` — `EtfDao.getAllEtfs()` 구독 (Repository 경유 or DAO 직접, `EtfViewModel` 방식 따름).
- `query: StateFlow<String>` / `sortMode: StateFlow<KeywordSortMode>`.
- `groups: StateFlow<List<KeywordGroup>>` = `combine(etfList, includeKeywords, query, sortMode)` → `groupEtfsByKeyword(...)`, `stateIn(WhileSubscribed(5_000), emptyList())`.
- `onQueryChange` / `onSortModeChange`. 갱신 버튼은 ETF 수집 재사용 → `WorkManagerHelper.runEtfUpdateNow` (없으면 생략, `CollectionProgressBar(tag = EtfUpdateWorker.TAG)`만 표시).

### 4.4 UI
**`KeywordListContent`** (`ThemeListContent` 미러):
- 상단 `CollectionProgressBar(tag = EtfUpdateWorker.TAG)` + 검색 `OutlinedTextField`("키워드 검색") + 정렬칩 Row + 카운트 텍스트.
- `KeywordCard`: 키워드명(titleSmall) · `ETF ${etfCount}개` · 우측 `평균 ${avgChangeRate}%`(부호색 `signColor`/`LocalFinanceColors`). 한국 컨벤션 상승=적 하락=청.
- 빈뷰: 포함 키워드 0개면 "설정에서 필터 키워드를 등록해 주세요", ETF 0개면 "ETF 목록 탭에서 데이터를 수집해 주세요".

**`KeywordDetailPane`** (`ThemeDetailPane` 미러):
- 헤더 요약: 키워드명 + ETF수 + 평균등락률.
- 멤버 ETF LazyColumn — **`EtfListItem` 재사용**(현재 `private` → 공용으로 추출, 4.5 참조). ETF 탭 → `onEtfClick(ticker)`.

### 4.5 리팩터 (소규모)
- `EtfAnalysisContent.kt`의 `private fun EtfListItem` → 공용 `EtfListItem`(같은 패키지 `presentation/etf`)으로 승격, `EtfAnalysisContent`·`KeywordDetailPane` 공유. 시그니처 유지(`etf`, `includeKeywords`, `onClick`).

### 4.6 ExploreScreen 배선
- `ExploreTab` enum 순서: `ETF_LIST, ETF_STATS, KEYWORD, THEME, REPORT`.
- `KEYWORD` when 분기:
  - **2-Pane**: `TwoPaneLayout` listPane=`KeywordListContent(onKeywordClick = { selectedKeyword = it })`, detailPane=선택된 키워드의 `KeywordDetailPane`(선택 없으면 "키워드를 선택해주세요"). 멤버 ETF 탭 → `selectedEtfTicker` 설정 후 ETF 상세로? — **주의**: 상세 pane이 이미 키워드 상세 점유. 멤버 ETF 탭은 single-pane 네비(`onEtfDetailClick`)로 넘기거나, 2-Pane에서도 ETF 상세는 별도 화면 push. → **결정 필요 항목 A** 참조.
  - **COMPACT(single)**: `KeywordListContent(onKeywordClick = ...)` → 선택 시 `KeywordDetailPane` 화면 교체(로컬 state) → 멤버 ETF 탭 → `onEtfDetailClick(ticker)`.
- `selectedKeyword` state는 `rememberSaveable`(String, nullable). ETF/테마/리포트 선택 state 패턴과 동일.

## 5. 결정 필요 항목 (구현 착수 전 확정)

- **A. 2-Pane에서 멤버 ETF 탭 동작**: (a) ETF 상세를 우측 pane에서 키워드 상세와 교체 표시 vs (b) 항상 전체화면 ETF 상세로 push(`onEtfDetailClick`). → 기본안 **(b)**: 단순·기존 네비 재사용, 2-Pane 상태 충돌 없음.
- **B. COMPACT 상세 표현**: (a) 로컬 state로 목록↔상세 교체 vs (b) 별도 nav route 추가. → 기본안 **(a)**: `ExploreScreen` 내부 state, nav 그래프 미변경. (테마 탭 COMPACT은 상세를 전체화면 route로 push하지만, 키워드는 경량 유지.)

> A·B 기본안으로 진행. 사용자 이견 시 이 절만 수정.

## 6. Phase 계획

### Phase 0 — 도메인 + 순수함수 (UI 없음)
- `KeywordGroup`·`KeywordSortMode` 추가, `groupEtfsByKeyword` 구현.
- 단위테스트 `GroupEtfsByKeywordTest`: 기본 그룹핑·멤버0 제외·중복매칭·avg(null 혼재)·정렬 3종·query 필터·빈 키워드.
- **수용**: `:app:testDebugUnitTest --tests "*GroupEtfsByKeyword*"` 그린.

### Phase 1 — ViewModel
- `KeywordViewModel` 구현 (combine + prefs 로드).
- 테스트 `KeywordViewModelTest`(MockK): 키워드/ETF 주입 → groups 방출, query·sort 변경 반영.
- **수용**: `--tests "*KeywordViewModel*"` 그린.

### Phase 2 — UI 컴포넌트
- `EtfListItem` 공용 추출(4.5) — `EtfAnalysisContent` 컴파일 유지.
- `KeywordListContent` + `KeywordCard` + 빈뷰.
- `KeywordDetailPane` + 헤더 + 멤버 ETF 리스트.
- **수용**: `:app:assembleDebug` 성공. (Compose는 단위테스트 대상 아님 — 컴파일로 확인.)

### Phase 3 — ExploreScreen 배선
- enum 순서 조정 + `KEYWORD` when 분기(COMPACT+2-Pane, 기본안 A·B).
- **수용**: `:app:assembleDebug` 성공 + 기존 탐색 탭 회귀 없음(수동 스모크).

### Phase 4 — 실기 QA (에뮬레이터)
- pixel_fold 등에서: 탭 순서(통계→키워드→테마) 확인, 키워드 카드 렌더, 카드 탭→멤버 ETF, ETF 탭→상세, 라이트/다크, 빈뷰(키워드 0 / ETF 0), 중복 매칭 ETF, 회전/폴드(2-Pane) 크래시 0.
- **수용**: 위 항목 전부 통과, 크래시 0. 결과 진행 로그 기록.

## 7. 함정·주의
- 한국 등락 색: 상승=적, 하락=청. 반드시 `signColor`/`LocalFinanceColors` 사용 (하드코딩 금지 — 다크 변형 전역 정책).
- `changeRate` nullable — 평균 계산 시 `mapNotNull`. 전부 null이면 0.0, 색 중립 처리 고려.
- `rememberSaveable` state saver — 테마/리포트처럼 nullable String 복원 확인.
- 포함 키워드 비어 있으면(`DEFAULT_INCLUDE_KEYWORDS`는 28개지만 사용자가 비울 수 있음) → 수집은 액티브 전체, 그룹핑 불가 → 빈뷰.
- 전체 테스트 스위트 금지 — 타겟 `--tests`만.

## 8. 예상 산출물
- 신규: `presentation/keyword/KeywordViewModel.kt`, `KeywordListContent.kt`, `KeywordDetailPane.kt`, `domain/usecase/GroupEtfsByKeywordUseCase.kt`(or model 함수), 테스트 2개.
- 수정: `domain/model/EtfModels.kt`(+모델), `presentation/explore/ExploreScreen.kt`(배선), `presentation/etf/EtfAnalysisContent.kt`(EtfListItem 추출).
- DB/스키마/워커/API 변경 0.

## 9. 진행 로그
- **Phase 0 완료(2026-07-21)**: `domain/model/EtfModels.kt`에 `KeywordGroup`·`KeywordSortMode`·`groupEtfsByKeyword` 추가(순수 Kotlin, Android 의존 0). `GroupEtfsByKeywordTest` 12건 신규(골든 11건 + 중복 키워드 안전성 1건 추가) 전부 그린(tests=12/0 fail). `includeKeywords`는 `distinct()` 처리로 중복 입력 시 결과 그룹 중복 생성 방지(테스트로 동작 고정). `:app:testDebugUnitTest --tests "com.tinyoscillator.domain.model.GroupEtfsByKeywordTest"` BUILD SUCCESSFUL, 컴파일 회귀 없음. UI·ViewModel(Phase 1~3) 미착수.
