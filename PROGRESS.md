# PROGRESS — BearSignal 이식 진행 기록

> 근거: `TASK.md` (「주도주 붕괴 판단 계기판」 이식 명세서 v1.0). 각 Phase 완료 시 `PROGRESS:` 마커 갱신.

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

## 점검 이력 (2026-07-09)
- kotlin-implementer 셀프리뷰(qa 점검) 결과: 스코어링 5개 함수(analyzeMarkets·scoreS1~S3·scoreGate·amplifier·composite) 전부 프로토타입 `bear_signal_dashboard.jsx`(작업 디렉터리 루트에서 재확보, git 미추적)와 문자 단위 일치 확인.
- **수정**: "도표48 전체 시드 미이관(18행 결손)" 편차 해소 — 확보된 `bear_signal_dashboard.jsx`의 `MARKETS` 상수(20지수) 전체를 `BearSignalReportBaseline.MARKETS`로 이관. 골든 케이스 테스트를 합성 픽스처 대신 실데이터로 교체(neg=11, worstNew=-5.1(나스닥), depth=SHALLOW → s1=1 재검증). 시드 검증 테스트 확장(20행 카운트 + 6개 지수 스팟체크).
- 재실행: `:app:testDebugUnitTest --tests "com.tinyoscillator.feature.*"` → 41 tests, 0 failures.
