# TASK: 주체별 매물대 (Investor Volume Profile)

- **대상 앱**: TinyOscillator (github.com/gmdjlee/TinyOscillator)
- **모듈 경로(제안)**: `feature/volumeprofile`
- **언어/스택**: Kotlin, Jetpack Compose, MVVM + Clean Architecture, Hilt, Room, Coroutines
- **연산 구현 언어**: 순수 Kotlin (Chaquopy 미사용)
- **품질 목표**: AAA — 아래 §10 완료 판정 기준을 전부 충족해야 완료로 간주한다.
- **문서 버전**: v1.0

---

## 0. 선행 확인 사항 (Phase 0에서 반드시 먼저 수행)

본 명세서는 저장소 코드를 직접 열람하지 않은 상태에서 작성되었다. 따라서 아래 항목은 구현 착수 전에 실제 코드베이스에서 확인하고, 확인 결과를 `PROGRESS.md`에 기록한 뒤 진행한다. 기존 자산이 있으면 신규 작성 대신 재사용한다.

| 확인 항목 | 확인 내용 | 재사용 시 조치 |
|---|---|---|
| KIS API 클라이언트 | 국내주식 시세·시세분석 TR 호출 래퍼가 존재하는지 | 신규 TR 두 건만 추가 |
| Room 데이터베이스 | 일봉 OHLCV 캐시 테이블이 이미 있는지 | 투자자 테이블만 신규 추가 |
| 호가 단위 유틸 | `TickSizeProvider` 같은 유틸이 있는지 | 없으면 §4.3대로 신규 작성 |
| 차트 컴포넌트 | 기존 캔들 차트가 Compose Canvas 기반인지, 가격 축 스케일을 외부에 노출하는지 | 노출되지 않으면 축 스케일 추출 리팩터링을 Phase 0에 포함 |
| 디자인 토큰 | 색상·타이포·간격 토큰의 정의 위치와 명명 규칙 | 신규 색상 정의 금지, 기존 토큰만 사용 |
| 기존 시리즈 색상 | ParkSignalOverlay 등에서 이미 쓰는 범주형 색상 순서 | 동일 순서를 따라 주체 색상 배정 |

**중요**: 위 표의 결과가 본 명세서의 가정과 다르면, 구현을 진행하지 말고 차이점을 보고한 뒤 지시를 기다린다.

---

## 1. 기능 개요

특정 종목에 대하여 개인·외국인·기관이 어느 가격대에서 매수했는지를 추정하여, 가격 구간별 매물대를 가로 막대 차트로 표시한다. 최종 목적은 **현재가 기준으로 상방과 하방 중 어디에 매물이 집중되어 있는지를 판단**하는 것이다.

### 1.1 사용자가 조작할 수 있는 항목

| 컨트롤 | 값 | 기본값 |
|---|---|---|
| 주체 선택 | 개인 / 외국인 / 기관 (복수 선택, 최소 1개) | 전체 3개 |
| 표시 방식 | 누적(stacked) / 분리(grouped) | 분리 |
| 산출 기준 | 매수 누적 / 잔존 추정 | 기간 길이에 따라 자동 (§4.5) |
| 기간 | 전체 / 1년 / 6개월 / 3개월 / 1개월 / 사용자 지정 / 직전 스윙 고점 이후 / 직전 스윙 저점 이후 | 6개월 |
| 봉 단위 | 일봉 / 주봉 | 일봉 |
| 구간 수 | 10 / 15 / 20 / 25 | 20 |

### 1.2 명시적 비목표

- 분봉 데이터를 사용하지 않는다. 일봉 해상도에서 구간 폭이 일간 변동폭보다 크거나 비슷하므로 정확도 이득이 없고, 호출량만 수백 배로 늘어난다.
- 실시간 갱신을 하지 않는다. 투자자 매매동향은 장 마감 후 확정되는 일별 데이터이므로 하루 1회 갱신으로 충분하다.
- 이 값은 추정치이며 거래소가 발표하는 실측 매물대가 아니다. 화면에 반드시 그 취지를 표기한다.

---

## 2. 아키텍처

