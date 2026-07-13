# TASK — 「주도주 붕괴 판단 계기판」 TinyOscillator 신규 메뉴 이식 명세서 (v1.0)

> 대상: Claude Code (kotlin-implementer / qa-verifier 서브에이전트)
> 근거 프로토타입: `bear_signal_dashboard.jsx` — **스코어링 로직·임계치의 단일 진실 공급원(SSOT)**
> 원전: 신영증권「주도주의 물리학」(2026.6.30)
> 진행 규칙: 각 Phase 완료 시 `PROGRESS:` 마커 갱신, 기존 `TASK.md`/`PROGRESS.md` 흐름 준수

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
| | 해외 19개 지수 동일 기간 수익률 | KIS 해외시세(제한적) 또는 무료 CSV(예: Stooq) | 부분 | **[B~C]** ¹ |
| **신호2 변동성 무게중심** | 코스피 일별 종가(직전 6M) → ±3σ/±4σ 상승·하락일 | `kotlin_krx` `get_index_ohlcv` | ✅ | **[A]** ² |
| **신호3 IPO 질** | Renaissance IPO ETF(티커 `IPO`) 방향 | KIS 해외 또는 무료 CSV | 부분 | **[B]** |
| | KR 상장사 적자·신주 발행 비중 | DART 증권신고서(파싱 난도 高) | 부분 | **[C~D]** ³ |
| | 대어(OpenAI·Anthropic) 공모 소화 | 뉴스/정성 | — | **[D]** |
| **신호4 금리(결정타)** | 한국은행 기준금리·방향 | **BOK ECOS**(기준금리 통계) | ✅ | **[A]** |
| | 미 연준 목표금리 상단 | FRED(예: `DFEDTARU`) | — | **[B]** |
| | 신용거래융자 잔고 | KRX 정보데이터시스템/KOFIA(**pykrx 미제공**) | ❌ | **[C]** ⁴ |
| | 반대매매 임박(담보유지 140% 근접) | 집계 미공개 → 파생/판단 | — | **[D]** |
| **증폭 · 집중** | 삼성전자+SK하이닉스 코스피 비중 | `kotlin_krx` 시가총액(`get_market_cap*`) | ✅ | **[A]** |
| | 반도체·완충(자동차/기계/석유) 수출 비중 | **관세청 무역통계 Open API** `apis.data.go.kr/1220000/nitemtrade/getNitemtradeList` (월 주기) 또는 무역협회 K-stat | — | **[B]** ⁵ |

> ¹ 해외지수: KIS 해외시세는 미국·일본·홍콩·중국 등 주요 거래소 위주로, RTS(러시아)·SET(태국)·JKSE(인니) 등 롱테일은 미커버 가능성. **미커버 지수는 수동 입력 폴백**(월 1회, 리포트 시드값 제공).
> ² 통계량(평균·표준편차·임계 초과일 카운트)은 Kotlin으로 자체 계산 → **Python 불필요**.
> ³ 증권신고서에서 신주/구주·직전 12M 적자 여부 자동 추출은 문서 파싱 난도가 높음. **v1은 수동 입력**, DART 자동 파싱은 v2 후보(그때 필요 시 Chaquopy Python 포팅 검토).
> ⁴ 신용거래융자 잔고는 `pykrx`(=`kotlin_krx`)가 제공하지 않음(공매도는 제공). KRX 정보데이터시스템/KOFIA 별도 수집 필요. **v1은 수동 입력**, v2에서 배치 스크래핑.
> ⁵ 리포트는 "15대 품목(MTI)" 기준. 관세청 API는 HS Code 기준이므로 **HS→MTI(또는 15대 품목) 매핑 테이블** 필요, 또는 K-stat의 MTI품목별 통계 활용.

### 1.2 결론 — 제작 가능 여부

