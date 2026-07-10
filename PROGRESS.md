# PROGRESS — BearSignal 이식 진행 기록

> 근거: `TASK.md` (「주도주 붕괴 판단 계기판」 이식 명세서 v1.0). 각 Phase 완료 시 `PROGRESS:` 마커 갱신.

PROGRESS: P5 — 구현 마감·에뮬레이터 QA 1차 통과·잔여 QA 대기(WorkManager 월간 주기 갱신(`BearSignalUpdateWorker`, 매월 5일 06:00) + 앱 시작 시 스케줄 복원 + shimmer 로딩(`BearSignalScreenSkeleton`) + 오프라인 폴백(`NetworkUtils`+`StaleBanner`, 캐시 데이터 유지+최신 갱신일 표기) + 접근성 최종 점검(신호1~4/증폭 카드·국가별 표 행 contentDescription 보강, 48dp 터치 타깃/색+텍스트 병기 확인) + JVM 테스트 신규 `feature.bearsignal` 패키지 +3건(ViewModel isLoading/isOffline, 총 226건) + `core.worker` 패키지 +6건(`BearSignalUpdateWorkerTest` companion 상수·알림ID 유일성·스케줄 입력 검증), 2026-07-10; 에뮬레이터 실기 QA 1차 통과 2026-07-10 — 하단 「실기 QA」 절 참조, 잔여: 360dp/다크/폰트스케일 렌더·월간 워커 실발화·관세청/FRED 실키·스펙 조정 2건; Stooq 대체 소스 결정은 2026-07-10 Yahoo 기본+Stooq 백업 멀티소스로 해소 — 하단 「시세 소스 교체」 절 참조)

PROGRESS: P4 — 완료 (UI 조립 — `BearSignalScreen` LazyColumn 7섹션(헤더·선행신호3카드·국가별수익률표(전치)·방아쇠증폭·3유형·역사검증·푸터) + Canvas 신호등/게이지/레이더 + `ObserveBearSignalStateUseCase` 신규 + 시장분석 탭 진입점 카드 + Pull-to-refresh/리셋/수동입력 BottomSheet 연결 + JVM 테스트 18건 신규(총 223건), 2026-07-10)

PROGRESS: P3 — 완료 ([C]/[D] 수동 입력 계층(신용잔고·적자상장비중·신주비중·대어소화·정책방향·반대매매임박·미커버 해외지수) + auto⊕manual 병합(MANUAL 우선) + 리포트 기준값 리셋 + Room v36 + JVM 테스트 46건 신규(총 205건), 2026-07-10)

PROGRESS: P2 — 완료 ([B] 자동 연동 4지표(관세청 수출비중·FRED/ECOS 금리·IPO ETF 방향·해외 19개 지수) + Room v35 + JVM 테스트 89건 신규(총 159건), 2026-07-10)

PROGRESS: P1 — 완료 ([A] 자동 연동 2지표(신호2 통계·코스피 2사 비중) + Room v34 + JVM 테스트 29건 신규(총 70건), 2026-07-09)

PROGRESS: P0 — 완료 (스캐폴딩·도메인 모델·순수 스코어링·JVM 테스트 41건, 2026-07-09; 시드 데이터 정정 2026-07-09 점검)

## P0 상세
- `feature/bearsignal/domain/model/BearSignalModels.kt` — SignalLevel·GateState·BearPhase·Depth·InputSource·MarketReturns·MarketAnalysis·BearSignalInputs·BearSignalResult + 플레이스홀더(BearType·MonitorItem)
- `feature/bearsignal/domain/model/BearSignalReportBaseline.kt` — 2026.6.30 리포트 기준값 스칼라 + 도표48 전체 20지수 시드(`MARKETS`, 프로토타입 jsx `MARKETS` 그대로 이관)
- `feature/bearsignal/domain/repository/BearSignalRepository.kt` — Phase 1+ 확장용 마커 인터페이스
- `feature/bearsignal/domain/usecase/ComputeBearSignalUseCase.kt` — §3/부록 A 1:1 순수 스코어링(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite), 안드로이드 의존성 0
- 테스트: `ComputeBearSignalUseCaseTest.kt` 41건 전부 통과 (골든 케이스 2026.6.30 → AMBER 재현, 도표48 실데이터 20지수 사용 + 전 임계 경계)

## P1 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalAutoModels.kt` — `AutoIndicator<T>`(값+source+updatedAt), `BearIndicatorKey` enum(캐시 키, Phase2+ 재사용 전제), `AutoBearSignalInputs`(up3/down3/up4/down4/kospi2)
  - `feature/bearsignal/domain/usecase/VolatilityStatsCalculator.kt` — §3.2 신호2 입력(±3σ/±4σ 카운트) 순수 계산. jsx 프로토타입에 σ 계산 로직이 없음을 확인(Stepper 수동입력 상수만 존재) → **단순수익률**(로그수익률 아님) + **표본표준편차(ddof=1)** 채택, KDoc에 근거 명시. 경계는 `strict >`(정확히 σ 도달은 미카운트)로 고정, `countBreaches`를 `internal`로 분리해 자기참조 없는 경계 테스트 가능하게 함
  - `feature/bearsignal/domain/usecase/Kospi2Calculator.kt` — §3.5 kospi2(삼성전자+SK하이닉스 시총/코스피 전체 시총×100) 순수 계산, `Map<String, Long>` 입력(kotlin_krx 타입 비의존)
  - `feature/bearsignal/domain/usecase/RefreshAutoInputsUseCase.kt` — repository.refreshAutoInputs() 위임 얇은 진입점
  - `feature/bearsignal/domain/repository/BearSignalRepository.kt` — Phase 0 마커 인터페이스 확장(`observeAutoInputs`/`getCachedAutoInputs`/`refreshAutoInputs`)
