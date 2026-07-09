# PROGRESS — BearSignal 이식 진행 기록

> 근거: `TASK.md` (「주도주 붕괴 판단 계기판」 이식 명세서 v1.0). 각 Phase 완료 시 `PROGRESS:` 마커 갱신.

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

## 점검 이력 (2026-07-09)
- kotlin-implementer 셀프리뷰(qa 점검) 결과: 스코어링 5개 함수(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite) 전부 프로토타입 `bear_signal_dashboard.jsx`(작업 디렉터리 루트에서 재확보, git 미추적)와 문자 단위 일치 확인.
- **수정**: "도표48 전체 시드 미이관(18행 결손)" 편차 해소 — 확보된 `bear_signal_dashboard.jsx`의 `MARKETS` 상수(20지수) 전체를 `BearSignalReportBaseline.MARKETS`로 이관. 골든 케이스 테스트를 합성 픽스처 대신 실데이터로 교체(neg=11, worstNew=-5.1(나스닥), depth=SHALLOW → s1=1 재검증). 시드 검증 테스트 확장(20행 카운트 + 6개 지수 스팟체크).
- 재실행: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.*"` → 41 tests, 0 failures.
