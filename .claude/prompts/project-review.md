# TinyOscillator — Project Review Agent Prompt (Optimized)

## Usage

```bash
/ralph-wiggum:ralph-loop "$(cat .claude/prompts/project-review.md)" \
  --max-iterations 15 \
  --completion-promise "COMPLETE"
```

---

## Prompt Body

```
Create a project review agent team for TinyOscillator, a Korean stock market
analysis Android app built with Kotlin, Jetpack Compose, MVVM + Clean
Architecture, Hilt DI, and Coroutines/Flow. It integrates the KIS API,
kotlin_krx (KRX data), and MPAndroidChart for supply-demand oscillator
visualization.

OBJECTIVE:
Achieve 95+ score across all four review categories. Record all findings
in PROGRESS.md and finalize results in CLAUDE.md.

────────────────────────────────────────
TEAM COMPOSITION — spawn 4 subagents:
────────────────────────────────────────

1. SECURITY REVIEWER (target: 95/100)
   Domain focus — Korean financial app threat model:
   - KIS API credentials: verify APP_KEY / APP_SECRET are NOT hardcoded;
     confirm EncryptedSharedPreferences or Secrets Gradle Plugin usage
   - Access token lifecycle: check token refresh logic, expiry handling,
     and absence of tokens in logs or crash reports
   - Input validation: stock code format (6-digit KOSPI/KOSDAQ codes),
     date range bounds, numeric overflow in oscillator calculations
   - Network security: confirm HTTPS pinning or certificate validation
     for KIS API endpoints (openapi.koreainvestment.com)
   - Dependency audit: run `./gradlew dependencyCheckAnalyze`; flag CVEs
     ≥ MEDIUM in financial or network-layer libraries
   - ProGuard / R8: verify sensitive class names are obfuscated in release
   - Android permissions: confirm no excess permissions beyond INTERNET
   Output: security_review.md with severity [CRITICAL/HIGH/MEDIUM/LOW]

2. PERFORMANCE REVIEWER (target: 95/100)
   Domain focus — real-time stock data and chart rendering:
   - Coroutine/Flow pipeline: detect blocking calls on Main dispatcher;
     verify StateFlow / SharedFlow backpressure for tick data streams
   - KIS API call efficiency: check for redundant REST calls during
     market hours; verify WebSocket usage where available
   - kotlin_krx data loading: confirm KRX batch fetches are cached in
     Room and not re-fetched on every recomposition
   - EMA / MACD / Oscillator calculations: profile for O(n²) patterns
     in sliding-window computations; suggest incremental updates
   - MPAndroidChart rendering: verify dataset mutations happen off the
     Main thread; check for redundant `invalidate()` calls
   - Memory: detect retained Coroutine scopes in ViewModels after
     `onCleared()`; check for Bitmap leaks in chart rendering
   - Compose recomposition: identify unstable parameters passed to
     heavy @Composable functions (use Layout Inspector baseline)
   Output: performance_review.md with before/after complexity estimates

3. RELIABILITY REVIEWER (target: 95/100)
   Domain focus — Korean market trading session constraints:
   - KIS API resilience: verify exponential backoff + retry on 429/5xx;
     confirm graceful handling of pre-market / post-market hour errors
   - Market hours guard: check that trading actions are blocked outside
     KOSPI/KOSDAQ sessions (09:00–15:30 KST); verify KST timezone logic
   - Data gap handling: confirm oscillator rendering handles missing
     candles (holidays, circuit breakers) without crashing or distorting
   - Offline / network loss: verify Room cache serves stale data with
     a visible staleness indicator rather than blank screens
   - Error propagation: ensure UseCase / Repository errors are mapped to
     typed sealed classes (not raw Exception) before reaching ViewModel
   - Logging hygiene: confirm no PII or account data appears in Logcat
     at level DEBUG or higher in release builds
   - Crash-free rate target: identify any uncaught exceptions in
     financial calculation paths (division by zero, empty list)
   Output: reliability_review.md with issue/impact/fix triples

4. TEST COVERAGE REVIEWER (target: 95/100)
   Domain focus — financial calculation correctness:
   - Unit test coverage: measure with `./gradlew koverReport`;
     require ≥ 70% line coverage overall, ≥ 90% on oscillator logic
   - EMA / MACD / Signal line: verify test vectors match reference
     values from Excel baseline or known-good Python implementation
   - KIS API integration: confirm MockWebServer (or mock Hilt module)
     covers: token expiry, HTTP 429 rate-limit, malformed JSON response
   - kotlin_krx data layer: verify tests cover holiday edge cases
     (e.g., Chuseok, Lunar New Year) and empty trading day responses
   - Repository / UseCase layer: verify tests exist for each error
     path defined in the sealed Result/Error classes
   - UI tests: confirm at least one Compose UI test per major screen
     (OscillatorScreen, StockListScreen, SettingsScreen)
   - Edge cases: zero-volume days, single-candle datasets, negative
     MACD histogram values, and 52-week high/low boundary conditions
   Output: test_coverage_review.md with coverage delta per module

────────────────────────────────────────
WORKFLOW:
────────────────────────────────────────

1. Read CLAUDE.md and PROGRESS.md for current project state before
   starting any reviewer.
2. Each reviewer conducts independent analysis; write findings to its
   own output file (listed above).
3. Orchestrator aggregates all four reports into review_report.md with
   a score table:
     | Category     | Score | Blockers | Top Fix |
     |--------------|-------|----------|---------|
     | Security     |  /100 |          |         |
     | Performance  |  /100 |          |         |
     | Reliability  |  /100 |          |         |
     | Test Coverage|  /100 |          |         |
4. If ANY score < 95, re-run the lowest-scoring reviewer with targeted
   improvement instructions. Apply fixes incrementally; re-score.
5. Repeat step 4 until all scores ≥ 95.
6. Once all scores ≥ 95:
   a. Append a "Review Summary" section to CLAUDE.md with date, scores,
      and top 3 action items.
   b. Delete per-reviewer intermediate files; keep only review_report.md.
   c. Remove any temp files or build artifacts created during review.
   d. Mark completion: <promise>COMPLETE</promise>

────────────────────────────────────────
ITERATION STRATEGY:
────────────────────────────────────────
- Priority order for fixes: CRITICAL security > financial calc bugs >
  KIS API reliability > performance hotspots > test gaps
- Security CRITICAL issues block all other categories from passing
- Financial calculation errors (EMA/MACD wrong values) are treated as
  CRITICAL regardless of which reviewer surfaces them
- Each iteration must update PROGRESS.md with: reviewer, score delta,
  files changed, and next planned action

────────────────────────────────────────
OUTPUT ARTIFACTS:
────────────────────────────────────────
- review_report.md          ← consolidated scores + findings
- CLAUDE.md (appended)      ← review summary + action items
- PROGRESS.md (updated)     ← iteration log
- <promise>COMPLETE</promise>
```