- **제작 가능하다.** 스코어링은 전부 순수 산술이므로 Kotlin native로 구현하고, 수집은 [A]/[B] 지표를 자동화한다.
- **Python(Chaquopy) 불필요.** v1 범위에서 자동화가 어려운 항목([C]/[D])은 **수동 입력 필드**로 처리하되 리포트 스냅샷을 기본값으로 프리시드하고 "최신 갱신일" 배지를 표기한다. → **계기판 기능은 하나도 누락되지 않는다.**
- 설계 골격: **하이브리드 데이터 아키텍처** = (자동 수집값) ⊕ (수동 오버라이드) 병합, 각 입력에 `source`(AUTO/MANUAL)와 `updatedAt`을 부착.

---

## 2. 아키텍처 (Clean Architecture · 기존 패턴 준수)

```
feature/bearsignal/
├─ domain/
│  ├─ model/            BearSignalInputs, BearSignalResult, SignalLevel, GateState,
│  │                    BearPhase, MarketReturns, MarketAnalysis, BearType, MonitorItem, InputSource
│  ├─ repository/       BearSignalRepository (interface)
│  └─ usecase/          ComputeBearSignalUseCase        (순수 스코어링, I/O 없음)
│                       ObserveBearSignalStateUseCase   (Flow<BearSignalResult>)
│                       RefreshAutoInputsUseCase        ([A][B] 자동 수집)
│                       UpdateManualInputUseCase        ([C][D] 수동 반영)
│                       ResetToReportBaselineUseCase
├─ data/
│  ├─ remote/           GlobalIndexApi, IpoEtfApi, FredApi, CustomsTradeApi(관세청)
│  │                    (+ 기존 EcosDataSource, KrxDataSource 재사용)
│  ├─ local/            BearSignalDao, CountryReturnEntity, ManualInputEntity, AutoCacheEntity
│  ├─ mapper/           *Dto ↔ domain
│  └─ repository/       BearSignalRepositoryImpl (auto ⊕ manual 병합)
├─ di/                  BearSignalModule (@Module @InstallIn — Hilt)
└─ presentation/
   ├─ BearSignalViewModel   (@HiltViewModel, StateFlow<BearSignalUiState>)
   ├─ BearSignalUiState
   └─ ui/                   BearSignalScreen + components (Compose)
```

- 스코어링은 **`ComputeBearSignalUseCase`에 격리**(플랫폼 의존성 0, JVM 단위테스트 대상).
- 자동 수집 실패 시 Room 캐시 + `updatedAt` 노출(오프라인 우선 표시 후 백그라운드 갱신, 기존 패턴).
- 주기 갱신은 **WorkManager**(월 1회, 무역통계·금리 발표 주기에 맞춤).

---

## 3. 스코어링 로직 (프로토타입과 1:1 — 임계치 변경 금지) ★

레벨 정의: `0 안전 · 1 주의 · 2 경고 · 3 위험`. 아래 임계치는 프로토타입과 **동일해야 하며 임의 변경 불가**.

### 3.1 신호1 — 주변부 압착 (도표 46~48)
```
analyzeMarkets(markets, period):
  col = 기간 인덱스 { 12m:0, 6m:1, 3m:2, 1m:3 } (기본 1m)
  neg = markets 중 해당 기간 수익률 < 0 개수
  worstNew = min( 해당 기간 수익률 )  단, 12M 수익률 > 0 이고 해당 기간 < 0 인 지수만 (신규 이탈)
  depth = worstNew <= -12 → "deep"; <= -6 → "deepening"; else "shallow"

scoreS1(neg, depth):
  many = neg >= 7                         # 닷컴 정점 직전 = 7개국
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
  if r > 1.0: return 3            # 큰 하락일이 큰 상승일 추월 = 천장
  if r >= 0.95: return 2
  if r >= 0.7: return deepening ? 1 : 0
  return 0
```

### 3.3 신호3 — IPO 질 (도표 51~53)
```
scoreS3(loss, etf, big):          # loss=적자상장비중%, etf∈{up,flat,down}, big∈{smooth,pending,failed}
  lv = (loss>=80)?3 : (loss>=60)?2 : (loss>=45)?1 : 0     # 평상 20~40, 버블 ~80
  if etf=="down": lv = max(lv, 2)
  if big=="failed": lv = max(lv, 3) else if big=="pending": lv = max(lv, 1)
  return lv
```