```
feature/volumeprofile/
├─ data/
│  ├─ remote/
│  │   ├─ InvestorFlowApi.kt          (TR: FHPTJ04160001)
│  │   ├─ DailyChartApi.kt            (TR: FHKST03010100)
│  │   └─ dto/
│  ├─ local/
│  │   ├─ InvestorFlowDao.kt
│  │   ├─ InvestorFlowEntity.kt
│  │   └─ DailyOhlcvEntity.kt         (기존 존재 시 재사용)
│  └─ VolumeProfileRepositoryImpl.kt
├─ domain/
│  ├─ model/                          (§3)
│  ├─ engine/
│  │   ├─ AllocationKernel.kt
│  │   ├─ PriceBinner.kt
│  │   ├─ SwingDetector.kt
│  │   ├─ DecayModel.kt
│  │   └─ VolumeProfileEngine.kt
│  ├─ VolumeProfileRepository.kt      (인터페이스)
│  └─ usecase/GetVolumeProfileUseCase.kt
└─ presentation/
   ├─ VolumeProfileViewModel.kt
   ├─ VolumeProfileUiState.kt
   ├─ VolumeProfileScreen.kt
   └─ components/
       ├─ VolumeProfileChart.kt
       ├─ InvestorFilterChips.kt
       └─ ProfileControlPanel.kt
```

의존 방향은 `presentation → domain ← data` 를 지킨다. `domain/engine` 은 Android 의존성이 전혀 없는 순수 Kotlin이어야 하며, JVM 단위 테스트만으로 전부 검증 가능해야 한다.

---

## 3. 도메인 모델

```kotlin
enum class InvestorType { INDIVIDUAL, FOREIGN, INSTITUTION }

enum class ProfileBasis { CUMULATIVE_BUY, RESIDUAL }   // 매수 누적 / 잔존 추정
enum class DisplayMode { STACKED, GROUPED }
enum class BarInterval { DAILY, WEEKLY }

sealed interface PeriodSpec {
    data object All : PeriodSpec
    data class LastMonths(val months: Int) : PeriodSpec
    data class Custom(val start: LocalDate, val end: LocalDate) : PeriodSpec
    data class SinceSwingHigh(val k: Int = 5) : PeriodSpec
    data class SinceSwingLow(val k: Int = 5) : PeriodSpec
}

/** 하루치 원천 데이터. 가격은 전부 수정주가 미반영 원주가. */
data class DailyFlow(
    val date: LocalDate,
    val open: Long, val high: Long, val low: Long, val close: Long,
    val volume: Long,
    val flows: Map<InvestorType, InvestorLeg>
)

/** 한 주체의 하루치 매수·매도. amount 단위는 원. */
data class InvestorLeg(
    val buyVolume: Long,  val buyAmount: Long,
    val sellVolume: Long, val sellAmount: Long
) {
    val buyVwap: Double  get() = if (buyVolume  > 0) buyAmount.toDouble()  / buyVolume  else 0.0
    val sellVwap: Double get() = if (sellVolume > 0) sellAmount.toDouble() / sellVolume else 0.0
}

data class PriceBin(val lower: Long, val upper: Long) {
    val center: Double get() = (lower + upper) / 2.0
}

data class VolumeProfile(
    val bins: List<PriceBin>,
    val byInvestor: Map<InvestorType, DoubleArray>,   // bins 와 같은 길이
    val totalVolume: DoubleArray,                     // 전체 거래량 기준 (주체 무관)
    val currentPrice: Long,
    val basis: ProfileBasis,
    val window: DateRange,
    val metrics: ProfileMetrics
)

data class ProfileMetrics(
    val poc: Long,                      // Point of Control
    val valueAreaHigh: Long,            // 상위 70% 밸류 에어리어
    val valueAreaLow: Long,
    val upperSupply: Map<InvestorType, Double>,   // 현재가 초과 구간 합계
    val lowerSupply: Map<InvestorType, Double>,   // 현재가 이하 구간 합계
    val topResistanceBins: List<Int>,   // 상방 상위 3구간 인덱스
    val topSupportBins: List<Int>,
    val lowVolumeNodes: List<Int>,      // 매물 공백 구간
    val absorptionDays: Double          // 상방 총물량 / 일평균 거래량
)
```