- **data**
  - `feature/bearsignal/data/local/BearSignalAutoCacheEntity.kt` + `BearSignalDao.kt` — 범용 key-value 캐시(지표키·값·source·updatedAt), Phase2+ [B] 지표도 키 추가만으로 재사용
  - `feature/bearsignal/data/mapper/BearSignalAutoCacheMapper.kt` — Entity↔AutoBearSignalInputs 변환, 필수 5키 중 하나라도 없으면 null
  - `feature/bearsignal/data/repository/BearSignalRepositoryImpl.kt` — KRX 로그인(ApiConfigProvider 자격증명) → `KrxIndex.getKospi("1001")` 130영업일 종가(200일 버퍼) → `VolatilityStatsCalculator` → `KrxStock.getMarketCap(KOSPI)` → `Kospi2Calculator` → Room upsert. 계정 미설정/로그인 실패/데이터 부족/예외 시 기존 캐시로 폴백(`Result.success`), 캐시도 없으면 `Result.failure`. 기존 `FearGreedRepository` 패턴(로그인→조회→계산→저장→finally close) 준수
  - `feature/bearsignal/di/BearSignalModule.kt` — Hilt `@Module` (`BearSignalRepository`, `RefreshAutoInputsUseCase` 제공)
- **Room**: `AppDatabase` v33→v34(`MIGRATION_33_34`, `bear_signal_auto_cache` 테이블 신규), `AppDatabaseMigrations.ALL`/`DaoModule`/`core/di/DaoModule` 갱신. 스키마 `app/schemas/.../34.json` 자동 export 확인 — Room 생성 SQL이 수기 마이그레이션 SQL과 문자 단위 일치.
- **테스트**: 신규 29건(총 70건, 0 실패) — `VolatilityStatsCalculatorTest`(Fixture A/B 골든값 Python 사전검증, MIN_RETURNS 경계, 표준편차 0 경계, 정확히 3σ/4σ 미카운트 경계, up=0 케이스), `Kospi2CalculatorTest`(합산·백분율·누락·0시총 경계), `BearSignalAutoCacheMapperTest`(왕복 변환, 키 누락, source 파싱 폴백), `BearSignalRepositoryImplTest`(MockK — 정상 수집, 로그인 실패/계정 미설정/데이터 부족 시 캐시 폴백, 폴백 캐시도 없을 때 failure), `RefreshAutoInputsUseCaseTest`(위임 검증). 기존 41건 회귀 통과.
- **assembleDebug**: BUILD SUCCESSFUL (Room v34 마이그레이션·Hilt 그래프 검증 겸용).

## P2 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalAutoModels.kt` — `BearIndicatorKey`에 [B] 등급 5키 추가(`AMP_SEMI`/`AMP_BUFFER`/`GATE_RATE`/`GATE_DIR`/`S3_ETF`). `AutoBearSignalInputs`에 `semi`/`buffer`/`rate`/`dir`/`etf`(전부 nullable, 구버전 5키 캐시 하위 호환) 추가
  - `feature/bearsignal/domain/model/MarketReturnsSnapshot.kt` — `MarketCoverage`(AUTO/MANUAL_REQUIRED), `AutoMarketReturn`, `MarketReturnsSnapshot`(도표48 20지수 스냅샷 + `manualRequiredNames`)
  - `feature/bearsignal/domain/model/GlobalIndexRegistry.kt` — 도표48 19개 해외지수(코스피 제외) ↔ Stooq 티커 매핑표. 신뢰도 높은 6개(다우·S&P·나스닥·DAX·닛케이·항생)만 AUTO, 나머지 13개(대만·CAC40·호주·유로·FTSE·태국·베트남·상하이·인도·멕시코·브라질·인니·RTS)는 MANUAL_REQUIRED
  - `feature/bearsignal/domain/model/CustomsTradeModels.kt` — 관세청 `getNitemtradeList` 파싱 결과 모델(`CustomsTradeItem`)
  - `feature/bearsignal/domain/usecase/CustomsTradeCalculator.kt` — §3.5 `semi`(15대 품목 합계 대비 반도체 비중 근사치, 총수출 미제공 한계 KDoc 명시) · `buffer`(완충 3산업 YoY 증감률 ≥ −20% 이면 건재, 구현 결정 임계값) 순수 계산
  - `feature/bearsignal/domain/usecase/RateGateInputCalculator.kt` — §3.4 `dir`(ECOS 기준금리 최신·직전 비교 hike/ease/hold) 순수 계산. `rate`는 FRED 값을 그대로 사용(근거: 리포트 기준값 3.75%가 미 연준 금리대와 정합)
  - `feature/bearsignal/domain/usecase/IpoEtfDirectionCalculator.kt` — §3.3 `etf`(최근 60거래일 고점 대비 괴리율로 up/flat/down, jsx에 계산 로직 부재 확인 후 본 구현이 결정) 순수 계산
  - `feature/bearsignal/domain/usecase/GlobalIndexReturnCalculator.kt` — §3.1 4기간 수익률(거래일 근사 252/126/63/21) 순수 계산
  - `feature/bearsignal/domain/usecase/RefreshExternalAutoInputsUseCase.kt` / `RefreshMarketReturnsUseCase.kt` — Phase 2 얇은 진입점(Phase 1 `RefreshAutoInputsUseCase`와 별도 경로, 상호 실패 격리)
