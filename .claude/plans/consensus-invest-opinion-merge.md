# Plan: 투자의견 ↔ 리포트 데이터 병합 → 컨센서스 탭 표시

## 사용자 요구사항 (확정)
1. **검토 범위**: 종목분석 > 투자의견에서 수집되는 데이터와 리포트 메뉴에서 수집되는 데이터를 비교 / 중복제거 / 병합
2. **표시 위치**: 병합된 데이터를 종목분석 > 컨센서스 탭에 표시
3. **중복 판정 규칙**: 일자 + 증권사 + 목표가가 같으면 동일 데이터
4. **우선순위**: 리포트(Equity + FnGuide) 데이터 우선, KIS API `FHKST663400C0`는 부족한 부분을 채우는 서포트
5. **저장**: 병합된 데이터를 DB에 저장

---

## 현재 코드 구조 (2026-04-11 기준)

### 영역 1: 종목분석 > 투자의견 탭 (KIS API)
- **DTO**: `app/src/main/java/com/tinyoscillator/data/dto/InvestOpinionDto.kt`
- **Domain**: `app/src/main/java/com/tinyoscillator/domain/model/InvestOpinionModels.kt`
  - `InvestOpinion(date, firmName, opinion, opinionCode, targetPrice?, currentPrice?, changeSign, changeAmount?)`
  - `InvestOpinionSummary(ticker, stockName, opinions, buyCount, holdCount, sellCount, avgTargetPrice?, currentPrice?)`
- **Repository**: `app/src/main/java/com/tinyoscillator/data/repository/InvestOpinionRepository.kt`
  - `getInvestOpinions(ticker, stockName, kisConfig): Result<InvestOpinionSummary>`
  - KIS API TR ID: `FHKST663400C0`
  - Endpoint: `/uapi/domestic-stock/v1/quotations/invest-opinion`
  - 조회 기간: 최근 6개월
  - **저장 없음 (메모리 only, Room 미사용)**
- **ViewModel**: `app/src/main/java/com/tinyoscillator/presentation/investopinion/InvestOpinionViewModel.kt`
- **Screen**: `app/src/main/java/com/tinyoscillator/presentation/investopinion/InvestOpinionContent.kt`

### 영역 2: 리포트 메뉴 (스크레이퍼)
- **Entity**: `app/src/main/java/com/tinyoscillator/core/database/entity/ConsensusReportEntity.kt`
  - Table: `consensus_reports`
  - **Primary Keys**: `(stock_ticker, write_date, author, institution)`
  - Fields: writeDate, category, prevOpinion, opinion, title, stockTicker, stockName, author, institution, targetPrice, currentPrice, divergenceRate
- **DAO**: `app/src/main/java/com/tinyoscillator/core/database/dao/ConsensusReportDao.kt`
- **Scrapers**:
  - `app/src/main/java/com/tinyoscillator/core/scraper/EquityReportScraper.kt` (equity.co.kr, 8~16s delay)
  - `app/src/main/java/com/tinyoscillator/core/scraper/FnGuideReportScraper.kt` (comp.fnguide.com, 1~5s delay, PROVIDER_RE로 제공처/작성자 분리)
- **Repository**: `app/src/main/java/com/tinyoscillator/data/repository/ConsensusRepository.kt`
  - `collectReports(startDate, endDate)`: Flow<ConsensusDataProgress>
  - `mergeReports()`: 현재 2단계 중복 제거 — 1차 `writeDate|stockName|title`, 2차 `writeDate|stockName|institution`
  - `getReportsByTicker(ticker)`
  - `getConsensusChartData(ticker, stockName)`
- **Worker**: `app/src/main/java/com/tinyoscillator/core/worker/ConsensusUpdateWorker.kt` (03:00 daily)
- **Screen**: `app/src/main/java/com/tinyoscillator/presentation/report/ReportScreen.kt`

### 영역 3: 종목분석 > 컨센서스 탭 (현재 = 리포트만)
- **ViewModel**: `app/src/main/java/com/tinyoscillator/presentation/consensus/ConsensusViewModel.kt`
  - `loadData(ticker, stockName)` — **ConsensusRepository만 사용 (KIS 투자의견 미통합)**
- **Screen**: `app/src/main/java/com/tinyoscillator/presentation/consensus/ConsensusContent.kt`
- **Chart**: `app/src/main/java/com/tinyoscillator/presentation/chart/ConsensusChart.kt`
- **Domain**: `app/src/main/java/com/tinyoscillator/domain/model/ConsensusModels.kt` (`ConsensusChartData`)

