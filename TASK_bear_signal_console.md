# TASK_bear_signal_console.md — 「주도주 붕괴 판단 계기판」 TinyOscillator 신규 메뉴 이식 명세서 (v1.2)

> 대상: Claude Code (kotlin-implementer / qa-verifier 서브에이전트)
> 근거 프로토타입: `bear_signal_dashboard.jsx` — **스코어링 로직의 단일 진실 공급원(SSOT)**
> 임계치 SSOT: `bear_thresholds.json` (§3.0 — v1.2에서 외부화)
> 원전: 신영증권「주도주의 물리학」(2026.6.30)
> 진행 규칙: 각 Phase 완료 시 `PROGRESS:` 마커 갱신, `PHASE_RUNBOOK.md`/`PROGRESS.md` 흐름 준수

## Changelog
- v1.2: 임계치 외부화(`bear_thresholds.json`, §3.0), 웹/LLM 3-tier 수집·승인 신설(§4.5),
        스냅샷 계약 신설(§4.6), Room 이력·전이 감지 Phase 3.5 추가(§6.1).
        병합 근거: `archive/TASK_v1.1_to_v1.2_merge_patch.md` + `PHASE_RUNBOOK.md`.
- v1.0: 최초 이식 명세 (`archive/TASK_bear_signal_console_v1.0.md`).

> **v1.2 편집 노트(재편성 매핑)**: 본 리포지토리의 Phase 0~3 + UI 조립 + 폴리시는 v1.0 계획으로 이미
> 구현 완료(`PROGRESS.md` P0~P3, P4(v1.0-UI), P5(v1.0-폴리시) 참조). v1.2 잔여 작업은
> §3.0 임계치 외부화(retrofit) → Phase 3.5(§6.1) → Phase 4(§4.5 웹/LLM) → Phase 5-1/5-2다.
> `PHASE_RUNBOOK.md`의 §3.8 표기는 본 문서 §3.7(정적 참조·3유형)에 해당한다. LeaderProfile
> 칩·CharacterAxes·TypePriorityEngine은 프로토타입 jsx에 없으며(레이더는 s1/s2/s3/gate 4축)
> SSOT 원칙상 스코어링 도입 금지 — §4.6 `axes`는 직렬화 계약상 선택 필드로만 유지한다.

---

## 0. 목표와 제약

TinyOscillator에 **AI 사이클 약세장 전환 신호를 판정하는 신규 메뉴**를 추가한다.

| 제약 | 반영 방식 |
|---|---|
| 기능 무손실 | 프로토타입 9개 기능 블록(§3, 부록 B)을 1:1 이식. 스코어링 결과가 프로토타입과 **바이트 단위로 동일** |
| TinyOscillator 디자인 부합 | 기존 Material 3 테마 토큰·타이포·shimmer·Vico 관례 재사용, 신규 그래픽만 Compose Canvas |
| 모바일 최적 | 360dp 기준 레이아웃, 광폭 표는 전치(§5.3), 색+텍스트 병기 |
| Kotlin native 우선 | 스코어링=순수 산술(Kotlin), 수집=Retrofit/기존 데이터 레이어(Kotlin). **Chaquopy(Python) 불필요** (§1 결론) |
| 기존 코딩·설계 패턴 유지 | MVVM + Clean Architecture + Hilt + Compose + StateFlow + Room, 기존 `kotlin_krx`/ECOS 데이터소스 재사용 |
| 사용자 친화 | 자동 수집 + 수동 오버라이드 + "최신 갱신일" 배지 + Pull-to-refresh + 리포트 기준값 리셋 |

---

## 1. 데이터 수집 타당성 검토 → 제작 가능 여부 판단 ★ 선행 필수

각 신호가 요구하는 입력값이 **실제로 수집 가능한지**를 먼저 판정한다. 자동화 등급:

- **[A] 완전 자동** — 기존 연동 소스로 즉시 획득
- **[B] 자동(외부 무료 API 추가)** — 신규 Retrofit 소스 1개 추가로 획득
- **[C] 반자동** — 수집 난도 높음, 별도 스크래핑/월간 배치. v1은 수동 권장
- **[D] 수동 입력** — 이벤트성·비정형(뉴스/정성). 리포트 스냅샷을 기본값으로 프리시드

### 1.1 타당성 매트릭스

| 신호 | 필요 데이터 | 소스(검증) | 기존 연동 | 등급 |
|---|---|---|---|---|
| **신호1 주변부 압착** | 코스피 지수 −12/−6/−3/−1M 누적수익률 | `kotlin_krx` `get_index_ohlcv("1001")` | ✅ | **[A]** |
| | 해외 19개 지수 동일 기간 수익률 | Yahoo chart API 기본 + Stooq 백업(2026-07-10 멀티소스 교체) | 부분 | **[B~C]** ¹ |
| **신호2 변동성 무게중심** | 코스피 일별 종가(직전 6M) → ±3σ/±4σ 상승·하락일 | `kotlin_krx` `get_index_ohlcv` | ✅ | **[A]** ² |
| **신호3 IPO 질** | Renaissance IPO ETF(티커 `IPO`) 방향 | Yahoo chart API 기본 + Stooq 백업 | 부분 | **[B]** |
| | KR 상장사 적자·신주 발행 비중 | DART 증권신고서(파싱 난도 高) | 부분 | **[C~D]** ³ |
| | 대어(OpenAI·Anthropic) 공모 소화 | 뉴스/정성 | — | **[D]** |
| **신호4 금리(결정타)** | 한국은행 기준금리·방향 | **BOK ECOS**(기준금리 통계) | ✅ | **[A]** |
| | 미 연준 목표금리 상단 | FRED(예: `DFEDTARU`) | — | **[B]** |
| | 신용거래융자 잔고 | KRX 정보데이터시스템/KOFIA(**pykrx 미제공**) | ❌ | **[C]** ⁴ |
| | 반대매매 임박(담보유지 140% 근접) | 집계 미공개 → 파생/판단 | — | **[D]** |
| **증폭 · 집중** | 삼성전자+SK하이닉스 코스피 비중 | `kotlin_krx` 시가총액(`get_market_cap*`) | ✅ | **[A]** |
| | 반도체·완충(자동차/기계/석유) 수출 비중 | **관세청 무역통계 Open API** `apis.data.go.kr/1220000/nitemtrade/getNitemtradeList` (월 주기) 또는 무역협회 K-stat | — | **[B]** ⁵ |