- **data**
  - `feature/bearsignal/data/remote/CustomsTradeApiClient.kt` — 관세청 무역통계 Open API(`apis.data.go.kr/1220000/nitemtrade/getNitemtradeList`), 공공데이터포털 표준 응답 래퍼 JSON 파싱(단건/배열 방어)
  - `feature/bearsignal/data/remote/FredApiClient.kt` — FRED `series/observations`(기본 `DFEDTARU`), 결측치(`"."`) 스킵 후 최신 유효값 반환
  - `feature/bearsignal/data/remote/StooqCsvClient.kt` — Stooq 무료 CSV(`q/d/l`, 인증키 불필요), IPO ETF·해외지수 공용
  - `feature/bearsignal/data/local/BearSignalCountryReturnEntity.kt` + `BearSignalDao.kt` 확장 — 국가별 4기간 수익률 전용 테이블(범용 스칼라 캐시와 분리)
  - `feature/bearsignal/data/mapper/BearSignalAutoCacheMapper.kt` — Boolean/String 지표를 Double로 인코딩(buffer: 1.0/0.0, dir: −1/0/1, etf: −1/0/1), 기존 5키 우선순위·null 안전성 유지
  - `feature/bearsignal/data/mapper/BearSignalCountryReturnMapper.kt` — `MarketReturnsSnapshot` ↔ Entity 변환
  - `feature/bearsignal/data/repository/BearSignalRepositoryImpl.kt` — `refreshExternalAutoInputs()`(관세청/FRED/ECOS/Stooq 지표별 best-effort, 개별 실패는 해당 지표만 이전 캐시 유지) · `refreshMarketReturns()`(코스피 KRX 재사용 + Stooq 커버 6지수, 실패 지수만 캐시 폴백, 미커버 13지수는 MANUAL_REQUIRED로 표시)
  - `feature/bearsignal/di/BearSignalModule.kt` — 신규 API 클라이언트 3종 + UseCase 2종 Hilt 프로바이더 추가
  - `core/config/ApiConfigProvider.kt` + `presentation/settings/SettingsPreferences.kt`/`ApiKeySettingsSection.kt`/`SettingsScreen.kt` — 관세청·FRED API 키를 기존 KIS/DART/ECOS 키 관리 패턴(EncryptedSharedPreferences, masked, 로깅 금지)으로 저장·설정 UI(API 탭) 추가
- **Room**: `AppDatabase` v34→v35(`MIGRATION_34_35`, `bear_signal_country_return` 테이블 신규). 스칼라 5지표는 마이그레이션 없이 기존 `bear_signal_auto_cache`(v34) 재사용(P1 설계 의도대로). 스키마 `app/schemas/.../35.json` 자동 export 확인.
- **테스트**: 신규 89건(총 159건, 0 실패) — 순수 계산기(`CustomsTradeCalculatorTest` 11, `RateGateInputCalculatorTest` 4, `IpoEtfDirectionCalculatorTest` 10, `GlobalIndexReturnCalculatorTest` 7, 전부 경계값 포함), API 클라이언트 fixture 파싱(`CustomsTradeApiClientTest` 7, `FredApiClientTest` 6, `StooqCsvClientTest` 7 — 실 응답 형태 JSON/CSV 기반), 매퍼(`BearSignalAutoCacheMapperTest` +9, `BearSignalCountryReturnMapperTest` 7), 레지스트리(`GlobalIndexRegistryTest` 6), UseCase 위임(`RefreshExternalAutoInputsUseCaseTest`/`RefreshMarketReturnsUseCaseTest` 각 1), 리포지토리(`BearSignalRepositoryImplTest` +13 — 지표별 best-effort 폴백·Phase1 캐시 보존·전체 실패 시나리오). 기존 70건 회귀 통과.
- **빌드**: `:app:compileDebugKotlin`/`:app:compileDebugUnitTestKotlin`/`:app:assembleDebug` 전부 BUILD SUCCESSFUL.
- **수동 폴백(MANUAL_REQUIRED) 처리 지수**: 대만, CAC40, 호주, 유로, FTSE, 태국, 베트남, 상하이, 인도, 멕시코, 브라질, 인니, RTS (13개) — Stooq 티커 신뢰도·RTS 접근성 이슈로 v1은 수동 입력 폴백(§1.1 각주1). v2에서 실기 검증 후 확장 검토.

## P3 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalManualModels.kt` — `ManualIndicatorKey`(LOSS/BIG/ISSUE_RATIO/CREDIT/MARGIN/DIR, `BearIndicatorKey`와 별도 키 공간), `IpoBigConsumption`(smooth/pending/failed 상수+VALID), `ManualBearSignalInputs`(6필드, 전부 `AutoIndicator<T>?`), `ManualMarketReturn`(국가별 수동 오버라이드, 기간별 null 허용), `ManualFieldUpdate` sealed interface(BottomSheet → UseCase 갱신 요청 페이로드, MarketReturn 포함)
  - `feature/bearsignal/domain/usecase/MergeBearSignalInputsUseCase.kt` — **auto⊕manual 병합 핵심 로직**(§1.2). 필드별 우선순위 MANUAL > AUTO > 리포트 기준값(`BearSignalReportBaseline`). `dir`만 AUTO(P2 ECOS)·MANUAL 양쪽 경로가 있어 실제로 MANUAL 우선 규칙이 검증되는 필드. 국가별 수익률은 지수×기간 단위로 동일 우선순위 적용(`mergeMarkets`, 기간별 부분 오버라이드 지원). `issueRatio`(신주비중)는 §3 스코어링 파라미터가 아니므로 조립 대상에서 명시적으로 제외. 순수 함수, 안드로이드 의존성 0
  - `feature/bearsignal/domain/usecase/UpdateManualInputUseCase.kt` — `ManualFieldUpdate` 검증(big/dir 허용값, MarketReturn 4기간) 후 repository 위임
  - `feature/bearsignal/domain/usecase/ResetToReportBaselineUseCase.kt` — repository.resetToReportBaseline() 위임. **범위 결정**: 수동 오버라이드만 삭제하고 [A]/[B] 자동 수집 캐시는 보존(최소 부작용 원칙, KDoc에 근거 명시) — `loss`/`big`/`credit`/`margin`/미커버 해외지수는 AUTO 경로가 아예 없으므로 이 리셋만으로 리포트 기준값과 완전히 일치, `dir`처럼 AUTO 경로가 있는 필드는 리셋 후 최신 자동 수집값이 다시 노출됨(의도된 동작)
  - `feature/bearsignal/domain/repository/BearSignalRepository.kt` — `observeManualInputs`/`getManualInputs`/`updateManualInput`/`observeManualMarketReturns`/`getManualMarketReturns`/`resetToReportBaseline` 추가
