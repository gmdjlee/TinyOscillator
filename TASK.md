# TASK.md — ETF Analysis Feature

## Phase 0: Analysis and Plan (iterations 1-3) — MUST COMPLETE BEFORE ANY IMPLEMENTATION
- [ ] E-001 Scan MarketMonitor_rev2: ETF menu structure, data models, scheduling, UI components
- [ ] E-002 Scan kotlin_krx latest: Active ETF APIs, constituent stock APIs, available functions
- [ ] E-003 Study current app patterns: navigation, code style, DI setup, existing settings structure
- [ ] E-004 Generate PLAN.md with phases, DB schema, API mapping, schedule strategy. Architect approves.
- [ ] E-005 STOP: Present PLAN.md to user. Wait for confirmation before proceeding.

## Phase 1: Quick Wins (iterations 4)
- [ ] E-006 Rename AppBar title: '수급 오실레이터' to '종목분석'
- [ ] E-007 Add '🖥 ETF분석' to main menu navigation (placeholder screen)
- [ ] E-008 Build verification after Phase 1

## Phase 2: Settings (iterations 5-6)
- [ ] E-009 Settings: KRX ID/PASSWORD input with EncryptedSharedPreferences storage
- [ ] E-010 Settings: ETF keyword include/exclude filter configuration (add/remove keywords, persist)
- [ ] E-011 Build verification. STOP: Present to user for confirmation.

## Phase 3: Data Layer (iterations 7-9)
- [ ] E-012 Room DB schema: active_etf (ticker, name, type, keywords), etf_daily_data (ohlcv, nav, etc.), etf_constituents (etf_ticker, stock_ticker, weight)
- [ ] E-013 kotlin_krx integration: Repository impl for fetching Active ETF list, daily data, constituents
- [ ] E-014 Keyword filter logic: filter ETF list by include/exclude keywords from settings
- [ ] E-015 Build verification. STOP: Present to user for confirmation.

## Phase 4: First Launch Flow (iterations 10-11)
- [ ] E-016 First launch: detect no KRX credentials, show KRX ID/PW input dialog
- [ ] E-017 After credential save: trigger initial 2-week data collection for filtered Active ETFs
- [ ] E-018 Build verification. STOP: Present to user for confirmation.

## Phase 5: ETF Analysis UI (iterations 12-14)
- [ ] E-019 ETF분석 screen: list of Active ETFs filtered by keywords, tap for detail
- [ ] E-020 ETF detail screen: daily data chart, constituent stocks list (adapt MarketMonitor_rev2 UI)
- [ ] E-021 Build verification. STOP: Present to user for confirmation.

## Phase 6: Scheduling (iterations 15-16)
- [ ] E-022 Implement scheduled data update: replicate MarketMonitor_rev2 timing approach
- [ ] E-023 Incremental update: fetch only new data since last update, append to DB
- [ ] E-024 Build verification. STOP: Present to user for confirmation.

## Phase 7: Final Verification (iterations 17-18)
- [ ] E-025 Full flow test: launch, credentials, collect, display, schedule update
- [ ] E-026 Regression: existing '종목분석' features unaffected
- [ ] E-027 Generate IMPLEMENTATION_REPORT.md, update CLAUDE.md

## Reference Mapping
| Feature | Source | Notes |
|---------|--------|-------|
| ETF menu UI/UX | MarketMonitor_rev2 ETF menu | Adapt to current patterns |
| ETF data APIs | kotlin_krx (latest) | Active ETF, constituents |
| Scheduling | MarketMonitor_rev2 scheduling | Same timing approach |
| KRX credentials | Current app settings pattern | EncryptedSharedPreferences |
| Keyword filters | New feature | Include/exclude keyword lists |