> ¹ 해외지수: 신뢰도 확보 6개(다우·S&P·나스닥·DAX·닛케이·항생)만 AUTO, 나머지 13개는 **수동 입력 폴백**(월 1회, 리포트 시드값 제공).
> ² 통계량(평균·표준편차·임계 초과일 카운트)은 Kotlin으로 자체 계산 → **Python 불필요**.
> ³ 증권신고서에서 신주/구주·직전 12M 적자 여부 자동 추출은 문서 파싱 난도가 높음. **v1은 수동 입력**, DART 자동 파싱은 v2 후보(그때 필요 시 Chaquopy Python 포팅 검토).
> ⁴ 신용거래융자 잔고는 `pykrx`(=`kotlin_krx`)가 제공하지 않음(공매도는 제공). KRX 정보데이터시스템/KOFIA 별도 수집 필요. **v1은 수동 입력 + §4.5 LLM 제안**, v2에서 배치 스크래핑.
> ⁵ 리포트는 "15대 품목(MTI)" 기준. 관세청 API는 HS Code 기준이므로 **HS→MTI(또는 15대 품목) 매핑 테이블** 필요, 또는 K-stat의 MTI품목별 통계 활용.

### 1.2 결론 — 제작 가능 여부

- **제작 가능하다.** 스코어링은 전부 순수 산술이므로 Kotlin native로 구현하고, 수집은 [A]/[B] 지표를 자동화한다.
- **Python(Chaquopy) 불필요.** v1 범위에서 자동화가 어려운 항목([C]/[D])은 **수동 입력 필드**로 처리하되 리포트 스냅샷을 기본값으로 프리시드하고 "최신 갱신일" 배지를 표기한다. → **계기판 기능은 하나도 누락되지 않는다.**
- 설계 골격: **하이브리드 데이터 아키텍처** = (자동 수집값) ⊕ (수동 오버라이드) 병합, 각 입력에 `source`와 `updatedAt`을 부착. 출처 우선순위는 §4.6.

---

## 2. 아키텍처 (Clean Architecture · 기존 패턴 준수)

```
feature/bearsignal/
├─ domain/
│  ├─ model/            BearSignalInputs, BearSignalResult, SignalLevel, GateState,
│  │                    BearPhase, MarketReturns, MarketAnalysis, BearType, MonitorItem, InputSource,
│  │                    BearThresholds(§3.0), BearSnapshot·Transition(§6.1), Suggestion(§4.5)
│  ├─ repository/       BearSignalRepository, SnapshotRepository (interface)
│  └─ usecase/          ComputeBearSignalUseCase        (순수 스코어링, I/O 없음, 임계치 주입)
│                       ObserveBearSignalStateUseCase   (Flow<BearSignalResult>)
│                       RefreshAutoInputsUseCase        ([A][B] 자동 수집)
│                       UpdateManualInputUseCase        ([C][D] 수동 반영)
│                       ResetToReportBaselineUseCase
│                       DetectTransitionsUseCase        (§6.1 이력 전이)
│                       ApplySuggestionUseCase          (§4.5 승인 반영)
├─ data/
│  ├─ remote/           GlobalIndexApi(Yahoo/Stooq), FredApi, CustomsTradeApi(관세청),
│  │                    LlmMarketDataSource(§4.5)  (+ 기존 EcosDataSource, KrxDataSource 재사용)
│  ├─ local/            BearSignalDao, CountryReturnEntity, ManualInputEntity, AutoCacheEntity,
│  │                    BearSnapshotEntity·BearSnapshotDao(§6.1)
│  ├─ mapper/           *Dto ↔ domain
│  └─ repository/       BearSignalRepositoryImpl (auto ⊕ manual 병합), SnapshotRepositoryImpl
├─ di/                  BearSignalModule (@Module @InstallIn — Hilt)
└─ presentation/
   ├─ BearSignalViewModel   (@HiltViewModel, StateFlow<BearSignalUiState>)
   ├─ BearSignalUiState
   └─ ui/                   BearSignalScreen + components (Compose)
```

- 스코어링은 **`ComputeBearSignalUseCase`에 격리**(플랫폼 의존성 0, JVM 단위테스트 대상). 임계치는 §3.0 주입.
- 자동 수집 실패 시 Room 캐시 + `updatedAt` 노출(오프라인 우선 표시 후 백그라운드 갱신, 기존 패턴).
- 주기 갱신은 **WorkManager**(§4 주기 열 기준. Phase 5-1에서 일 Tier A / 주 Tier B 조정 검토).