- **data**
  - `feature/bearsignal/data/local/BearSignalManualInputEntity.kt` + `BearSignalManualCountryReturnEntity.kt` — 자동 캐시(`bear_signal_auto_cache`/`bear_signal_country_return`)와 분리된 전용 테이블. 자동 갱신이 매번 지표 행을 덮어쓰므로 같은 테이블에 수동값을 두면 유실되기 때문(별도 테이블 필수). `source` 컬럼 없음(테이블 존재 자체가 MANUAL 의미)
  - `feature/bearsignal/data/mapper/BearSignalManualInputMapper.kt` — margin(Boolean)/big(smooth=0/pending=1/failed=2)/dir(ease=-1/hold=0/hike=1, `BearSignalAutoCacheMapper`와 동일 코드값) Double 인코딩, 키 누락 시 필드별 null
  - `feature/bearsignal/data/mapper/BearSignalManualCountryReturnMapper.kt` — `ManualMarketReturn` ↔ Entity 변환
  - `feature/bearsignal/data/repository/BearSignalRepositoryImpl.kt` — `updateManualInput`(6개 `ManualFieldUpdate` 하위타입 → 인코딩 후 upsert) · `resetToReportBaseline`(`clearManualInputs`+`clearManualCountryReturns`, 자동 캐시 테이블 미터치)
  - `feature/bearsignal/data/local/BearSignalDao.kt` — 수동 오버라이드 CRUD 8메서드 추가(observe/get/upsert/clear ×2테이블)
  - `feature/bearsignal/di/BearSignalModule.kt` — `UpdateManualInputUseCase`/`ResetToReportBaselineUseCase`/`MergeBearSignalInputsUseCase` Hilt 프로바이더 추가
- **presentation** (구현 항목 3 — "입력→상태 반영·병합·persistence" 중심, 전체 화면 조립은 Phase 4)
  - `feature/bearsignal/presentation/ManualInputViewModel.kt` — `@HiltViewModel`, auto/manual/marketsSnapshot/manualMarkets 4-Flow `combine` → `MergeBearSignalInputsUseCase`로 즉시 병합 미리보기(`ManualInputUiState`), 필드별 update 함수 + `reset()`
  - `feature/bearsignal/presentation/ui/ManualInputBottomSheet.kt` — Material3 `ModalBottomSheet` + Slider(loss/issueRatio) + SegmentedButton(big/dir) + Stepper(credit, +/- IconButton) + Switch(margin) + 리셋 버튼. 헤더·카드·표 등 전체 화면 조립은 Phase 4로 이연
- **Room**: `AppDatabase` v35→v36(`MIGRATION_35_36`, `bear_signal_manual_input`+`bear_signal_manual_country_return` 테이블 신규). 스키마 `app/schemas/.../36.json` 자동 export 확인 — Room 생성 `createSql`이 수기 마이그레이션 SQL과 문자 단위 일치.
- **테스트**: 신규 46건(총 205건, 0 실패) — `MergeBearSignalInputsUseCaseTest` 16(골든 케이스 재현: AUTO/MANUAL 전무 → `BearSignalReportBaseline.toInputs()`와 완전 동일 + AMBER 재현, `dir` MANUAL>AUTO>기준값 3단 우선순위, 국가별 수익률 지수×기간 단위 병합 및 부분 오버라이드), `UpdateManualInputUseCaseTest` 8(big/dir 유효값·잘못된 값 예외, MarketReturn 기간 수 검증), `ResetToReportBaselineUseCaseTest` 1, `BearSignalManualInputMapperTest` 8(인코딩 왕복, 키 누락 시 null), `BearSignalManualCountryReturnMapperTest` 3, `BearSignalRepositoryImplTest` +12(6개 `ManualFieldUpdate` 타입별 upsert 검증, get/observe 매핑, 리셋 시 수동 테이블만 삭제·자동 캐시 미터치 검증). 기존 159건 회귀 통과.
- **빌드**: `:app:compileDebugKotlin`/`:app:compileDebugUnitTestKotlin`/`:app:assembleDebug` 전부 BUILD SUCCESSFUL. Compose `ModalBottomSheet`/`SegmentedButton` 실험적 API `@OptIn(ExperimentalMaterial3Api::class)` 필요(private 헬퍼 컴포저블에도 개별 부여).
- **범위 참고**: TASK.md 사용자 보충 지시가 나열한 "정책 방향"은 §1.1 매트릭스상 [A] 등급(ECOS 자동, Phase 2 완료)이지만 §4 폴백 열에 "수동"이 명시돼 있어 Phase 3에서 MANUAL 오버라이드 경로를 추가했다(`ManualIndicatorKey.DIR`) — MANUAL 우선 규칙이 실제로 auto와 충돌하는 유일한 필드.

## P4 상세

- **domain**
  - `feature/bearsignal/domain/model/BearSignalModels.kt` — `BearType`을 프로토타입 `TYPES` 구조에 맞춰 확장(index/title/axis/recoveryLabel/recoveryOutlook/theory/cases/why/monitor), `RecoveryOutlook`(LOWEST/MEDIUM/PATIENCE) enum 신규
  - `feature/bearsignal/domain/model/BearSignalStaticContent.kt` — 약세장 3유형(TYPES, 프로토타입 1:1) + 활성 방아쇠 인덱스(유형3) + 역사 검증 3대 모니터링 지표 + 지표↔리포트 매핑 + 면책 문구, 전부 정적 데이터(§3 SSOT 무관)
  - `feature/bearsignal/domain/usecase/ObserveBearSignalStateUseCase.kt` — TASK.md §2 아키텍처가 명시한 화면 조립 UseCase 신규 구현. Room 4-Flow(auto/manual/marketsSnapshot/manualMarkets) + 기간 선택 Flow를 `combine`해 `MergeBearSignalInputsUseCase` → `ComputeBearSignalUseCase`로 이어지는 파이프라인을 하나의 `State`로 합성. 안드로이드 의존성 0
  - `feature/bearsignal/di/BearSignalModule.kt` — `ComputeBearSignalUseCase`/`ObserveBearSignalStateUseCase` Hilt 프로바이더 추가
