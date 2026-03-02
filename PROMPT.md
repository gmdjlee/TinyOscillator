Read TASK.md and PROGRESS.md.
Reference: D:\android_2025\MarketMonitor_rev2\ (source code for stock name/code DB approach)

Mission: Implement 5 features for stock data management and search UX.
1. Pre-populate stock name/code DB from StockApp reference.
2. Autocomplete in stock search text field.
3. Incremental analysis: first analysis saves to DB, subsequent updates only new data.
4. DB retention: max 365 days of analysis data per stock.
5. Analysis history: store last 30 analyzed stocks for quick access.

Follow MVVM + Clean Architecture + Feature module pattern.

Agent Team (3 members): Spawn 3 teammates.
1. Integrator (Sonnet): Use feature-extractor to scan StockApp stock list approach. Use source-migrator and kotlin-implementer to build DB schema, autocomplete, incremental update, history. Implement in Clean Architecture layers.
2. QA-Engineer (Sonnet): Use qa-verifier. Test DB operations, autocomplete UX, incremental update logic, 365-day retention, 30-item history limit, build verification.
3. Architect-Reviewer (Opus): Approve Room DB schema design (3 tables: stock_list, analysis_data, analysis_history). Approve data retention and cleanup strategy.

Use Subagents:
- feature-extractor (haiku): Scan StockApp for stock list DB structure.
- source-migrator (sonnet): Adapt StockApp patterns to current project.
- kotlin-implementer (sonnet): Implement in Clean Architecture.
- qa-verifier (sonnet): Test and verify.

Rules:
- DB schema plan requires Architect approval before implementation.
- Autocomplete must be responsive: filter locally from pre-populated DB, no network call.
- Incremental update: compare last saved date, fetch only newer data from API.
- 365-day cleanup: run on each analysis, delete records older than 365 days for that stock.
- History: FIFO when exceeding 30 items, oldest entry removed.
- After every code change: run gradlew assembleDebug.
- Log all changes to PROGRESS.md.
- Use only Kiwoom API and KIS API.

Workflow:
1. Lead reads TASK.md, picks next task.
2. DB schema tasks: Architect approves before implementation.
3. Implement, QA verifies each feature independently.
4. Lead marks task done after verification.

Completion (ALL must be met):
- Every task in TASK.md is checked done.
- Stock list DB pre-populated and autocomplete works.
- Incremental update saves new data only.
- Old data beyond 365 days auto-cleaned.
- History stores max 30 stocks with quick access UI.
- gradlew assembleDebug passes.
- gradlew test passes.
- IMPLEMENTATION_REPORT.md generated.
- CLAUDE.md updated.
- PROGRESS.md contains LOOP_COMPLETE.

Output COMPLETE when ALL verified.