# PROGRESS.md — Stock DB/Search/History
## Status: LOOP_COMPLETE

## Completed Tasks

### D-001: StockApp Reference Analysis
- Stock list source: Kiwoom API `ka10099` (existing `StockListResponse`/`StockListItem` DTOs)
- DB structure: 3 tables (stock_master, analysis_cache, analysis_history)
- Reference: MarketMonitor_rev2 Room-based pattern adapted

### D-002: Room DB Schema Design
- `stock_master`: ticker (PK), name (indexed), market, last_updated
- `analysis_cache`: ticker+date (composite PK, indexed), market_cap, foreign_net, inst_net
- `analysis_history`: id (auto PK), ticker, name, last_analyzed_at (indexed DESC)
- Status: APPROVED & IMPLEMENTED

### D-003: Incremental Update Strategy
- On analysis: check `analysisCacheDao.getLatestDate(ticker)`
- If cache exists and < today: fetch only new days (latestDate+1 ~ today)
- If no cache: fetch full 365 days
- After save: `deleteOlderThan(ticker, today - 365 days)`
- Status: IMPLEMENTED

### D-004: Room Entities
- `StockMasterEntity.kt` — @Entity stock_master
- `AnalysisCacheEntity.kt` — @Entity analysis_cache (composite PK)
- `AnalysisHistoryEntity.kt` — @Entity analysis_history (autoGenerate PK)

### D-005: Stock Master Pre-population
- `StockMasterRepository.kt` — Checks DB count, calls ka10099 if empty, bulk inserts
- Triggered on ViewModel init

### D-006: Search UseCase
- `SearchStocksUseCase.kt` — SQL LIKE on name/ticker, returns Flow

### D-007-D-008: Autocomplete ViewModel + UI
- ViewModel: `_searchQuery` → debounce(200ms) → flatMapLatest → searchResults
- UI: Local DB search replaces API-based search, shows up to 10 results

### D-009-D-011: Analysis Cache + Incremental + Retention
- `StockRepository` refactored with `@Inject constructor` (AnalysisCacheDao)
- `getDailyTradingData()` implements incremental fetch + cache save + 365-day cleanup
- `loadFromCache()` reads from DB when cache is current

### D-012-D-014: History Entity + Logic + UI
- `SaveAnalysisHistoryUseCase` — delete existing ticker, insert new, FIFO cleanup (max 30)
- ViewModel exposes `analysisHistory: StateFlow<List<AnalysisHistoryEntity>>`
- UI: History section with clickable cards showing name, ticker, date

### D-015: Hilt DI Wiring
- `TinyOscillatorApp.kt` — @HiltAndroidApp
- `DatabaseModule.kt` — Room DB + 3 DAOs
- `AppModule.kt` — KiwoomApiClient, Json, CalcOscillatorUseCase
- `AndroidManifest.xml` — android:name=".TinyOscillatorApp"
- `MainActivity.kt` — @AndroidEntryPoint, hiltViewModel()
- `OscillatorViewModel.kt` — @HiltViewModel with @Inject constructor

### D-016-D-017: Build Verification
- `./gradlew assembleDebug` — BUILD SUCCESSFUL (0 errors, 0 warnings)

## Changes Applied

| Task | Files Created | Files Modified | Verified |
|------|--------------|----------------|----------|
| D-003 | - | build.gradle.kts (root), app/build.gradle.kts | Y |
| D-004 | 3 entity files | - | Y |
| D-005-006 | StockMasterRepository.kt, SearchStocksUseCase.kt | - | Y |
| D-007-008 | - | OscillatorViewModel.kt, MainActivity.kt | Y |
| D-009-011 | - | StockRepository.kt | Y |
| D-012-014 | SaveAnalysisHistoryUseCase.kt | OscillatorViewModel.kt, MainActivity.kt | Y |
| D-015 | TinyOscillatorApp.kt, DatabaseModule.kt, AppModule.kt, AppDatabase.kt, 3 DAOs | AndroidManifest.xml | Y |

## New Files (14)
1. `core/database/entity/StockMasterEntity.kt`
2. `core/database/entity/AnalysisCacheEntity.kt`
3. `core/database/entity/AnalysisHistoryEntity.kt`
4. `core/database/dao/StockMasterDao.kt`
5. `core/database/dao/AnalysisCacheDao.kt`
6. `core/database/dao/AnalysisHistoryDao.kt`
7. `core/database/AppDatabase.kt`
8. `core/di/DatabaseModule.kt`
9. `core/di/AppModule.kt`
10. `data/repository/StockMasterRepository.kt`
11. `domain/usecase/SearchStocksUseCase.kt`
12. `domain/usecase/SaveAnalysisHistoryUseCase.kt`
13. `TinyOscillatorApp.kt`

## Modified Files (5)
1. `build.gradle.kts` (root) — KSP + Hilt plugins
2. `app/build.gradle.kts` — Room, Hilt, KSP dependencies
3. `AndroidManifest.xml` — android:name=".TinyOscillatorApp"
4. `MainActivity.kt` — @AndroidEntryPoint, hiltViewModel(), history UI
5. `presentation/viewmodel/OscillatorViewModel.kt` — @HiltViewModel, local search, cache, history
6. `data/repository/StockRepository.kt` — @Inject, incremental cache logic

---