- **presentation**
  - `feature/bearsignal/presentation/BearSignalViewModel.kt` — `@HiltViewModel`. `ObserveBearSignalStateUseCase(periodIdx)` + `isRefreshing` + `errorMessage` 3-Flow `combine` → `BearSignalUiState`(`stateIn(WhileSubscribed(5_000))`). `refresh()`(Phase1/2 자동 UseCase 3종 순차 호출, 지표별 실패 메시지 합성) · `selectPeriod()`(§5.3 FilterChip) · `updateMarketReturn()`/`updateLoss()` 등(Phase3 `UpdateManualInputUseCase` 위임) · `reset()`(Phase3 `ResetToReportBaselineUseCase` 위임) · `lastUpdatedAt`(자동+수동 전 지표 `updatedAt` 중 최댓값, §5.2 섹션7)
  - `feature/bearsignal/presentation/ui/BearSignalScreen.kt` — `LazyColumn` 7섹션 조립(헤더/선행신호3카드/국가별수익률표/방아쇠·증폭/3유형/역사검증/푸터), TopAppBar(뒤로·새로고침·리셋), `PullToRefreshContainer`(material3 1.2.0 API, `rememberPullToRefreshState`+`nestedScroll`), 리셋 확인 다이얼로그, Phase3 `ManualInputBottomSheet` 재사용(별도 `ManualInputViewModel` 인스턴스 — 동일 Room 소스라 자동 동기화)
  - `feature/bearsignal/presentation/ui/BearSignalHeaderSection.kt` — 섹션1: 신호등+국면 라벨+선행점수 게이지(0~100)+금리방아쇠/증폭/경고신호 readout+레이더+해설문구
  - `feature/bearsignal/presentation/ui/BearSignalLeadingSignalsSection.kt` — 섹션2: 신호1/2/3 카드(4단 게이지+레벨칩+readout). 신호1은 §섹션3 표에서, 신호3(loss/big)만 실제 MANUAL 오버라이드 경로가 있어 "수동 입력" 버튼 노출 — 신호2(±3σ)는 [A] 완전자동이라 버튼 없음(§1.1 근거, 상세는 "범위 참고" 항목)
  - `feature/bearsignal/presentation/ui/BearSignalCountryTableSection.kt` — 섹션3: 국가=행(20)×기간=열(4) 전치 표, FilterChip 기간 선택 + 이탈수 요약 칩, 행 탭 → 4기간 편집 다이얼로그(20×4 텍스트필드를 LazyColumn에 직접 배치하지 않고 행 단위 다이얼로그로 대체 — 포커스/성능 리스크 회피)
  - `feature/bearsignal/presentation/ui/BearSignalGateAmpSection.kt` — 섹션4: 금리 GATE 카드(4상태 게이지+수동입력 버튼) + 증폭 계수 카드(읽기전용, semi/kospi2/buffer는 MANUAL 경로 없음)
  - `feature/bearsignal/presentation/ui/BearSignalTypesHistorySection.kt` — 섹션5(3유형 카드+로컬 체크박스 모니터링 체크리스트+유형3 활성 하이라이트)+섹션6(일본 3충격 역사 검증+3대 지표)
  - `feature/bearsignal/presentation/ui/BearSignalFooterSection.kt` — 섹션7: 지표↔리포트 매핑+면책+`LastUpdatedText`(기존 컴포넌트 재사용)
  - `feature/bearsignal/presentation/ui/BearSignalGraphics.kt` — Canvas 커스텀 그래픽: `TrafficLightColumn`(4구 신호등), `SignalGauge`(4단 세그먼트+라벨, Layout 기반), `BearSignalRadar`(4축 미니 레이더, `TextMeasurer`로 축 라벨 렌더)
  - `feature/bearsignal/presentation/ui/BearSignalColors.kt` — LEVEL 색 매핑(안전=primary·주의=secondary·경고=오렌지 보강 상수·위험=error, 다크/라이트 대응) + `PhaseMeta`(국면별 라벨·해설, 프로토타입 `PHASE_META` 1:1) + `SourceBadge`(AUTO/MANUAL/기준값+갱신일)
  - `feature/bearsignal/presentation/ui/BearSignalEntryCard.kt` — 신규 메뉴 진입점(§5.1 권장안), 실시간 국면 미리보기(자체 `BearSignalViewModel` 인스턴스, Room 캐시 기반)
- **네비게이션**: `MainActivity.kt`에 `composable("bear_signal")` 라우트 추가(기존 4→5탭 유지, 6번째 하단 탭 신설 안함 — §5.1 "권장안" 채택). `MarketAnalysisScreen`(Fear&Greed 탭) `MarketSummaryCard` 바로 아래 `BearSignalEntryCard` 배치, `onBearSignalClick` 콜백을 `MainActivity` → `MainScaffold` → `MarketAnalysisScreen` → `FearGreedTab`으로 스레딩
- **접근성/모바일**: LEVEL 색 항상 텍스트 라벨 병기(`SignalLevel.label`/`GateState.label`), 헤더 카드에 `Modifier.semantics { contentDescription }` 요약 부여, 표는 전치(국가=행)로 360dp 세로 스크롤 대응, Material3 기본 컴포넌트(Card/Chip/Button) 사용으로 폰트 스케일 대응은 시스템 기본값 상속
- **테스트**: 신규 18건(총 223건, 0 실패) — `BearSignalStaticContentTest` 5(3유형 개수·순서·활성인덱스·모니터링 비어있지 않음·면책문구), `ObserveBearSignalStateUseCaseTest` 3(Room 캐시 전무 시 골든 케이스 AMBER 재현, 수동 오버라이드 즉시 반영, 기간 선택 반영), `BearSignalViewModelTest` 10(초기값·Room 방출 반영·lastUpdatedAt 계산·refresh 성공/실패/에러클리어·selectPeriod 내부 Flow 갱신·수동입력 위임 3종·reset 위임). `WhileSubscribed(5_000)` StateFlow 테스트 시 `backgroundScope.launch { uiState.collect() }`로 구독 유지 필요(주석에 근거 명시). 기존 205건 회귀 통과.
- **빌드**: `:app:compileDebugKotlin`/`:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"`/`:app:assembleDebug` 전부 BUILD SUCCESSFUL.
- **미검증(가능 범위 밖)**: 360dp 실기/에뮬레이터 렌더링, 다크·라이트 모드 시각 대비, 폰트 스케일 1.3x 붕괴 여부 — 이번 세션은 Bash/Gradle만 사용 가능해 실기 캡처 불가. 코드 레벨로는 Material3 표준 컴포넌트·기존 테마 토큰만 사용해 다크/라이트 자동 대응하도록 작성했으나 시각 검증은 Phase 5(qa-verifier)에서 필요.
- **PullToRefreshContainer**: Compose Material3 1.2.0(BOM 2024.02.00) API(`rememberPullToRefreshState`+`Modifier.nestedScroll`+`PullToRefreshContainer`, `PullToRefreshBox` 상위 헬퍼는 1.3.0+에만 존재) 확인 후 수동 조립.
- **범위 참고**: §5.2 섹션2 "자동값 표시/수동 입력 버튼"은 실제 Phase3 도메인에 MANUAL 경로가 있는 필드(신호3의 loss/big, 금리의 dir/credit/margin)에만 노출했다. 신호1(국가별 수익률은 섹션3 표에서 별도 편집)·신호2(±3σ, [A] 완전자동)·증폭(semi/kospi2/buffer, MANUAL 경로 없음)은 §1.1 매트릭스상 애초에 수동 입력이 불필요하거나(완전자동) Phase3에서 그 경로를 구현하지 않았다(v1 범위 밖) — Phase4는 UI 조립만 담당하므로 Room 스키마·Phase3 완료 항목을 확장하지 않았다.

