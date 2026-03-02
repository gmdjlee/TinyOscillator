# TASK.md — Stock DB, Search, History Features

## Phase 1: Analysis and DB Design (iterations 1-3)
- [ ] D-001 Scan StockApp: how stock name/code list is stored and populated
- [ ] D-002 Design Room DB schema. Architect approves before proceeding:
    - stock_master: ticker, name, market (pre-populated, searchable)
    - stock_analysis: ticker, date, ohlcv, indicators (max 365 days per stock)
    - analysis_history: ticker, name, last_analyzed_at (max 30 entries, FIFO)
- [ ] D-003 Design incremental update strategy: compare DB last date vs API, fetch delta only

## Phase 2: Stock Master DB (iterations 4-5)
- [ ] D-004 Create Room Entity, DAO, migration for stock_master table
- [ ] D-005 Implement pre-population: load stock list from StockApp approach (asset file or initial API call)
- [ ] D-006 Create Repository and UseCase: SearchStocks(query) returns filtered list

## Phase 3: Autocomplete Search (iterations 6-7)
- [ ] D-007 Create autocomplete ViewModel: debounce input, query stock_master locally
- [ ] D-008 Create autocomplete UI: text field with dropdown suggestions (name + code)

## Phase 4: Incremental Analysis Storage (iterations 8-10)
- [ ] D-009 Create stock_analysis Entity, DAO: insert, query by date range, delete older than 365 days
- [ ] D-010 Implement incremental update logic in Repository: check last saved date, fetch only new data
- [ ] D-011 Add 365-day retention cleanup: triggered on each analysis save

## Phase 5: Analysis History (iterations 11-13)
- [ ] D-012 Create analysis_history Entity, DAO: insert/update, query ordered by recency, limit 30
- [ ] D-013 Implement history logic: add entry on analysis, remove oldest if over 30, update timestamp on re-analysis
- [ ] D-014 Create history UI: list of recent 30 stocks, tap to navigate to analysis screen

## Phase 6: Integration and Verification (iterations 14-15)
- [ ] D-015 Wire Hilt DI for all new DAOs, Repositories, UseCases
- [ ] D-016 Test all flows: pre-populate, search, analyze, incremental update, retention cleanup, history FIFO
- [ ] D-017 Build verification + IMPLEMENTATION_REPORT.md + update CLAUDE.md