---

## 3. 스코어링 로직 (프로토타입과 1:1 — 로직·경계 불변) ★

### 3.0 임계치 출처 (v1.2 신설)

> §3의 스코어링 로직·경계는 불변이다. 단, 숫자 임계치는 코드 상수가 아니라 `bear_thresholds.json`에서 로드한다. 기존 하드코딩 상수를 아래 `BearThresholds`로 대체하고, 스코어링 엔진은 이를 생성자 주입으로 받는다. 테스트는 리터럴/테스트 JSON으로 직접 구성한다(안드로이드 무의존 유지).

```kotlin
// domain — 프레임워크 무의존. bear_thresholds.json과 1:1.
data class BearThresholds(
    val version: String, val basis: String,
    val s1: S1, val s2: S2, val s3: S3, val gate: Gate, val amp: Amp, val phase: PhaseCfg,
) {
    data class S1(val manyCountries: Int, val deepPct: Double, val deepeningPct: Double)
    data class S2(val redLine: Double, val warnLine: Double, val watchLine: Double)
    data class S3(val loss1: Double, val loss2: Double, val loss3: Double)
    data class Gate(val critical: Double, val approach: Double, val creditWarn: Double)
    data class Amp(val semiExport: Double, val kospi2: Double,
                   val wSemi: Double, val wKospi2: Double, val wNoBuffer: Double, val cap: Double)
    data class PhaseCfg(val leadOrange: Int, val leadAmber: Int)
}

// 스코어링 엔진은 임계치를 주입받는다 (기존 SSOT 로직 그대로, 상수만 t.* 참조)
class ComputeBearSignalUseCase(private val t: BearThresholds) { /* scoreS1..composite: t.s1.manyCountries 등 */ }
```

```kotlin
// data — assets/bear_thresholds.json 로드 (Hilt @Provides @Singleton)
class ThresholdsProvider(private val context: Context, private val json: Json) {
    fun load(): BearThresholds =
        context.assets.open("bear_thresholds.json").bufferedReader().use {
            json.decodeFromString(it.readText())
        }
}
```

파일 배치: `app/src/main/assets/bear_thresholds.json` (리포지토리 루트 사본과 값 동일 유지).
리포트 개정 시 JSON만 교체하면 판정이 바뀌어야 한다(**코드 무수정 — §7 수용 기준**).

레벨 정의: `0 안전 · 1 주의 · 2 경고 · 3 위험`. 아래 의사코드의 숫자는 `bear_thresholds.json` v1.2 값(주석 목적)이며, 구현은 전부 `BearThresholds` 참조로 대체한다.

### 3.1 신호1 — 주변부 압착 (도표 46~48)
```
analyzeMarkets(markets, period):
  col = 기간 인덱스 { 12m:0, 6m:1, 3m:2, 1m:3 } (기본 1m)
  neg = markets 중 해당 기간 수익률 < 0 개수
  worstNew = min( 해당 기간 수익률 )  단, 12M 수익률 > 0 이고 해당 기간 < 0 인 지수만 (신규 이탈)
  depth = worstNew <= s1.deepPct(-12) → "deep"; <= s1.deepeningPct(-6) → "deepening"; else "shallow"

scoreS1(neg, depth):
  many = neg >= s1.manyCountries(7)       # 닷컴 정점 직전 = 7개국
  if !many: return (depth=="deep") ? 1 : 0
  if depth=="shallow": return 1
  if depth=="deepening": return 2
  return 3
```
> 낙폭은 **만성 약세국(12M도 마이너스)을 제외한 신규 이탈** 기준. 이탈 수·낙폭이 동시에 확대될 때만 위험 상향.

### 3.2 신호2 — 변동성 무게중심 (도표 49~50)
```
# 직전 6M 코스피 일별 로그·단순수익률에서 μ, σ 산출 → ±3σ / ±4σ 초과 상승일/하락일 카운트
scoreS2(up, down, deepening):     # up=큰 상승일 수, down=큰 하락일 수 (±3σ 기준)
  r = (up==0) ? 9 : down/up
  if r > s2.redLine(1.0): return 3        # 큰 하락일이 큰 상승일 추월 = 천장
  if r >= s2.warnLine(0.95): return 2
  if r >= s2.watchLine(0.7): return deepening ? 1 : 0
  return 0
```

### 3.3 신호3 — IPO 질 (도표 51~53)
```
scoreS3(loss, etf, big):          # loss=적자상장비중%, etf∈{up,flat,down}, big∈{smooth,pending,failed}
  lv = (loss>=s3.loss3(80))?3 : (loss>=s3.loss2(60))?2 : (loss>=s3.loss1(45))?1 : 0   # 평상 20~40, 버블 ~80
  if etf=="down": lv = max(lv, 2)
  if big=="failed": lv = max(lv, 3) else if big=="pending": lv = max(lv, 1)
  return lv
```

### 3.4 신호4 — 금리 방아쇠 [GATE] (도표 54~57)
```
scoreGate(rate, dir, credit, margin):   # rate=기준금리상단%, dir∈{ease,hold,hike}, credit=신용잔고(조), margin=반대매매임박
  lv = (rate>=gate.critical(4.5))?3 : (rate>=gate.approach(4.0))?2 : (dir=="hike"?1:0)   # 임계 4.5% = 진짜 긴축
  if margin: lv = max(lv, 2) else if credit>=gate.creditWarn(35): lv = max(lv, 1)   # 2023말 17.5조 → 2배 이상
  return lv
# 상태 라벨: 0 정상화 구간 · 1 경계 접근 · 2 임계 접근 · 3 긴축 돌입
```

