Read TASK.md and PROGRESS.md.

Reference projects:
- D:\android_2025\MarketMonitor_rev2\ (ETF menu feature, scheduling logic)
- D:\android_2025\kotlin_krx\ (latest kotlin_krx for data collection)

Mission: Update app with ETF Analysis main menu and related features. 10 requirements:
1. Rename Top AppBar title from '수급 오실레이터' to '종목분석'
2. Add new main menu '🖥 ETF분석'
3. ETF분석: adapt MarketMonitor_rev2 ETF menu to current project patterns
4. ETF분석: collect only Active ETFs, filter display by include/exclude keywords
5. Store Active ETF data including constituent stocks in Room DB
6. Use latest kotlin_krx from D:\android_2025\kotlin_krx for data collection
7. Settings: add KRX ID/PASSWORD input and secure save
8. Settings: add ETF keyword include/exclude filter configuration
9. First launch: prompt KRX ID/PASSWORD, then collect default 2 weeks of data
10. Schedule data updates at configured times (same as MarketMonitor_rev2 approach)

Architecture: Follow existing app patterns exactly. MVVM + Clean Architecture + Feature modules.

IMPORTANT: Create PLAN.md first. Do NOT implement anything until user confirms each phase.

Agent Team (3 members): Spawn 3 teammates.
1. Integrator (Sonnet): Use feature-extractor to analyze MarketMonitor_rev2 ETF feature and kotlin_krx APIs. Use source-migrator and kotlin-implementer to build in current project. Handles implementation of all features.
2. QA-Engineer (Sonnet): Use qa-verifier. Verify build after each phase, test ETF data collection, DB operations, scheduling, keyword filtering, settings persistence.
3. Architect-Reviewer (Opus): Design overall plan. Approve DB schema (ETF master, ETF daily data, constituent stocks). Approve scheduling strategy. Review each phase before user confirmation.

Use Subagents:
- feature-extractor (haiku): Scan MarketMonitor_rev2 ETF feature and kotlin_krx APIs.
- source-migrator (sonnet): Adapt MarketMonitor_rev2 code to current project.
- kotlin-implementer (sonnet): Implement in Clean Architecture layers.
- qa-verifier (sonnet): Test and verify.

Rules:
- PLAN FIRST: Generate PLAN.md with phased approach. Wait for user confirmation before each phase.
- Match existing app code patterns exactly. Study current code style before writing new code.
- KRX credentials: use EncryptedSharedPreferences. Never store in plain text or logs.
- kotlin_krx: use the version from D:\android_2025\kotlin_krx, not any other source.
- Scheduling: replicate MarketMonitor_rev2 approach (WorkManager or AlarmManager pattern).
- After every code change: run gradlew assembleDebug.
- Log all changes to PROGRESS.md.

Workflow:
1. Phase 1 (Analysis): Scan both reference projects, create PLAN.md with detailed phases.
2. STOP and present PLAN.md to user. Wait for confirmation.
3. For each subsequent phase: implement, verify build, present results, wait for user confirmation.
4. Mark tasks done only after user confirms each phase.

Completion (ALL must be met):
- Every task in TASK.md is checked done.
- AppBar title changed to '종목분석'.
- ETF분석 main menu functional with Active ETF filtering.
- DB stores ETF data and constituents.
- Settings has KRX ID/PW and keyword filters.
- First launch flow: KRX login then 2-week collection.
- Scheduled updates working.
- gradlew assembleDebug passes.
- gradlew test passes.
- IMPLEMENTATION_REPORT.md generated.
- CLAUDE.md updated.
- PROGRESS.md contains LOOP_COMPLETE.

Output COMPLETE when ALL verified.