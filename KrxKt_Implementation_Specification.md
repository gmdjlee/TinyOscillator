# KrxKt 프로젝트를 위한 Claude Code 설정 가이드

## Skills, Agents, CLAUDE.md 완전 정복

Version 1.0 | February 2026

---

## 목차

1. [전체 구조 이해](#1-전체-구조-이해)
2. [CLAUDE.md — 프로젝트 기본 지시사항](#2-claudemd--프로젝트-기본-지시사항)
3. [Skills — 자동 호출되는 전문 역량 패키지](#3-skills--자동-호출되는-전문-역량-패키지)
4. [Agents (Subagents) — 독립 컨텍스트의 전문 에이전트](#4-agents-subagents--독립-컨텍스트의-전문-에이전트)
5. [MCP 서버 연동](#5-mcp-서버-연동)
6. [Custom Slash Commands — 수동 호출 명령어](#6-custom-slash-commands--수동-호출-명령어)
7. [Hooks — 자동화 트리거](#7-hooks--자동화-트리거)
8. [KrxKt 프로젝트 전체 설정 예시](#8-krxkt-프로젝트-전체-설정-예시)
9. [핵심 요약 및 체크리스트](#9-핵심-요약-및-체크리스트)

---

## 1. 전체 구조 이해

Claude Code는 4가지 핵심 커스터마이징 메커니즘을 제공합니다.

```
┌─────────────────────────────────────────────────────┐
│                    Claude Code                       │
│                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │  CLAUDE.md   │  │   Skills     │  │  Agents    │ │
│  │  (항상 로드)  │  │ (자동 감지)   │  │ (자동 위임) │ │
│  └──────┬───────┘  └──────┬───────┘  └─────┬──────┘ │
│         │                 │                │         │
│         ▼                 ▼                ▼         │
│  ┌─────────────────────────────────────────────────┐ │
│  │            시스템 프롬프트 + 컨텍스트              │ │
│  └─────────────────────────────────────────────────┘ │
│                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │
│  │  Commands    │  │   Hooks      │  │ MCP Server │ │
│  │ (수동 호출)   │  │ (이벤트 트리거)│  │ (외부 도구) │ │
│  └──────────────┘  └──────────────┘  └────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 핵심 차이점

| 구성 요소 | 호출 방식 | 컨텍스트 | 저장 위치 | 용도 |
|-----------|----------|---------|----------|------|
| **CLAUDE.md** | 세션 시작 시 항상 로드 | 메인 컨텍스트에 포함 | 프로젝트 루트 / `~/.claude/` | 프로젝트 규칙, 코딩 표준 |
| **Skills** | Claude가 작업 매칭 시 **자동 로드** | 필요 시에만 로드 (온디맨드) | `.claude/skills/` / `~/.claude/skills/` | 전문 워크플로우, 스크립트 번들 |
| **Agents** | Claude가 작업 매칭 시 **자동 위임** | **독립된 컨텍스트 윈도우** | `.claude/agents/` / `~/.claude/agents/` | 전문 서브에이전트 |
| **Commands** | 사용자가 `/명령어`로 수동 호출 | 메인 컨텍스트 | `.claude/commands/` | 반복 작업 단축키 |
| **Hooks** | 도구 실행 전/후 **자동 트리거** | N/A (셸 스크립트) | `settings.json` | 자동 포매팅, 검증 |
| **MCP** | 세션 시작 시 항상 로드 | 메인 컨텍스트 소비 | `settings.json` | 외부 API/서비스 연동 |

### 파일 시스템 구조 (전체 맵)

```
프로젝트 루트/
├── CLAUDE.md                          ← 프로젝트 레벨 지시사항
├── .claude/
│   ├── settings.json                  ← 프로젝트 설정 (권한, 환경변수, 훅)
│   ├── skills/                        ← 프로젝트 레벨 스킬
│   │   ├── krx-data/
│   │   │   ├── SKILL.md
│   │   │   └── scripts/
│   │   └── kotlin-codegen/
│   │       └── SKILL.md
│   ├── agents/                        ← 프로젝트 레벨 에이전트
│   │   ├── krx-api-researcher.md
│   │   └── kotlin-implementer.md
│   └── commands/                      ← 슬래시 명령어
│       ├── test-krx.md
│       └── build-apk.md
│
~/.claude/                             ← 글로벌 (모든 프로젝트 공통)
├── CLAUDE.md                          ← 글로벌 지시사항
├── settings.json                      ← 글로벌 설정
├── skills/                            ← 글로벌 스킬
├── agents/                            ← 글로벌 에이전트
└── commands/                          ← 글로벌 명령어
```

**우선순위:** 프로젝트 레벨 > 글로벌 레벨 (동일 이름 충돌 시)

---

## 2. CLAUDE.md — 프로젝트 기본 지시사항

### 2.1 역할

CLAUDE.md는 Claude Code 세션이 시작될 때 **항상 자동으로 로드**됩니다. 시스템 프롬프트의 일부가 되어 모든 대화에 적용됩니다.

### 2.2 생성 방법

```bash
# 방법 1: Claude에게 자동 생성 요청
cd /your/krxkt-project
claude
> /init

# 방법 2: 수동 생성
touch CLAUDE.md

# 방법 3: 대화 중 # 키로 메모리 추가
# (Claude Code 세션에서 # 키 누르면 즉시 CLAUDE.md에 추가)
```

### 2.3 KrxKt 프로젝트용 CLAUDE.md 예시

```markdown
# KrxKt Project

## 프로젝트 개요
pykrx(Python KRX 데이터 라이브러리)를 Native Kotlin으로 전환하는 프로젝트.
Android 앱에서 Python 런타임 없이 한국거래소(KRX) 시장 데이터에 직접 접근.

## 기술 스택
- Language: Kotlin
- HTTP Client: OkHttp 4.12.0
- JSON Parser: Gson 2.10.1
- Async: Kotlin Coroutines 1.7.3
- Date: kotlinx-datetime 0.5.0
- Target: Android (minSdk 26)

## 핵심 규칙

### 코딩 표준
- Kotlin 코드는 반드시 Coroutine 기반 suspend 함수로 작성
- Data class에 nullable 타입 적절히 사용 (KRX API가 빈 값 반환 가능)
- 숫자 파싱 시 쉼표 제거 처리 필수 ("82,200" → 82200L)
- 패키지 구조: com.krxkt.{api|model|parser|cache}

### KRX API 패턴
- Base URL: http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd
- Method: POST (application/x-www-form-urlencoded)
- Referer 헤더 필수: http://data.krx.co.kr/contents/MDC/MDI/mdiLoader/...
- 응답 구조: JSON → "OutBlock_1" 배열에 데이터 행 포함
- 날짜 형식: "yyyyMMdd" (예: "20210122")

### 테스트 기준
- 기준 날짜: 20210122 (과거 안정 데이터)
- 기준 종목: 005930 (삼성전자), 069500 (KODEX 200)
- 빌드: ./gradlew test
- Lint: ./gradlew ktlintCheck

### 참조 문서
- 명세서: docs/KrxKt_Implementation_Specification.md
- pykrx 소스: https://github.com/sharebook-kr/pykrx

## Skills & Agents 활용 지침
- KRX API 엔드포인트 조사 시 → `krx-api-researcher` 에이전트 활용
- Kotlin 코드 생성 시 → `kotlin-codegen` 스킬의 데이터 클래스 템플릿 따르기
- KRX 데이터 파싱 작업 시 → `krx-data` 스킬 참조
- 구현 완료 후 코드 리뷰 → `code-reviewer` 에이전트 위임
- pykrx 소스 분석 필요 시 → Web Fetch로 GitHub 소스 직접 조회
- KIS API 크로스 검증 시 → KIS MCP 서버 활용
```

### 2.4 계층 구조 활용

CLAUDE.md는 디렉토리별로 배치 가능하며, 중첩 시 가장 구체적인 파일이 우선됩니다.

```
krxkt/
├── CLAUDE.md                    ← 프로젝트 전체 규칙
├── app/
│   └── CLAUDE.md                ← Android 앱 관련 추가 규칙
├── library/
│   └── CLAUDE.md                ← 라이브러리 코어 관련 추가 규칙
└── tests/
    └── CLAUDE.md                ← 테스트 관련 추가 규칙
```

### 2.5 `@` Include로 모듈화

```markdown
# 메인 CLAUDE.md
@docs/coding-standards.md
@docs/krx-api-reference.md
@docs/test-guidelines.md
```

---

## 3. Skills — 자동 호출되는 전문 역량 패키지

### 3.1 Skills의 핵심 특성

Skills는 **Claude가 작업 내용을 분석하여 자동으로 관련 스킬을 로드**합니다. 핵심은 `description` 필드입니다. Claude는 이 설명문을 보고 현재 작업에 해당 스킬이 필요한지 판단합니다.

```
세션 시작 시:
  컨텍스트에 로드되는 것 = 스킬의 name + description (메타데이터만)
  → 컨텍스트 소비 최소화

스킬 호출 시:
  Claude가 SKILL.md 본문 읽기 → 스크립트/리소스 참조 → 작업 수행
  → 필요할 때만 상세 지시사항 로드 (Progressive Disclosure)
```

### 3.2 SKILL.md 구조

```yaml
---
name: skill-name                        # /skill-name 으로도 호출 가능
description: >                          # ⭐ 가장 중요! Claude가 이걸로 자동 호출 판단
  언제 이 스킬을 사용해야 하는지 명확하게 작성.
  200자 이내. 구체적인 트리거 키워드 포함.
disable-model-invocation: false         # true면 자동 호출 차단 (/명령어로만 호출)
context: inline                         # inline(메인 컨텍스트) | fork(서브에이전트)
---

# 스킬 본문 (지시사항)

여기에 Claude가 따를 상세 워크플로우 작성.
번들 스크립트나 추가 파일 참조 가능.
```

### 3.3 Frontmatter 필드 상세

| 필드 | 필수 | 설명 |
|------|------|------|
| `name` | ✅ | 스킬 이름, 64자 이내. `/name`으로 수동 호출 가능 |
| `description` | ✅ | **자동 호출 판단 기준**. 200자 이내. 구체적으로! |
| `disable-model-invocation` | ❌ | `true`: 자동 호출 금지, 수동만 가능 |
| `context` | ❌ | `inline`(기본): 메인에서 실행, `fork`: 서브에이전트에서 실행 |
| `dependencies` | ❌ | 필요한 소프트웨어 패키지 목록 |

### 3.4 KrxKt 프로젝트용 Skills 생성

#### Skill 1: KRX 데이터 파싱 스킬

```bash
mkdir -p .claude/skills/krx-data
```

`.claude/skills/krx-data/SKILL.md`:
```yaml
---
name: krx-data
description: >
  KRX(한국거래소) API 데이터 파싱 및 Kotlin 데이터 클래스 변환.
  KRX JSON 응답의 OutBlock_1 파싱, 쉼표 숫자 처리, OHLCV/시가총액/
  펀더멘탈 데이터 모델 생성 시 사용. pykrx 패턴 참조.
---

# KRX Data Parsing Skill

## JSON 응답 구조
KRX API는 항상 `OutBlock_1` 키 아래에 데이터 배열을 반환합니다.

```json
{
  "OutBlock_1": [
    {
      "ISU_SRT_CD": "005930",
      "TDD_CLSPRC": "82,200",
      "ACC_TRDVOL": "16,543,541"
    }
  ]
}
```

## 숫자 파싱 규칙
1. 모든 숫자 필드는 문자열로 반환됨
2. 쉼표 제거 후 파싱: `"82,200"` → `82200L`
3. 음수 처리: `"-300"` → `-300L`
4. 빈 문자열은 null 처리: `""` → `null`

## 필드 매핑 테이블

| KRX 필드 | Kotlin 프로퍼티 | 타입 |
|----------|----------------|------|
| ISU_SRT_CD | ticker | String |
| ISU_ABBRV | name | String |
| TDD_OPNPRC | open | Long |
| TDD_HGPRC | high | Long |
| TDD_LWPRC | low | Long |
| TDD_CLSPRC | close | Long |
| ACC_TRDVOL | volume | Long |
| ACC_TRDVAL | tradingValue | Long |
| FLUC_RT | changeRate | Double |
| MKTCAP | marketCap | Long |
| LIST_SHRS | sharesOutstanding | Long |
| PER | per | Double? |
| PBR | pbr | Double? |
| EPS | eps | Long? |
| BPS | bps | Long? |
| DVD_YLD | dividendYield | Double? |

## 파서 유틸리티 패턴

```kotlin
// 표준 파서 함수
fun String.parseKrxLong(): Long? =
    this.replace(",", "").replace("-", "")
        .toLongOrNull()?.let { if (this.startsWith("-")) -it else it }

fun String.parseKrxDouble(): Double? =
    this.replace(",", "").toDoubleOrNull()
```

상세 엔드포인트별 파라미터는 `endpoints-reference.md`를 참조하세요.
```

#### Skill 2: Kotlin 코드 생성 스킬

`.claude/skills/kotlin-codegen/SKILL.md`:
```yaml
---
name: kotlin-codegen
description: >
  KrxKt 프로젝트의 Kotlin 코드 생성. suspend 함수 작성, OkHttp 기반
  HTTP 클라이언트 구현, data class 정의, Coroutine 에러 핸들링 패턴 적용.
  새로운 KRX API 엔드포인트 구현 시 사용.
---

# Kotlin Code Generation for KrxKt

## 파일 생성 시 반드시 따를 패턴

### 1. API 함수 패턴
```kotlin
suspend fun getMarketOhlcv(
    date: String,
    market: Market = Market.ALL
): List<MarketOhlcv> {
    require(isValidTradingDate(date)) { "Invalid date format: $date" }

    val params = mapOf(
        "bld" to KrxEndpoints.STOCK_OHLCV_ALL,
        "mktId" to market.code,
        "trdDd" to date
    )

    return krxClient.post(params)
        .parseOutBlock()
        .map { it.toMarketOhlcv() }
}
```

### 2. Data Class 패턴
- 모든 가격은 `Long` (원 단위 정수)
- API가 빈 값 반환할 수 있는 필드는 `?` nullable
- `@SerializedName` 대신 커스텀 파서 사용

### 3. 에러 핸들링
- 네트워크 에러: 지수 백오프 재시도 (3회, 1s/2s/4s)
- 파싱 에러: 빈 리스트 반환 + 로깅
- 잘못된 날짜: IllegalArgumentException

### 4. 테스트 패턴
```kotlin
@Test
fun `함수명 describes expected behavior`() = runTest {
    // Given
    val date = "20210122"
    // When
    val result = krxStock.getMarketOhlcv(date, Market.KOSPI)
    // Then
    assertTrue(result.isNotEmpty())
}
```
```

### 3.5 자동 호출을 잘 되게 하는 핵심 팁

**✅ description을 구체적으로 작성**
```yaml
# 나쁜 예 — 너무 모호
description: "Helps with data processing"

# 좋은 예 — 구체적 키워드와 트리거 상황 명시
description: >
  KRX(한국거래소) API 데이터 파싱 및 Kotlin 데이터 클래스 변환.
  KRX JSON 응답의 OutBlock_1 파싱, 쉼표 숫자 처리, OHLCV/시가총액/
  펀더멘탈 데이터 모델 생성 시 사용.
```

**✅ CLAUDE.md에서 스킬 참조를 명시**
```markdown
## Skills 활용 지침
- KRX 데이터 파싱 작업 시 → `krx-data` 스킬 참조
- 새 Kotlin 파일 생성 시 → `kotlin-codegen` 스킬의 패턴 따르기
```

이렇게 하면 Claude가 "아, 이 작업에는 저 스킬이 있었지" 하고 로드할 확률이 높아집니다.

---

## 4. Agents (Subagents) — 독립 컨텍스트의 전문 에이전트

### 4.1 Agents vs Skills 핵심 차이

| 특성 | Skills | Agents |
|------|--------|--------|
| 실행 환경 | 메인 컨텍스트에서 실행 (`inline`) 또는 서브에이전트 (`fork`) | **항상 독립 컨텍스트 윈도우** |
| 시스템 프롬프트 | Claude Code의 전체 시스템 프롬프트 상속 | **자체 시스템 프롬프트만 사용** |
| 도구 접근 | 메인과 동일 | **제한 가능** (Read, Grep만 등) |
| 모델 | 메인과 동일 | **별도 모델 지정 가능** (Haiku로 비용 절감) |
| 결과 반환 | 메인 컨텍스트에 직접 반영 | 요약된 결과만 반환 |
| 주 용도 | 지시사항 + 리소스 번들 | 독립적 분석/조사/리뷰 |

### 4.2 Agent 파일 구조

```yaml
---
name: agent-name                # 필수
description: >                  # 필수 — Claude가 이걸로 자동 위임 판단
  언제 이 에이전트에게 위임해야 하는지 구체적으로 작성.
tools: Read, Grep, Glob         # 선택 — 허용할 도구 목록 (생략 시 전체 상속)
model: sonnet                   # 선택 — sonnet, haiku, opus, inherit
color: blue                     # 선택 — 터미널에서 시각 구분용 색상
---

# 시스템 프롬프트 (마크다운 본문)

이 에이전트의 역할과 행동 지침을 작성합니다.
```

### 4.3 생성 방법

```bash
# 방법 1: /agents 명령어로 대화형 생성 (추천)
claude
> /agents
> Create new agent → Project level → Generate with Claude

# 방법 2: 수동 파일 생성
mkdir -p .claude/agents
cat > .claude/agents/krx-api-researcher.md << 'EOF'
---
name: krx-api-researcher
description: >
  KRX API 엔드포인트 조사 및 pykrx 소스 코드 분석. HTTP 요청 패턴,
  파라미터, 응답 구조를 분석하고 Kotlin 구현에 필요한 정보 정리.
  새로운 API 엔드포인트 추가 시 또는 pykrx 동작 확인 시 사용.
tools: Read, Grep, Glob, WebFetch, WebSearch
model: sonnet
color: cyan
---

You are a KRX API research specialist. Your tasks:

1. **pykrx 소스 분석**: GitHub에서 pykrx 소스 코드를 분석하여 
   HTTP 요청 패턴, bld 값, 파라미터 구조를 정리
2. **KRX 엔드포인트 검증**: 실제 KRX data.krx.co.kr 사이트의 
   API 구조를 확인하고 문서화
3. **응답 구조 분석**: JSON 응답 필드명과 데이터 타입 매핑

Always return findings in a structured format:
- Endpoint URL and BLD value
- Required parameters with types
- Response field mapping table
- Sample request/response
EOF
```

### 4.4 KrxKt용 Agent 세트

#### Agent 1: KRX API 리서처

`.claude/agents/krx-api-researcher.md` (위 예시 참조)

#### Agent 2: Kotlin 구현자

```markdown
---
name: kotlin-implementer
description: >
  KrxKt Kotlin 코드 구현 전담. API 클라이언트, 데이터 모델,
  파서, 캐시 레이어의 Kotlin 코드 작성. 새로운 모듈 구현이나
  기존 코드 리팩토링 시 사용.
tools: Read, Write, Edit, Bash, Glob, Grep
model: sonnet
color: green
---

You are a Kotlin implementation specialist for the KrxKt project.

## 반드시 따를 규칙
1. 모든 API 호출 함수는 `suspend fun`으로 작성
2. OkHttp 기반 HTTP 클라이언트 사용
3. 에러 핸들링: 지수 백오프 재시도 패턴
4. 데이터 클래스: KRX API 필드와 1:1 매핑
5. 숫자 파싱: 쉼표 제거 처리 필수

## 패키지 구조
- `com.krxkt.api/` — HTTP 클라이언트, 엔드포인트
- `com.krxkt.model/` — 데이터 클래스
- `com.krxkt.parser/` — JSON 파서
- `com.krxkt.cache/` — 캐싱 레이어
```

#### Agent 3: 코드 리뷰어

```markdown
---
name: code-reviewer
description: >
  KrxKt 코드 리뷰 전담. 코드 품질, 에러 핸들링, 코루틴 사용 패턴,
  KRX API 호환성을 검토. PR 전 코드 리뷰나 구현 완료 후 검증 시 사용.
tools: Read, Grep, Glob
model: sonnet
color: orange
---

You are a code reviewer for the KrxKt project. Focus on:

1. **Coroutine Safety**: suspend 함수 사용, Dispatcher 적절성
2. **Error Handling**: 네트워크 에러 재시도, null safety
3. **KRX API Compliance**: Referer 헤더, POST 형식, 파라미터 정확성
4. **Data Parsing**: 쉼표 숫자, 빈 문자열, 음수 처리
5. **Test Coverage**: 정상 케이스, 공휴일, 빈 응답 테스트 유무

Report only bugs and potential issues. Be concise.
```

### 4.5 자동 위임이 잘 작동하게 하려면

1. **description을 최대한 구체적으로**: Claude는 description을 보고 위임을 결정합니다
2. **CLAUDE.md에서 에이전트 사용 시점 안내**:
   ```markdown
   ## Agent 활용 가이드
   - 새 API 엔드포인트 조사 → `krx-api-researcher` 에이전트
   - Kotlin 코드 구현 → `kotlin-implementer` 에이전트
   - 구현 완료 후 리뷰 → `code-reviewer` 에이전트
   ```
3. **도구(tools) 범위를 최소화**: 리뷰어는 `Read, Grep, Glob`만, 구현자는 `Write, Edit, Bash`까지

---

## 5. MCP 서버 연동

MCP 서버는 외부 서비스(KIS API, Notion 등)를 Claude Code에 연결합니다.

### 5.1 설정 방법

`.claude/settings.json`:
```json
{
  "mcpServers": {
    "kis-api": {
      "type": "url",
      "url": "https://server.smithery.ai/@KISOpenAPI/kis-code-assistant-mcp",
      "name": "KIS API"
    }
  }
}
```

### 5.2 주의점

- MCP 서버는 **세션 시작 시 항상 로드**되어 컨텍스트를 소비합니다
- Skills와 달리 온디맨드가 아니므로, 필요한 MCP만 선별적으로 등록하세요
- KIS API MCP: 데이터 크로스 검증에 유용 → 등록 권장
- 불필요한 MCP가 많으면 컨텍스트 낭비 → 신중하게 선택

---

## 6. Custom Slash Commands — 수동 호출 명령어

반복적으로 사용하는 워크플로우를 `/명령어`로 단축합니다.

### 6.1 생성

```bash
mkdir -p .claude/commands
```

`.claude/commands/test-krx.md`:
```markdown
KRX API에 대해 다음 테스트를 실행합니다:

1. 기준 날짜(20210122)로 KOSPI 전종목 OHLCV 조회
2. 삼성전자(005930) 단일 종목 히스토리 조회
3. ETF 가격 데이터(KODEX 200, 069500) 조회
4. 응답 데이터의 숫자 파싱 정확성 검증
5. 공휴일(20210101) 빈 응답 처리 확인

테스트 결과를 요약 보고서로 정리해 주세요.
```

`.claude/commands/implement-endpoint.md`:
```markdown
다음 KRX API 엔드포인트를 구현합니다: $ARGUMENTS

1. `krx-api-researcher` 에이전트로 pykrx 소스에서 해당 엔드포인트 분석
2. `kotlin-codegen` 스킬의 패턴에 따라 Kotlin 코드 생성
3. Data class, API 함수, 파서 함수 모두 구현
4. 단위 테스트 작성
5. `code-reviewer` 에이전트로 코드 리뷰 수행
```

### 6.2 사용

```bash
claude
> /test-krx
> /implement-endpoint getMarketFundamental
```

---

## 7. Hooks — 자동화 트리거

특정 이벤트 발생 시 자동으로 셸 명령을 실행합니다.

### 7.1 설정

`.claude/settings.json`:
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write",
        "pattern": "**/*.kt",
        "command": "ktlint --format $CLAUDE_FILE_PATH 2>/dev/null || true"
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Write",
        "pattern": "**/*Test.kt",
        "command": "./gradlew test --tests $(basename $CLAUDE_FILE_PATH .kt) 2>/dev/null || true"
      }
    ]
  }
}
```

### 7.2 활용 시나리오

| Hook 이벤트 | 용도 |
|-------------|------|
| `PreToolUse` (Write) | Kotlin 파일 생성 전 디렉토리 구조 확인 |
| `PostToolUse` (Write) | `.kt` 파일 저장 후 자동 ktlint 포매팅 |
| `PostToolUse` (Write) | `*Test.kt` 저장 후 자동 테스트 실행 |
| `SessionStart` | 환경변수 로드, 프로젝트 상태 표시 |

---

## 8. KrxKt 프로젝트 전체 설정 예시

### 최종 디렉토리 구조

```
krxkt/
├── CLAUDE.md                               ← 프로젝트 규칙 + 스킬/에이전트 활용 안내
├── .claude/
│   ├── settings.json                       ← MCP, 훅, 권한 설정
│   ├── skills/
│   │   ├── krx-data/
│   │   │   ├── SKILL.md                    ← KRX 데이터 파싱 전문 지식
│   │   │   └── endpoints-reference.md      ← 엔드포인트 상세 참조
│   │   └── kotlin-codegen/
│   │       └── SKILL.md                    ← Kotlin 코드 생성 패턴
│   ├── agents/
│   │   ├── krx-api-researcher.md           ← API 조사 전담 (Read-only)
│   │   ├── kotlin-implementer.md           ← 코드 구현 전담 (Write 가능)
│   │   └── code-reviewer.md                ← 코드 리뷰 전담 (Read-only)
│   └── commands/
│       ├── test-krx.md                     ← /test-krx
│       ├── implement-endpoint.md           ← /implement-endpoint {name}
│       └── cross-validate.md               ← /cross-validate (KIS API 비교)
├── docs/
│   ├── KrxKt_Implementation_Specification.md
│   └── krx-api-reference.md
└── src/
    └── ...
```

### settings.json 전체

```json
{
  "permissions": {
    "deny": [
      "Read(./.env)",
      "Read(./.env.*)",
      "Read(./secrets/**)"
    ]
  },
  "mcpServers": {
    "kis-api": {
      "type": "url",
      "url": "https://server.smithery.ai/@KISOpenAPI/kis-code-assistant-mcp",
      "name": "KIS API"
    }
  },
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write",
        "pattern": "**/*.kt",
        "command": "ktlint --format $CLAUDE_FILE_PATH 2>/dev/null || true"
      }
    ]
  }
}
```

---

## 9. 핵심 요약 및 체크리스트

### "항상 적절한 Skill과 Agent를 사용하게 하려면"

```
┌─────────────────────────────────────────────────────────────┐
│  ✅ 1. description을 구체적이고 키워드 풍부하게 작성          │
│     → Claude는 description으로 자동 호출/위임을 결정          │
│                                                              │
│  ✅ 2. CLAUDE.md에서 "언제 어떤 스킬/에이전트를 쓸지" 명시     │
│     → "KRX 파싱 시 → krx-data 스킬 참조" 같은 안내           │
│                                                              │
│  ✅ 3. 스킬과 에이전트의 역할을 명확히 분리                    │
│     → 조사 / 구현 / 리뷰 각각 별도 에이전트                   │
│                                                              │
│  ✅ 4. 에이전트 도구(tools) 범위를 최소화                      │
│     → 리뷰어는 Read만, 구현자는 Write까지                     │
│                                                              │
│  ✅ 5. 복잡한 워크플로우는 Slash Command로 체이닝               │
│     → /implement-endpoint 하나로 조사→구현→테스트→리뷰 연결    │
│                                                              │
│  ✅ 6. 반복적인 포스트 프로세싱은 Hooks로 자동화                │
│     → .kt 저장 시 자동 ktlint, 테스트 자동 실행               │
└─────────────────────────────────────────────────────────────┘
```

### 자동 호출 신뢰도 높이기 팁

| 전략 | 설명 |
|------|------|
| **Description에 트리거 키워드 포함** | "KRX", "OHLCV", "pykrx", "Kotlin" 등 작업에서 등장할 키워드 |
| **부정적 경계도 명시** | "Bond 데이터에는 사용하지 말 것" 같은 제외 조건 |
| **예시 시나리오 포함** | `description`이나 본문에 "User asks: ..." 형태의 예시 |
| **중복 스킬 피하기** | 역할이 겹치면 Claude가 혼동 → 하나의 스킬로 통합하거나 명확히 구분 |
| **정기적으로 # 키로 피드백** | "krx-data 스킬을 KRX 파싱 시 항상 사용해줘" → CLAUDE.md에 축적 |

### 참조 링크

- Claude Code Skills 공식 문서: https://code.claude.com/docs/en/skills
- Claude Code Subagents 공식 문서: https://code.claude.com/docs/en/sub-agents
- Claude Code Settings 공식 문서: https://code.claude.com/docs/en/settings
- CLAUDE.md 가이드: https://claude.com/blog/using-claude-md-files
- Agent Skills 엔지니어링 블로그: https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills
- Anthropic 공식 Skills 저장소: https://github.com/anthropics/skills
- awesome-claude-code (커뮤니티): https://github.com/hesreallyhim/awesome-claude-code

---

### Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-05 | Claude | Initial guide |