### 3.5 증폭 · 집중 [AMP] (유형4, 도표 44)
```
amplifier(semi, kospi2, buffer):  # semi=반도체수출비중%, kospi2=삼성+SK 코스피비중%, buffer=완충산업건재
  a = 1.0 + (semi>=amp.semiExport(20) ? amp.wSemi(.15):0)
          + (kospi2>=amp.kospi2(50) ? amp.wKospi2(.15):0)
          + (!buffer ? amp.wNoBuffer(.20):0)
  return min(a, amp.cap(1.6))
```

### 3.6 종합 국면 판정 [상태 기계]
```
composite(inputs):
  ma   = analyzeMarkets(markets, period)
  s1   = scoreS1(ma.neg, ma.depth)
  s2   = scoreS2(up, down, deepening)
  s3   = scoreS3(loss, etf, big)
  gate = scoreGate(rate, dir, credit, margin)
  amp  = amplifier(semi, kospi2, buffer)
  lead = s1 + s2 + s3                 # 0..9  (선행 신호 합)
  leadPct = round(lead/9*100)
  warn = count([s1,s2,s3] >= 2)       # 경고 이상 선행 신호 수

  phase =
    (gate>=3 && warn>=1)                          → RED     # 긴축 돌입 + 선행 경고 = 톱니바퀴 격발
    (gate>=2 || (lead>=phase.leadOrange(6) && gate>=1)) → ORANGE  # 방아쇠 임박
    (lead>=phase.leadAmber(3) || gate>=1)         → AMBER   # 신호 점등 · 방아쇠 대기
    else                                          → GREEN   # 안정
```
**골든 케이스(리포트 2026.6.30 기준값):** `s1=1, s2=1, s3=1, gate=1, amp=1.30 → AMBER`. 이식본이 반드시 재현해야 함.

### 3.7 정적 참조 데이터 (부록 C 시드)
- **약세장 3유형**(회복 가능성): 유형1 경쟁·역전=최저 / 유형2 전방수요·사이클=중간 / 유형3 밸류·금리=펀더멘털 생존·인내. **유형3이 현재 활성 방아쇠(gate≥1일 때 하이라이트).**
- **유형별 모니터링 체크리스트**, **역사 검증(일본 1980s 3충격 + 3대 지표)**, **도표48 국가별 수익률 시드**(20개 지수 × 4기간) — 프로토타입 값 그대로 이관(부록 C).
- *(`PHASE_RUNBOOK.md`의 "§3.8 유형 축" 참조는 본 절에 해당. TypePriorityEngine·CharacterAxes는 jsx 프로토타입에 없어 v1 스코어링 도입 금지 — §4.6 `axes`는 직렬화 선택 필드.)*

---

## 4. 데이터 소스 연동 명세 (지표별)

| 지표 | 엔드포인트/메서드 | 파라미터·매핑 | 주기 | 폴백 |
|---|---|---|---|---|
| 코스피 지수 시세 | `kotlin_krx get_index_ohlcv("1001")` | 최근 ~130영업일 종가 | 일 | 캐시 |
| 신호2 통계 | (자체 계산) | μ,σ,±3σ/±4σ 카운트 | 일 | — |
| 코스피 2사 비중 | `kotlin_krx get_market_cap*` | (SS+SK 시총)/(KOSPI 시총) | 일 | 캐시 |
| 해외 19개 지수 | Yahoo chart API 기본 + Stooq 백업 | 지수코드→기간수익률 | 월 | **미커버=수동** |
| IPO ETF 방향 | Yahoo `IPO` / Stooq `ipo.us` | 최근 고점 대비 방향 up/flat/down | 주 | 수동 |
| 한은 기준금리 | 기존 `EcosDataSource` | 기준금리 통계코드(재사용) | 이벤트 | 수동 |
| 미 연준 상단 | `FredApi`(예: `DFEDTARU`) | series → 최신값 | 이벤트 | 수동·LLM 제안(§4.5) |
| 수출 비중 | `CustomsTradeApi` `…/getNitemtradeList` | 인증키·기간·HS(반도체 8541/8542, 자동차 8703, 기계 84xx, 석유 27xx …) → 15대품목 매핑 | 월 | K-stat/수동 |
| 신용잔고 | (v1 수동) + LLM 제안(§4.5, KOFIA 주간) | KRX 정보데이터시스템/KOFIA(v2 배치) | 월(v1) | 수동 |
| 대어 소화·정책방향·반대매매 | (수동) + LLM 제안(§4.5) | 뉴스/판단, SentimentEngine 연계는 v2 | 이벤트 | 수동 |

> 관세청 Open API는 공공데이터포털 활용신청·인증키 필요(개발계정 10,000 트래픽). BuildConfig/보안 저장소에 키 관리(기존 KIS 키 관리 패턴 준수).

### 4.5 웹/LLM 3-tier 수집 · 승인 흐름 (v1.2 신설)

> [C]/[D] 판단성 필드의 수동 입력 부담을 낮추는 **제안(suggestion) 계층**. 자동 수집(Tier 1: [A], Tier 2: [B])과 달리 Tier 3(웹/LLM)은 **사용자 승인 없이는 절대 상태를 변경하지 않는다**. *(§4.5의 WEB 출처는 §4.6 우선순위상 AUTO에 속한다.)*

