# TASK — 종목분석 「주체별 매물대」 탭 (Investor Volume Profile)

> 명세 SSOT. 구현은 이 문서의 Phase 순서를 따른다. 진행 기록은 각 Phase 완료 시 하단 **§14 진행 로그**에 추가한다.
> 버전: **v1.1 (2026-09-07)**. v1.0(코드 미열람 초안, `archive/TASK_investor_volume_profile_v1.0.md`)을 코드베이스 대조 후 개정했다. 개정 근거는 §0에 있다.
> 구현 담당: `kotlin-implementer` · 수용 검증: `qa-verifier` · 스택: Kotlin / Compose(Material 3) / MVVM + Clean / Hilt / Room / Coroutines · 연산은 순수 Kotlin(Chaquopy 미사용).
> 진입점: **종목분석 메뉴**(`BottomNavItem.STOCK_ANALYSIS` → `OscillatorScreen`) 안의 **기술분석 그룹 세부 탭**으로 추가한다.

---

## 0. v1.0 → v1.1 개정 요약 (코드베이스 대조 결과)

### 0.1 v1.0 §0 선행 확인표의 답

| 확인 항목 | 확인 결과 | 조치 |
|---|---|---|
| KIS API 클라이언트 | 존재. `core/api/KisApiClient.kt` `get(trId, url, queryParams, config, parser): Result<T>`(rate limit 500ms 직렬, 서킷브레이커, 토큰 캐시). 단일 GET TR 템플릿은 `data/repository/InvestOpinionRepository.kt`. `FHPTJ04160001`·`FHKST03010100` 모두 앱에 없음 | 신규 TR은 **`FHPTJ04160001` 1건만** 추가. `FHKST03010100`은 채택하지 않음(§0.2-4) |
| Room 일봉 캐시 | `analysis_cache`(Kiwoom `ka10081`, **수정주가**, 2년 보존) 존재. 투자자 TR은 원주가·원수량이라 섞을 수 없음 | 투자자 TR 응답의 자체 가격·거래량(`stck_hgpr/lwpr/clpr`, `acml_vol`)을 사용. 신규 테이블 `investor_flow` 1개(§6.4) |
| 호가 단위 유틸 | 없음(`tick`·`호가`·`priceUnit` 0건). 유사물은 `BuildVolumeProfileUseCase.autoPriceStep()`(4단 휴리스틱) | **신설하지 않음**(§0.2-5) |
| 차트 컴포넌트 | 종목분석 세부 탭 차트 3종 전부 MPAndroidChart `AndroidView` + `rememberChartTheme()`. 캔들은 `KoreanCandleChartView`(`PatternCombinedChart`)이며 `ChartAxisBridge`로 Y축 범위를 노출하고, 그 위에 **기존 매물대 오버레이 `VolumeProfileOverlay`(Compose Canvas, 캔들 방향 기준 bull/bear 분할, POC·VA)** 가 이미 얹혀 있음 | 캔들 병행 표시는 하지 않고 **독립 `HorizontalBarChart`** 채택(§0.2-9). 기존 `VolumeProfile`/`VolumeBucket`과 **이름 충돌 회피**를 위해 신규 타입은 전부 `Investor` 접두(§3) |
| 디자인 토큰 | `ui/theme/Color.kt`·`Theme.kt`(`LocalFinanceColors`, `LocalExtendedColors`), `presentation/chart/ChartTheme.kt`(MPAndroidChart용 ARGB, "hex 재하드코딩 금지" 주석). 간격·모서리 토큰은 **없음**(ad hoc `8/12/16.dp`, `FinanceCard` 기본 패딩 16dp) | 색상은 토큰 파생만 허용. 간격은 형제 탭 관례(16dp 좌우, 8dp 세로) |
| 기존 시리즈 색상 | 범주형 팔레트 토큰 없음. `ParkSignalOverlay`는 존재하지 않음. 토큰에서 파생된 3색은 `MaterialTheme.colorScheme.primary/secondary/tertiary`(비취/황동/자두)뿐. `IndicatorColors`·`PortfolioPieChart.CHART_COLORS` 등은 hex 하드코딩이라 복사 금지 | 개인=primary, 외국인=secondary, 기관=tertiary. `ChartTheme`에 `tertiaryLine` 필드 1개 추가(§7.5) |

### 0.2 설계 변경 목록 (번호 · 변경 · 근거 · 복원 조건)