## P5 상세

- **WorkManager 월간 주기 갱신**
  - `core/worker/BearSignalUpdateWorker.kt` 신규 — `BaseCollectionWorker` 상속(기존 패턴: HiltWorker, foreground DATA_SYNC, exp backoff 30s, `worker_logs` 기록). `RefreshAutoInputsUseCase`([A])→`RefreshExternalAutoInputsUseCase`([B])→`RefreshMarketReturnsUseCase`(도표48) 3단계를 `BearSignalViewModel.refresh()`와 동일 순서로 호출. 각 UseCase는 이미 Phase1/2에서 개별 실패 시 캐시 폴백을 구현했으므로, 워커는 3개 결과를 집계해 **3개 전부 실패했을 때만** retry/failure, 일부 성공 시 success로 처리(부분 갱신도 캐시 신선도 개선에 기여)
  - `core/worker/WorkManagerHelper.kt` — `scheduleMonthlyWorker` 제네릭 헬퍼 신규(`scheduleDailyWorker`와 대칭 구조) + `scheduleBearSignalUpdate`/`cancelBearSignalUpdate`/`runBearSignalUpdateNow`. 기본 스케줄 매월 5일 06:00, flex 1일(월별 일수 차이 흡수). WorkManager `PeriodicWorkRequest`는 고정 길이 간격만 지원해 정확한 "매월 N일" 보장은 불가하나, 관세청/ECOS 조회가 이미 전월(lag) 데이터를 쓰므로(Phase 2) ±수일 드리프트는 스코어링에 영향 없음 — KDoc에 근거 명시
  - `core/worker/CollectionNotificationHelper.kt` — `BEAR_SIGNAL_NOTIFICATION_ID = 1016` 추가(기존 1001~1015와 겹치지 않는 다음 번호)
  - `TinyOscillatorApp.kt` — `onCreate()`에 `WorkManagerHelper.scheduleBearSignalUpdate(this)` 추가(Macro/Regime/MetaLearner와 동일하게 사용자 토글 없이 항상 복원 — 기존 관례 준수)
  - **캐시 우선 렌더**: `ObserveBearSignalStateUseCase`가 Room 4-Flow를 구독하는 기존 구조 자체가 이미 "워커가 백그라운드에서 갱신 → 화면은 Room 변경을 자동 반영"을 보장하므로 Phase5에서 화면 쪽 추가 배선은 불필요(그대로 재사용 확인)
- **shimmer 로딩**
  - `feature/bearsignal/presentation/BearSignalViewModel.kt` — `BearSignalUiState.isLoading: Boolean = true`(기존 shimmer 관례와 동일하게 "Room 캐시 최초 방출 전"만 true). `stateIn`의 초기값(`BearSignalUiState()`)에서만 true, `combine(...)` 람다는 Room 4-Flow가 최소 한 번 합성된 뒤에만 실행되므로 그 시점부터 항상 `isLoading = false`로 고정 — 별도 플래그 오케스트레이션 없이 "초기값 vs 합성값" 차이만으로 로딩 상태를 표현
  - `presentation/common/skeleton/ScreenSkeletons.kt` — `BearSignalScreenSkeleton` 신규(기존 `ShimmerBox`/`ShimmerLine` 그대로 재사용, §5.2 7섹션 구조를 헤더/3카드/표/방아쇠·증폭/유형·역사 6블록으로 축약)
  - `feature/bearsignal/presentation/ui/BearSignalScreen.kt` — `uiState.isLoading`이면 `Scaffold` 컨텐츠를 스켈레톤으로 조기 반환(`return@Scaffold`), TopAppBar/BottomSheet/리셋 다이얼로그는 그대로 유지