1. **`LlmMarketDataSource`** — Anthropic API + `web_search` 도구. 그룹 분할 호출(부분 실패 격리, `Promise.allSettled` 상당):
   - ① `rate`/`dir` — 공식 발표·날짜 명시 요구
   - ② `bigDeal`/`lossRatio`
   - ③ `credit` — KOFIA 주간 통계
   - 응답의 열거형 값은 화이트리스트 검증(`dir∈{ease,hold,hike}`, `big∈{smooth,pending,failed}` 등). 위반 시 해당 필드 제안 폐기.
2. **`Suggestion(field/current/next/as_of/origin/stale)`** — 필드별 허용 연령 초과 시 `STALE` 마킹.
   급변 값(금리 ±0.5%p 초과·신용잔고 ±30% 초과)은 **자동 1회 재확인**, 두 결과 일치 시에만 목록화.
3. **`SuggestionPanel` + `ApplySuggestionUseCase`** — 제안은 목록으로만 표시. 개별/일괄 **승인 시에만** `source=AUTO`로 반영(§4.6 우선순위 준수, **MANUAL 불패**).
4. 네트워크: fetch 타임아웃 30s + 백오프 1회. API 키는 기존 `AiApiClient`/`ApiConfigProvider` 자격증명 패턴 재사용.

### 4.6 스냅샷 계약 (v1.2 신설)

> 수집 계층 · 엔진 · Room · React 뷰어를 잇는 단일 경계. 이 스키마를 먼저 고정한다. `inputs`는 React 골든 입력과 완전 동형(계산 결과가 아닌 원입력만 담는다). Room 직렬화도 이 스키마를 그대로 쓴다(별도 규약 금지).

#### 스키마 `bear-snapshot/1`
```json
{
  "schema": "bear-snapshot/1",
  "as_of": "2026-07-11",
  "generator": "tinyoscillator | routine | manual",
  "config_basis": "신영 2026.6.30",
  "inputs": {
    "markets": [{ "name": "코스피", "r": [173.1, 103.7, 54.0, 4.5] }],
    "s1_period": "1m",
    "s2_up": 14, "s2_down": 12, "s2_deepening": true,
    "s3_lossRatio": 45, "s3_etf": "up", "s3_bigDeal": "pending",
    "s4_rate": 3.75, "s4_dir": "hike", "s4_credit": 38, "s4_marginCall": false,
    "amp_semiExport": 23.1, "amp_kospi2": 56, "amp_buffer": true,
    "axes": { "commodity": 1, "capex": 2, "chase": 1, "duration": 2, "contract": 2 }
  },
  "field_meta": {
    "s2_up":   { "source": "SNAPSHOT", "as_of": "2026-07-11", "origin": "kotlin_krx:KS11" },
    "s4_rate": { "source": "AUTO",     "as_of": "2026-06-18", "origin": "FRED:DFEDTARU" },
    "s4_credit": { "source": "MANUAL", "as_of": "2026-07-04", "origin": "user" }
  }
}
```
> `axes`는 선택 필드(프로토타입 jsx 미보유 — v1은 생략 또는 정적 시드 직렬화만 허용, 스코어링 사용 금지).

#### 값 출처 우선순위
```
MANUAL 〉 SNAPSHOT 〉 AUTO 〉 BASELINE(기준값)
```
사용자 수동 조정은 언제나 자동 수집을 이긴다(무인 갱신이 사람 판단을 덮지 않는다). *(§4.5 웹 수집의 WEB 출처는 AUTO에 속한다.)*

```kotlin
enum class ValueSource { MANUAL, SNAPSHOT, AUTO, BASELINE }  // ordinal = 우선순위(작을수록 강함)
data class FieldSource(val source: ValueSource, val asOf: LocalDate?, val origin: String?)
```

---

## 5. UI/UX 명세 (Compose · Material 3 · 모바일)

### 5.1 진입점(신규 메뉴) — 권장안
- **권장:** 기존 4탭을 유지하고, 시장/대시보드 성격 탭 안에 **"시장 국면 · 리스크" 카드**를 두어 `BearSignalScreen`으로 내비게이트(하단바 혼잡 회피).
- **대안:** 상시 노출이 중요하면 **5번째 하단 탭 "국면"**(Material 3 권장 3~5 목적지 내). 최종 배치는 기존 네비게이션 그래프 관례에 맞춰 선택.

### 5.2 화면 구조 (`LazyColumn` 섹션)
1. **종합 국면 헤더** — 신호등(GREEN/AMBER/ORANGE/RED 4등) · 선행점수 게이지(0~100) · 방아쇠 상태 · 증폭 배수 · **레이더(4축)**. 국면별 색·해설 문구.
   *(v1.2: 헤더 바로 아래 **Sparkline(lead·gate)** + **TransitionLog** 배치 — §6.1)*
2. **선행 신호 3 카드** — 신호1/2/3, 4셀 게이지 + 레벨 칩(색+텍스트) + [자동값 표시 / 수동 입력 버튼].
3. **신호1 상세: 국가별 수익률(도표48)** — §5.3.
4. **방아쇠(금리)·증폭(집중) 카드**.
5. **약세장 3유형 카드** — 회복 가능성 + 모니터링 체크리스트, 활성 방아쇠 하이라이트.
6. **역사 검증(일본 3충격)** + 3대 모니터링 지표.
7. **지표↔리포트 매핑 + 면책 + 전체 최신 갱신일**.