### DB 버전
- **Current**: v24 (`app/src/main/java/com/tinyoscillator/core/database/AppDatabase.kt:81`)

---

## 데이터 필드 비교

| 의미 | InvestOpinion (KIS) | ConsensusReportEntity (리포트) | 차이 |
|------|---------------------|-------------------------------|------|
| 작성일 | `date` (yyyyMMdd) | `writeDate` (yyyy-MM-dd) | **형식 변환 필요** |
| 종목코드 | 상위 summary에만 | `stockTicker` | KIS 개별 항목엔 없음 |
| 종목명 | 상위 summary에만 | `stockName` | KIS 개별 항목엔 없음 |
| 증권사 | `firmName` | `institution` | 표기 차이 가능성 → 정규화 필요 |
| 의견 | `opinion` + `opinionCode` | `opinion` | KIS는 코드도 보유 |
| 목표가 | `targetPrice: Long?` | `targetPrice: Long` | nullable 차이 |
| 현재가 | `currentPrice: Long?` | `currentPrice: Long` | nullable 차이 |
| 괴리율 | `upsidePct` (summary) | `divergenceRate` (row) | 계산 범위 다름 |
| **KIS 고유** | opinionCode, changeSign, changeAmount | — | 리포트에 없음 |
| **리포트 고유** | — | title, author, category, prevOpinion | KIS에 없음 |

---

## 발견된 구조적 이슈 3가지

### 이슈 A: Primary Key 충돌 위험
- 현재 PK: `(stock_ticker, write_date, author, institution)`
- KIS에는 `author`가 없음 → `""` 또는 `"KIS"`로 강제 세팅 시 동일 증권사/일자/목표가 다중 건에서 덮어쓰기 발생

### 이슈 B: 증권사명 정규화
- KIS: "삼성증권", "미래에셋증권"
- FnGuide: PROVIDER_RE 정규식 추출, 표기 다를 수 있음
- Equity: 원본 유지
- 예: "삼성증권" vs "삼성증권 리서치센터" vs "삼성증권(리서치)" → 중복 판정 실패 위험

### 이슈 C: 목표가 0/null 처리
- KIS `targetPrice: Long?` — 목표가 미제공 의견 존재 가능
- "일자+증권사+목표가" 규칙 그대로 적용 시 목표가가 0/null인 항목들이 잘못 뭉쳐질 위험

---

## 제안 설계

### 스키마 변경 (v24 → v25)
```kotlin
@Entity(
    tableName = "consensus_reports",
    primaryKeys = ["stock_ticker", "write_date", "institution", "target_price", "source"],
    indices = [
        Index(value = ["write_date"]),
        Index(value = ["stock_ticker", "write_date"]),
        Index(value = ["institution"]),
        Index(value = ["category"]),
        Index(value = ["source"])  // 신규
    ]
)
data class ConsensusReportEntity(
    val writeDate: String,
    val category: String,
    val prevOpinion: String,
    val opinion: String,
    val title: String,
    val stockTicker: String,
    val stockName: String,
    val author: String,            // PK에서 제거, 일반 컬럼으로 유지

    val institution: String,
    val targetPrice: Long,
    val currentPrice: Long,
    val divergenceRate: Double,

    // 신규 필드
    @ColumnInfo(name = "source", defaultValue = "REPORT")
    val source: String,            // "REPORT" | "KIS_OPINION"

    @ColumnInfo(name = "opinion_code", defaultValue = "")
    val opinionCode: String,       // KIS 고유 (예: "01" 매수)

    @ColumnInfo(name = "change_amount", defaultValue = "0")
    val changeAmount: Long         // KIS 고유
)
```

**마이그레이션 `MIGRATION_24_25`**:
1. 임시 테이블 `consensus_reports_new` 생성 (새 PK + 3개 신규 컬럼)
2. 기존 데이터 복사 시 `source='REPORT'`, `opinion_code=''`, `change_amount=0` 세팅
3. 원본 drop, rename
4. 인덱스 재생성

### 중복 판정 키
```
key = "${normalizeDate(date)}|${stockTicker}|${normalizeInstitution(institution)}|${targetPrice}"
```
- `normalizeDate`: yyyyMMdd ↔ yyyy-MM-dd 양방향 → yyyy-MM-dd 통일
- `normalizeInstitution`: 공백/괄호/특수문자 제거 후 소문자 (저장값은 원본 유지, 키 계산용만)
- `stockTicker`: 종목 간 충돌 방지 위해 포함 (사용자 원안에는 없지만 권장)
- `targetPrice`: 0/null은 별개 행으로 취급 (PK에 포함되므로 자연 해결)