- **오프라인 폴백**
  - `BearSignalViewModel.refresh()` — `NetworkUtils.isNetworkAvailable(context)`를 API 호출 전에 확인(기존 `OscillatorViewModel`/`QuickAnalysisViewModel` 관례 재사용). 오프라인이면 3개 UseCase를 아예 호출하지 않고 `isOffline=true`만 설정 — 화면은 이미 Room 캐시로 렌더돼 있으므로(§1.2 하이브리드 아키텍처) 재시도할 필요가 없다
  - `BearSignalScreen.kt` — `uiState.isOffline`이면 `LazyColumn` 최상단에 기존 `core/ui/composable/UiStateContent.kt`의 `StaleBanner`(문구 "오프라인 · 마지막 저장 데이터를 표시 중입니다" + 새로고침 버튼)를 노출. 캐시 데이터(`inputs`/`result`)와 "전체 최신 갱신일"(`BearSignalFooterSection`의 `LastUpdatedText`, Phase4에서 이미 구현)은 그대로 유지되므로 요구사항("캐시 데이터 렌더 + 최신 갱신일 표기") 충족
  - `@ApplicationContext Context` 생성자 주입 추가(`EtfViewModel` 등 기존 패턴과 동일) — Hilt가 자동 주입, JVM 테스트는 `mockk<Context>(relaxed = true)` + `mockkObject(NetworkUtils)`로 검증(기존 `QuickAnalysisViewModelTest` 패턴 재사용)
- **접근성 최종 점검**
  - `BearSignalLeadingSignalsSection.kt` — `SignalCard`에 선택적 `contentDescription` 파라미터 추가, 신호1/2/3 카드에 레벨+핵심수치 요약 부여(Phase4는 헤더 카드에만 적용돼 있었음)
  - `BearSignalGateAmpSection.kt` — 금리 방아쇠·증폭 계수 카드에 각각 `Modifier.semantics { contentDescription }` 추가
  - `BearSignalCountryTableSection.kt` — `CountryRow`(이미 `.heightIn(min = 48.dp)` 확보돼 있던 터치 타깃)에 국가명+4기간 값 요약 `contentDescription` 추가
  - **점검 결과(기존 구현 재확인, 변경 불요)**: `TextButton`/`IconButton`/`FilterChip`/`Checkbox` 등 Material3 표준 컴포넌트는 라이브러리 차원에서 최소 48dp 터치 타깃을 강제하므로 추가 조치 불요. 모든 LEVEL 색상은 `SignalLevel.label`/`GateState.label` 텍스트와 항상 병기(Phase4부터 일관). Canvas 커스텀 그래픽(`BearSignalGraphics.kt`)의 라벨은 `sp` 단위 사용으로 시스템 폰트 스케일에 자동 반응 — 다만 **360dp 실기 렌더·다크모드 대비·폰트 1.3x 실측 검증은 이번 세션(Bash/Gradle 전용) 범위 밖**으로 미검증 상태 유지(Phase4와 동일한 한계)
- **테스트**: `BearSignalViewModelTest` +3(오프라인 시 UseCase 미호출·캐시 데이터 유지·네트워크 복구 후 재갱신, 초기/Room방출 테스트에 isLoading/isOffline 단언 추가) — `feature.bearsignal` 패키지 총 226건. `core/worker/BearSignalUpdateWorkerTest.kt` 신규 6건(companion 상수, 알림ID 유일성, `scheduleBearSignalUpdate` 입력 검증 4종 — `dayOfMonth`/`hour`/`minute` 경계). `doWork()` 자체는 `BaseCollectionWorker.setForeground()`가 실제 WorkManager/Android 런타임을 요구해 JVM 테스트 불가 — 기존 `MacroUpdateWorker`/`ThemeUpdateWorker`도 동일한 이유로 `doWork()` 단위테스트가 없는 것과 동일한 구조적 한계(KDoc·PROGRESS에 명시).
- **빌드**: `:app:compileDebugKotlin`/`:app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"`(226건, 0 실패)/`:app:testDebugUnitTest --tests "com.tinyoscillator.core.worker.*"`(0 실패)/`:app:assembleDebug` 전부 BUILD SUCCESSFUL.
- **미검증(가능 범위 밖, Phase4와 동일한 한계 승계)**: 실기/에뮬레이터에서 shimmer 애니메이션·오프라인 배너·다크모드 대비·폰트 스케일 1.3x 렌더링 확인, WorkManager 월간 트리거의 실제 발화(달력월 30일 스케줄이므로 단시간 세션에서 실행 확인 불가 — `runBearSignalUpdateNow()` 수동 트리거 경로는 존재하나 실기 실행은 미검증), 관세청 API 실키 검증.

## 실기 QA 1차 (2026-07-10, 에뮬레이터 pixel_fold · 라이트 테마)

- **결과: 통과 (크래시 0, 앱 레벨 에러 로그 0)** — `assembleDebug` APK 설치 후 검증.
- **렌더**: `BearSignalScreen` 7섹션 전부 정상 — 헤더(신호등 "신호 점등·방아쇠 대기"·선행점수 33/100 게이지·금리 방아쇠 "경계 접근"·집중 증폭 ×1.30·4축 레이더 Canvas), 선행신호 3카드(전부 주의 + 세그먼트 게이지), 국가별 수익률 표 20행(기간 FilterChip·이탈 지수 11/20 배지·"수동 필요" 13지수 배지·양/음수 색 구분), 방아쇠·증폭 카드(기준값 배지), 3유형 카드(유형3 활성 방아쇠 강조 테두리·체크리스트), 역사검증(일본 1980s), 매핑·면책 푸터.
- **진입점**: 시장분석 탭 `BearSignalEntryCard` 실시간 상태 미리보기("주의" 배지) → 진입 → back 왕복 정상.
- **수동입력 BottomSheet**: 슬라이더 드래그 중 실시간 반영 확인(logcat `updateManualInput` 5회 연속 발화, 45.2→67.7%) → "리포트 기준값으로 리셋" → `resetToReportBaseline` 로그 + 재오픈 시 45.0% 복원 확인.
- **월간 워커(ecf4682 수정 실기 검증)**: 앱 시작 로그 `BearSignal 지표 월간 업데이트 스케줄 등록: 매월 5일 06:00 (초기 딜레이: 37571분, policy=KEEP)` — 37571분 ≈ 26.1일 = 정확히 다음 달 5일 06:00. flex 제거 수정이 실기에서 의도대로 동작.
- **새로고침 폴백**: KRX [A] 지표 수집 성공(up3/down3/kospi2 산출), [B] 외부 지표 전부 null(관세청/FRED 키 미설정 — 기준값 폴백 정상), 해외지수 수집 실패 시 "기존 캐시 유지" 로그 + UI 값 유지.
- **MINOR 재현**: 체크리스트 체크 → back → 재진입 시 소실 (`remember`→`rememberSaveable` 기지 이슈, `TypesHistorySection`).
- **신규 발견(외부 요인, 앱 버그 아님)**: **Stooq 안티봇 차단** — `stooq.com/q/d/l/` CSV 엔드포인트가 JS SHA-256 proof-of-work 챌린지 HTML을 HTTP 200으로 반환(호스트 curl 재현 동일). `StooqCsvClient.parseCsv`가 CSV 헤더 불일치로 0건 → AUTO 해외지수 6종(닛케이/다우/S&P/DAX/나스닥/항생) + IPO ETF(`ipo.us`) 자동수집 불가. 앱은 설계된 폴백(캐시/리포트 기준값 유지 + 수동입력 경로)으로 무해 강등. **대응 결정 필요**: 대체 무료 소스(예: Yahoo Finance chart API) 교체 vs 해당 7지표 MANUAL_REQUIRED 강등. → **해소(2026-07-10)**: Yahoo Finance chart API 기본 소스 채택 + Stooq 백업 강등, 설정에서 소스 선택 가능 — 하단 「시세 소스 교체」 절 참조.
- **미검증(잔여 QA)**: 360dp 폰 폭·다크 테마·폰트스케일 1.3x 렌더, 월간 워커 실제 발화(`runBearSignalUpdateNow()` 수동 트리거 포함), 관세청/FRED 실키 응답 필드 검증, 스펙 조정 2건 판단(P4 절 참조).