### 5.3 국가별 수익률 표 — 모바일 대응(전치)
- 프로토타입은 기간=행/국가=열(광폭). **모바일은 국가=행(20) × 기간=열(4)로 전치**하여 세로 스크롤·가독 확보.
- 기간 선택은 **FilterChip**(−12/−6/−3/−1M), 선택 기간이 신호1 판정에 반영.
- 각 셀 색상(+텍스트 부호) 구분, 상단에 **선택 기간 이탈 수 요약 칩**. 값은 인라인 편집(수동 갱신) 가능.

### 5.4 상호작용·디자인 토큰
- **Pull-to-refresh** → [A][B] 자동 수집. **BottomSheet** 수동 입력(Stepper·SegmentedButton·Slider). **리셋** → 리포트 기준값.
- LEVEL 색 매핑(기존 시맨틱 컬러 재사용): 안전=성공/그린, 주의=앰버, 경고=오렌지, 위험=에러/레드. **다크·라이트 모두** 검증.
- 커스텀 그래픽(게이지·신호등·레이더·표)은 **Compose Canvas/Layout**(Vico는 기존 라인/바 관례에만 사용).
- **접근성:** 색+텍스트 병기(색각), 최소 탭 타깃 48dp, 폰트 스케일 대응, 주요 지표 contentDescription.
- **성능:** shimmer 로딩(기존 패턴), 캐시 우선 렌더 후 백그라운드 갱신.

---

## 6. 구현 순서 (Phase · 서브에이전트 · PROGRESS)

| Phase | 내용 | 담당 | 완료 마커 |
|---|---|---|---|
| **0** | 스캐폴딩 · 도메인 모델 · **순수 스코어링 + JVM 단위테스트**(골든/경계 케이스) · §3.0 임계치 주입 | kotlin-implementer | `PROGRESS: P0` |
| **1** | [A] 자동 연동 — `kotlin_krx`(신호2 통계, 코스피 2사 비중) | kotlin-implementer | `PROGRESS: P1` |
| **2** | [B] 자동 연동 — 관세청 수출 · FRED/ECOS 금리 · IPO ETF · 해외지수 | kotlin-implementer | `PROGRESS: P2` |
| **3** | [C][D] 수동 입력 계층 — BottomSheet · Room 오버라이드 · `updatedAt` 배지 · 핵심 화면 | kotlin-implementer | `PROGRESS: P3` |
| **3.5** | **Room 스냅샷 이력 영속 + 국면/방아쇠 전이 감지 + 스파크라인·전이 로그** (§6.1) | kotlin-implementer | `PROGRESS: P3.5-1` → `PROGRESS: P3.5` |
| **4** | **웹/LLM 갱신 + 승인 흐름** (§4.5) | kotlin-implementer | `PROGRESS: P4` |
| **5-1** | WorkManager 주기 갱신 · shimmer · 접근성 · 엣지케이스 마감 | kotlin-implementer | `PROGRESS: P5-1` |
| **5-2** | 최종 QA (§7) | qa-verifier | `LOOP_COMPLETE` |

> **v1.0→v1.2 재편성 주의**: v1.0 계획의 P4(UI 조립)·P5(폴리시)는 이미 완료됐고 `PROGRESS.md`에
> `P4(v1.0-UI)`/`P5(v1.0-폴리시)`로 재태깅돼 있다. 위 표의 P4는 **웹/LLM 갱신**이며 별개다.

### 6.1 Phase 3.5 상세 — Room 이력 · 국면 전이 감지 (v1.2 신설)

배치: Phase 3([C]/[D]) 완료 후, Phase 4(웹/LLM 갱신) 전. 근거 — 리포트 판정 논리("이탈 수+낙폭 동시 확대", "빈도 역전 순간", "임계 접근")가 전부 변화율 개념이므로, 자동 수집(P1~P2)이 갖춰진 직후 시계열 축적을 시작해야 실측 전이를 볼 수 있다.

```kotlin
// data/local — 일 단위 upsert (inputsJson·fieldMetaJson은 §4.6 스키마 그대로)
@Entity(tableName = "bear_snapshot")
data class BearSnapshotEntity(
    @PrimaryKey val day: String,            // "YYYY-MM-DD"
    val phase: String, val lead: Int, val gate: Int,
    val s1: Int, val s2: Int, val s3: Int, val amp: Double,
    val configBasis: String, val inputsJson: String, val fieldMetaJson: String,
    val createdAt: Long,
)

@Dao
interface BearSnapshotDao {
    @Upsert suspend fun upsert(e: BearSnapshotEntity)
    @Query("SELECT * FROM bear_snapshot ORDER BY day DESC LIMIT 1")
    fun observeLatest(): Flow<BearSnapshotEntity?>
    @Query("SELECT * FROM bear_snapshot WHERE day BETWEEN :from AND :to ORDER BY day ASC")
    fun observeRange(from: String, to: String): Flow<List<BearSnapshotEntity>>
    @Query("SELECT * FROM bear_snapshot ORDER BY day DESC LIMIT 1")
    suspend fun latest(): BearSnapshotEntity?
}
```

```kotlin
// domain/usecase — 연속 스냅샷에서 국면·방아쇠 전이 파생
class DetectTransitionsUseCase {
    operator fun invoke(series: List<BearSnapshot>): List<Transition> = buildList {
        for (i in 1 until series.size) {
            val a = series[i - 1]; val b = series[i]
            if (a.phase != b.phase) add(Transition(b.asOf, PhaseChange(a.phase, b.phase)))
            if (b.gate > a.gate)   add(Transition(b.asOf, GateAdvance(b.gate)))
        }
    }
}
```