**금지 사항**: `byInvestor` 값에 음수를 담아 UI로 내보내지 않는다. `CUMULATIVE_BUY` 는 정의상 비음수이고, `RESIDUAL` 도 상각 후 비음수여야 한다. 순매수 기반의 부호 있는 값이 필요하면 별도 필드로 분리하고 이름에 `net` 을 넣는다. 부호 있는 값과 없는 값을 같은 필드에 섞으면 누적 막대가 무의미해진다.

---

## 4. 산출 알고리즘 (핵심)

### 4.1 주체별 일중 평균단가

각 주체의 매수·매도 거래대금을 거래량으로 나누어 그날의 평균 체결가를 구한다. 이 값이 배분의 중심을 고정하는 유일한 관측 앵커이므로, 다른 값으로 대체해서는 안 된다.

검증: 산출된 평균단가는 반드시 `[low, high]` 범위 안에 들어야 한다. 벗어나면 데이터 오류이므로 해당 일자를 로그와 함께 건너뛰고 `PROGRESS.md` 에 기록한다.

### 4.2 배분 커널

```
w_i ∝ exp(-0.5 * ((c_i - vwap) / σ)²),   σ = max((high - low) / 4, binWidth / 4)
[low - binWidth, high + binWidth] 밖의 구간은 0으로 절단한 뒤 합이 1이 되도록 정규화
배분량 = 거래량 × w
```

절단정규 커널을 기본값으로 하되 `AllocationKernel` 인터페이스로 추상화하여 균등·삼각 구현도 교체 가능하게 한다. 구간 폭이 일간 변동폭보다 큰 상황에서는 커널 종류가 결과에 거의 영향을 주지 않으므로, 튜닝에 시간을 쓰지 않는다.

**불변식 1 (질량 보존)**: 배분 결과의 합은 원 거래량과 상대오차 1e-9 이내로 일치한다.
**불변식 2 (1차 모멘트)**: 배분 결과의 가중평균 가격과 평균단가의 차이는 구간 폭의 절반 이하이다.

### 4.3 가격 구간 분할

구간 개수를 고정하면 기간이 바뀔 때마다 구간 폭이 달라져 기간 간 비교가 불가능해진다. 따라서 다음 순서를 지킨다.

1. 대상 기간의 최저가와 최고가로 가격 범위를 구한다.
2. `rawWidth = range / binCount` 를 계산한다.
3. 해당 가격대의 호가 단위로 올림하여 `binWidth` 를 확정한다.
4. 최저가를 `binWidth` 배수로 내림한 값을 첫 구간 하한으로 삼는다.
5. 확정된 `binWidth` 를 UI에 항상 표기한다.

호가 단위는 시장(KOSPI/KOSDAQ)과 가격대에 따라 다르며 규정 개정 이력이 있으므로, 반드시 단일 상수 파일 `TickSize.kt` 에 표로 정의하고 KRX 현행 규정과 대조한 근거 주석을 남긴다. 값을 코드 여러 곳에 흩어 놓지 않는다.

### 4.4 일중 회전 보정 (선택 기능, 기본 비활성)

같은 날 사고판 물량이 그대로 매물대에 적재되면 개인 구간이 과대 추정된다. 보정 계수 `α` 를 두고 매수·매도 양쪽에서 `α × min(buyVolume, sellVolume)` 를 차감한 뒤 배분한다. 기본값은 `α = 0.0` 이며 설정에서만 변경 가능하다. 기본값을 0이 아닌 값으로 두면 사용자가 인지하지 못한 채 결과가 바뀌므로 금지한다.

### 4.5 상각 모델과 기간 길이의 상호작용

이 부분이 본 기능에서 가장 오해하기 쉬운 지점이므로 정확히 구현한다.

**RESIDUAL (비례상각)**: 각 주체에 대해 보유 분포 벡터 `H` 를 유지한다. 매일 매수 배분을 더한 뒤, 그날 매도량만큼을 `H` 전체에서 비례 차감한다. `H` 잔량보다 매도량이 크면 잔량까지만 차감하고 초과분은 `residualSell` 로 따로 누적한다. 이 초과분은 "관측 기간 이전부터 보유하던 물량의 매도"를 뜻한다.