### 3.4 신호4 — 금리 방아쇠 [GATE] (도표 54~57)
```
scoreGate(rate, dir, credit, margin):   # rate=기준금리상단%, dir∈{ease,hold,hike}, credit=신용잔고(조), margin=반대매매임박
  lv = (rate>=4.5)?3 : (rate>=4.0)?2 : (dir=="hike"?1:0)   # 임계 4.5% = 진짜 긴축
  if margin: lv = max(lv, 2) else if credit>=35: lv = max(lv, 1)   # 2023말 17.5조 → 2배 이상
  return lv
# 상태 라벨: 0 정상화 구간 · 1 경계 접근 · 2 임계 접근 · 3 긴축 돌입
```

### 3.5 증폭 · 집중 [AMP] (유형4, 도표 44)
```
amplifier(semi, kospi2, buffer):  # semi=반도체수출비중%, kospi2=삼성+SK 코스피비중%, buffer=완충산업건재
  a = 1.0 + (semi>=20 ? .15:0) + (kospi2>=50 ? .15:0) + (!buffer ? .20:0)
  return min(a, 1.6)
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
    (gate>=3 && warn>=1)              → RED     # 긴축 돌입 + 선행 경고 = 톱니바퀴 격발
    (gate>=2 || (lead>=6 && gate>=1)) → ORANGE  # 방아쇠 임박
    (lead>=3 || gate>=1)              → AMBER   # 신호 점등 · 방아쇠 대기
    else                             → GREEN    # 안정
```
**골든 케이스(리포트 2026.6.30 기준값):** `s1=1, s2=1, s3=1, gate=1, amp=1.30 → AMBER`. 이식본이 반드시 재현해야 함.

### 3.7 정적 참조 데이터 (부록 C 시드)
- **약세장 3유형**(회복 가능성): 유형1 경쟁·역전=최저 / 유형2 전방수요·사이클=중간 / 유형3 밸류·금리=펀더멘털 생존·인내. **유형3이 현재 활성 방아쇠(gate≥1일 때 하이라이트).**
- **유형별 모니터링 체크리스트**, **역사 검증(일본 1980s 3충격 + 3대 지표)**, **도표48 국가별 수익률 시드**(20개 지수 × 4기간) — 프로토타입 값 그대로 이관(부록 C).

---

## 4. 데이터 소스 연동 명세 (지표별)

| 지표 | 엔드포인트/메서드 | 파라미터·매핑 | 주기 | 폴백 |
|---|---|---|---|---|
| 코스피 지수 시세 | `kotlin_krx get_index_ohlcv("1001")` | 최근 ~130영업일 종가 | 일 | 캐시 |
| 신호2 통계 | (자체 계산) | μ,σ,±3σ/±4σ 카운트 | 일 | — |
| 코스피 2사 비중 | `kotlin_krx get_market_cap*` | (SS+SK 시총)/(KOSPI 시총) | 일 | 캐시 |
| 해외 19개 지수 | `GlobalIndexApi`(KIS 해외 또는 무료 CSV) | 지수코드→기간수익률 | 월 | **미커버=수동** |
| IPO ETF 방향 | `IpoEtfApi`(티커 `IPO`) | 최근 고점 대비 방향 up/flat/down | 주 | 수동 |
| 한은 기준금리 | 기존 `EcosDataSource` | 기준금리 통계코드(재사용) | 이벤트 | 수동 |
| 미 연준 상단 | `FredApi`(예: `DFEDTARU`) | series → 최신값 | 이벤트 | 수동 |
| 수출 비중 | `CustomsTradeApi` `…/getNitemtradeList` | 인증키·기간·HS(반도체 8541/8542, 자동차 8703, 기계 84xx, 석유 27xx …) → 15대품목 매핑 | 월 | K-stat/수동 |
| 신용잔고 | (v1 수동) | KRX 정보데이터시스템/KOFIA(v2 배치) | 월(v1) | 수동 |
| 대어 소화·정책방향·반대매매 | (수동) | 뉴스/판단, SentimentEngine 연계는 v2 | 이벤트 | 수동 |

> 관세청 Open API는 공공데이터포털 활용신청·인증키 필요(개발계정 10,000 트래픽). BuildConfig/보안 저장소에 키 관리(기존 KIS 키 관리 패턴 준수).