### 병합 알고리즘
```kotlin
suspend fun collectAndMergeForTicker(
    ticker: String,
    stockName: String,
    kisConfig: KisApiKeyConfig
) {
    // 1. 기존 리포트 조회 (최근 6개월)
    val existingReports = dao.getByTickerDateRange(ticker, sixMonthsAgo, today)

    // 2. KIS 투자의견 조회 (실패 시 조용히 종료)
    val kisResult = investOpinionRepository.getInvestOpinions(ticker, stockName, kisConfig)
    val kisOpinions = kisResult.getOrNull()?.opinions ?: return

    // 3. 중복 키 맵 생성
    fun key(date: String, inst: String, price: Long) =
        "${normalizeDate(date)}|$ticker|${normalizeInst(inst)}|$price"

    val reportMap = existingReports.associateBy {
        key(it.writeDate, it.institution, it.targetPrice)
    }

    val toInsert = mutableListOf<ConsensusReportEntity>()
    val toUpdate = mutableListOf<ConsensusReportEntity>()

    for (opinion in kisOpinions) {
        val k = key(opinion.date, opinion.firmName, opinion.targetPrice ?: 0)
        val existing = reportMap[k]

        if (existing != null) {
            // 리포트 우선, KIS는 보강만
            val enriched = existing.copy(
                currentPrice = if (existing.currentPrice == 0L)
                    opinion.currentPrice ?: 0L else existing.currentPrice,
                opinionCode = if (existing.opinionCode.isBlank())
                    opinion.opinionCode else existing.opinionCode,
                changeAmount = opinion.changeAmount ?: existing.changeAmount,
                divergenceRate = recalculateIfNeeded(...)
            )
            if (enriched != existing) toUpdate.add(enriched)
        } else {
            // KIS 단독 항목 → 신규 insert
            toInsert.add(opinion.toEntity(
                ticker = ticker,
                stockName = stockName,
                source = "KIS_OPINION"
            ))
        }
    }

    if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
    if (toUpdate.isNotEmpty()) dao.updateAll(toUpdate)
}
```

### KIS → Entity 변환
```kotlin
fun InvestOpinion.toEntity(ticker: String, stockName: String, source: String) =
    ConsensusReportEntity(
        writeDate = parseKisDate(date),        // "20260401" → "2026-04-01"
        category = "투자의견",
        prevOpinion = "",
        opinion = opinion,
        title = "",                             // KIS는 제목 없음
        stockTicker = ticker,
        stockName = stockName,
        author = "",
        institution = firmName,
        targetPrice = targetPrice ?: 0L,
        currentPrice = currentPrice ?: 0L,
        divergenceRate = calcDivergence(targetPrice, currentPrice),
        source = source,
        opinionCode = opinionCode,
        changeAmount = changeAmount ?: 0L
    )
```

### 병합 실행 시점: 옵션 A (On-demand) 선택
**이유**: KIS API는 종목별 단건 조회만 가능 → 기존 전체 리포트 배치 워커에 통합 어려움.

```kotlin
// ConsensusViewModel.loadData()
fun loadData(ticker: String?, stockName: String?) {
    if (ticker == null || ticker == currentTicker) return
    currentTicker = ticker
    viewModelScope.launch {
        _isLoading.value = true
        try {
            // KIS 보강 시도 (실패해도 조용히 무시)
            val kisConfig = apiConfigProvider.getKisConfig()
            if (kisConfig.isValid()) {
                runCatching {
                    consensusRepository.collectAndMergeForTicker(
                        ticker, stockName ?: ticker, kisConfig
                    )
                }.onFailure { Timber.w(it, "KIS 보강 실패, 리포트만 표시") }
            }
            // DB에서 최종 데이터 조회
            _reports.value = consensusRepository.getReportsByTicker(ticker)
            _chartData.value = consensusRepository.getConsensusChartData(ticker, stockName ?: ticker)
        } finally {
            _isLoading.value = false
        }
    }
}
```

---

## 사용자 답변 대기 중인 5가지 결정 사항

1. **PK 변경 승인**: `author`를 PK에서 제외하고 `(stock_ticker, write_date, institution, target_price, source)`로 변경 동의?
   - 권장: ✅ 승인

2. **중복 키에 `stockTicker` 포함**: 사용자 원안은 "일자+증권사+목표가"였으나, 다른 종목 간 충돌 방지를 위해 ticker 포함 권장
   - 권장: ✅ 포함