**CUMULATIVE_BUY (매수 누적)**: 상각 없이 매수 배분만 누적한다.

**자동 선택 규칙**: 대상 기간이 60거래일 미만이면 `CUMULATIVE_BUY`, 60거래일 이상이면 `RESIDUAL` 을 기본값으로 한다. 사용자가 수동으로 변경할 수 있으며, 어느 기준인지 화면에 항상 표기한다.

**근거(반드시 유지할 것)**: 회전율이 높은 외국인과 기관은 짧은 구간에서 비례상각을 적용하면 잔존 물량이 0으로 소거된다. 실제로 삼성전자 2026-08-18 이후 14거래일 기준으로 외국인 잔존이 0만주로 산출되었다. 짧은 구간에 비례상각을 강제하면 차트가 비어 보이는 버그로 오인된다.

### 4.6 주봉 처리

주체별 원천 데이터는 일별로만 제공된다. **배분과 상각은 반드시 일봉 단위로 수행하고, 주봉은 캔들 표시와 구간 폭 산정에만 사용한다.** 일별 매수·매도를 주 단위로 먼저 합산한 뒤 배분하면 주중 회전이 상계되어 외국인·기관 잔존이 소거되는 것이 실측으로 확인되었다. 이 순서를 뒤집는 구현은 리뷰에서 반려한다.

### 4.7 스윙 전환점 탐지

`SwingDetector` 인터페이스에 두 구현을 둔다.

- `FractalSwingDetector(k)`: 좌우 k봉 중 최고가/최저가인 봉을 전환점으로 판정. 일봉 k=5, 주봉 k=3 기본값.
- `ZigZagSwingDetector(thresholdPct)`: 변동률 임계 방식. 종목 간 비교 시 파라미터가 하나뿐이라 유리하다.

기본 구현은 프랙탈로 하고, 전환점을 찾지 못하면 전체 기간으로 폴백하며 그 사실을 UI에 알린다.

### 4.8 수정주가 정합

투자자 매매동향 API가 반환하는 가격은 수정주가 미반영이다. 시세 API는 반영 여부를 선택할 수 있다. **배분 계산은 원주가로 수행하고, 표시 단계에서만 조정 배율을 적용한다.** 순서를 뒤집으면 액면분할 이전 구간의 매물대가 전부 잘못된 가격대에 쌓인다. 조정 배율은 `DailyChartApi` 를 `fid_org_adj_prc = 0` 과 `1` 로 각각 호출하여 종가 비율로 산출하거나, 앱에 기존 수정주가 처리 로직이 있으면 그것을 재사용한다.

---

## 5. 데이터 계층

### 5.1 원격 API

| 용도 | TR ID | 비고 |
|---|---|---|
| 종목별 투자자매매동향(일별) | `FHPTJ04160001` | 1회 호출당 30거래일 반환. `fid_input_date_1` 기준 **과거 방향**으로 조회된다. |
| 국내주식기간별시세(일/주/월/년) | `FHKST03010100` | `fid_period_div_code` 로 D/W 전환, `fid_org_adj_prc` 로 수정주가 제어 |

**페이징**: 반환된 가장 오래된 일자에서 하루를 뺀 값을 다음 `fid_input_date_1` 로 넘긴다. 필요 호출 횟수는 1년 약 9회, 3년 약 25회, 5년 약 42회이다. 동시 실행은 4로 제한하고 KIS 호출 한도 정책을 준수한다.

**사용할 응답 필드** (주체별 매수량·매수대금·매도량·매도대금):

| 주체 | 매수량 | 매수대금 | 매도량 | 매도대금 |
|---|---|---|---|---|
| 개인 | `prsn_shnu_vol` | `prsn_shnu_tr_pbmn` | `prsn_seln_vol` | `prsn_seln_tr_pbmn` |
| 외국인 | `frgn_shnu_vol` | `frgn_shnu_tr_pbmn` | `frgn_seln_vol` | `frgn_seln_tr_pbmn` |
| 기관 | `orgn_shnu_vol` | `orgn_shnu_tr_pbmn` | `orgn_seln_vol` | `orgn_seln_tr_pbmn` |