---

## 5. UI/UX 명세 (Compose · Material 3 · 모바일)

### 5.1 진입점(신규 메뉴) — 권장안
- **권장:** 기존 4탭을 유지하고, 시장/대시보드 성격 탭 안에 **"시장 국면 · 리스크" 카드**를 두어 `BearSignalScreen`으로 내비게이트(하단바 혼잡 회피).
- **대안:** 상시 노출이 중요하면 **5번째 하단 탭 "국면"**(Material 3 권장 3~5 목적지 내). 최종 배치는 기존 네비게이션 그래프 관례에 맞춰 선택.

### 5.2 화면 구조 (`LazyColumn` 섹션)
1. **종합 국면 헤더** — 신호등(GREEN/AMBER/ORANGE/RED 4등) · 선행점수 게이지(0~100) · 방아쇠 상태 · 증폭 배수 · **레이더(4축)**. 국면별 색·해설 문구.
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
| **0** | 스캐폴딩 · 도메인 모델 · **순수 스코어링 + JVM 단위테스트**(골든/경계 케이스) | kotlin-implementer | `PROGRESS: P0` |
| **1** | [A] 자동 연동 — `kotlin_krx`(신호2 통계, 코스피 2사 비중) | kotlin-implementer | `PROGRESS: P1` |
| **2** | [B] 자동 연동 — 관세청 수출 · FRED/ECOS 금리 · IPO ETF · 해외지수 | kotlin-implementer | `PROGRESS: P2` |
| **3** | [C][D] 수동 입력 계층 — BottomSheet · Room 오버라이드 · `updatedAt` 배지 | kotlin-implementer | `PROGRESS: P3` |
| **4** | UI 조립 — 헤더·카드·표(전치)·유형·역사, 모바일/다크 대응 | kotlin-implementer | `PROGRESS: P4` |
| **5** | WorkManager 주기 갱신 · shimmer · 접근성 · 최종 QA | qa-verifier | `PROGRESS: P5` |

---

## 7. 수용 기준 (qa-verifier 체크리스트)

- [ ] **스코어링 동치:** §3의 모든 함수가 프로토타입과 동일 입력→동일 출력. 골든 케이스 `→ AMBER`, 각 임계 경계(예: neg=6/7, rate=4.49/4.5, ratio=0.94/0.95/1.0, loss=44/45/59/60/79/80) 테스트 통과.
- [ ] **기능 무손실:** 부록 B의 9개 블록 전부 존재·동작.
- [ ] **데이터:** [A][B] 자동 갱신 성공 및 실패 시 캐시+갱신일 표시, [C][D] 수동 입력이 즉시 재계산 반영.
- [ ] **아키텍처:** Clean 레이어 분리, Hilt 주입, ViewModel `StateFlow`, UI는 Compose only, 스코어링에 안드로이드 의존성 0.
- [ ] **모바일:** 360dp에서 표(전치)·게이지·레이더 정상, 다크모드, 폰트 스케일 1.3x 붕괴 없음.
- [ ] **오프라인/성능:** 캐시 우선 렌더, shimmer, WorkManager 월간 트리거 동작.

---

## 8. 리스크 · 대응

| 리스크 | 대응 |
|---|---|
| 해외지수·신용잔고 API 커버리지 부족 | 수동 폴백 + `updatedAt`. v2에서 KRX 정보데이터시스템/KOFIA 배치 |
| 관세청 HS ↔ 15대 품목(MTI) 매핑 | 매핑 테이블 명시 or K-stat MTI 통계 사용 |
| 정성 지표(정책 방향·대어 소화·반대매매) | v1 수동. v2에서 SentimentAnalysisEngine 연계 |
| DART 증권신고서 파싱 난도 | v1 수동. 자동화 필요 시 v2에서 Chaquopy Python 포팅 후보 |
| 임계치 임의 변경 | §3은 SSOT. 변경은 리포트 근거 + 골든 테스트 갱신 동반 시에만 |

---

## 부록 A — Kotlin 스코어링 스켈레톤 (순수 함수, 안드로이드 의존성 0)

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