3. **증권사명 정규화 수준**:
   - (a) 완전 일치만
   - (b) 공백/괄호 제거 후 일치 — **권장**
   - (c) "증권"/"리서치센터" suffix까지 제거

4. **보강 필드 범위** (KIS → 리포트):
   - [ ] `currentPrice` (리포트가 0일 때) — **권장**
   - [ ] `opinionCode` (리포트는 항상 비어있음) — **권장**
   - [ ] `divergenceRate` (재계산) — **권장**
   - [ ] `changeAmount` (리포트에 없음, 신규 컬럼 필요) — **선택사항**

5. **병합 시점**:
   - 옵션 A: 화면 진입 시 on-demand — **권장**
   - 옵션 B: 관심종목 배치 워커

**기본 권장안**: 1=승인 / 2=포함 / 3=(b) / 4=currentPrice+opinionCode+divergenceRate+changeAmount / 5=옵션 A

---

## 구현 시 변경 대상 파일 목록 (예상)

### 수정
1. `app/src/main/java/com/tinyoscillator/core/database/entity/ConsensusReportEntity.kt` — PK 재설계 + 3개 신규 컬럼
2. `app/src/main/java/com/tinyoscillator/core/database/AppDatabase.kt` — v24 → v25
3. `app/src/main/java/com/tinyoscillator/core/database/dao/ConsensusReportDao.kt` — getByTickerDateRange, updateAll 추가
4. `app/src/main/java/com/tinyoscillator/data/repository/ConsensusRepository.kt` — `collectAndMergeForTicker()` 추가, mergeReports() 중복 키 갱신, InvestOpinionRepository 주입
5. `app/src/main/java/com/tinyoscillator/presentation/consensus/ConsensusViewModel.kt` — InvestOpinionRepository + ApiConfigProvider 주입, loadData 수정
6. `app/src/main/java/com/tinyoscillator/domain/model/ConsensusModels.kt` — ConsensusReport 도메인 모델에 source/opinionCode 필드 추가
7. `app/src/main/java/com/tinyoscillator/core/di/AppModule.kt` — ConsensusRepository provider에 InvestOpinionRepository 의존성 추가
8. `app/src/main/java/com/tinyoscillator/presentation/consensus/ConsensusContent.kt` — (선택) source 뱃지 표시

### 신규
9. `app/src/main/java/com/tinyoscillator/core/database/Migration_24_25.kt` — Room 마이그레이션
10. `app/src/main/java/com/tinyoscillator/data/mapper/InvestOpinionEntityMapper.kt` — InvestOpinion → ConsensusReportEntity 변환, normalizeInstitution/parseKisDate 유틸
11. `app/schemas/com.tinyoscillator.core.database.AppDatabase/25.json` — Room 스키마 export (KSP 자동 생성)

### 테스트 추가
12. `app/src/test/java/com/tinyoscillator/data/repository/ConsensusRepositoryMergeTest.kt` — 병합 로직 단위 테스트
13. `app/src/test/java/com/tinyoscillator/data/mapper/InvestOpinionEntityMapperTest.kt` — 변환/정규화 테스트

---

## 다음 단계 (재개 시)

1. 사용자에게 **5가지 결정 사항** 확인 → 답변 수신
2. 확정 정책 반영하여 코드 변경 착수
3. Room 마이그레이션 작성 후 기존 DB 손상 여부 수동 테스트
4. 단위 테스트 추가 및 `./gradlew :app:testDebugUnitTest --tests "*Consensus*" --tests "*InvestOpinion*"`
5. 실기기에서 KIS 키 있는 상태 / 없는 상태 양쪽 동작 확인
6. 커밋 메시지: `feat: 종목분석 컨센서스 탭 — 리포트+KIS 투자의견 병합 저장`

## Notes
- 현재 리포트 메뉴(`ReportScreen`)는 건드리지 않음. 병합 데이터를 조회하지만 기존 UI는 그대로 유지됨 (source='KIS_OPINION' 행이 섞여 들어가므로 필터 옵션에 영향 가능성 있음 → 검토 필요).
- `ConsensusUpdateWorker`는 수정하지 않음 (전체 리포트 배치만 담당).
- KIS API 키 미설정 시 graceful degradation — 기존 리포트만 표시.
- InvestOpinion 탭(`InvestOpinionContent`)은 기존대로 유지. 컨센서스 탭은 별개의 "통합 뷰" 역할.