| # | v1.0 | v1.1 | 근거 | 복원하려면 |
|---|---|---|---|---|
| 1 | 모듈 경로 `feature/volumeprofile/` (수직 슬라이스) | 종목분석 형제 탭과 동일한 **평면 계층**(`domain/model`·`domain/usecase`·`data/dto`·`data/repository`·`core/database`·`presentation/investorprofile`) | 형제 세부 탭 10개가 전부 평면 구조. `feature/` 수직 슬라이스는 BearSignal(89파일 독립 메뉴) 단독 선례. 평면이면 신규 DI 모듈 파일이 필요 없음(`AppModule`·`DaoModule` `@Provides` 1줄씩) | §0.3-A 지시 시 `feature/investorprofile/{data,domain,presentation,di}`로 이동 |
| 2 | 진입점 미정(`VolumeProfileScreen`) | `OscillatorScreen.kt`의 `MainTab`에 `INVESTOR_PROFILE("주체별 매물대")` 추가, `TabGroup.TECHNICAL`에 편입, `when` 분기 1개. NavHost 무변경. 티커는 화면이 내려주는 `ticker/stockName` 사용(자체 검색 없음). 세부 탭 칩 행은 가로 스크롤(`OscillatorScreen.kt:219-221`)이라 라벨 길이로 넘치지 않음 | 종목분석 세부 탭 배선 방식과 동일(`DemarkTDContent(ticker, stockName, modifier, viewModel)`) | 해당 없음 |
| 3 | KIS TR 2건(`FHPTJ04160001`+`FHKST03010100`) | **`FHPTJ04160001` 1건.** 실측 계약(§6.1) 반영: 30행/호출, `tr_cont` 무효(날짜 스텝만 유효), 당일 15:40 전 호출은 `OPSQ2001` 전면 거부, 금액 백만원, 응답 키 `output2`, **모의투자 미지원** | 근거 `D:\wp_2026\stock_analyzer_mcp\docs\sources.md` §2(2026-09-03 실측, 20콜 600행 중복 0 누락 0) | 해당 없음 |
| 4 | §4.8 수정주가 정합: `FHKST03010100`을 adj 0/1 이중 호출해 배율 산출 | **가격 불연속 감지 후 절단**(§5.7): 연속 거래일 종가 비율이 [0.7, 1.3] 밖이면 그 이후 구간만 사용하고 UI에 알림 | 앱 OHLCV는 Kiwoom 수정주가라 배율 산출용으로 재사용 불가. 이중 호출은 3년 기준 +8콜·전용 파서·배율 소급 로직이 필요한데, 최근 3년 내 분할 종목은 소수. 가격제한폭 ±30%라 [0.7,1.3] 밖 변동은 기업행위로만 발생 | 분할 이전 구간 합산이 필요하면 §0.4 백로그 1 |
| 5 | §4.3 호가단위 올림 + `TickSize.kt` 표 | **제거.** `binWidth = (maxHigh − minLow) / binCount`(실수), 첫 구간 하한 = `minLow` | v1.0 §9.2 골든값(18구간 구간 폭 **10,294원** = 185,300/18, POC 256,114원)이 반올림 미적용을 전제하므로 호가단위 올림과 양립 불가. 2023-01-25 이후 KRX 호가단위는 시장 구분 없이 단일표라 v1.0의 "시장별 상이" 서술도 낡음 | 해당 없음(골든값 우선) |
| 6 | `AllocationKernel`/`SwingDetector` 인터페이스, `ZigZagSwingDetector`, `DecayModel.kt`, `PriceBinner.kt` 분리 | **단일 순수 클래스 `CalcInvestorVolumeProfileUseCase`** 안의 internal 함수들(목록은 §5 스케치가 유일한 정의) | 구현 1개짜리 인터페이스 금지. ZigZag는 어떤 컨트롤도 사용하지 않음. v1.0 스스로 "커널 튜닝에 시간을 쓰지 않는다"고 명시 | 두 번째 커널·탐지기가 실제로 필요해지면 그때 인터페이스 추출 |
| 7 | §4.4 일중 회전 보정 α(기본 0, 설정에서만 변경) | **제거** | 기본값 0이면 결과에 영향 없고, 설정 UI·영속·테스트만 추가됨(YAGNI) | §0.3-D 지시 시 `alpha` 파라미터 1개로 복원 가능 |
| 8 | 봉 단위(일봉/주봉) 컨트롤, 주봉 k=3 | **제거.** 스윙 탐지는 일봉 k=5 고정 | 캔들 병행 표시가 없으므로 주봉의 유일한 효과가 스윙 k뿐. 배분·상각은 원래 일봉 고정(§5.5) | §0.3-D |
| 9 | Compose Canvas 직접 드로잉(Path/Paint remember, 2dp 간격, 상방 틴트, VA 띠) | **MPAndroidChart `HorizontalBarChart`** + `rememberChartTheme()`. 기준가·캡·지지는 `LimitLine`(#18), 누적 세그먼트 경계는 `barBorderWidth` | 종목분석 세부 탭 차트 관례가 전부 MPAndroidChart. stacked/grouped/LimitLine/범례/축 포맷터가 내장. Canvas 선례는 BearSignal 커스텀 그래픽(레이더·게이지)과 오버레이뿐 | 캔들 위 오버레이가 필요하면 §0.4 백로그 2 |
| 10 | `UiText`, `EmptyReason`, `flatMapLatest`+200ms 디바운스 | `String` 메시지, `sealed class InvestorProfileState`(NoStock/Loading/Success/Empty/Error/NoApiKey), 재계산은 Job 취소만 | `UiText` 없음. 형제 탭 상태 관례는 sealed class(`DemarkTDState`·`FinancialState`). 컨트롤은 이산 탭이라 디바운스 불필요 | 해당 없음 |
| 11 | §9.2 골든 픽스처 `rows.csv`·`investor_volume_profile_v2.py` | **`docs/fixtures/`에 제공됨(2026-09-07).** 참조 구현을 실행해 정확한 기대값을 §9.2에 고정했고 Phase 2에서 활성화한다. 픽스처는 `src/test/resources`가 아니라 테스트 소스의 **Kotlin 원시 문자열**(CSV 원문 내장 + 10줄 파서)로 둔다 | 앱에 `src/test/resources` 디렉터리 자체가 없음. 60행×18열을 생성자로 옮겨 적으면 전사 오류 위험 | 해당 없음 |
| 12 | WCAG **AAA** 7:1, 신규 색상 0 | 앱 기준(WCAG **AA** 4.5:1, 48dp 터치 타깃, 폰트 스케일 1.3x)으로 정렬. 7:1은 검사만 하고 미달 시 보고 | 색상 토큰 신설 금지와 7:1 강제는 양립 불가(토큰 변경은 범위 밖). BearSignal Phase 5-1 기준이 AA | 토큰 개정을 별건으로 승인하면 AAA 재적용 |
| 13 | 성능: 5년 최초 수집 8초(동시 4) | KIS 레이트리미터는 **500ms 직렬**(동시성 무의미). **전체 = 최근 3년**(≈750거래일, `MAX_PAGES = 26`, ≈13초), 기본 6개월 ≈ 5콜 ≈ 3초. 진행률 표시 | `BaseApiClient.waitForRateLimit` 단일 뮤텍스. `Semaphore` 선례 없음. `MAX_PAGES` 가드는 `ThemeRepository`(50)·`StockRepository`(3) 관례 | §0.3-C |
| 14 | 속성 기반 테스트 1,000회, `detekt`/`ktlint` 0건, Compose UI 테스트 5건 | `kotlin.random.Random(seed)` 루프 1,000회(JUnit4, 신규 의존성 0). detekt/ktlint **없음** → Android lint 신규 경고 0 + `!!` 0. Compose는 androidTest smoke 1건 + 실기 QA 체크리스트 | 테스트 의존성은 JUnit4·MockK·Turbine·Robolectric·room-testing뿐. 형제 탭은 androidTest smoke + 에뮬레이터 실기 QA로 검증 | 해당 없음 |
| 15 | 진행 기록 `PROGRESS.md` | **본 문서 §14 진행 로그** | 최신 명세(`TASK_keyword_tab.md`) 관례 | 해당 없음 |
| 16 | (검토 반영 중간안) 전체 거래량 중심 = 시장 평균단가 `acml_tr_pbmn / acml_vol` | **대표가 `(high + low + close) / 3`로 확정** | 참조 구현이 대표가를 쓰므로 골든 POC 동치를 위해 환원. `acml_tr_pbmn`(원 단위 실측)은 미사용 | POC 정의를 바꾸려면 §9.2 POC 행 재산출 동반 |
| 17 | 사용자 지정 기간 제거(#8과 함께 정리) | **복원**(§0.3-D 결정): `ProfilePeriod.CUSTOM` + Material 3 `DateRangePicker` 다이얼로그(§7.2) | 사용자 결정. 앱에 `DatePickerDialog` 선례(`ReportScreen.kt:331`)가 있고 material3 1.2.0이 `DateRangePicker`를 제공 | 해당 없음 |
| 18 | 지표 `topResistanceBins`/`topSupportBins`(상위 3구간 인덱스) | **상방 캡 존·하방 지지 존**(`capZone`/`supportZone`: 최대 구간에서 50% 이상인 인접 구간으로 확장한 연속 범위 + 물량 + 일평균 배수) + 스윙 앵커(`anchorDate`/`anchorPrice`). 차트 LimitLine은 기준가·캡 상단·지지 하단 3개 | 사용자 요청(2026-09-07): 직전 스윙 고점·저점 이후 창에서 하방 지지점·상방 캡 표시. 픽스처 실행 결과 최대 단일 구간은 이웃과 1% 차이(2,504 vs 2,485만주)라 불안정하고 연속 존이 안정적(§5.8) | 상위 3구간이 필요하면 존 안 정렬로 파생 |

### 0.3 결정 사항 (2026-09-07 사용자 확정)

- **A. 패키지 배치**: 평면 계층(§3). `feature/investorprofile/` 수직 슬라이스는 불채택.
- **B. 골든 픽스처**: `docs/fixtures/rows.csv`·`docs/fixtures/investor_volume_profile_v2.py` 제공됨. 참조 구현 대조 결과는 §5 서두, 정확한 기대값은 §9.2.
- **C. 전체 기간 상한**: 3년(`MAX_PAGES = 26`, 26콜 ≈ 13초). 5년은 불채택.
- **D. 제거 항목 복원**: **사용자 지정 기간만 복원**(§1.2, §4, §7.2). α 회전 보정·주봉·분할 이전 합산은 §0.4 백로그 유지.

### 0.4 백로그 (이번 범위 밖. 본문은 번호로만 참조한다)
1. **분할 이전 구간 합산**: `FHKST03010100`(`/quotations/inquire-daily-itemchartprice`, `FID_ORG_ADJ_PRC` 0=수정주가·1=원주가, 100행/콜, `tr_cont` 불가라 날짜 창 분할)을 adj 0/1로 호출해 종가 비율로 가격·수량을 소급 조정한다. 3년 기준 +8콜.
2. **캔들 위 오버레이**: `ChartAxisBridge` + `VolumeProfileOverlay` 확장으로 주체별 시리즈를 오실레이터 탭 캔들 옆에 얹는다.
3. **일중 회전 보정 α**: `allocateDay` 호출 전 `α × min(buyVolume, sellVolume)`을 양쪽에서 차감하는 파라미터 1개.
4. **주봉 스윙(k=3)**: 백로그 2가 생기면 함께.

---

## 1. 목적과 범위

### 1.1 기능 개요
특정 종목에 대하여 개인·외국인·기관이 어느 가격대에서 매수했는지를 일별 매매대금으로 추정하여, 가격 구간별 매물대를 가로 막대 차트로 표시한다. 최종 목적은 **기준가(최근 종가) 기준으로 상방과 하방 중 어디에 매물이 집중되어 있는지를 판단**하는 것이다. 특히 직전 스윙 고점·저점 이후 창에서는 그 구간에 쌓인 매물로 **상방 캡 존과 하방 지지 존**(§5.8)을 찾아 표시한다.

### 1.2 사용자가 조작할 수 있는 항목 (v1.1)

| 컨트롤 | 값 | 기본값 | 효과 |
|---|---|---|---|
| 주체 선택 | 개인 / 외국인 / 기관 (복수, 최소 1개) | 전체 3개 | **표시 필터만.** 재계산·네트워크 없음 |
| 표시 방식 | 분리(grouped) / 누적(stacked) | 분리 | 표시만 |
| 산출 기준 | 자동 / 매수 누적 / 잔존 추정 | 자동(§5.4) | 재계산 |
| 기간 | 전체(최근 3년) / 1년 / 6개월 / 3개월 / 1개월 / 직전 스윙 고점 이후 / 직전 스윙 저점 이후 / 사용자 지정(시작일~종료일, 최근 3년 안) | 6개월 | 재계산. 캐시 범위 밖이면 증분 수집 |
| 구간 수 | 10 / 15 / 20 / 25 | 20 | 재계산 |

### 1.3 명시적 비목표
- 분봉을 사용하지 않는다. 일봉 해상도에서 구간 폭이 일간 변동폭과 비슷하므로 정확도 이득이 없고 호출량만 늘어난다.
- 실시간 갱신을 하지 않는다. 투자자 매매동향은 장 마감 후(15:40 이후) 확정되는 일별 데이터다.
- 캔들 차트와 나란히 그리지 않는다(§0.2-9). 기존 `VolumeProfileOverlay`(캔들 방향 기준)와는 별개 기능이며 서로 수정하지 않는다.
- 이 값은 추정치이며 거래소 실측 매물대가 아니다. 화면에 반드시 그 취지를 표기한다(§7.4).

---

## 2. 현행 재료 (이미 존재한다. 재탐색하지 말 것)

| 재료 | 위치 | 비고 |
|---|---|---|
| KIS GET TR 호출 | `core/api/KisApiClient.kt:44` `get(trId, url, queryParams, config, parser): Result<T>` | 내부에서 `executeWithRetry`(auth 실패 시 토큰 갱신 1회, 재시도 1s/2s) + 500ms 직렬 rate limit. **수동 `delay` 불필요** |
| 단일 TR 리포지토리 템플릿 | `data/repository/InvestOpinionRepository.kt:18-130` | `kisConfig.isValid()` 선검사 → `queryParams` → `kisApiClient.get(...) { it }` → `json.decodeFromString<...>` → `rt_cd != "0"` 실패 처리 → `Result`. **이 구조를 그대로 복제**한다(단, `android.util.Log` 대신 Timber) |
| KIS 응답 DTO 관례 | `data/dto/FinancialDto.kt:9-19` `KisFinancialApiResponse(rt_cd, msg_cd, msg1, output, output1)` + `parseNumericLong` | 행은 `List<Map<String, String?>>`로 받아 키로 매핑. **`output2`가 없으므로 신규 DTO 필요**(§6.3) |
| KIS 자격증명 | `core/config/ApiConfigProvider.kt:66` `suspend fun getKisConfig(): KisApiKeyConfig`; `core/api/ApiModels.kt:108-121` `isValid()`, `investmentMode: InvestmentMode(MOCK/PRODUCTION)`, `getBaseUrl()` | MOCK 모드면 이 TR은 실패한다(§6.5) |
| 오류 타입·메시지 | `core/api/ApiModels.kt:41-77` `ApiError`(NoApiKeyError·NetworkError·…), `core/api/ApiErrorExtensions.kt` `Throwable.toUserMessage()` | ViewModel에서 `toUserMessage()`로 변환 |
| 네트워크 선검사 | `core/network/NetworkUtils.isNetworkAvailable(context)` | ViewModel에서 리포지토리 호출 전 검사(`FinancialInfoViewModel.kt:98` 관례) |
| 종목분석 세부 탭 배선 | `presentation/stock/OscillatorScreen.kt:66-86` `MainTab`·`TabGroup`, `:238-322` `when` 분기, `:111-116` `currentTicker/currentStockName` | 새 탭은 enum 값 + `when` 분기만 추가 |
| 세부 탭 Content 템플릿 | `presentation/demark/DemarkTDContent.kt:14-19` 시그니처, `:24-30` `LaunchedEffect(ticker, stockName)`, `:67-80` `FilterChip` 기간 행 | NoStock/Loading/Success/Error 렌더 구조 복제 |
| 세부 탭 ViewModel 템플릿 | `presentation/demark/DemarkTDViewModel.kt` (`AndroidViewModel`, `ApiConfigProvider` 주입, `@Volatile currentTicker`, `loadForStock`/`clearStock`) | `sealed class DemarkTDState` 관례 |
| 에러 카드 + 재시도 | `presentation/financial/FinancialInfoContent.kt:134-168` (`errorContainer` 카드 + `TextButton("다시 시도")`) | 스낵바 사용 금지(종목분석 관례) |
| NoApiKey 렌더 | `presentation/financial/FinancialInfoContent.kt:69-80` 중앙 정적 텍스트 | 동일 문구 패턴 |
| 진행률 로딩 | `presentation/ai/AiAnalysisProbabilitySection.kt:146-166` (`LinearProgressIndicator` + "n/total") | 다중 페이지 수집 진행 표시에 재사용 |
| 순수 계산 UseCase 관례 | `domain/usecase/CalcDemarkTDUseCase.kt:21`(plain class, `fun execute(...)`), `operator fun invoke` 관례는 `domain/usecase/BuildVolumeProfileUseCase.kt:10`, 제공은 `core/di/AppModule.kt:95` `@Provides fun provideCalcDemarkTDUseCase()` | 동일 방식(plain class + `@Provides`)으로 `CalcInvestorVolumeProfileUseCase` 제공 |
| Room 신규 테이블 절차 | `core/database/migration/AppDatabaseMigrations.kt:16-21` KDoc 체크리스트, `:1029-1047` `MIGRATION_37_38`(try/catch + Timber + rethrow), `:25-63` `ALL`; `core/database/AppDatabase.kt:66-102`(entities, `version = 38`); `core/di/DaoModule.kt:110-117` | 최신 DAO 관례는 `@Upsert`(`BearSnapshotDao.kt:17`). 스키마 JSON은 KSP 자동 export(`app/schemas/.../39.json`) |
| DAO 인메모리 테스트 템플릿 | `app/src/test/.../feature/bearsignal/data/local/BearSnapshotDaoInMemoryTest.kt:22-61` | `@RunWith(RobolectricTestRunner::class)` `@Config(sdk=[33], manifest=Config.NONE, application=Application::class)` |
| 테스트 룰 | `app/src/test/.../core/testing/MainDispatcherRule.kt` | `@get:Rule val mainDispatcherRule = MainDispatcherRule()` |
| 골든 픽스처 관례 | `feature/bearsignal/domain/model/BearSignalReportBaseline.kt`(Kotlin object 리터럴) | `src/test/resources` 없음 |
| 차트 테마 | `presentation/chart/ChartTheme.kt:13-43` `ChartTheme(neutralLine, emphasisLine, positive, negative, grid, axisText, holeFill, neutral, isDark)` + `rememberChartTheme()`. 생성자 호출처 2곳: `ChartTheme.kt:31`, 테스트 `app/src/test/.../presentation/chart/ext/CandleDataExtTest.kt:18` | `update` 람다에서 매 recomposition 색 재적용(`DemarkTDChart.kt:68-72` 관례) |
| 축 포맷터 | `presentation/chart/formatter/KoreanPriceFormatter.kt`(천단위 콤마), `KoreanVolumeFormatter.kt`(조/억/만), `presentation/chart/ext/FormatExt.kt` `Long.formatKRW()` | 가격축 = `KoreanPriceFormatter`, 물량축 = `KoreanVolumeFormatter`, 라벨 = `formatKRW()` |
| 그룹 막대 선례 | `presentation/financial/FinancialCharts.kt:575-638` (`BarData(...).barWidth`, `chart.groupBars(0f, groupSpace, barSpace)`) | 역학만 재사용, 색상(hex)은 복사 금지 |
| 세그먼트/필터 UI | `presentation/common/DesignComponents.kt:143` `PillTabRow<T>`(단일 선택, `fillMaxWidth` 고정), `presentation/keyword/KeywordListContent.kt:139-148` `FilterChip` 행. 복수 `PillTabRow` 선례는 `presentation/marketanalysis/MarketAnalysisScreen.kt:276,286`(세로 적층 + `contentPadding = PaddingValues(0.dp)`) | 주체 칩 = `FilterChip`(복수), 기준·표시·구간수 = `PillTabRow` 세로 3행 |
| 부호·의미 색 | `ui/theme/SignColor.kt` `signColor(value)`, `ui/theme/Theme.kt:12-24` `LocalFinanceColors` | 상방/하방 합계 표시에 사용하지 않는다(부호 개념이 아님). 중립 텍스트 |
| Canvas/뷰 접근성 선례 | `feature/bearsignal/presentation/ui/BearSignalGraphics.kt:143-146` `.semantics { contentDescription = 요약문 }` | AndroidView에도 동일 modifier 적용 |
| 날짜 포맷 | `core/util/DateFormats.yyyyMMdd` | KIS 일자 파싱·요청 |
| KST 시각 | `ZoneId.of("Asia/Seoul")` (BearSignal 주간 워커 관례) | 15:40 규칙 판정 |
| 1시간 쿨다운 선례 | `data/repository/StockRepository.kt:84-91, :635 COOLDOWN_MS` | 주말·휴일 반복 호출 방지 |
| 날짜 선택 다이얼로그 선례 | `presentation/report/ReportScreen.kt:331-395` `ReportDatePickerDialog`(`DatePickerDialog` + `rememberDatePickerState` + `SelectableDates`, UTC epoch millis ↔ `LocalDate` 변환) | 사용자 지정 기간은 같은 구조에 `DateRangePicker`(material3 1.2.0, `@ExperimentalMaterial3Api`)를 넣는다 |
| 골든 픽스처 | `docs/fixtures/rows.csv`(삼성전자 2026-06-11~09-04, 60행, 헤더 없음, 18열 `date,open,high,low,close,vol,p_bv,p_bp,p_sv,p_sp,f_bv,f_bp,f_sv,f_sp,o_bv,o_bp,o_sv,o_sp`, `*_bp/*_sp`는 백만원) · `docs/fixtures/investor_volume_profile_v2.py`(참조 구현) | 기대값은 §9.2에 고정. 참조 구현은 근거일 뿐 빌드에 포함하지 않는다 |
| 기존 매물대(별개) | `domain/model/VolumeProfile.kt`(`VolumeProfile`·`VolumeBucket`), `domain/usecase/BuildVolumeProfileUseCase.kt`, `presentation/chart/overlay/VolumeProfileOverlay.kt`, `presentation/chart/bridge/ChartAxisBridge.kt` | **수정 금지·이름 재사용 금지.** VA 70% 확장 알고리즘(`BuildVolumeProfileUseCase.kt:33-46`)은 §5.8에서 동일 규칙으로 재구현 |
| 기존 투자자 동향(별개) | `data/dto/StockApiModels.kt:30-44` `InvestorTrendResponse`/`InvestorTrendItem`(Kiwoom `ka10059`), `data/repository/StockRepository.kt:361-397` `fetchInvestorTrend`, `:645-660` `InvestorTrendData` | 외국인·기관 **순매수 금액만**(개인 없음, 매수/매도 미분리) → 대체 불가. `InvestorFlow*`/`DailyInvestorFlow`와 **이름 혼동 금지** |

---

## 3. 아키텍처 · 파일 목록

`presentation → domain ← data` 의존 방향을 지킨다. `domain/usecase/CalcInvestorVolumeProfileUseCase`는 Android 의존성이 없어야 하며 JVM 단위 테스트만으로 전부 검증된다.

```
app/src/main/java/com/tinyoscillator/
├─ domain/model/InvestorVolumeProfileModels.kt      §4 전체 (enum·데이터 클래스)
├─ domain/usecase/CalcInvestorVolumeProfileUseCase.kt   §5 순수 연산 (배분·상각·구간·스윙·절단·지표)
├─ data/dto/InvestorFlowDto.kt                       KisInvestorFlowResponse(output2) + 행→DailyInvestorFlow 매퍼
├─ data/repository/InvestorFlowRepository.kt         FHPTJ04160001 날짜 스텝 페이징 + Room 증분 캐시 (§6)
├─ core/database/entity/InvestorFlowEntity.kt        investor_flow (ticker, date PK)
├─ core/database/dao/InvestorFlowDao.kt
├─ core/database/migration/AppDatabaseMigrations.kt  MIGRATION_38_39 + ALL 등록
├─ core/database/AppDatabase.kt                      entities + version 39 + investorFlowDao()
├─ core/di/DaoModule.kt                              provideInvestorFlowDao
├─ core/di/AppModule.kt                              provideInvestorFlowRepository · provideCalcInvestorVolumeProfileUseCase
├─ presentation/chart/ChartTheme.kt                  tertiaryLine 필드 추가
├─ presentation/investorprofile/InvestorProfileViewModel.kt   상태·질의·표시 옵션 (§7.1)
├─ presentation/investorprofile/InvestorProfileContent.kt     컨트롤·표기·지표 카드 (§7.2, §7.4)
├─ presentation/investorprofile/InvestorProfileChart.kt       HorizontalBarChart AndroidView (§7.3)
└─ presentation/stock/OscillatorScreen.kt            MainTab.INVESTOR_PROFILE + TabGroup.TECHNICAL + when 분기
app/schemas/com.tinyoscillator.core.database.AppDatabase/39.json   (KSP 자동 생성)
app/src/test/java/com/tinyoscillator/
├─ domain/usecase/CalcInvestorVolumeProfileUseCaseTest.kt   §9.1 속성 1,000회 + §9.2 골든 9행 + 스윙·절단·자동기준·CUSTOM 창
├─ domain/usecase/InvestorProfileFixture.kt                  rows.csv 원문 내장 + 파서 (§9.2)
├─ data/dto/InvestorFlowDtoTest.kt                          단위 변환·결측 행
├─ data/repository/InvestorFlowRepositoryTest.kt            페이징 이음매·OPSQ2001·MAX_PAGES·쿨다운
├─ core/database/dao/InvestorFlowDaoInMemoryTest.kt         upsert·range·latest/earliest·삭제
└─ presentation/investorprofile/InvestorProfileViewModelTest.kt   상태 전이·필터 무호출·마지막 칩 거부
app/src/androidTest/java/com/tinyoscillator/presentation/investorprofile/InvestorProfileSmokeTest.kt
수정: app/src/test/java/com/tinyoscillator/presentation/chart/ext/CandleDataExtTest.kt   (ChartTheme 생성자 인자 1개 추가, §7.5)
```

명명 규칙: 기존 `VolumeProfile`·`VolumeBucket`·`BuildVolumeProfileUseCase`·`VolumeProfileOverlay`와 구분하기 위해 신규 타입은 **`Investor` 접두**를 쓴다. 클래스 명명은 CLAUDE.md 관례(`동사+명사+UseCase`, `명사+Repository`)를 따른다.

---

## 4. 도메인 모델 (`domain/model/InvestorVolumeProfileModels.kt`)

```kotlin
enum class InvestorType(val label: String) { INDIVIDUAL("개인"), FOREIGN("외국인"), INSTITUTION("기관") }
enum class ProfileBasis(val label: String) { CUMULATIVE_BUY("매수 누적"), RESIDUAL("잔존 추정") }
enum class ProfileDisplayMode(val label: String) { GROUPED("분리"), STACKED("누적") }
enum class ProfilePeriod(val label: String, val days: Long?) {
    ALL("전체", 365L * 3), Y1("1년", 365), M6("6개월", 182), M3("3개월", 91), M1("1개월", 30),
    SINCE_SWING_HIGH("스윙고점 이후", null), SINCE_SWING_LOW("스윙저점 이후", null),
    CUSTOM("사용자 지정", null)
}

/** 한 주체의 하루치 매수·매도. amount 단위는 원(백만원 → ×1_000_000L 승격 완료). sellAmount는 원천 보존용(현재 소비처 없음). */
data class InvestorLeg(val buyVolume: Long, val buyAmount: Long, val sellVolume: Long, val sellAmount: Long) {
    val buyVwap: Double get() = if (buyVolume > 0) buyAmount.toDouble() / buyVolume else 0.0
}

/** 하루치 원천 데이터. 가격은 전부 수정주가 미반영 원주가, 수량은 원수량. 시가·거래대금은 소비처가 없어 담지 않는다. */
data class DailyInvestorFlow(
    val date: LocalDate,
    val high: Long, val low: Long, val close: Long,
    val volume: Long,
    val flows: Map<InvestorType, InvestorLeg>
)

data class InvestorProfileQuery(
    val period: ProfilePeriod = ProfilePeriod.M6,
    val basis: ProfileBasis? = null,     // null이면 §5.4 자동 규칙
    val binCount: Int = 20,              // 10/15/20/25
    val customStart: LocalDate? = null,  // period == CUSTOM일 때만 사용
    val customEnd: LocalDate? = null
)

data class InvestorPriceBin(val lower: Double, val upper: Double) { val center: Double get() = (lower + upper) / 2 }

data class InvestorProfileMetrics(
    val poc: Double,                              // 전체 거래량 기준 최다 구간 중심
    val valueAreaHigh: Double, val valueAreaLow: Double,   // 전체 거래량 70% 밸류 에어리어
    val upperSupply: Map<InvestorType, Double>,   // center > basePrice 구간 합계
    val lowerSupply: Map<InvestorType, Double>,   // center <= basePrice 구간 합계
    val capZone: IntRange?,                       // 상방 캡 존: 구간 인덱스 범위(§5.8). 상방에 물량이 없으면 null
    val capZoneVolume: Double,                    // 캡 존 안 주체 합계(주)
    val supportZone: IntRange?,                   // 하방 지지 존
    val supportZoneVolume: Double,
    val lowVolumeNodeCount: Int,                  // 주체 합계 < 평균의 20%인 구간 수
    val absorptionDays: Double,                   // 상방 주체 합계 / 창 내 일평균 거래량
    val averageDailyVolume: Double                // 창 내 일평균 거래량(존 물량의 일수 환산용)
)

data class InvestorVolumeProfile(
    val bins: List<InvestorPriceBin>,
    val byInvestor: Map<InvestorType, DoubleArray>,   // bins와 같은 길이, 전부 >= 0 (전체 거래량 배분은 유스케이스 내부 변수로만 존재)
    val basePrice: Long,                              // 창 내 최근 종가 ("현재가" 표기 금지, "기준가(최근 종가)")
    val basis: ProfileBasis,
    val autoBasis: Boolean,
    val startDate: LocalDate, val endDate: LocalDate,
    val tradingDays: Int,
    val binWidth: Double,
    val metrics: InvestorProfileMetrics,
    val truncatedAt: LocalDate?,                      // §5.7 절단 발생 시 절단 시작일
    val swingFallback: Boolean,                       // §5.6 전환점 미검출 폴백 여부
    val anchorDate: LocalDate?,                       // 스윙 기간일 때 전환점 날짜(창 첫 행), 그 외 null
    val anchorPrice: Long?                            // 스윙 고점이면 그 날 high, 저점이면 low
)
```

**금지 사항**: `byInvestor`에 음수를 담지 않는다. `CUMULATIVE_BUY`는 정의상 비음수이고 `RESIDUAL`도 상각 후 비음수여야 한다. 순매수 기반 부호 있는 값이 필요하면 별도 필드로 분리하고 이름에 `net`을 넣는다.

---

## 5. 산출 알고리즘 (`CalcInvestorVolumeProfileUseCase`)

```kotlin
class CalcInvestorVolumeProfileUseCase {
    /** rows는 날짜 오름차순. 창 내 행이 0이면 null(→ Empty). */
    operator fun invoke(rows: List<DailyInvestorFlow>, query: InvestorProfileQuery, today: LocalDate): InvestorVolumeProfile?
    // internal(테스트용): resolveWindow · truncateAtDiscontinuity · binEdges · allocateDay · findSwingHighs · findSwingLows · valueArea
}
```
처리 순서: ① 기간 창 해소(`resolveWindow`: 일수 기간은 `[today − days, today]`, 스윙은 §5.6, `CUSTOM`은 `[customStart, customEnd]`를 `[today − 3년, today]`로 클램프. `customStart > customEnd`이거나 null이면 ViewModel이 사전 차단하므로 유스케이스는 `require`로만 방어) → ② 불연속 절단(§5.7) → ③ 구간 분할(§5.3) → ④ 일자 순회 배분·상각(§5.1, §5.2, §5.4) → ⑤ 지표(§5.8).

참조 구현(`docs/fixtures/investor_volume_profile_v2.py`의 `profile()`·`swing_points()`)과 대조한 결과, 구간 분할·커널·비례상각·스윙 규칙·기준가·흡수일수 정의는 동일하다. 차이는 세 가지이며 전부 픽스처 골든값에 영향이 없음을 실행으로 확인했다(2026-09-07). ⓐ 매수 누적 기준은 참조 구현에 없다(참조의 `net` 모드는 부호 있는 순매수라 §4 금지 사항에 해당). ⓑ vwap 범위 이탈 처리(§5.1). ⓒ 저항·지지 상위 구간 수(참조 2개, 본 명세 3개).

### 5.1 주체별 일중 평균단가
각 주체의 매수 거래대금을 매수량으로 나누어 그날의 평균 체결가를 구한다(`InvestorLeg.buyVwap`). 이 값이 배분 중심을 고정하는 유일한 관측 앵커이므로 다른 값으로 대체하지 않는다.

검증: `buyVwap`은 반드시 `[low, high]` 안에 들어야 한다. 벗어나면 **그 주체·그 날의 leg만** 건너뛰고(다른 주체와 전체 거래량 배분은 유지) `Timber.d`로 기록한다. 거래대금이 백만원 단위로 반올림되므로 소량 매수일에는 이탈이 정상적으로 발생한다. `buyVolume == 0`이면 배분 없음. 참조 구현은 이탈 vwap도 절단 커널(전부 0이면 최근접 구간 원핫)로 배분하지만, 픽스처 60행×3주체에는 이탈 leg와 `buyVolume == 0` leg가 0건이라 골든값이 같다.

### 5.2 배분 커널
```
c_i = 구간 i 중심,  σ = max((high − low) / 4, binWidth / 4)
w_i ∝ exp(−0.5 · ((c_i − vwap) / σ)²),  단 c_i ∉ [low − binWidth, high + binWidth]이면 w_i = 0
Σ w_i = 1 로 정규화,  배분량_i = volume × w_i
```
**불변식 1 (질량 보존)**: `Σ 배분량 = volume` (상대오차 1e-9).
**불변식 2 (1차 모멘트)**: `|배분 가중평균 가격 − vwap| ≤ σ + binWidth / 2`. 절단이 비대칭이면(vwap이 `high`나 `low`에 붙은 날) 절단 정규분포 평균이 최대 약 0.8σ 이동하고, 이산화 오차가 최대 `binWidth/2`이므로 이 상한이 안전하다. v1.0의 "구간 폭 절반 이하"는 이 경우 성립하지 않아 완화했다.
전체 거래량 배분(POC·밸류 에어리어 산출용, 유스케이스 내부 변수)은 같은 커널로 `volume`을 배분하되 중심은 대표가 `(high + low + close) / 3`을 쓴다(참조 구현과 동일. 골든 POC 동치 조건). 대표가는 항상 `[low, high]` 안이고 vwap도 §5.1로 보장되므로 가중치 합이 0이 되는 경우가 없다. 참조 구현의 원핫 폴백은 구현하지 않는다.

### 5.3 가격 구간 분할
1. 창 내 `minLow = min(low)`, `maxHigh = max(high)`. `maxHigh == minLow`이면 `binWidth = 1.0`.
2. `binWidth = (maxHigh − minLow) / binCount` (Double, 반올림 없음).
3. 구간 i = `[minLow + i·binWidth, minLow + (i+1)·binWidth)`, 마지막 구간은 상한 포함.
4. `binWidth`는 UI에 정수 원으로 항상 표기한다(§7.4).
구간 개수를 고정하면 기간이 바뀔 때 구간 폭이 달라져 기간 간 비교가 안 된다. 그래서 구간 폭을 항상 화면에 적는다.

### 5.4 산출 기준과 기간 길이의 상호작용 (오해하기 쉬운 지점, 정확히 구현)
**RESIDUAL (비례상각)**: 주체별 보유 분포 벡터 `H`를 유지한다. 날짜 오름차순으로 ① 그날 매수 배분을 `H`에 더한 뒤 ② 그날 매도량 `s = min(sellVolume, ΣH)`를 `H` 전체에서 비례 차감(`H *= 1 − s/ΣH`)하고 ③ 초과분 `sellVolume − s`는 `excessSell`에 따로 누적한다(관측 기간 이전 보유분의 매도).
**CUMULATIVE_BUY (매수 누적)**: 상각 없이 매수 배분만 누적한다.
**자동 규칙**: `query.basis == null`이면 창 내 거래일이 **60 미만이면 CUMULATIVE_BUY, 60 이상이면 RESIDUAL**. `autoBasis = true`로 표기한다.

**근거(코드 KDoc으로 반드시 남길 것)**: 회전율이 높은 외국인·기관은 짧은 구간에 비례상각을 적용하면 잔존 물량이 0으로 소거된다. 삼성전자 2026-08-18 이후 14거래일 기준으로 외국인 잔존이 0주로 산출되었다. 짧은 구간에 상각을 강제하면 빈 차트가 버그로 오인된다.

### 5.5 일봉 단위 배분 원칙
배분과 상각은 **반드시 일봉 단위**로 수행한다. 일별 매수·매도를 주 단위로 먼저 합산한 뒤 배분하면 주중 회전이 상계되어 외국인·기관 잔존이 소거되는 것이 실측으로 확인되었다(KDoc에 기록). 주봉 컨트롤은 v1.1에서 제거했으므로 코드 경로 자체가 없어야 한다.

### 5.6 스윙 전환점 (프랙탈, k = 5)
- 스윙 고점: 인덱스 `i ∈ [k, n − k − 1]`에서 `high[i] == max(high[i−k .. i+k])`인 봉(동률 포함, 참조 구현과 동일). 스윙 저점은 `low`의 최소로 대칭. 양 끝 k봉은 후보에서 제외한다. 탐색 범위는 **최근 1년 행**(창이 그보다 짧으면 전체).
- `SINCE_SWING_HIGH`/`SINCE_SWING_LOW`는 가장 최근 전환점의 날짜부터 `today`까지를 창으로 삼는다. 전환점을 찾지 못하면 1년 창으로 폴백하고 `swingFallback = true`로 UI에 알린다.

### 5.7 가격 불연속(액면분할·병합·권리락) 처리: 절단
투자자 TR의 가격·수량은 수정주가 미반영이다. 창 안에서 연속 거래일 종가 비율 `close_t / close_{t−1}`이 `[0.7, 1.3]` 밖이면 기업행위로 판단하고, **그 날부터의 구간만 사용**하며 `truncatedAt`으로 UI에 알린다. 가격제한폭이 ±30%라 정상 거래로는 이 범위를 벗어날 수 없다.
```kotlin
// ponytail: 절단만 지원. 분할 이전 구간까지 합산하려면 FHKST03010100 adj 0/1 종가 비율로 가격·수량을 소급 조정하는 §0.4 백로그 1이 필요하다.
```

### 5.8 지표 정의
- `basePrice` = 창 내 마지막 행의 `close`. UI 라벨은 "기준가(최근 종가)".
- `poc` = 전체 거래량 배분(§5.2)의 최대 구간 중심. `valueAreaHigh/Low` = POC에서 양쪽으로 큰 쪽을 먼저 편입하며 전체의 70%에 도달할 때까지 확장(기존 `BuildVolumeProfileUseCase.kt:33-46`과 동일 규칙).
- 주체 합계 `S_i = Σ_type byInvestor[type][i]`. `upperSupply[type]` = `center_i > basePrice`인 구간 합, `lowerSupply` = 나머지.
- **캡 존·지지 존**: 상방 구간(`center_i > basePrice`) 가운데 `S_i`가 최대인 구간 `m`에서 출발해, 같은 쪽 이웃 구간이 `S ≥ 0.5 × S_m`인 동안 양옆으로 확장한 연속 인덱스 범위가 `capZone`이다. `supportZone`은 하방 구간에서 대칭으로 구한다. 해당 쪽에 `S > 0`인 구간이 없으면 null. `capZoneVolume`/`supportZoneVolume` = 존 안 `S_i` 합. 근거: 픽스처의 스윙 고점 이후 창에서 최대 단일 구간(2,504만주)과 이웃(2,485만주)의 차이가 1%라 단일 구간은 불안정하고, 연속 존(256,500~267,750원, 1.03억주 = 일평균 5.1일분)이 매물 벽을 안정적으로 표현한다. 참조 구현이 출력하는 상위 2구간은 존 안에 포함된다.
```kotlin
// ponytail: 존 확장 임계 0.5는 휴리스틱. 조정은 상수 1개(ZONE_THRESHOLD)만 바꾼다.
```
- `lowVolumeNodeCount` = `S_i < 0.2 × mean(S)`인 구간 수. `averageDailyVolume` = `mean(row.volume)`.
- 스윙 앵커: `SINCE_SWING_HIGH`면 `anchorDate` = 전환점 날짜, `anchorPrice` = 그 날 `high`(예: 2026-08-18, 288,000). `SINCE_SWING_LOW`면 `low`. 캡 존 위쪽 구간이 희박하면 앵커가 다음 목표가가 된다(픽스처: 267,750원 위 8개 구간이 전부 최대 구간의 40% 미만).
- `absorptionDays` = `Σ_{상방} S_i / mean(row.volume)`.

---

## 6. 데이터 계층

### 6.1 KIS TR 계약 (실측, 2026-09-03)
| 항목 | 값 |
|---|---|
| TR / 경로 | `FHPTJ04160001` · `GET /uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily` |
| 요청 파라미터 | `FID_COND_MRKT_DIV_CODE=J`, `FID_INPUT_ISCD=<6자리>`, `FID_INPUT_DATE_1=<yyyyMMdd 기준일>`, `FID_ORG_ADJ_PRC=""`, `FID_ETC_CLS_CODE=""` (`""`와 `"1"`은 결과 동일) |
| 응답 | `rt_cd`, `msg_cd`, `msg1`, **`output2`**: 배열, **30행/호출**, 기준일부터 **과거 방향**, 최신 행이 먼저 |
| 연속조회 | **`tr_cont` 무효**(30페이지 모두 같은 30행). 다음 페이지는 `FID_INPUT_DATE_1 = 반환된 최고(最古) 일자 − 1일`(달력일)로 재호출. 실측 20콜 → 600행, 중복 0, 누락 0 |
| 시간 제한 | 기준일이 **당일이고 15:40 KST 이전**이면 `rt_cd=2, msg_cd=OPSQ2001, msg1="TIME LIMIT 00:00 ~ 15:40"`로 호출 자체가 실패 |
| 단위 | 투자자별 `*_shnu_tr_pbmn` / `*_seln_tr_pbmn` **백만원**. `acml_tr_pbmn`(미사용, 기록용)은 **원**(실측 20260902: `3,825,055,733,000 / 15,176,841주 = 252,032원` ∈ [249,500, 255,500]; 개인 매수 `1,175,428백만원 / 4,662,430주 = 252,106원` ∈ 동일 범위). `*_vol`·`acml_vol` 주, 가격 원(수정주가 미반영) |
| 모의투자 | **미지원**(`InvestmentMode.MOCK` 베이스 URL에서 실패) |
| 사용 필드 | 개인 `prsn_shnu_vol / prsn_shnu_tr_pbmn / prsn_seln_vol / prsn_seln_tr_pbmn`, 외국인 `frgn_*`, 기관 `orgn_*`, 가격 `stck_hgpr / stck_lwpr / stck_clpr`, 거래량 `acml_vol`, 일자 `stck_bsop_date`. `stck_oprc`·`acml_tr_pbmn`은 미사용 |

근거: `D:\wp_2026\stock_analyzer_mcp\docs\sources.md` §2, `D:\wp_2026\stock_analyzer_mcp\docs\p0\U2_*.json`. (`FHKST01010900 inquire-investor`는 최근 30일만 주고 페이징이 없어 채택하지 않는다.)

### 6.2 페이징 · 증분 캐시 알고리즘 (`InvestorFlowRepository`)
```kotlin
class InvestorFlowRepository(private val dao: InvestorFlowDao, private val kisApiClient: KisApiClient, private val json: Json) {
    /** [from, today] 구간을 캐시 우선으로 반환. 부족분만 KIS에서 수집. allowNetwork=false면 캐시만. onProgress(page, maxPages). */
    suspend fun getFlows(ticker: String, from: LocalDate, config: KisApiKeyConfig, allowNetwork: Boolean = true, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result<List<DailyInvestorFlow>>
    companion object { const val MAX_PAGES = 26; const val COOLDOWN_MS = 60 * 60 * 1000L; const val RETENTION_DAYS = 365L * 3 + 30 }
}
```
0. `allowNetwork == false`이면 3~6을 건너뛰고 7만 수행한다(오프라인 캐시 전용. 자격증명 검사도 생략).
1. `config.isValid()`가 아니면 `ApiError.NoApiKeyError`, `investmentMode == MOCK`이면 `IllegalStateException("모의투자 모드에서는 투자자매매동향을 조회할 수 없습니다. 설정에서 실전투자로 전환해 주세요.")`로 실패.
2. `latest = dao.latestDate`, `earliest = dao.earliestDate`, `fetchedAt = dao.latestFetchedAt`.
3. **전방 채움**: `latest == null` 이거나 `now − fetchedAt > COOLDOWN_MS`이면 `date1 = startCursor()`부터 페이지를 내려가며 수집한다. 중단 조건: 캐시가 있으면(`latest != null`) 페이지의 최고(最古) 일자 ≤ `latest`에 도달할 때까지 **`from`을 지나쳐서라도** 이어 붙인다(캐시 연속성 불변식: 최신일이 `from`보다 오래된 묵은 캐시에서 `from`에서 멈추면 구멍이 생기고, 이후 더 긴 요청이 그 구멍을 영구히 건너뛴다). 캐시가 없으면 최고 일자 ≤ `from`에서 중단(콜드 캐시 6개월 요청이 26콜로 새지 않도록). 빈 페이지·`MAX_PAGES` 도달도 중단. 구멍이 26페이지를 넘으면 옛 블록은 3년 밖이라 6항의 보존 정리로 삭제되므로 잔여 구멍은 어떤 창에도 걸리지 않는다. 최신 페이지는 항상 다시 받으므로 미확정 행(당일분)은 다음 수집에서 덮어써진다(별도 확정 판정 불필요).
4. **후방 채움**: `earliest == null` 이거나 `earliest > from`이면서 **전방 채움이 아직 `from`에 도달하지 못했을 때만** `date1 = (earliest ?: 전방 채움의 최고 일자) − 1`부터 내려간다(콜드 캐시에서 전방 채움이 이미 `from`을 덮었으면 1콜 낭비를 막기 위해 생략). 최고 일자 ≤ `from`이거나 빈 페이지이거나 누적 `MAX_PAGES` 도달이면 중단.
5. 각 루프는 **페이지를 메모리에 모았다가 루프 완료 후 한 번에 `upsertAll`** 한다. 중간 실패 시 아무것도 저장하지 않아 캐시에 구멍이 생기지 않는다(전방·후방 커서 방식은 캐시가 연속 구간이라는 전제 위에 있다).
6. 저장 후 `dao.deleteOlderThan(ticker, today − RETENTION_DAYS)`.
7. 반환 = `dao.getRange(ticker, from, today)` 매핑.
- `startCursor()` = `now(Asia/Seoul).toLocalTime() < 15:40 ? today − 1 : today`. 그래도 `msg_cd == "OPSQ2001"`이면 `date1 − 1`로 **1회 재시도**.
- 무한 루프 가드: 페이지의 최고 일자가 직전 커서보다 작아지지 않으면 중단하고 `Timber.w`.
- 행 단위 유효성: 투자자 필드가 `""`인 행(당일 미확정)은 매퍼가 `null`로 버린다.
- rate limit은 `KisApiClient`가 처리하므로 루프에 `delay`를 넣지 않는다. 호출 수 상한 = `MAX_PAGES` 26(3년 ≈ 750거래일).

### 6.3 DTO · 매핑 · 단위 (`data/dto/InvestorFlowDto.kt`)
```kotlin
@Serializable
data class KisInvestorFlowResponse(
    @SerialName("rt_cd") val rtCd: String = "",
    @SerialName("msg_cd") val msgCd: String = "",
    @SerialName("msg1") val msg1: String = "",
    val output2: List<Map<String, String?>>? = null
)
fun Map<String, String?>.toDailyInvestorFlowOrNull(): DailyInvestorFlow?   // 필수 필드 결측·비숫자면 null
```
- 투자자별 거래대금은 파싱 직후 `parseNumericLong(...) * 1_000_000L`로 **`Long` 승격**한다. 백만원 × 1e6은 `Int` 범위를 조용히 넘는다(예: 3,931,838백만원 → 3.93e12). `acml_tr_pbmn`은 파싱하지 않는다. 단위 변환 테스트 필수.
- 일자 파싱은 `DateFormats.yyyyMMdd`.

### 6.4 Room (v38 → v39)
```kotlin
@Entity(tableName = "investor_flow", primaryKeys = ["ticker", "date"])
data class InvestorFlowEntity(
    val ticker: String, val date: String,                       // yyyyMMdd
    val high: Long, val low: Long, val close: Long, val volume: Long,
    val prsnBuyVol: Long, val prsnBuyAmt: Long, val prsnSellVol: Long, val prsnSellAmt: Long,   // Amt = 원
    val frgnBuyVol: Long, val frgnBuyAmt: Long, val frgnSellVol: Long, val frgnSellAmt: Long,
    val orgnBuyVol: Long, val orgnBuyAmt: Long, val orgnSellVol: Long, val orgnSellAmt: Long,
    val fetchedAt: Long
)
@Dao interface InvestorFlowDao {
    @Upsert suspend fun upsertAll(rows: List<InvestorFlowEntity>)
    @Query("SELECT * FROM investor_flow WHERE ticker = :ticker AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(ticker: String, from: String, to: String): List<InvestorFlowEntity>
    @Query("SELECT MAX(date) FROM investor_flow WHERE ticker = :ticker") suspend fun latestDate(ticker: String): String?
    @Query("SELECT MIN(date) FROM investor_flow WHERE ticker = :ticker") suspend fun earliestDate(ticker: String): String?
    @Query("SELECT MAX(fetchedAt) FROM investor_flow WHERE ticker = :ticker") suspend fun latestFetchedAt(ticker: String): Long?
    @Query("DELETE FROM investor_flow WHERE ticker = :ticker AND date < :cutoff") suspend fun deleteOlderThan(ticker: String, cutoff: String)
}
```
- `MIGRATION_38_39`: `CREATE TABLE IF NOT EXISTS investor_flow (...) PRIMARY KEY(ticker, date)`; 기존 관례대로 try/catch + `Timber.d/e` + rethrow. `ALL`에 추가, `AppDatabase.version = 39`, entities 배열·`abstract fun investorFlowDao()` 추가, `DaoModule.provideInvestorFlowDao`. 빌드 후 `app/schemas/.../39.json` 생성 확인. `fallbackToDestructiveMigration()` 금지.
- 캐시 정책: 과거 확정 행은 재조회하지 않는다. 절단(§5.7)은 계산 시점에 처리하므로 분할 시 캐시 무효화가 필요 없다.

### 6.5 오류 · 전제
| 상황 | 처리 |
|---|---|
| KIS 키 미설정 | `InvestorProfileState.NoApiKey("API 키가 설정되지 않았습니다.\n설정 화면에서 KIS API 키를 입력해주세요.")` |
| MOCK 모드 | `NoApiKey("모의투자 모드에서는 …")` (동일 상태, 문구만 다름) |
| 오프라인 + 캐시 있음 | ViewModel이 `allowNetwork = false`로 호출 → 캐시 범위로 계산하고 `Success` (추가 배지 없음. 실제 시작일·최신 일자를 표기 라인에 노출) |
| 오프라인 + 캐시 0행 | `Error("네트워크에 연결되어 있지 않습니다. 인터넷 연결을 확인해주세요.")` + 재시도 |
| `rt_cd != 0`(OPSQ2001 제외) | `Error("KIS API 오류 [msg_cd]: msg1")` |
| 창 내 행 0 | `Empty("해당 기간에 투자자 데이터가 없습니다.")` |

---

## 7. 표현 계층

### 7.1 상태 · ViewModel (`InvestorProfileViewModel`, `@HiltViewModel`, `AndroidViewModel`)
```kotlin
sealed class InvestorProfileState {
    data object NoStock : InvestorProfileState()
    data class Loading(val page: Int = 0, val maxPages: Int = 0) : InvestorProfileState()   // page 0 = 캐시 확인 중
    data class Success(val profile: InvestorVolumeProfile) : InvestorProfileState()   // 질의는 query StateFlow가 단일 출처
    data class Empty(val message: String) : InvestorProfileState()
    data class Error(val message: String) : InvestorProfileState()
    data class NoApiKey(val message: String) : InvestorProfileState()
}
```
- 생성자: `application`, `InvestorFlowRepository`, `CalcInvestorVolumeProfileUseCase`, `ApiConfigProvider` (`DemarkTDViewModel` 미러).
- 노출: `state`, `query: StateFlow<InvestorProfileQuery>`, `selectedInvestors: StateFlow<Set<InvestorType>>`, `displayMode: StateFlow<ProfileDisplayMode>`.
- 동작: `loadForStock(ticker, name)` / `clearStock()` / `retry()` / `setPeriod` / `setCustomRange(start: LocalDate, end: LocalDate)`(`start ≤ end ≤ today`가 아니면 무시, 통과하면 `period = CUSTOM`으로 재계산) / `setBasis(ProfileBasis?)` / `setBinCount` / `toggleInvestor(type)` / `setDisplayMode`.
- 규칙: ① 원천 행은 ViewModel이 `@Volatile cachedRows`로 보관한다. ② `setBasis`·`setBinCount`는 **리포지토리를 호출하지 않고** 재계산만 한다. ③ `setPeriod`는 `repository.getFlows(ticker, from)`를 호출하되 리포지토리가 캐시 범위 안이면 네트워크를 타지 않는다. ④ `toggleInvestor`·`setDisplayMode`는 **재계산도 하지 않는다**(표시 필터). ⑤ 재계산은 `viewModelScope.launch(Dispatchers.Default)`이며 이전 Job은 취소한다. ⑥ `toggleInvestor`는 마지막 남은 1개를 해제하지 않는다(무시). ⑦ 네트워크 선검사: `NetworkUtils.isNetworkAvailable`이 false면 `repository.getFlows(..., allowNetwork = false)`로 호출한다. 결과가 0행이면 `Error(네트워크 문구)`, 있으면 캐시 범위로 `Success`.
- `today = LocalDate.now(ZoneId.of("Asia/Seoul"))`, `from = today − period.days`(스윙 기간은 1년 창을 요청한 뒤 유스케이스가 좁힌다. `CUSTOM`은 `from = customStart`이며 리포지토리는 항상 `today`까지 채우고 유스케이스가 `customEnd`로 좁힌다).

### 7.2 컨트롤 UI (`InvestorProfileContent(ticker, stockName, modifier, viewModel = hiltViewModel())`)
세로 스크롤 `Column`. 좌우 16dp, 요소 간 8dp(형제 탭 관례).
1. **주체 칩 행**: `FilterChip` 3개(개인·외국인·기관, 복수 선택). 선택 칩의 `leadingIcon`은 해당 주체 색 원(`Modifier.size(10.dp).background(color, CircleShape)`), 라벨은 "개인 · 상방 1,624만주" 형태로 상방 합계를 함께 표기한다. `Modifier.semantics { contentDescription = "개인, 선택됨, 상방 1,624만주" }`. 행 아래 `labelSmall` 캡션 "최소 1개 주체는 선택되어야 합니다."(상시 표시, 토스트·스낵바 없음).
2. **기간 행**: `ScrollablePillTabRow<ProfilePeriod>` 8개. "사용자 지정" 탭은 선택 시 `InvestorProfileDateRangeDialog`(같은 파일의 private 컴포저블)를 연다. `ReportDatePickerDialog`(`ReportScreen.kt:331-395`) 구조를 그대로 따르되 내용물을 `DateRangePicker(state = rememberDateRangePickerState(initialSelectedStartDateMillis, initialSelectedEndDateMillis, selectableDates = today − 3년 ~ today))`로 바꾼다. 확인 → `viewModel.setCustomRange(start, end)`, 취소 → 이전 기간 유지. 활성 상태의 탭 라벨은 `"06-01~08-31"` 형식으로 범위를 보여 준다. UTC epoch millis ↔ `LocalDate` 변환은 선례와 동일.
3. **옵션 행 3개(세로 적층)**: `PillTabRow`는 `fillMaxWidth` 고정이라 가로 병치가 불가하다. `MarketAnalysisScreen.kt:276,286` 관례대로 세로로 쌓고 각각 `contentPadding = PaddingValues(0.dp)`: 산출 기준(자동/매수 누적/잔존 추정) · 표시(분리/누적) · 구간 수(10/15/20/25).
4. **표기 라인**(§7.4) → 5. **차트**(§7.3, 높이 360dp) → 6. **지표 카드**(§7.4) → 7. 조건부 안내: 절단(`truncatedAt`)·스윙 폴백(`swingFallback`)은 `Card(secondaryContainer)`에 한 줄.
상태별 렌더는 `DemarkTDContent` 구조를 그대로 따른다. `Loading(page, maxPages)`는 `maxPages > 0`일 때 `LinearProgressIndicator` + "수집 중 page/maxPages", 아니면 `CircularProgressIndicator` + "매물대를 계산하는 중...". `Error`는 `errorContainer` 카드 + `TextButton("다시 시도") { viewModel.retry() }`.

### 7.3 차트 (`InvestorProfileChart(profile, selected, displayMode, modifier)`)
MPAndroidChart **`HorizontalBarChart`**를 `AndroidView`로 감싼다. 가격축이 세로(위=고가), 물량축이 가로(좌→우).
- 가격축(`xAxis`)은 **실제 가격(원)** 단위: `axisMinimum = bins.first().lower`, `axisMaximum = bins.last().upper`, `granularity = binWidth`, `valueFormatter = KoreanPriceFormatter()`, **`position = XAxis.XAxisPosition.BOTTOM`**(HorizontalBarChart에서 BOTTOM이 왼쪽. 기본값 TOP은 오른쪽에 그려진다).
- STACKED: `BarEntry(bins[i].center, floatArrayOf(선택 주체 값…))` 1개 데이터셋, `barData.barWidth = binWidth × 0.82`, `stackLabels`, `barBorderWidth = 1f`, `barBorderColor = theme.holeFill`(세그먼트 경계).
- GROUPED, 선택 주체 **n ≥ 2**: 데이터셋 n개(라벨 = `InvestorType.label`, 순서 고정 개인→외국인→기관), **모든 데이터셋이 `bins.size`개 엔트리**를 가져야 한다(`groupBars`는 엔트리 x를 인덱스 순으로 재배치하고 개수가 모자란 데이터셋은 조용히 건너뛴다). `barWidth = binWidth × 0.82 / n`, `groupBars(bins.first().lower, groupSpace = binWidth × 0.18, barSpace = 0f)` → 그룹 총폭 = binWidth.
- GROUPED, 선택 주체 **n == 1**: `groupBars`를 호출하지 않는다(데이터셋 2개 미만이면 `RuntimeException`). STACKED와 같은 폭의 단일 데이터셋으로 그린다.
- 색: `ChartTheme` 파생만. 개인 = `emphasisLine`(primary), 외국인 = `neutralLine`(secondary), 기관 = `tertiaryLine`(tertiary, §7.5). 값 라벨은 끈다(`setDrawValues(false)`).
- 물량축: HorizontalBarChart는 `axisLeft`를 **위**, `axisRight`를 **아래**에 그린다(`calculateOffsets`). 물량축 = **`axisRight`**(하단): 모든 데이터셋 `axisDependency = YAxis.AxisDependency.RIGHT`, `axisRight.valueFormatter = KoreanVolumeFormatter()`, `axisRight.axisMinimum = 0f`, `axisLeft.isEnabled = false`, `gridColor = theme.grid`, 축 텍스트 `theme.axisText`.
- `LimitLine`(모두 `xAxis`에 추가, **3개로 제한**): 기준가 = 파선(`enableDashedLine(12f, 6f, 0f)`), 라벨 `"기준가 " + basePrice.formatKRW()`, 색 `theme.axisText`; 캡 = `capZone` **상단 가격**(`bins[capZone.last].upper`, 벽이 끝나는 돌파 기준선), 실선 1f, 라벨 `"캡 " + formatKRW()`, 색 `theme.positive`(저항=적); 지지 = `supportZone` **하단 가격**(`bins[supportZone.first].lower`, 붕괴 기준선), 실선 1f, 라벨 `"지지 " + formatKRW()`, 색 `theme.negative`(지지=청). 존이 null이면 해당 선을 생략한다. POC·밸류 에어리어는 카드에만 표시한다(선이 많으면 25구간 차트에서 판독 불가).
- 범례 표시(색 견본 + 텍스트), `legend.textColor = theme.axisText`. 터치는 하이라이트만(`setScaleEnabled(false)`, `isDoubleTapToZoomEnabled = false`).
- `update` 람다에서 테마 색을 매번 재적용한다(`DemarkTDChart.kt:68-72` 관례). 데이터셋은 `remember(profile, selected, displayMode)`로 만든 뒤 `update`에서 `chart.data = …; chart.notifyDataSetChanged(); chart.invalidate()`.
- 접근성: `Modifier.semantics { contentDescription = 요약 }.testTag("InvestorProfileChart")`. 요약 예: "스윙고점 이후 주체별 매물대. 기준가 255,500원. 상방 캡 256,500원에서 267,750원, 1.0억주, 일평균 5.1일분. 하방 지지 247,500원에서 256,500원, 8,578만주."
- 색상만으로 구분하지 않는다: 범례 텍스트 + 분리 모드 고정 순서 + 누적 모드 세그먼트 테두리.

### 7.4 표기 의무 (숨기거나 도움말로 밀어 넣지 않는다) · 지표 카드
차트 바로 위 표기 라인(`labelSmall`, `onSurfaceVariant.copy(alpha = 0.7f)`, `AiAnalysisProbabilityResult.kt:664` 관례):
1. **추정치**: "주체별 매물대는 일별 매매대금에서 산출한 추정치입니다."
2. **산출 기준**: "산출 기준: 잔존 추정(자동)" 또는 "매수 누적(수동)".
3. **구간 폭**: "구간 폭 10,294원 · 2026-03-09~2026-09-05 · 125거래일 · 최신 2026-09-05".
지표 카드(`Card`, 형제 탭 관례), 위에서부터: ① 기준가 · 스윙 앵커(스윙 기간일 때만, 예 "스윙 고점 288,000원 (08-18)") ② **상방 캡** "256,500~267,750원 · 1.0억주 · 일평균 5.1일분" ③ **하방 지지** "247,500~256,500원 · 8,578만주 · 4.2일분" ④ 주체별 상방/하방 합계(3행) ⑤ POC · 밸류 에어리어 ⑥ 흡수 일수 · 매물 공백 구간 수. 존이 null이면 "상방(하방) 매물 없음"으로 표기한다.
물량 표기는 `InvestorProfileContent.kt` 안의 `private fun Double.toKoreanShares(): String`(≥ 1억: `"%.1f억주"`, ≥ 1만: `"%,d만주"`, 그 외 `"%,d주"`. 예: 1.2억주 · 1,624만주)를 쓴다. `KoreanVolumeFormatter`는 정수 단위(`"1억"`, `"1624만"`)만 내므로 **축 전용**이다. 가격은 `Long.formatKRW()`.

### 7.5 디자인 · 접근성 규칙
- 색상은 `ChartTheme`/`MaterialTheme.colorScheme`/`LocalFinanceColors`에서만 가져온다. **hex 하드코딩 0건.** `ChartTheme`에 `tertiaryLine: Int // tertiary` 필드를 **기본값 없이** 추가하고 `rememberChartTheme()`에서 `scheme.tertiary.toArgb()`로 채운다. 생성자 호출처 2곳(`ChartTheme.kt:31`, `CandleDataExtTest.kt:18`)을 함께 갱신한다. 기본값을 두면 누락이 컴파일 오류로 잡히지 않는다.
- 다크 모드에서 동일하게 판독 가능해야 한다(토큰 파생이므로 자동).
- 대비: 앱 기준 WCAG AA(4.5:1). 7:1은 검사 결과만 기록한다.
- 터치 타깃: `FilterChip`·`PillTabRow` 기본 높이 유지(M3 최소 48dp). 폰트 스케일 1.3x에서 붕괴 없음.
- 문자열은 컴포저블 안 한국어 리터럴(앱 관례, `strings.xml` 미사용).

---

## 8. 성능 예산 (실측 기록 필수, §10-5)

| 항목 | 기준 |
|---|---|
| 엔진 연산 (3년 ≈ 750행 × 3주체, 20구간) | 단일 스레드 30ms 이하(JVM 테스트에서 `measureTimeMillis` 기록, 단언은 CI 편차를 고려해 300ms) |
| 캐시 적중 시 최초 표시 | 100ms 이하(Room 읽기 + 연산) |
| 캐시 미적중 최초 수집 | 6개월 ≈ 5콜 ≈ 3초, 전체(3년) ≈ 26콜 ≈ 13초(500ms 직렬). 진행률 표시 |
| 주체 선택·표시 방식 변경 | 네트워크 0회 · 재계산 0회 · 다음 프레임 반영 |
| 산출 기준·구간 수 변경 | 네트워크 0회 · 재계산 1회 |
| 프레임 드롭 | 스크롤·전환 중 0회 |

---

## 9. 테스트 계획

### 9.1 속성 기반 테스트 (필수, `CalcInvestorVolumeProfileUseCaseTest`)
`Random(42)`로 1,000회. 매회 임의 `binCount ∈ {10,15,20,25}`, 거래일 1~120, 가격 1,000~1,000,000원(일간 변동 0~30%), 거래량 0~1e8, 주체별 매수·매도 조합(매도 > 누적 매수 포함). 검증:
- 질량 보존: `Σ allocateDay = volume` (상대오차 1e-9)
- 1차 모멘트: `|배분 가중평균 − vwap| ≤ σ + binWidth/2`
- 비음수: `byInvestor` 전 원소 ≥ 0
- 상각 정합(RESIDUAL): `ΣH_final + Σs = Σ 매수량`(상대오차 1e-9, `s`는 §5.4의 일별 실제 차감량). `excessSell = Σ sellVolume − Σs ≥ 0`. (`excessSell`은 `H`에 들어간 적이 없으므로 항등식에 더하지 않는다)
- 창 해소: `CUSTOM` 임의 구간이 `[today − 3년, today]`로 클램프되고 창 밖 행이 배분에 섞이지 않음
- 구간 분할: 창 내 모든 `low`·`high`가 `[bins.first().lower, bins.last().upper]` 안
- 자동 규칙: 거래일 59 → CUMULATIVE_BUY, 60 → RESIDUAL

### 9.2 골든 테스트 (필수, Phase 2에서 활성화)
픽스처 `docs/fixtures/rows.csv`를 `app/src/test/.../domain/usecase/InvestorProfileFixture.kt`에 **원시 문자열로 그대로 내장**하고(`object InvestorProfileFixture { val CSV = """…"""; fun rows(): List<DailyInvestorFlow> }`), 열 순서 `date,open,high,low,close,vol,p_bv,p_bp,p_sv,p_sp,f_bv,f_bp,f_sv,f_sp,o_bv,o_bp,o_sv,o_sp`로 파싱한다(`*_bp/*_sp` 백만원 → `× 1_000_000L`, `open`은 버림). 기대값은 2026-09-07에 참조 구현 `investor_volume_profile_v2.py`를 실행해 얻은 값이며 삭제·완화하지 않는다. `today`는 픽스처 마지막 날 2026-09-04, 기준가는 마지막 종가 255,500원이다.

허용 오차: 합계·상방·하방·흡수일수는 상대 1e-6, 구간 폭·구간 중심은 절대 0.01, 인덱스·날짜·기준 열거형은 정확 일치.

| # | 조건 | 기대값 |
|---|---|---|
| 1 | 프랙탈 k=5 스윙 고점(전체 60행) | 2026-06-19 (374,500) · 2026-07-31 (267,000) · 2026-08-18 (288,000) |
| 2 | 프랙탈 k=5 스윙 저점 | 2026-06-23 (310,000) · 2026-07-20 (240,000) · 2026-07-29 (189,200) · 2026-08-11 (227,500) · 2026-08-25 (245,000) |
| 3 | 18구간 · 매수 누적 · 전체 | minLow 189,200 · maxHigh 374,500 · 구간 폭 10,294.44 · POC 인덱스 6 · POC 중심 256,113.89 · POC 구간 [250,966.7, 261,261.1) · 개인 합 485,363,394 · 외국인 합 580,627,279 · 기관 합 589,250,289(= 각 주체 매수량 총합) · 개인 상방 357,410,789 |
| 4 | 20구간 · 기준 자동 · 전체(60거래일) | `basis == RESIDUAL`, `autoBasis == true` · 구간 폭 9,265 · POC 인덱스 7(중심 258,687.5) · 개인 50,209,362 · 외국인 2,998,694 · 기관 2,489,812 · 개인 상방 26,610,378 / 하방 23,598,984 · 기관 상방 2,314,476 · 흡수일수 1.0951 · 공백 구간 수 12 · 일평균 28,157,199.4 · 캡 존 7..7 [254,055, 263,320) 물량 18,647,628 · 지지 존 6..6 [244,790, 254,055) 물량 17,160,129 |
| 5 | 20구간 · 잔존 추정 · `SINCE_SWING_HIGH`(2026-08-18~, 14거래일) | 외국인 **0**(소거) · 기관 2,489,812 · 개인 3,565,032 · 구간 폭 2,250 · POC 인덱스 7 |
| 6 | 20구간 · 매수 누적 · 동일 창 | 개인 71,887,984 · 외국인 86,179,825 · 기관 103,455,270(전부 양수) · 흡수일수 8.3290 · 일평균 20,227,777.4 · **캡 존 6..10 [256,500, 267,750) 물량 103,221,544(5.1030일분)** · **지지 존 2..5 [247,500, 256,500) 물량 85,783,190(4.2409일분)** · 앵커 2026-08-18 / 288,000 |
| 7 | 20구간 · 기준 자동 · 동일 창 | `basis == CUMULATIVE_BUY`, `autoBasis == true`, 값은 6행과 동일 |
| 8 | 자동 규칙 경계 | 전체 60행 → RESIDUAL, 첫 행을 제외한 59행 → CUMULATIVE_BUY |
| 9 | 20구간 · 잔존 추정 · `SINCE_SWING_LOW`(2026-08-25~, 9거래일) | 개인 0 · 외국인 0 · 기관 2,489,812 · 구간 폭 1,400 |
| 10 | 20구간 · 기준 자동 · `SINCE_SWING_LOW`(9거래일) | `basis == CUMULATIVE_BUY` · 구간 폭 1,400 · 일평균 16,626,820 · 캡 존 9..14 [255,600, 264,000) 물량 55,230,228(3.3218일분) · 지지 존 4..8 [248,600, 255,600) 물량 51,865,967(3.1194일분) · 앵커 2026-08-25 / 245,000 |

5·9행은 §5.4 자동 규칙의 **근거**(짧은 창 + 상각 = 소거)에 대한 회귀 가드이고, 4·7·8행이 자동 규칙 자체를 검증하며, 6·10행이 캡·지지 존과 앵커를 고정한다. 3행에서 주체 합이 매수량 총합과 같다는 사실은 매수 누적 모드의 질량 보존이 창 전체로 확장됨을 뜻한다.

### 9.3 단위 테스트
- `InvestorFlowDtoTest`: 백만원 → 원 `Long` 승격(`3_931_838` → `3_931_838_000_000L`; 실측 행 20260902 개인 `4,662,430주 / 1,175,428백만원` → vwap 252,106원 ∈ [249,500, 255,500]), 투자자 필드 `""` 행 → null, 콤마 포함 숫자.
- `CalcInvestorVolumeProfileUseCaseTest`: vwap 범위 이탈 leg 스킵(다른 주체 유지), 스윙 미검출 → 1년 폴백 + `swingFallback`, 불연속 절단(비율 0.5 → `truncatedAt`), 창 내 행 0 → null, `maxHigh == minLow`, `CUSTOM` 창 `[start, end]` 필터 + 3년 클램프, 존 확장(수제 `S` 배열로 이웃이 50% 경계 위/아래일 때 포함/제외, 한쪽이 전부 0이면 null, 앵커는 스윙 기간에서만 non-null).
- `InvestorFlowRepositoryTest`(MockK `KisApiClient`, 캔드 30행 페이지): 이음매 중복·누락 0, `OPSQ2001` → date1−1 재시도 1회, `MAX_PAGES` 도달 시 중단 + `Timber.w`, 쿨다운 내 재호출 시 네트워크 0회, 후방 채움이 `from`까지만, 중간 실패 시 `upsertAll` 미호출, MOCK 모드 실패.
- `InvestorFlowDaoInMemoryTest`(Robolectric): upsert 덮어쓰기, `getRange` 정렬, `latestDate/earliestDate/latestFetchedAt`, `deleteOlderThan`.
- `InvestorProfileViewModelTest`: NoStock → Loading → Success, 키 없음 → NoApiKey, 실패 → Error → `retry()`, `toggleInvestor`/`setDisplayMode`/`setBinCount` 후에도 초기 로드 1회 외 추가 호출 없음(`coVerify(exactly = 1) { repository.getFlows(any(), any(), any(), any(), any()) }`), 오프라인 + 캐시 0행 → Error(네트워크 문구), 오프라인 + 캐시 있음 → Success, `setCustomRange` 유효성(`start > end`·`end > today`는 무시, 유효하면 `period == CUSTOM`으로 재계산), 마지막 칩 해제 거부, `clearStock` → NoStock.

### 9.4 UI · 실기 QA
- androidTest `InvestorProfileSmokeTest`: `createComposeRule` + `setContent { InvestorProfileContent(...) }`(Success 상태 주입) → 칩 3개·표기 라인·`testTag("InvestorProfileChart")` 노출 확인(형제 smoke 4건 관례).
- 에뮬레이터 실기(실키, pixel_fold): 탭 진입 → 수집 진행률 → 렌더, 주체 칩 해제 시 해당 시리즈만 사라짐, 마지막 칩 미해제, 누적↔분리 즉시 전환(logcat에 KIS 호출 0), 기간을 스윙 고점 이후로 바꾸면 구간 폭 갱신, 사용자 지정 → 다이얼로그 → 탭 라벨에 범위 표시 → 구간 폭 갱신, Empty·Error·NoApiKey 문구, 라이트/다크, 폰트 1.3x, 15:40 이전 진입, 크래시 0.

### 9.5 접근성 검사
TalkBack으로 칩(상태+값)·차트 요약·표기 라인 낭독, 대비 자동 검사(AA 통과, 7:1 결과 기록), 폰트 스케일 1.3x 레이아웃 유지.

---

## 10. 완료 판정 기준 (전부 충족해야 완료)
1. §0.1 선행 확인이 본 문서에 기록되어 있고 §0.3 결정이 진행 로그에 명시되어 있다.
2. `CalcInvestorVolumeProfileUseCase`·`InvestorVolumeProfileModels`에 `android.*`·`androidx.*` import가 0건이다(grep).
3. §9.1 속성 테스트 1,000회 통과.
4. §9.2 골든 9행 전부 통과.
5. §8 예산 실측값이 진행 로그에 있다.
6. §9.3 단위 테스트·§9.4 smoke·실기 QA·§9.5 통과, 크래시 0.
7. §7.4 표기 세 가지가 화면에 상시 노출된다.
8. 신규 hex 색상 0건(`Color(0x`·`parseColor`·`Color.rgb` grep), 전부 토큰 파생.
9. 공개 API에 KDoc이 있고 §5.4·§5.5 근거가 코드 주석으로 남아 있다.
10. `:app:assembleDebug` 성공, Android lint 신규 경고 0, 신규 코드에 `!!` 0, `CancellationException` 재던지기 준수.
11. Room `39.json` export 존재, `MIGRATION_38_39`가 `ALL`에 등록, 기존 `VolumeProfile*` 파일 무변경(`git diff --stat`).

---

## 11. Phase 계획

| Phase | 산출물 | 수용 조건 | 담당 |
|---|---|---|---|
| **0** | 코드베이스 대조·개정(본 v1.1) + §0.3 결정 확정 | **완료(2026-09-07)** | Advisor |
| **1** | 데이터 계층: `InvestorFlowDto`·`InvestorFlowEntity/Dao`·`MIGRATION_38_39`·`AppDatabase` v39·`DaoModule`/`AppModule` 제공·`InvestorFlowRepository` | `:app:testDebugUnitTest --tests "*InvestorFlow*"` 그린, `:app:assembleDebug` 성공, `39.json` 생성 | kotlin-implementer |
| **2** (1과 병렬) | `InvestorVolumeProfileModels`·`CalcInvestorVolumeProfileUseCase`·`InvestorProfileFixture`·§9.1/§9.2/§9.3 엔진 테스트 | `--tests "*CalcInvestorVolumeProfile*"` 그린(골든 9행 포함), Android import 0 | kotlin-implementer |
| **3** | `InvestorProfileViewModel` + 테스트 | `--tests "*InvestorProfileViewModel*"` 그린 | kotlin-implementer |
| **4** | `ChartTheme.tertiaryLine`(+`CandleDataExtTest` 갱신)·`InvestorProfileChart`·`InvestorProfileContent`(사용자 지정 기간 다이얼로그 포함)·`OscillatorScreen` 배선·`@Preview` | `:app:assembleDebug` 성공, 형제 탭 회귀 없음(수동 스모크) | kotlin-implementer |
| **5** | smoke androidTest·실기 QA·접근성·§8 실측·§10 점검 | §9.4·§9.5 전항 통과, §10 11항 근거 첨부 | qa-verifier |

각 Phase 완료 시 §14에 로그를 남기고 **STOP**, 사용자 승인 후 다음 Phase로 간다. 전체 테스트 스위트는 돌리지 않는다(타깃 `--tests`만).

---

## 12. Worker 위임 브리프

### 12.1 Phase 1 → `kotlin-implementer`
- **공유 컨텍스트**: §2 표(재탐색 금지), §6 전체, §3 파일 목록. 템플릿 `InvestOpinionRepository.kt`, DTO 관례 `FinancialDto.kt`, DAO 관례 `BearSnapshotDao.kt`, 마이그레이션 관례 `MIGRATION_37_38`.
- **작업 기준**: `KisApiClient.get` 재사용(수동 delay 금지), `Result<T>` 반환, `Timber` printf 스타일(`android.util.Log` 금지), `@Upsert`, 페이지 누적 후 일괄 저장(§6.2-5), 상수 `MAX_PAGES=26`·`COOLDOWN_MS=1h`·`RETENTION_DAYS`.
- **알려진 함정**: 백만원 × 1e6 `Int` 오버플로. 30행 이음매에서 커서는 "최고 일자 − 1일"(거래일이 아니라 달력일). 당일 15:40 전 `OPSQ2001`. `tr_cont`는 쓰지 않는다. `KisFinancialApiResponse`에는 `output2`가 없다. MOCK 모드 실패. `CancellationException`은 재던진다.
- **완료 조건**: §9.3의 Dto·Repository·Dao 테스트 그린, `assembleDebug`, `39.json`.

### 12.2 Phase 2 → `kotlin-implementer` (Phase 1과 병렬, 데이터 계층 불필요)
- **공유 컨텍스트**: §4, §5(참조 구현 대조 결과 포함), §9.1, §9.2(픽스처 `docs/fixtures/rows.csv` 원문을 테스트 소스에 내장), §9.3 엔진 항목. 유스케이스 관례는 `BuildVolumeProfileUseCase.kt:10`(plain class + `operator fun invoke`), 제공 관례는 `AppModule.kt:95`.
- **작업 기준**: 순수 Kotlin, 인터페이스·추상화 추가 금지(§0.2-6), `DoubleArray` 사용, §5.4·§5.5 근거 KDoc, §5.7 `ponytail:` 주석, 1,000회 속성 테스트는 `Random(42)` 루프(신규 라이브러리 금지).
- **알려진 함정**: 불변식 2의 상한은 `σ + binWidth/2`(v1.0의 `binWidth/2`가 아니다). vwap 이탈은 leg 단위 스킵. 매도 > 보유 시 초과분 분리. 기존 `VolumeProfile`·`VolumeBucket`과 이름 충돌 금지.
- **완료 조건**: §9.1 1,000회 + §9.2 골든 9행 + 엔진 단위 테스트 그린, Android import 0. 골든이 어긋나면 기대값을 고치지 말고 §5와의 차이를 보고한다.

### 12.3 Phase 3·4 → `kotlin-implementer`
- **공유 컨텍스트**: §7 전체, §2의 Content/ViewModel/에러 카드/차트 테마/포맷터/그룹 막대 선례.
- **작업 기준**: `DemarkTDViewModel`·`DemarkTDContent` 미러, 색은 `ChartTheme` 파생만(`tertiaryLine` 추가), 주체 선택·표시 방식은 재계산 없음, 마지막 칩 유지, `OscillatorScreen`은 enum 값·그룹·`when` 분기 외 무변경. 사용자 지정 기간 다이얼로그는 `ReportDatePickerDialog` 구조 + `DateRangePicker`(§7.2-2).
- **알려진 함정**: `groupBars`의 `fromX`는 첫 구간 하한이고 총 그룹 폭이 `binWidth`와 같아야 하며, 데이터셋이 1개면 `groupBars`가 예외를 던진다. HorizontalBarChart는 `axisLeft`가 위·`axisRight`가 아래, `XAxisPosition.BOTTOM`이 왼쪽이다. `LimitLine`은 `xAxis`에 추가하며 기준가·캡·지지 3개로 제한한다(가격축). `update` 람다에서 테마 색을 재적용하지 않으면 다크 전환 시 축 라벨이 사라진다. `ChartTheme` 필드 추가 시 `CandleDataExtTest.kt:18` 컴파일이 깨지므로 함께 고친다. 스낵바·토스트 금지.
- **완료 조건**: Phase 3 VM 테스트 그린, Phase 4 `assembleDebug` + `@Preview` + 형제 탭 회귀 없음.

### 12.4 Phase 5 → `qa-verifier`
§10의 11개 항목을 근거(테스트 XML 카운트, grep 출력, 스크린샷 경로, logcat 발췌)와 함께 pass/fail로 보고한다. 미통과 항목은 완화하지 않고 해당 Phase로 되돌린다.

---

## 13. 리스크 · 함정

| 리스크 | 대응 |
|---|---|
| KIS 키가 없는 사용자(앱은 KIS 없이도 대부분 동작) | `NoApiKey` 상태 + 설정 안내(재무정보 탭과 동일) |
| 15:40 규칙·휴장일(KRX 휴일 캘린더 없음) | `startCursor` + `OPSQ2001` 1회 재시도 + 1시간 쿨다운. 휴장일은 API가 이전 거래일부터 반환하므로 별도 처리 없음 |
| 3년 수집 13초 | 진행률 표시, 기본 기간 6개월(≈3초), 캐시 우선 |
| 백만원 반올림으로 소량 leg의 vwap 이탈 | leg 단위 스킵 + `Timber.d`. 물량 비중이 미미하므로 결과 왜곡 없음 |
| 분할 종목 | 절단 + 안내. 합산은 §0.4 백로그 1 |
| 짧은 기간 + 잔존 추정 = 빈 차트 오인 | 자동 규칙(60거래일) + 산출 기준 상시 표기 + 회귀 테스트 |
| 기존 `VolumeProfile*`·`InvestorTrend*`와 혼동 | `Investor` 접두 + `Flow` 어간, §2 "별개" 행 2개 "수정 금지·대체 불가" |
| 형제 탭 회귀 | `OscillatorScreen` 변경을 enum·`when`으로 한정, Phase 4 수동 스모크 |

---

## 14. 진행 로그
- **v1.1 개정(2026-09-07)**: v1.0 초안을 코드베이스와 대조해 §0의 15개 항목을 변경했다. 원본은 `archive/TASK_investor_volume_profile_v1.0.md`에 보존했다. 픽스처(`rows.csv`, `investor_volume_profile_v2.py`)는 저장소에 없어 §9.2를 보류 상태로 두었다. 독립 검토(파일:라인 전수 재확인) 지적 25건을 반영했다: `CalcDemarkTDUseCase` 시그니처 정정, `ChartTheme` 생성자 호출처(테스트) 갱신 의무, HorizontalBarChart 축 배치(`axisRight` 하단·`XAxisPosition.BOTTOM` 왼쪽), `groupBars` 단일 데이터셋 예외, `acml_tr_pbmn` 원 단위 실측 반영(전체 거래량 중심 = 시장 평균단가), 상각 항등식 정정, 전방 채움 `from` 정지 조건, 오프라인 `allowNetwork` 경로, 소비처 없는 필드 제거(`sellVwap`·`open`·`totalVolume`·`Success.query`, `lowVolumeNodes` → 개수). Phase 1 착수 전 §0.3 A~D 결정 확인이 필요하다.
- **결정 확정·픽스처 반영(2026-09-07)**: 사용자가 A(평면 계층)·B(픽스처 `docs/fixtures/` 제공)·C(3년)·D(사용자 지정 기간만 복원)를 확정했다. 참조 구현 `investor_volume_profile_v2.py`를 실행해 §9.2를 정확한 기대값 9행으로 교체했고(스윙 고점 3·저점 5, 18구간 POC 256,113.89원·구간 폭 10,294.44원, 20구간 잔존 개인 50,209,362주·외국인 2,998,694주·기관 2,489,812주, 스윙 고점 이후 외국인 0주 등), 픽스처의 vwap 이탈 leg·`buyVolume == 0` leg가 0건임을 확인해 §5.1 규칙이 골든에 영향 없음을 기록했다. 전체 거래량 중심은 참조 구현 동치를 위해 대표가 `(H+L+C)/3`로 환원하고 `acml_tr_pbmn`·`amount` 열을 제거했다. `ProfilePeriod.CUSTOM`·`setCustomRange`·`InvestorProfileDateRangeDialog`(material3 1.2.0 `DateRangePicker`, `ReportDatePickerDialog` 구조)를 추가했다. Phase 1·2 착수 가능.
- **캡·지지 존 표시(2026-09-07, 사용자 요청)**: 직전 스윙 고점·저점 이후 창에서 하방 지지점·상방 캡 표시 가능 여부를 픽스처로 검증했다. 스윙 고점(08-18, 288,000) 이후 14거래일·자동(매수 누적)에서 캡 존 256,500~267,750원(1.03억주, 일평균 5.1일분)·지지 존 247,500~256,500원(8,578만주)이 산출되고, 그 위 8개 구간은 최대 구간의 40% 미만으로 희박해 앵커 288,000이 다음 목표가가 된다. 스윙 저점(08-25, 245,000) 이후 9거래일도 캡 255,600~264,000원·지지 248,600~255,600원으로 산출된다. 같은 창을 잔존 추정으로 계산하면 외국인(고점 창)·개인+외국인(저점 창)이 0으로 소거되어 판독 불가 → 자동 규칙이 필수임을 재확인했다. 상위 3구간 지표를 연속 존(`capZone`/`supportZone` + 물량 + 일수) + 앵커로 교체하고 차트 LimitLine을 기준가·캡 상단·지지 하단 3개로 정리했다(§0.2-18, §5.8, §7.3, §7.4, §9.2 6·10행).
- **Phase 1 완료(2026-09-07, kotlin-implementer, Phase 2와 병렬)**: §4 모델 파일은 Advisor가 선행 작성(`domain/model/InvestorVolumeProfileModels.kt`). 신규 `data/dto/InvestorFlowDto.kt`(`KisInvestorFlowResponse.output2` + `toDailyInvestorFlowOrNull`, 백만원→원 Long 승격, 결측 행 null) · `core/database/entity/InvestorFlowEntity.kt` · `core/database/dao/InvestorFlowDao.kt`(`@Upsert`) · `data/repository/InvestorFlowRepository.kt`(날짜 스텝 페이징, `startCursor` 15:40 규칙, `OPSQ2001` 1회 재시도, 루프별 일괄 upsert, `MAX_PAGES` 26 누적 공유, `now` 주입) · `MIGRATION_38_39` + `AppDatabase` v39 + `DaoModule`/`AppModule` `@Provides`, `39.json` export 확인. Advisor 검증에서 결함 1건 발견·재위임 수정: 캐시 최신일이 `from`보다 오래된 묵은 캐시에서 전방 채움이 `from`에서 멈춰 구멍이 생기던 문제 → 캐시가 있으면 기존 블록까지 이어 붙이는 연속성 불변식(§6.2-3 개정) + 콜드 캐시에서 후방 채움 1콜 생략(§6.2-4 개정). 테스트 `InvestorFlowDtoTest` 8 · `InvestorFlowDaoInMemoryTest` 7(Robolectric) · `InvestorFlowRepositoryTest` 11(콜드 6개월 = 정확히 5콜·upsert 1회, 묵은 캐시 4페이지 연결, OPSQ2001, MAX_PAGES, 쿨다운, 중간 실패 미저장, MOCK, NoApiKey, allowNetwork=false) 전부 그린. `assembleDebug` 성공.
- **Phase 2 완료(2026-09-07, kotlin-implementer, Phase 1과 병렬)**: `domain/usecase/CalcInvestorVolumeProfileUseCase.kt`(Android import 0, 인터페이스 0, `internal` 헬퍼 `resolveWindow`·`truncateAtDiscontinuity`·`binEdges`·`allocateDay`·`allocateWindow`·`findSwingHighs/Lows`·`valueArea`·`zone`, §5.4·§5.5 근거 KDoc, `ponytail:` 주석 2건) + 테스트 소스 `InvestorProfileFixture.kt`(rows.csv 60행 원문 내장, diff로 동일성 확인) + `CalcInvestorVolumeProfileUseCaseTest` 22건: **§9.2 골든 10행 첫 실행 전부 통과**(기대값 무수정; `absorptionDays`만 전체 정밀도 리터럴 사용), §9.1 속성 1,000회, §9.3 단위 10건, §8 성능 **30ms**(750행×3주체×20구간 RESIDUAL, 예산 300ms). Advisor가 코드 대조(커널 절단·비례상각 순서·프랙탈 경계·절단·존·앵커) 및 골든 리터럴 18개 명세 일치 확인 후 Phase 1·2 테스트 48건 일괄 재실행 그린. Phase 3 착수.
- **Phase 4 완료(2026-09-07, kotlin-implementer)**: `ChartTheme.tertiaryLine`(기본값 없음, `CandleDataExtTest` 생성자 갱신) · `presentation/investorprofile/InvestorProfileChart.kt`(`HorizontalBarChart`: 가격축 `xAxis` BOTTOM=왼쪽, 물량축 `axisRight`+`AxisDependency.RIGHT`, STACKED/GROUPED n≥2 `groupBars`/n==1 단일셋, LimitLine 기준가·캡 상단·지지 하단, `semantics`+`testTag`) · `InvestorProfileContent.kt`(상태 없는 `InvestorProfileBody` 분리, 주체 칩+캡션, 기간 8탭+`InvestorProfileDateRangeDialog`(material3 1.2.0 `DateRangePicker` resolve 확인), `PillTabRow` 3행 세로 적층, 표기 3줄, 지표 카드, 절단/폴백 안내, `@Preview`) · `OscillatorScreen` `MainTab.INVESTOR_PROFILE("주체별 매물대")`(enum 말미 추가·`TabGroup.TECHNICAL`·`when` 1분기) · androidTest `InvestorProfileSmokeTest`. `assembleDebug`·`compileDebugAndroidTestKotlin` 성공, `CandleDataExtTest` 9/9·VM 13/13, hex 색상 0. Advisor 검증에서 nit 1건 직접 수정: 누적/단일 주체일 때 범례에 데이터셋 라벨 "주체별 매물대"가 노출 → 스택 2개 이상은 라벨 `""`, 단일 주체는 주체명.
- **Phase 5 실기 QA 통과(2026-09-07, Advisor 직접, pixel_fold API 36 EXPANDED 레이아웃, 삼성전자 실키 KIS)**: ① 탭 노출·진입 → `FHPTJ04160001` **정확히 5콜**(6개월, 500ms 간격) → 렌더(칩 개인 상방 2,957만주·외국인 6만주·기관 105만주, 잔존 추정(자동), 구간 폭 10,375원·125거래일·최신 09-07) ② `HorizontalBarChart` 3색 그룹 막대·가격축 좌측·물량축 하단(만 단위)·LimitLine 캡 281,125/기준가 270,000(파선)/지지 250,000·범례 ③ 지표 카드 전항목(캡 270,750~281,125원·1,164만주·0.4일분, 지지 250,000~270,750원·8,720만주·3.0일분, POC 265,563원, VA, 흡수 1.1일, 공백 7개) ④ 칩 해제 → 해당 시리즈만 소멸·범례 갱신, **네트워크 0회**(logcat 콜 수 불변) ⑤ 누적 전환 즉시·네트워크 0회 ⑥ 스윙고점 이후 → 08-18~09-07 15거래일·매수 누적(자동)·구간 폭 2,250원·앵커 288,000원(08-18)·1년 탐색창 후방 채움 4콜 ⑦ 사용자 지정 `DateRangePicker`(미래일 비활성) 09-01~09-07 → 탭 라벨 범위 표시·5거래일·구간 폭 1,350원·네트워크 0회 ⑧ 마지막 칩 해제 거부(기관 단독 유지, 단일 데이터셋 렌더) ⑨ 라이트 테마 축 라벨·선·카드 가독 ⑩ 폰트 1.3x 붕괴 없음·구성 변경 후 VM 상태 보존 ⑪ 크래시 0(FATAL 0). 계측: `testInstrumentationRunner` 미설정(레거시 `android.test` 러너 → "No tests found"/크래시, 기존 smoke 4건도 실기 미실행 상태였음)을 `app/build.gradle.kts`에 `AndroidJUnitRunner` 1줄 추가로 해소 + smoke 단언 중복 매칭 수정 → **androidTest 6/6 통과**(기존 5 + `InvestorProfileSmokeTest` 1). Empty·Error·NoApiKey 상태는 실기 강제 불가 → VM 단위테스트 13건으로 대체. §8 실측: 엔진 30ms, 6개월 최초 수집 ≈3초(5콜). **환경 부작용**: `connectedDebugAndroidTest`가 종료 시 앱을 언인스톨해 에뮬레이터 앱 데이터(EncryptedSharedPreferences API 키·Room)가 초기화됨 — 실키 재입력 필요(설정 > API 키 백업/복원). 범례 nit 수정분은 재설치 후 데이터 초기화로 실기 재확인 불가(컴파일·계측 그린으로 대체). §10 11항 중 미충족 없음. **Phase 0~5 전체 완료, 커밋 대기(사용자 지시 후).**
- **Phase 3 완료(2026-09-07, kotlin-implementer)**: `presentation/investorprofile/InvestorProfileViewModel.kt` 신설(§7.1 상태 클래스 verbatim, `DemarkTDViewModel` 미러 — `@Volatile currentTicker`·`cachedRows`, `loadJob`/`recomputeJob` 취소 관리) + `AppModule.provideCalcInvestorVolumeProfileUseCase` 1줄 추가(`InvestorFlowRepository` 프로바이더는 Phase 1 산출물, 무변경). 규칙 ①~⑦ 그대로 구현: 표시 필터(`toggleInvestor`/`setDisplayMode`)는 동기 필드 갱신만 하고 재계산·네트워크를 유발하지 않음, `setBasis`/`setBinCount`는 `recompute()`만(리포지토리 미호출), `setPeriod`/`setCustomRange`는 `load()`로 재조회, 마지막 남은 주체 해제 거부, 오프라인 `allowNetwork=false` 경로 + 0행 시 네트워크 오류 문구. `InvestorProfileViewModelTest` 13건 작성: NoStock→Success, 키 없음/모의투자→NoApiKey, 리포지토리 실패→Error→`retry()`→Success, 표시 필터·산출 기준·구간 수 변경 후 `getFlows` 추가 호출 0(`coVerify(exactly=1)`), 오프라인+캐시 0행→Error, 오프라인+캐시 있음→Success, `setCustomRange` 경계(역전·미래일 무시, 유효 시 CUSTOM), 마지막 칩 거부, `clearStock`→NoStock. `recompute()`가 `Dispatchers.Default`(실 스레드)에서 돌아 `advanceUntilIdle()`만으로는 완료를 보장 못 하므로 `state.first { predicate }`로 종료 상태를 대기(표시 필터 변경 검증은 `!is Loading`이 아니라 `bins.size`/`autoBasis` 등 변경분 자체를 predicate로 삼아 스테일 값 오탐을 차단). `--tests "*InvestorProfileViewModel*"` 13/13 그린(4.7s). Phase 4(차트·Content·`OscillatorScreen` 배선) 착수 가능.