거래대금 필드 단위는 **백만원**이다. 원 단위로 변환할 때 `Int` 오버플로가 발생하므로 파싱 직후 `Long` 으로 승격한다. 이 지점은 실제로 값이 조용히 깨지는 자리이므로 단위 변환 테스트를 반드시 둔다.

OHLC 필드는 같은 응답의 `stck_oprc`, `stck_hgpr`, `stck_lwpr`, `stck_clpr`, `acml_vol` 을 사용하면 시세 API 호출 없이도 일봉을 확보할 수 있다. 다만 수정주가 미반영이므로 §4.8 을 따른다.

### 5.2 로컬 캐시

```kotlin
@Entity(tableName = "investor_flow", primaryKeys = ["ticker", "date"])
data class InvestorFlowEntity(
    val ticker: String, val date: String,
    val open: Long, val high: Long, val low: Long, val close: Long, val volume: Long,
    val prsnBuyVol: Long, val prsnBuyAmt: Long, val prsnSellVol: Long, val prsnSellAmt: Long,
    val frgnBuyVol: Long, val frgnBuyAmt: Long, val frgnSellVol: Long, val frgnSellAmt: Long,
    val orgnBuyVol: Long, val orgnBuyAmt: Long, val orgnSellVol: Long, val orgnSellAmt: Long,
    val fetchedAt: Long
)
```

정책: 캐시된 최신 일자 이후 구간만 증분 조회한다. 과거 확정 데이터는 재조회하지 않는다. 액면분할 등으로 가격 기준이 바뀐 종목은 해당 종목 캐시를 전량 무효화한다.

---

## 6. 표현 계층

### 6.1 상태 정의

```kotlin
data class VolumeProfileQuery(
    val ticker: String,
    val selectedInvestors: Set<InvestorType> = InvestorType.entries.toSet(),
    val displayMode: DisplayMode = DisplayMode.GROUPED,
    val basis: ProfileBasis? = null,        // null 이면 기간 길이로 자동 결정
    val period: PeriodSpec = PeriodSpec.LastMonths(6),
    val interval: BarInterval = BarInterval.DAILY,
    val binCount: Int = 20
)

sealed interface VolumeProfileUiState {
    data object Loading : VolumeProfileUiState
    data class Success(
        val profile: VolumeProfile,
        val query: VolumeProfileQuery,
        val binWidth: Long,
        val autoBasisApplied: Boolean
    ) : VolumeProfileUiState
    data class Empty(val reason: EmptyReason) : VolumeProfileUiState
    data class Error(val message: UiText, val retryable: Boolean) : VolumeProfileUiState
}
```

`ViewModel` 은 `MutableStateFlow<VolumeProfileQuery>` 를 보유하고, 질의 변경을 200ms 디바운스한 뒤 `flatMapLatest` 로 재계산한다. 연산은 `Dispatchers.Default` 에서 수행하며 이전 계산은 취소한다. 원천 데이터 재조회는 기간이 캐시 범위를 벗어날 때만 발생시키고, 주체 선택이나 표시 방식 변경은 네트워크를 타지 않아야 한다.

### 6.2 주체 선택 UI

`InvestorFilterChips` 는 Material 3 `FilterChip` 3개를 가로로 배치한다.

- 복수 선택이며 선택된 칩은 해당 주체의 시리즈 색상을 선택 표시에 반영한다.
- **최소 1개는 항상 선택되어 있어야 한다.** 마지막 남은 칩을 해제하려 하면 해제하지 않고, 짧은 안내를 표시한다. 빈 차트를 보여 주는 대신 조작을 막는 편이 낫다.
- 각 칩에 해당 주체의 상방 물량 합계를 보조 텍스트로 표시하면, 칩을 켜 보기 전에도 비중을 가늠할 수 있다.
- `contentDescription` 은 "개인, 선택됨, 상방 1624만주" 형태로 상태와 값을 함께 읽어 준다.

주체 선택은 **표시 필터일 뿐 재계산 대상이 아니다.** 세 주체를 모두 계산해 두고 렌더링 단계에서 걸러야, 칩을 껐다 켤 때 즉시 반응한다. 선택된 주체만 계산하도록 구현하면 매번 재계산이 발생하므로 반려한다.

