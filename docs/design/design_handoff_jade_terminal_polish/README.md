# Handoff: Jade Terminal 디자인 폴리시 (TinyOscillator)

## Overview
TinyOscillator(한국 리테일 주식분석 Android 앱)의 디자인 리뷰에서 도출된 개선안을 실제
코드베이스에 반영하기 위한 핸드오프입니다. 목표는 **이미 존재하는 자체 디자인 시스템("Jade
Terminal")을 화면에서 실제로 일관되게 쓰도록** 만드는 것 — 전면 리디자인이 아니라, 정의돼
있으나 미사용인 컴포넌트/토큰으로 수렴시키는 폴리시입니다.

리뷰 근거는 5개 초점: 시각적 완성도 · 정보 밀도/가독성 · UX 흐름/내비게이션 · 일관성 · 접근성.
종합 판정: 6.8/10 (단단한 시스템, 흐트러진 적용).

## About the Design Files
이 번들의 `.dc.html` 파일들은 **HTML로 만든 디자인 레퍼런스**입니다 — 의도한 룩앤필과 동작을
보여주는 프로토타입이며, 그대로 복붙할 프로덕션 코드가 아닙니다. 작업은 이 디자인을 **대상
코드베이스의 기존 환경(Kotlin + Jetpack Compose, Material3, MVVM + Clean)에서, 기존 패턴과
라이브러리로 재현**하는 것입니다.

- HTML 프로토타입의 색·간격·서체 값은 최종 사양입니다(hifi). Compose에서 동일 값으로 재현하세요.
- HTML의 인라인 스타일/레이아웃 구조는 참고용입니다 — Compose의 `Card`, `Row/Column`,
  `LazyColumn`, 커스텀 컴포저블로 옮기세요.

## Fidelity
**High-fidelity (hifi).** 색은 hex 확정, 서체·간격·반경 확정. 픽셀 단위로 재현하되 구현은
코드베이스의 기존 컴포저블(`FinanceCard`, `PillTabRow`, `CarvedTextField`, `SectionHeader` 등
`presentation/common/DesignComponents.kt`)을 사용합니다. 이 컴포넌트들은 **이미 존재**하므로
대부분의 작업은 "신규 작성"이 아니라 "치환"입니다.

## 코드베이스 운영 규칙 (반드시 준수)
- 환경: Windows / PowerShell. `JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`.
- 빌드: `.\gradlew.bat :app:assembleDebug --console=plain`
- 타겟 테스트만 실행(전체 스위트 금지, ~1,420건 느림):
  `.\gradlew.bat :app:testDebugUnitTest --tests "com.tinyoscillator.<Class>"`
- **BearSignal 스코어링·임계치는 불가침**: `bear_thresholds.json`과 스코어링 함수는 골든 테스트로
  가드된 SSOT. 이번 작업은 전부 **표시 계층**이므로 스코어링 로직·임계치·`ComputeBearSignalUseCase`
  를 건드리지 마세요. UI 파생 상태가 필요하면 ViewModel에 파생 플래그만 추가.
- Room 마이그레이션/도메인/데이터 계층 변경 없음. 전 작업이 `presentation/`·`ui/theme/`에 한정.
- 커밋은 사용자 명시 요청 시에만. main이면 브랜치 분기.

---

## 작업 항목 (1·2부 리뷰 → 12개 작업)

우선순위·난이도·임팩트는 `개선 계획.dc.html` 참조. 아래는 파일·값·수용 기준.

### P1 · 파운데이션 (난이도 하, 회귀 최소)

**T2 — 한글 디스플레이 서체 페어링** *(가장 체감 큼)*
- 문제: 디스플레이 서체 Syne가 한글 글리프 없음 → 한글 제목이 Manrope→시스템 폴백으로 렌더.
- 조치: `ui/theme/Type.kt` — 디스플레이 패밀리를 **Gothic A1 (ExtraBold 800)**로 교체.
  앱은 이미 `GoogleFont.Provider`(다운로더블 폰트)를 쓰므로 선언만 추가.
  ```kotlin
  private val gothicA1 = GoogleFont("Gothic A1")
  val DisplayFamily = FontFamily(
      Font(googleFont = gothicA1, fontProvider = fontProvider, weight = FontWeight.Bold),
      Font(googleFont = gothicA1, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
      // 번들 폴백 유지: Manrope
      Font(R.font.manrope_bold, FontWeight.Bold),
  )
  ```
  `displayLarge/Medium/Small`·`headline*`·`title*`의 `fontFamily`를 `DisplayFamily`로.
  본문(`body*`/`label*`)은 DM Sans 유지(Noto Sans KR이 한글 담당 — 시스템 폴백).
  letterSpacing은 기존 값 유지(display -1.5~-0.5sp).
- 수용 기준: "오늘의 시장"·"시장 선택" 등 한글 제목이 Gothic A1로 렌더(에뮬레이터 육안), 숫자
  헤드라인도 동일 패밀리로 통일. 앱 빌드 통과.

**T1 — 차트 색 테마화** *(사양: `차트 팔레트 리매핑.dc.html`)*
- 문제: MPAndroidChart 색이 여러 `AndroidView` 콜백에 Material 기본색으로 하드코딩.
- 조치: `ChartTheme(isDark: Boolean)` data class 신설(예: `presentation/chart/ChartTheme.kt`),
  아래 값을 반환. 각 차트 콜백이 하드코딩 대신 이 객체 참조. 기존 `luminance() < 0.5` 분기 유지.
- 리매핑(다크 기준):
  | 시리즈 | 현재 | → 신규 | 위치 |
  |---|---|---|---|
  | 시장 지수선 | `#1976D2` | `#E8C36A` (secondary) | `MarketAnalysisScreen.kt · bindMarketDemarkData` |
  | TD Sell(고점) | `#F44336` | `#EF7B68` (finance.positive) | 동일 · sellDataSet |
  | TD Buy(저점) | `#2196F3` | `#68B0F0` (finance.negative) | 동일 · buyDataSet |
  | 9+ 강조 마커 | `#F44336` | `#EF7B68` | 동일 · sellHighlight |
  | 격자선 | `#444444` | `#2A3040` (surfaceVariant) | setupMarketDemarkChart · gColor |
  | 축 텍스트 | `WHITE` | `#A8A4A0` (onSurfaceVariant) | chartTextColor |
  라이트: 지수 `#7A5A10`, Sell `#D05540`, Buy `#4088CC`, 격자 `#E0D8C8`, 축 `#504A3A`.
- 규칙: 부호는 한국식 고정(상승/과매수/TD Sell=적, 하락/TD Buy=청). 중립 지수선은 청자·황동.
- 수용 기준: DeMark 지수선이 파랑이 아니며 TD Buy와 구분됨. 캔들 양봉=적/음봉=청 관례 통일.

**T3 — "경고(오렌지)" 토큰화**
- 문제: 레벨2 색 `#E8823A`(다크)/`#B25D1E`(라이트)가 `BearSignalColors.kt`에 하드코딩.
- 조치: `ui/theme/Theme.kt`의 `ExtendedColors`에 `warn` 필드 추가(다크/라이트 각 값), `levelColor()`
  가 `LocalExtendedColors.current.warn` 참조. 값 변경 없음, 위치만 토큰으로.
- 수용 기준: `levelColor(2)`가 테마 토큰 경유. bearsignal 골든 테스트 재실행 그린.

**T4 — 저대비 캡션 상향**
- 문제: 10~11px 캡션을 `onSurfaceVariant(#A8A4A0)`로 먹빛(#0B0E14) 위 → AA 경계.
- 조치: `Color.kt`의 `DarkOnSurfaceVariant`를 `#A8A4A0` → `#B3AEA6`로 상향(대비 ≥4.5:1 확보).
  라이트(`#504A3A`)는 유지(이미 충분).
- 수용 기준: 잔글씨(공포·탐욕, 게이지 라벨, 갱신시각) 대비 WCAG AA 통과.

### P2 · 컴포넌트 통일 (난이도 중, 화면 단위 PR 권장)

**T5 — 카드 통일 (FinanceCard)**
- 문제: 기본 `Card` + `surfaceVariant.copy(alpha=.5)`가 홈·핵심 화면에 쓰여 시그니처(비대칭
  코너+그라디언트 보더) 실종. `FinanceCard`/`GlassCard`는 정의됐으나 미사용.
- 조치: 아래 사용처의 `Card {...}`를 `FinanceCard {...}`로 치환.
  - `MarketSummaryCard.kt`("오늘의 시장"), `MarketAnalysisScreen.kt`(FearGreedSummaryCard, 시장선택),
    `BearSignalEntryCard.kt`, `BearSignalHeaderSection.kt`·`SignalCard`(BearSignal 카드류),
    `OscillatorScreen.kt`(HistoryItem 등).
  - 히어로급 1개(홈 최상단)는 `GlassCard` 고려.
- 수용 기준: 홈·BearSignal 주요 카드가 비대칭 코너(20/6/20/6)+제이드 링으로 렌더.

**T6 — 칩·검색창 단일화**
- 문제: 커스텀 `PillTabRow`와 Material `FilterChip`이 한 화면에 공존(칩 패러다임 2종); 검색창이
  `CarvedTextField` 대신 로컬 `OutlinedTextField` 복붙.
- 조치:
  - 규칙 확정: **내비/기간 전환 = PillTabRow(또는 세그먼트), 다중 필터 = FilterChip 한 스타일만**.
    `MarketAnalysisScreen.kt`의 "시장 선택" FilterChip → 세그먼트/Pill로, 기간칩과 스타일 정합.
  - `OscillatorScreen.kt`의 검색 `OutlinedTextField` → `CarvedTextField`(DesignComponents.kt) 사용.
- 수용 기준: 한 화면에 칩 스타일 1종. 검색창이 공용 컴포넌트 경유.

**T7 — SectionHeader 2단 개편** *(개선 예시: 프로토타입의 헤더)*
- 문제: BearSignal 섹션 헤더가 서술형 한 문장("온도계 · 선행 신호 3종 — 위험선호가 …") → 스캔 불가.
- 조치: `DesignComponents.kt`의 `SectionHeader`에 `subtitle: String? = null` 파라미터 추가(짧은
  디스플레이 제목 + 작은 캡션 2단). `BearSignalScreen.kt`의 각 `SectionHeader(title=…)` 호출을
  `title="선행 신호", subtitle="위험선호가 어디까지 식었나 · 온도계 3종"` 형태로 분리.
- 수용 기준: 헤더가 목차처럼 스캔됨. 기존 정보(부제) 손실 없음.

**T10 — AI 채팅 테마화** *(2부 · AI 분석)*
- 문제: 대화 말풍선·입력 바가 범용 메신저 톤이라 카드 시스템 밖. 토큰 사용량(입력/출력)이
  사용자에게 상시 노출(개발자 지표).
- 조치: `presentation/ai/AiAnalysisChatSection.kt`(+`AiAnalysisCommonComponents.kt`) —
  말풍선 색을 테마 토큰으로 고정(사용자=`primary`/`onPrimary`, AI=`surfaceContainer`/`onSurface`),
  코너 반경을 시스템 값과 정합(예 14dp, 꼬리쪽 4dp). 입력 바는 `CarvedTextField` 계열로.
  토큰 사용량 표시는 **기본 숨김** — 필요 시 설정/개발자 옵션 뒤로.
- 수용 기준: 채팅이 앱의 카드·색 언어와 이어짐. 일반 화면에서 토큰량 미노출.

### P3 · 화면 재구성 (난이도 중상, 디자인 선행 = 프로토타입)

참조: `개선 시안 프로토타입.dc.html`(폰), `개선 시안 2pane.dc.html`(태블릿/폴더블).

**T8 — 홈 위계 재구성**
- 조치: `MarketAnalysisScreen.kt` FearGreedTab — **Fear&Greed 점수를 히어로**(대형 숫자)로 최상단
  배치. 기존 `MarketSummaryCard`의 F&G 지표와 `FearGreedSummaryCard`의 중복 표시를 하나로 통합.
  나머지 지표(오실레이터·예탁금·상위테마)는 compact 스트립으로 밀도↓. BearSignal 진입 카드는 유지.
- 수용 기준: 스크롤 첫 화면에서 "오늘의 한 줄"(F&G)이 시각적 1순위. F&G 중복 카드 제거.

**T9 — BearSignal 반응형 재편**
- 결정된 방식:
  - **폰(1-pane, `WindowType.COMPACT`)**: 요약(헤더: 국면·선행점수·방아쇠·증폭·경고)은 항상 표시,
    나머지 세부(선행신호·국가표·방아쇠·유형·역사)는 **아코디언**으로 접기.
  - **태블릿/폴더블(2-pane)**: **좌측 요약+세부 목록 / 우측 상세** 마스터·디테일로 분리.
    `MainActivity.kt`의 기존 `WindowType`/`NavigationRail` 분기와 동일한 기준 사용.
  - "정세 업데이트" 버튼은 유형·역사 두 곳 중복 → **한 곳(상단 액션)으로 통합**.
- 상태: ViewModel에 `expandedSections: Set<Key>`(폰) / `selectedSection`(2-pane) 파생 상태 추가.
  스코어링·데이터 무접촉.
- 수용 기준: 폰에서 스크롤 길이 대폭 감소, 요약 즉시 가시. 넓은 화면에서 목록↔상세 동작.

**T11 — 설정 "데이터" 탭 분해** *(2부 · 설정)*
- 문제: `DataManagementTab`이 8개 데이터 소스(F&G·ETF·오실레이터·예탁금·마감갱신·컨센서스·
  테마·무결성) × (스케줄 on/off·시각·수집일수·수동수집·마지막 로그)를 한 탭에 세로로 쌓아
  스크롤 지옥.
- 조치: `settings/DataManagementTab.kt`(+`ScheduleSettingsSection.kt`) — 소스별 **접기(아코디언)**
  카드로 분해(헤더에 소스명·다음 실행·상태 요약, 펼침 시 상세 컨트롤). 또는 "자동 수집" 요약
  리스트 → 소스 상세 진입 2단계. API 탭도 **필수(Kiwoom·KRX)/선택·고급** 섹션으로 그룹핑하고
  입력됨/미입력 상태 배지 추가(온보딩의 필수/선택 구분과 정합).
- 수용 기준: 원하는 소스를 한눈에 찾음. 저장은 변경 시에만 뜨는 고정 저장 바 권장(선택).

**T12 — 포트폴리오 카드 리스트화 + 요약 카드 통일** *(2부 · 포트폴리오)*
- 문제: `SummaryCard`만 `primaryContainer`(초록) 필로 앱에서 유일하게 튐. 보유종목 5열 표가
  11px 셀 + 만/억 축약 + 확장으로 여전히 조밀. 한 행에 클릭 타깃 3개(행=거래내역/이름=퀵분석/
  아이콘=펼침)로 발견성 낮음.
- 조치: `portfolio/PortfolioContent.kt` —
  1) `SummaryCard` 배경을 `primaryContainer` → surface 계열 + `FinanceCard`로(강조는 숫자 타이포).
  2) `HoldingsTable`을 **종목별 카드 리스트**로 전환(모바일에서 표보다 스캔·터치 유리). 각 카드가
     종목명·현재가·수익률·수익금·신호를 담고, 우측에 **명시적 오버플로(⋯) 메뉴**로 거래내역/
     퀵분석/상세를 정리(3중 클릭 타깃 해소). 태블릿은 표 유지 가능.
