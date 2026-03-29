# CLAUDE.md — Probabilistic Stock Analysis Engine

## Project Overview
Android 한국 주식 분석 앱에 7개 통계 알고리즘 + 로컬 LLM 메타분석 기능을 추가한다.

### Architecture Principle
- **Kotlin = Calculator**: 모든 수치 계산은 Kotlin에서 결정적으로 수행
- **LLM = Analyst**: 계산 결과를 종합 해석하는 통역사 역할만 담당
- LLM에게 절대 수치 계산을 시키지 않는다

### Tech Stack
- Language: Kotlin
- Architecture: MVVM + Clean Architecture
- DI: Hilt
- DB: Room
- Async: Coroutines + Flow
- Chart: MPAndroidChart
- LLM Runtime: llama.cpp (JNI/NDK)
- Model Format: GGUF (Q4_K_M quantization)
- Build: Gradle (Kotlin DSL)

### Package Structure
```
com.app.stockanalysis/
├── domain/
│   ├── model/          # Data classes (StatisticalResult, PatternMatch, etc.)
│   ├── repository/     # Repository interfaces
│   └── usecase/        # AnalyzeStockUseCase, etc.
├── data/
│   ├── local/
│   │   ├── db/         # Room DB, DAOs
│   │   ├── llm/        # LlmRepositoryImpl (JNI wrapper)
│   │   └── engine/     # StatisticalAnalysisEngine + 7 algorithms
│   └── mapper/         # PromptBuilder, ResponseParser
├── presentation/
│   ├── viewmodel/      # StockAnalysisViewModel
│   └── ui/             # Compose screens
└── di/                 # Hilt modules
```

### Existing DB Schema (Room)
이미 존재하는 테이블들 — 새로 만들 필요 없음:
- `daily_price`: date, open, high, low, close, volume, change_percent
- `indicator`: date, stock_code, ema20, ema60, macd, macd_signal, macd_histogram, oscillator_value, oscillator_signal, volume_change_rate
- `demark_data`: date, stock_code, buy_setup_count, sell_setup_count, buy_countdown_count, sell_countdown_count
- `financial_data`: stock_code, per, pbr, roe, dividend_yield, ...
- `etf_data`: etf_code, sector_name, date, close, volume

### Naming Conventions
- Use case: `동사 + 명사 + UseCase` (e.g., `AnalyzeStockProbabilityUseCase`)
- Repository: `명사 + Repository` (e.g., `StatisticalRepository`)
- Engine classes: `명사 + Engine` (e.g., `NaiveBayesEngine`)
- Result classes: `명사 + Result` (e.g., `BayesResult`)
- 한국어 주석 허용, 코드는 영어

### Key Constraints
- Room DB 데이터만 사용 (외부 API 호출 없음)
- 단일 종목 분석 (single stock code)
- 통계 엔진은 외부 ML 라이브러리 없이 순수 Kotlin
- LLM 추론은 백그라운드 스레드 (Dispatchers.Default)
- 모든 통계 엔진은 suspend fun으로 구현
- 테스트: 각 알고리즘에 대한 unit test 필수

### Model Tier (for Claude Code)
- Opus: 아키텍처 설계, 복잡한 알고리즘 구현, 프롬프트 엔지니어링
- Sonnet: 일반 구현, 리팩토링, 테스트 작성
- Haiku: 반복적 boilerplate, data class 생성, import 정리

---

## Review Summary (2026-03-29)

| Category | Score | Ceiling |
|---|---|---|
| Security | 95/100 | 95 (KIS API header constraint) |
| Performance | 95/100 | 97 |
| Reliability | 98/100 | 99 |
| Test Coverage | 95/100 | 95 (androidTest required) |

### Top 3 Action Items
1. **MarketOscillatorCalculator KRX caching** — Cache raw KRX OHLCV in Room for incremental updates (Performance +2)
2. **androidTest infrastructure** — Set up Compose UI + Room DAO tests (Test Coverage +5)
3. **Holiday awareness** — Add Korean market holiday calendar to TradingHours (Reliability +0.5)