## 시세 소스 교체 — Stooq → Yahoo 기본 + 멀티소스 폴백 (2026-07-10)

- **배경**: 실기 QA 1차에서 Stooq 안티봇(JS PoW) 차단 확인 → AUTO 해외지수 6종 + IPO ETF 자동수집 전멸(위 절 참조).
- **백업 소스 검토 결과**: **Yahoo Finance chart API 채택(기본)** — `query1.finance.yahoo.com/v8/finance/chart/{symbol}?range=2y&interval=1d`, 무인증 JSON, 필요 7개 티커(`^N225`/`^DJI`/`^GSPC`/`^GDAXI`/`^IXIC`/`^HSI`/`IPO`) 전부 실응답 검증(2y ≈ 501봉 ≥ 12M 수익률 요구 253봉). **Stooq는 백업으로 유지**(무인증·차단 해제 가능성). 탈락: FRED(S&P/다우/나스닥만 — DAX·항생·IPO ETF 불가), Alpha Vantage(무료 지수 미지원), Twelve Data(지수 유료 플랜), FMP(무료 제한), 스크래핑(취약).
- **구현**:
  - `domain/model/GlobalIndexSource.kt` 신규 — `YAHOO`(기본)/`STOOQ` enum, 검토 근거 KDoc.
  - `data/remote/YahooChartApiClient.kt` 신규 — chart JSON 파싱(`timestamp`+`quote[0].close`, null 종가 스킵, error 응답 방어), 브라우저 UA 명시(기본 okhttp UA 간헐 차단), 1000ms rate limit. `data/remote/IndexDailyBar.kt` 공용 모델(기존 `StooqDailyBar` 대체, `StooqCsvClient`도 공용화).
  - `GlobalIndexRegistry` — `GlobalIndexSpec`에 소스별 티커(`yahooTicker`/`stooqTicker`) + `tickerFor(source)`/`autoCovered`. AUTO 6지수는 두 소스 모두 티커 보유(커버리지 변화 없음, MANUAL 13지수 유지).
  - `BearSignalRepositoryImpl` — `fetchDailyClosesWithFallback()`: 사용자 선택 소스 우선 조회, 실패(빈 응답·예외) 시 나머지 소스 자동 폴백. IPO ETF 티커 소스별 맵(`IPO`/`ipo.us`). `indexSourceProvider` 주입(설정 즉시 반영 위해 매 갱신 시 prefs 재조회).
  - 설정 — `PrefsKeys.BEAR_SIGNAL_INDEX_SOURCE`(EncryptedSharedPreferences, 기존 관례) + API 탭 「해외지수 시세 소스 (계기판)」 드롭다운(Yahoo Finance/Stooq, 자동 폴백 안내 캡션).
- **테스트**: `YahooChartApiClientTest` 신규 7건(fixture 파싱·null 종가·error 응답·비JSON·길이 불일치), `GlobalIndexSourceTest` 3건, `GlobalIndexRegistryTest` 7건(소스별 티커 검증으로 갱신), `BearSignalRepositoryImplTest` 33건(Yahoo 우선·Stooq 폴백·양소스 실패·STOOQ 선택 시 역순 검증 추가). `feature.bearsignal` 전체 0 실패, `compileDebugKotlin` 통과.
- **실검증**: 호스트 curl로 7개 티커 전부 정상 JSON 응답 + `^DJI` 2y 501봉 확인(2026-07-10). 에뮬레이터 실기(설정 UI·자동수집 라이브)는 잔여 QA에 병합.

## 점검 이력 (2026-07-09)
- kotlin-implementer 셀프리뷰(qa 점검) 결과: 스코어링 5개 함수(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite) 전부 프로토타입 `bear_signal_dashboard.jsx`(작업 디렉터리 루트에서 재확보, git 미추적)와 문자 단위 일치 확인.
- **수정**: "도표48 전체 시드 미이관(18행 결손)" 편차 해소 — 확보된 `bear_signal_dashboard.jsx`의 `MARKETS` 상수(20지수) 전체를 `BearSignalReportBaseline.MARKETS`로 이관. 골든 케이스 테스트를 합성 픽스처 대신 실데이터로 교체(neg=11, worstNew=-5.1(나스닥), depth=SHALLOW → s1=1 재검증). 시드 검증 테스트 확장(20행 카운트 + 6개 지수 스팟체크).
- 재실행: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.*"` → 41 tests, 0 failures.