- 수용 기준: 요약 카드가 다른 카드와 같은 표면 언어. 행 액션이 명시적. 원 단위 정밀값은 카드
  확장 또는 상세에서 제공.

### P4 · 검증 (각 단계 말미 반복)
- 대비 감사: 다크/라이트 전 화면 WCAG AA(4.5:1, 잔글씨 우선).
- 에뮬레이터 실기: 360dp · 폰트스케일 1.3x · 다크(기존 QA 관례).
- 회귀: `.\gradlew.bat :app:testDebugUnitTest --tests "com.tinyoscillator.feature.bearsignal.*"`
  골든 테스트 그린으로 표시 계층 무접촉 증명.

---

## Design Tokens (확정값)

### 색 — 다크 (Ink Terminal) / 라이트 (Hanji)
| 역할 | 다크 | 라이트 |
|---|---|---|
| primary (청자) | `#6ECBA8` | `#1A6B4D` |
| onPrimary | `#003828` | `#FFFFFF` |
| secondary (황동) | `#E8C36A` | `#7A5A10` |
| tertiary (자두) | `#D4899E` | `#884058` |
| warn (레벨2) | `#E8823A` | `#B25D1E` |
| error | `#FF6B6B` | `#C42B2B` |
| background | `#0B0E14` | `#F5EFE3` |
| onSurface | `#E2DED5` | `#1A1710` |
| onSurfaceVariant | `#B3AEA6` (T4 상향) | `#504A3A` |
| surfaceVariant | `#2A3040` | `#E0D8C8` |
| surfaceContainer | `#151820` | `#ECE6DA` |
| surfaceContainerHigh | `#1C2030` | `#E5DED0` |
| outline | `#3E4555` | `#C0B8A5` |
| **상승/과매수 (적)** | `#EF7B68` | `#D05540` |
| **하락 (청)** | `#68B0F0` | `#4088CC` |