### 6.3 차트 렌더링

`VolumeProfileChart` 는 Compose `Canvas` 로 직접 그린다.

- 세로축이 가격, 가로축이 물량이다. 위쪽이 고가, 아래쪽이 저가이다.
- 막대는 좌측 기준선에서 우측으로 뻗는다.
- `GROUPED`: 한 구간 안에서 선택된 주체 수만큼 막대를 나란히 배치한다. 막대 높이는 `구간높이 × 0.82 / 주체수`.
- `STACKED`: 한 구간에 하나의 막대를 그리고 주체별로 이어 붙인다. 세그먼트 사이에 2dp의 배경색 간격을 둔다.
- 현재가 위치에 수평 점선을 그리고 우측 끝에 현재가 라벨을 붙인다. 현재가 위쪽 영역에는 아주 옅은 배경 틴트를 깔아 저항 영역임을 구분한다.
- POC 구간은 테두리를 강조하고, 밸류 에어리어 범위는 좌측 얇은 띠로 표시한다.
- 캔들 차트와 나란히 배치할 때는 **가격 축 스케일을 공유**한다. 두 컴포넌트가 각자 스케일을 계산하면 눈금이 어긋난다.

성능: 구간 수가 최대 25이므로 그리기 부하는 작다. 다만 `Canvas` 안에서 객체를 새로 만들지 말고, `remember` 로 `Path`·`Paint` 를 재사용한다.

### 6.4 디자인 규칙 준수

- 색상·타이포·간격·모서리 반경은 전부 기존 디자인 토큰에서 가져온다. 이 기능만을 위한 신규 색상 정의를 금지한다.
- 주체 색상은 앱의 기존 범주형 색상 순서를 따라 개인·외국인·기관에 순서대로 배정한다.
- 다크 모드에서 동일하게 판독 가능해야 한다.
- **색상만으로 주체를 구분하지 않는다.** 범례에 색상 견본과 텍스트를 함께 두고, 분리 모드에서는 막대 순서가 고정되어 있으므로 순서 자체가 보조 단서가 된다. 누적 모드에서는 세그먼트 경계를 명확히 하여 구분한다.
- 텍스트 대비는 WCAG AAA 기준인 7:1 이상을 만족한다 (큰 텍스트는 4.5:1).
- 최소 터치 대상은 48dp를 지킨다.
- 차트 전체에 `contentDescription` 으로 요약을 제공한다. 예: "6개월 매물대. 현재가 25만 5500원. 상방 최대 매물 구간 26만 1천원에서 27만 2천원, 1억 1900만주."

---

## 7. 표기 의무 사항

다음 세 가지는 화면에서 언제나 확인 가능해야 한다. 숨기거나 도움말 안으로만 밀어 넣으면 안 된다.

1. **추정치 표기**: "주체별 매물대는 일별 매매대금에서 산출한 추정치입니다."
2. **산출 기준**: 매수 누적인지 잔존 추정인지, 자동 선택되었다면 그 사실도 함께.
3. **구간 폭**: 기간에 따라 달라지므로 항상 숫자로 표기한다.

---

## 8. 성능 예산

| 항목 | 기준 |
|---|---|
| 엔진 연산 (5년 일봉 약 1,230행 × 3주체) | 단일 스레드 30ms 이하 |
| 캐시 적중 시 최초 표시까지 | 100ms 이하 |
| 캐시 미적중 5년치 최초 수집 | 8초 이하 (동시 4) |
| 주체 선택·표시 방식 변경 후 반영 | 16ms 이하, 네트워크 호출 0회 |
| 프레임 드롭 | 스크롤·전환 중 0회 |

---

## 9. 테스트 계획

### 9.1 속성 기반 테스트 (필수)

임의로 생성한 OHLC와 거래량, 주체별 매매 조합에 대하여 다음을 검증한다.

- 질량 보존: 배분 합 = 원 거래량 (상대오차 1e-9)
- 1차 모멘트: |배분 가중평균 − 평균단가| ≤ 구간 폭 / 2
- 비음수: 모든 구간 값 ≥ 0
- 상각 정합: 잔존 합 + 상각된 합 + 초과 매도 = 매수 누적 합
- 구간 분할: 모든 일자의 고가·저가가 구간 범위 안에 들어감