구현 범위:
- `SnapshotRepository`(upsertToday·observeLatest·observeRange·latestOrNull) + Impl + Hilt 바인딩. 기존 `AppDatabase`에 DAO 추가, 마이그레이션 버전 +1(기존 데이터 무손실).
- 세션 진입 시 `state:latest` 복원(WEB/MANUAL 출처·수동값 유지). 최신 스냅샷 `as_of`가 로컬보다 오래되면 갱신 제안(승인 원칙 유지 — 자동 반영 금지).
- 프레젠테이션: `Sparkline`(lead·gate 60~90일) + `TransitionLog`("6/30 GREEN→AMBER · 방아쇠 경계 접근"). Canvas로 경량 구현. 이력 상태 3종(empty/단일/다수) 처리.

### 6.2 PROGRESS.md 마커 순서
```
P0 → P1 → P2 → P3 → P3.5-1 → P3.5 → P4 → P5-1 → LOOP_COMPLETE
```

---

## 7. 수용 기준 (qa-verifier 체크리스트)

- [ ] **스코어링 동치:** §3의 모든 함수가 프로토타입과 동일 입력→동일 출력. 골든 케이스 `→ AMBER`, 각 임계 경계(예: neg=6/7, rate=4.49/4.5, ratio=0.94/0.95/1.0, loss=44/45/59/60/79/80) 테스트 통과.
- [ ] **기능 무손실:** 부록 B의 9개 블록 전부 존재·동작.
- [ ] **데이터:** [A][B] 자동 갱신 성공 및 실패 시 캐시+갱신일 표시, [C][D] 수동 입력이 즉시 재계산 반영.
- [ ] **아키텍처:** Clean 레이어 분리, Hilt 주입, ViewModel `StateFlow`, UI는 Compose only, 스코어링에 안드로이드 의존성 0.
- [ ] **모바일:** 360dp에서 표(전치)·게이지·레이더 정상, 다크모드, 폰트 스케일 1.3x 붕괴 없음.
- [ ] **오프라인/성능:** 캐시 우선 렌더, shimmer, WorkManager 트리거 동작.
- [ ] **이력 영속(v1.2):** 앱 재시작 후에도 일자별 스냅샷 유지(upsert·range 조회 통과).
- [ ] **전이 감지(v1.2):** 국면·방아쇠 전이 로그 표시, in-memory DB 테스트 통과.
- [ ] **승인 흐름(v1.2):** §4.5 제안이 승인 없이 상태를 바꾸지 않음, MANUAL 불패.
- [ ] **config 구동(v1.2):** `bear_thresholds.json` 교체만으로 판정 변화(코드 무수정), §3 임계치 코드 하드코딩 없음.

---

## 8. 리스크 · 대응

| 리스크 | 대응 |
|---|---|
| 해외지수·신용잔고 API 커버리지 부족 | 수동 폴백 + `updatedAt`. v2에서 KRX 정보데이터시스템/KOFIA 배치 |
| 관세청 HS ↔ 15대 품목(MTI) 매핑 | 매핑 테이블 명시 or K-stat MTI 통계 사용 |
| 정성 지표(정책 방향·대어 소화·반대매매) | v1 수동 + §4.5 LLM 제안(승인제). v2에서 SentimentAnalysisEngine 연계 |
| DART 증권신고서 파싱 난도 | v1 수동. 자동화 필요 시 v2에서 Chaquopy Python 포팅 후보 |
| 임계치 임의 변경 | `bear_thresholds.json`이 SSOT(§3.0). 변경은 리포트 근거 + 골든 테스트 갱신 동반 시에만 |
| LLM 제안 오염(환각·낡은 값) | 화이트리스트 검증 + STALE 마킹 + 급변 재확인 + 승인제(§4.5) |

---

## 부록 A — Kotlin 스코어링 스켈레톤 (순수 함수, 안드로이드 의존성 0)

> v1.2 주: 아래 스켈레톤의 하드코딩 숫자는 §3.0 `BearThresholds` 주입 참조로 대체됐다(값 동일).
> 로직·비교 연산자·반올림은 불변.