### 타이포
- 디스플레이/헤드라인/타이틀: **Gothic A1** (800/700/600), tracking 타이트(-1.5~-0.5sp 유지).
- 본문/라벨: **DM Sans** + Noto Sans KR(한글). 스케일: display 52/42/34, headline 30/26/22,
  title 20/16/14, body 16/14/12, label 14/12/11.

### 반경 / 간격 / 이펙트
- 반경: sm 8 · md 14 · lg 20 · pill 999 · **FinanceCard 비대칭 20/6/20/6**.
- 간격: 4px 베이스(4/8/12/16/20/24/32).
- FinanceCard 시그니처: `surfaceContainerHigh` 필 + 제이드 톤 인셋 하링(rgba(110,203,168,.22)).
  엘리베이션은 그림자보다 하링 우선. 골드 글로우는 신호등 "점등" 도트 전용.

## Screens / Views (레퍼런스 대응)
- **시장분석 홈** — `개선 시안 프로토타입.dc.html`(홈 탭), `Jade Terminal DS · ui_kits/app/index.html`.
- **종목분석** — `개선 시안 프로토타입.dc.html`(종목 탭): 2단 내비.
- **BearSignal (폰)** — `개선 시안 프로토타입.dc.html`(리스크 카드 → 아코디언).
- **BearSignal (2-pane) / 넓은 화면** — `개선 시안 2pane.dc.html`.
- **차트 색** — `차트 팔레트 리매핑.dc.html`.
- **전체 진단·근거** — `앱 디자인 리뷰.dc.html`, 로드맵 `개선 계획.dc.html`.