### 9.2 골든 테스트 (필수)

첨부 `rows.csv` (삼성전자 2026-06-11 ~ 09-04, 60거래일)를 고정 픽스처로 삼아 아래 기대값을 검증한다. 이 값들은 참조 구현으로 산출된 것이다.

| 조건 | 기대값 |
|---|---|
| 18구간, 매수 누적, 전체 기간 | POC = 256,114원 (구간 중심), 구간 폭 = 10,294원 |
| 동일 조건, 현재가 255,500원 | 25.1만~26.1만 구간이 최대 물량 구간 |
| 프랙탈 k=5 스윙 고점 | 2026-06-19 (374,500), 2026-07-31 (267,000), 2026-08-18 (288,000) |
| 프랙탈 k=5 스윙 저점 | 5개 검출, 최저는 2026-07-29 (189,200) |
| 20구간, 비례상각, 전체 기간 | 개인 약 5,021만주 / 외국인 약 300만주 / 기관 약 249만주 |
| 20구간, 비례상각, 2026-08-18 이후 | 외국인 0만주 (소거 확인), 기관 약 249만주 |
| 20구간, 매수 누적, 2026-08-18 이후 | 세 주체 모두 양수 |

마지막 두 행은 §4.5의 자동 선택 규칙이 실제로 필요한 이유를 고정하는 회귀 테스트이므로 삭제하지 않는다.

### 9.3 단위 테스트

- 거래대금 백만원 → 원 변환 시 `Long` 승격 확인 (오버플로 회귀)
- 평균단가가 `[low, high]` 를 벗어나는 이상 데이터의 건너뛰기 동작
- 호가 단위 경계값 (예: 199,999 / 200,000 / 200,001)
- 페이징 경계: 30일 단위 반환의 이음매에서 일자 중복·누락이 없을 것
- 스윙 전환점 미검출 시 전체 기간 폴백

### 9.4 UI 테스트 (Compose)

- 주체 칩 해제 시 해당 시리즈가 사라지고 나머지가 유지된다
- 마지막 칩은 해제되지 않는다
- 누적/분리 전환이 데이터 재조회 없이 즉시 반영된다
- 기간을 스윙 고점 이후로 바꾸면 구간 폭 표기가 갱신된다
- 빈 상태·오류 상태가 각각 올바른 문구로 표시된다

### 9.5 접근성 검사

- TalkBack으로 컨트롤과 차트 요약을 모두 읽을 수 있다
- 대비비 7:1 이상 (자동 검사 도구 + 다크 모드 수동 확인)
- 글꼴 크기 200% 확대에서 레이아웃이 깨지지 않는다

---

## 10. 완료 판정 기준 (AAA)

아래를 **전부** 충족해야 완료로 간주한다. 하나라도 미충족이면 미완료로 보고한다.

1. §0 선행 확인 결과가 `PROGRESS.md` 에 기록되어 있다.
2. `domain/engine` 이 Android 의존성 없이 JVM 테스트만으로 전부 검증된다.
3. §9.1 속성 테스트가 최소 1,000회 시행에서 전부 통과한다.
4. §9.2 골든 테스트가 전부 통과한다.
5. §8 성능 예산을 실측으로 충족하고 측정값을 기록한다.
6. §9.4 UI 테스트와 §9.5 접근성 검사를 통과한다.
7. §7 표기 의무 사항 세 가지가 화면에 모두 노출된다.
8. 신규 색상 정의가 0건이며 전부 기존 토큰을 참조한다.
9. 공개 API에 KDoc이 작성되어 있고, §4.5와 §4.6의 근거가 코드 주석으로 남아 있다.
10. `detekt` / `ktlint` 경고 0건, 신규 코드에 `!!` 와 미처리 예외가 없다.

---

## 11. 단계별 진행 계획

