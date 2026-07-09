# PROGRESS — BearSignal 이식 진행 기록

> 근거: `TASK.md` (「주도주 붕괴 판단 계기판」 이식 명세서 v1.0). 각 Phase 완료 시 `PROGRESS:` 마커 갱신.

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

## 점검 이력 (2026-07-09)
- kotlin-implementer 셀프리뷰(qa 점검) 결과: 스코어링 5개 함수(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite) 전부 프로토타입 `bear_signal_dashboard.jsx`(작업 디렉터리 루트에서 재확보, git 미추적)와 문자 단위 일치 확인.
- **수정**: "도표48 전체 시드 미이관(18행 결손)" 편차 해소 — 확보된 `bear_signal_dashboard.jsx`의 `MARKETS` 상수(20지수) 전체를 `BearSignalReportBaseline.MARKETS`로 이관. 골든 케이스 테스트를 합성 픽스처 대신 실데이터로 교체(neg=11, worstNew=-5.1(나스닥), depth=SHALLOW → s1=1 재검증). 시드 검증 테스트 확장(20행 카운트 + 6개 지수 스팟체크).
- 재실행: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.*"` → 41 tests, 0 failures.