## Interactions & Behavior
- Pill 탭 선택: 배경/텍스트 색 크로스페이드(스프링, ~200ms), 기존 `animateColorAsState` 유지.
- 아코디언(폰 BearSignal): 헤더 탭 → 셰브런 180° 회전(~220ms) + 본문 fade/slide(~240ms).
- 마스터·디테일(2-pane): 좌측 목록 선택 → 우측 상세 fade/slide 교체, 선택 항목 하이라이트.
- 시장 선택 세그먼트: 단일 선택 토글.
- 부호색: 값 부호에 따라 적/청 자동(`signColor()` 규칙 그대로 차트·표·칩에 적용).

## State Management
- T8: 홈 히어로는 기존 `FearGreedViewModel.summary` 재사용(신규 fetch 없음).
- T9: `BearSignalViewModel`에 UI 전용 파생 상태(`expandedSections`/`selectedSection`) 추가 —
  스코어링/데이터 호출 없음. `WindowType`은 `MainActivity`에서 이미 계산(하향 전달).
- T3/T7: 파생 UI 플래그·파라미터 추가만. 도메인 무변경.

## Assets
- 아이콘: **Material Symbols**(Compose `Icons.Default.*`) 유지 — 신규 아이콘 불요.
- 폰트: Gothic A1 / DM Sans는 `GoogleFont.Provider`(다운로더블). 번들 폴백 Manrope/Inter 유지
  (`app/src/main/res/font/`). 신규 폰트 파일 불요.