```kotlin
enum class SignalLevel(val label: String) { SAFE("안전"), CAUTION("주의"), WARN("경고"), DANGER("위험") }
enum class GateState(val label: String) { NORMALIZING("정상화 구간"), NEARING("경계 접근"), CRITICAL("임계 접근"), TIGHTENING("긴축 돌입") }
enum class BearPhase { GREEN, AMBER, ORANGE, RED }
enum class Depth { SHALLOW, DEEPENING, DEEP }
enum class InputSource { AUTO, MANUAL }

data class MarketReturns(val name: String, val r: List<Double?>, val lead: Boolean = false) // r = [-12M,-6M,-3M,-1M]

data class MarketAnalysis(val neg: Int, val worstNew: Double, val depth: Depth)

fun analyzeMarkets(markets: List<MarketReturns>, periodIdx: Int): MarketAnalysis {
    var neg = 0; var worstNew = 0.0
    markets.forEach { m ->
        val v = m.r.getOrNull(periodIdx); val v12 = m.r.getOrNull(0)
        if (v != null && v < 0) { neg++; if (v12 != null && v12 > 0) worstNew = minOf(worstNew, v) }
    }
    val depth = when { worstNew <= -12 -> Depth.DEEP; worstNew <= -6 -> Depth.DEEPENING; else -> Depth.SHALLOW }
    return MarketAnalysis(neg, worstNew, depth)
}

fun scoreS1(neg: Int, depth: Depth): Int {
    val many = neg >= 7
    if (!many) return if (depth == Depth.DEEP) 1 else 0
    return when (depth) { Depth.SHALLOW -> 1; Depth.DEEPENING -> 2; Depth.DEEP -> 3 }
}

fun scoreS2(up: Int, down: Int, deepening: Boolean): Int {
    val r = if (up == 0) 9.0 else down.toDouble() / up
    return when { r > 1.0 -> 3; r >= 0.95 -> 2; r >= 0.7 -> if (deepening) 1 else 0; else -> 0 }
}

fun scoreS3(loss: Double, etf: String, big: String): Int {
    var lv = when { loss >= 80 -> 3; loss >= 60 -> 2; loss >= 45 -> 1; else -> 0 }
    if (etf == "down") lv = maxOf(lv, 2)
    lv = if (big == "failed") maxOf(lv, 3) else if (big == "pending") maxOf(lv, 1) else lv
    return lv
}

fun scoreGate(rate: Double, dir: String, credit: Double, margin: Boolean): Int {
    var lv = when { rate >= 4.5 -> 3; rate >= 4.0 -> 2; else -> if (dir == "hike") 1 else 0 }
    lv = if (margin) maxOf(lv, 2) else if (credit >= 35) maxOf(lv, 1) else lv
    return lv
}

fun amplifier(semi: Double, kospi2: Double, buffer: Boolean): Double {
    var a = 1.0 + (if (semi >= 20) .15 else .0) + (if (kospi2 >= 50) .15 else .0) + (if (!buffer) .20 else .0)
    return minOf(a, 1.6)
}

data class BearResult(
    val s1: Int, val s2: Int, val s3: Int, val gate: Int, val amp: Double,
    val lead: Int, val leadPct: Int, val warn: Int, val phase: BearPhase, val ma: MarketAnalysis
)

fun composite(i: BearSignalInputs): BearResult {
    val ma = analyzeMarkets(i.markets, i.periodIdx)
    val s1 = scoreS1(ma.neg, ma.depth)
    val s2 = scoreS2(i.up, i.down, i.deepening)
    val s3 = scoreS3(i.loss, i.etf, i.big)
    val gate = scoreGate(i.rate, i.dir, i.credit, i.margin)
    val amp = amplifier(i.semi, i.kospi2, i.buffer)
    val lead = s1 + s2 + s3
    val warn = listOf(s1, s2, s3).count { it >= 2 }
    val phase = when {
        gate >= 3 && warn >= 1 -> BearPhase.RED
        gate >= 2 || (lead >= 6 && gate >= 1) -> BearPhase.ORANGE
        lead >= 3 || gate >= 1 -> BearPhase.AMBER
        else -> BearPhase.GREEN
    }
    return BearResult(s1, s2, s3, gate, amp, lead, Math.round(lead / 9.0 * 100).toInt(), warn, phase, ma)
}
```

---

## 부록 B — 프로토타입 기능 ↔ 이식 매핑 (무손실 체크)

| # | 프로토타입 기능 | 이식 위치 | 필수 |
|---|---|---|---|
| 1 | 4대 신호 게이지·레벨(안전/주의/경고/위험) | 헤더+선행 카드 | ✅ |
| 2 | 금리 방아쇠 GATE(4상태) | 방아쇠 카드 | ✅ |
| 3 | 집중 증폭 계수(×1.0~1.6) | 증폭 카드 | ✅ |
| 4 | 종합 국면 신호등 + 선행점수 + 레이더 | 헤더 | ✅ |
| 5 | 약세장 3유형 + 회복 가능성 + 모니터링 체크리스트 + 활성 방아쇠 | 유형 카드 | ✅ |
| 6 | 역사 검증(일본 3충격) + 3대 모니터링 | 역사 섹션 | ✅ |
| 7 | 신호1 상세 국가별 수익률 표(도표48, 기간 선택·자동 산출·편집) | 표 섹션(전치) | ✅ |
| 8 | 리포트 기준값 리셋 | 헤더 액션 | ✅ |
| 9 | 지표↔리포트 매핑 + 면책 | 푸터 | ✅ |

---

## 부록 C — 시드 데이터 (프로토타입에서 이관)

- **도표48 국가별 수익률**(20지수 × [−12M,−6M,−3M,−1M], 코스피 lead=true): 코스피 173.1/103.7/54.0/4.5 … RTS −17.1/−16.4/−13.7/−17.6. (`bear_signal_dashboard.jsx` `MARKETS` 그대로)
- **기준값(2026.6.30):** period=1m, up=14/down=12/deepening=true, loss=45/etf=up/big=pending, rate=3.75/dir=hike/credit=38/margin=false, semi=23.1/kospi2=56/buffer=true → **국면 AMBER**.
- **3유형·모니터링 체크리스트·역사(일본 3충격)·3대 지표:** 프로토타입 `TYPES`/역사 섹션 텍스트 그대로 이관.

> 원문·임계치는 신영증권「주도주의 물리학」(2026.6.30)에만 근거. 본 계기판은 투자 판단 보조 도구이며 종목 선택·투자 시기의 최종 책임 근거로 사용될 수 없다.