| Phase | 산출물 | 완료 조건 |
|---|---|---|
| 0 | 코드베이스 조사 보고 | §0 표 전부 채움, 명세와의 차이 보고 |
| 1 | 데이터 계층 (API·DTO·Room·Repository) | 페이징 통합 테스트 통과, 단위 변환 테스트 통과 |
| 2 | 도메인 엔진 | §9.1 속성 테스트 + §9.2 골든 테스트 통과 |
| 3 | UseCase + ViewModel + UiState | 상태 전이 테스트 통과, 필터 변경 시 네트워크 0회 확인 |
| 4 | 차트 렌더링 | 누적·분리 양쪽 렌더 확인, 캔들과 축 정렬 확인 |
| 5 | 컨트롤 UI + 접근성 | §9.4, §9.5 통과 |
| 6 | 성능 측정·문서화·정리 | §8 실측 기록, §10 전체 점검 |

Phase 1과 Phase 2는 서로 독립적이므로 **병렬로 위임한다.** Phase 2는 픽스처 CSV만 있으면 데이터 계층 없이도 완성할 수 있다.

---

## 12. Worker 위임 브리프

### 12.1 Phase 1 → `kotlin-implementer` (Sonnet)

- **공유 컨텍스트**: 본 문서 §2, §5, §4.8. 앱은 Kotlin/Hilt/Room 기반이며 KIS API 클라이언트가 이미 존재할 수 있으므로 §0 조사 결과를 먼저 확인한다.
- **작업 기준**: 의존 방향 `data → domain` 준수. Repository 인터페이스는 `domain` 에 둔다. 거래대금은 파싱 즉시 `Long` 으로 승격한다. 페이징은 반환된 최소 일자 −1일을 다음 커서로 쓴다. 동시 실행 4 제한.
- **알려진 함정**: 거래대금 단위가 백만원이라 `Int` 오버플로가 조용히 발생한다. 30일 페이징 이음매에서 일자가 중복되거나 빠지기 쉽다. 투자자 API 가격은 수정주가 미반영이다.
- **완료 조건**: 페이징 통합 테스트 통과, 단위 변환 테스트 통과, 증분 캐시 갱신 동작 확인.

### 12.2 Phase 2 → `kotlin-implementer` (Sonnet), Phase 1과 병렬

- **공유 컨텍스트**: 본 문서 §3, §4, §9.1, §9.2. 첨부 `rows.csv` 와 참조 구현 `investor_volume_profile_v2.py`.
- **작업 기준**: 순수 Kotlin, Android 의존성 금지. 커널·구간·상각·스윙을 각각 인터페이스로 분리한다. §4.5와 §4.6의 근거를 코드 주석으로 남긴다.
- **알려진 함정**: 주 단위로 먼저 합산해 배분하면 외국인·기관 잔존이 소거된다. 짧은 구간에 비례상각을 강제하면 빈 차트가 나온다. 구간 개수를 고정하면 기간 간 비교가 불가능해진다.
- **완료 조건**: §9.1 속성 테스트 1,000회 통과, §9.2 골든 테스트 전부 통과.

### 12.3 Phase 4·5 → `kotlin-implementer` (Sonnet)

- **공유 컨텍스트**: 본 문서 §6, §7. 기존 디자인 토큰 위치와 캔들 차트 구조는 §0 조사 결과를 따른다.
- **작업 기준**: 신규 색상 정의 금지. 주체 선택은 표시 필터이며 재계산을 유발하지 않는다. 최소 1개 선택을 강제한다. 대비비 7:1 이상.
- **알려진 함정**: 캔들과 축 스케일을 각자 계산하면 눈금이 어긋난다. `Canvas` 람다 안에서 객체를 생성하면 프레임마다 할당이 발생한다.
- **완료 조건**: §9.4, §9.5 통과, §7 표기 노출 확인.

### 12.4 전 단계 검증 → `qa-verifier` (Sonnet)

각 Phase 종료 시 §10의 해당 항목을 점검하고, 통과·미통과를 근거와 함께 보고한다. 미통과 항목은 임의로 완화하지 않는다.

---

## 13. 첨부

- `rows.csv` — 골든 테스트 픽스처 (삼성전자 60거래일, 일봉 OHLCV + 3주체 매수·매도 물량·대금)
- `investor_volume_profile_v2.py` — 참조 구현 (배분·상각·스윙·기간·상하방 지표)