- 로고/브랜드 마크: 코드베이스에 없음 — 워드마크는 디스플레이 서체로 조판.

## Files (이 번들의 디자인 레퍼런스)
- `앱 디자인 리뷰.dc.html` — 진단(문제 지점 주석) + 5개 초점 점수.
- `앱 디자인 리뷰 2.dc.html` — 2부: AI분석·포트폴리오·탐색·설정 진단(T10~T12 근거).
- `개선 계획.dc.html` — 작업 매핑표 + 4단계 로드맵 + 서체 후보.
- `차트 팔레트 리매핑.dc.html` — 차트 색 리매핑 사양(T1).
- `개선 시안 프로토타입.dc.html` — 개선 3화면(폰) 인터랙티브.
- `개선 시안 2pane.dc.html` — 태블릿/폴더블 2-pane 인터랙티브.
- **Jade Terminal 디자인 시스템**(이 프로젝트 루트) — `styles.css` + `tokens/` + `components/core/`
  + `guidelines/` + `ui_kits/app/`. 토큰 값의 단일 소스.

> 참고: `.dc.html`은 브라우저 디자인 레퍼런스입니다. Compose 재현 시 위 토큰 값과 기존
> 컴포저블을 사용하세요 — HTML을 그대로 옮기지 마세요.
