package com.tinyoscillator.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.ConsensusReportDao
import com.tinyoscillator.core.database.dao.DartDao
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.dao.WorkerLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /** Migration v1→v2: added financial_cache table */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `financial_cache` (" +
                        "`ticker` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`data` TEXT NOT NULL, " +
                        "`cachedAt` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`ticker`))"
                )
                Timber.d("Migration v1→v2 성공: financial_cache 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v1→v2 실패")
                throw e
            }
        }
    }

    /** Migration v2→v3: added close_price column to analysis_cache */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "ALTER TABLE `analysis_cache` ADD COLUMN `close_price` INTEGER NOT NULL DEFAULT 0"
                )
                Timber.d("Migration v2→v3 성공: close_price 컬럼 추가")
            } catch (e: Exception) {
                Timber.e(e, "Migration v2→v3 실패")
                throw e
            }
        }
    }

    /** Migration v3→v4: added etfs and etf_holdings tables */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `etfs` (" +
                        "`ticker` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`isin_code` TEXT NOT NULL, " +
                        "`index_name` TEXT, " +
                        "`total_fee` REAL, " +
                        "`updated_at` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`ticker`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `etf_holdings` (" +
                        "`etf_ticker` TEXT NOT NULL, " +
                        "`stock_ticker` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`stock_name` TEXT NOT NULL, " +
                        "`weight` REAL, " +
                        "`shares` INTEGER NOT NULL DEFAULT 0, " +
                        "`amount` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`etf_ticker`, `stock_ticker`, `date`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_etf_holdings_date` ON `etf_holdings` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_etf_holdings_etf_ticker_date` ON `etf_holdings` (`etf_ticker`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_etf_holdings_stock_ticker` ON `etf_holdings` (`stock_ticker`)")
                Timber.d("Migration v3→v4 성공: etfs, etf_holdings 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v3→v4 실패")
                throw e
            }
        }
    }

    /** Migration v4→v5: added market_oscillator and market_deposits tables */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `market_oscillator` (" +
                        "`id` TEXT NOT NULL, " +
                        "`market` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`index_value` REAL NOT NULL, " +
                        "`oscillator` REAL NOT NULL, " +
                        "`last_updated` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_oscillator_date` ON `market_oscillator` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_market_oscillator_market_date` ON `market_oscillator` (`market`, `date`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `market_deposits` (" +
                        "`date` TEXT NOT NULL, " +
                        "`deposit_amount` REAL NOT NULL, " +
                        "`deposit_change` REAL NOT NULL, " +
                        "`credit_amount` REAL NOT NULL, " +
                        "`credit_change` REAL NOT NULL, " +
                        "`last_updated` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`date`))"
                )
                Timber.d("Migration v4→v5 성공: market_oscillator, market_deposits 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v4→v5 실패")
                throw e
            }
        }
    }

    /** Migration v5→v6: added sector column to stock_master */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "ALTER TABLE `stock_master` ADD COLUMN `sector` TEXT NOT NULL DEFAULT ''"
                )
                Timber.d("Migration v5→v6 성공: stock_master에 sector 컬럼 추가")
            } catch (e: Exception) {
                Timber.e(e, "Migration v5→v6 실패")
                throw e
            }
        }
    }

    /** Migration v6→v7: added portfolio tables (portfolios, portfolio_holdings, portfolio_transactions) */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `portfolios` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`max_weight_percent` INTEGER NOT NULL, " +
                        "`total_amount_limit` INTEGER, " +
                        "`created_at` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `portfolio_holdings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`portfolio_id` INTEGER NOT NULL, " +
                        "`ticker` TEXT NOT NULL, " +
                        "`stock_name` TEXT NOT NULL, " +
                        "`market` TEXT NOT NULL, " +
                        "`sector` TEXT NOT NULL, " +
                        "`last_price` INTEGER NOT NULL DEFAULT 0, " +
                        "`price_updated_at` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_portfolio_holdings_portfolio_id` ON `portfolio_holdings` (`portfolio_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_portfolio_holdings_ticker` ON `portfolio_holdings` (`ticker`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `portfolio_transactions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`holding_id` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`shares` INTEGER NOT NULL, " +
                        "`price_per_share` INTEGER NOT NULL, " +
                        "`memo` TEXT NOT NULL DEFAULT '', " +
                        "`created_at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_portfolio_transactions_holding_id` ON `portfolio_transactions` (`holding_id`)")
                Timber.d("Migration v6→v7 성공: portfolios, portfolio_holdings, portfolio_transactions 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v6→v7 실패")
                throw e
            }
        }
    }

    /** Migration v7→v8: added target_price column to portfolio_holdings */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "ALTER TABLE `portfolio_holdings` ADD COLUMN `target_price` INTEGER NOT NULL DEFAULT 0"
                )
                Timber.d("Migration v7→v8 성공: portfolio_holdings에 target_price 컬럼 추가")
            } catch (e: Exception) {
                Timber.e(e, "Migration v7→v8 실패")
                throw e
            }
        }
    }

    /** Migration v8→v9: added fundamental_cache table */
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fundamental_cache` (" +
                        "`ticker` TEXT NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`close` INTEGER NOT NULL, " +
                        "`eps` INTEGER NOT NULL, " +
                        "`per` REAL NOT NULL, " +
                        "`bps` INTEGER NOT NULL, " +
                        "`pbr` REAL NOT NULL, " +
                        "`dps` INTEGER NOT NULL, " +
                        "`dividend_yield` REAL NOT NULL, " +
                        "PRIMARY KEY(`ticker`, `date`))"
                )
                Timber.d("Migration v8→v9 성공: fundamental_cache 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v8→v9 실패")
                throw e
            }
        }
    }

    /** Migration v9→v10: added worker_logs table */
    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `worker_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`worker_name` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`message` TEXT NOT NULL, " +
                        "`error_detail` TEXT, " +
                        "`executed_at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_worker_logs_worker_name` ON `worker_logs` (`worker_name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_worker_logs_executed_at` ON `worker_logs` (`executed_at`)")
                Timber.d("Migration v9→v10 성공: worker_logs 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v9→v10 실패")
                throw e
            }
        }
    }

    /** Migration v10→v11: added consensus_reports table */
    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `consensus_reports` (" +
                        "`write_date` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`prev_opinion` TEXT NOT NULL, " +
                        "`opinion` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`stock_ticker` TEXT NOT NULL, " +
                        "`author` TEXT NOT NULL, " +
                        "`institution` TEXT NOT NULL, " +
                        "`target_price` INTEGER NOT NULL, " +
                        "`current_price` INTEGER NOT NULL, " +
                        "`divergence_rate` REAL NOT NULL, " +
                        "PRIMARY KEY(`stock_ticker`, `write_date`, `author`, `institution`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consensus_reports_write_date` ON `consensus_reports` (`write_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consensus_reports_stock_ticker_write_date` ON `consensus_reports` (`stock_ticker`, `write_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consensus_reports_institution` ON `consensus_reports` (`institution`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consensus_reports_category` ON `consensus_reports` (`category`)")
                Timber.d("Migration v10→v11 성공: consensus_reports 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v10→v11 실패")
                throw e
            }
        }
    }

    /** Migration v12→v13: Fear & Greed 지수 테이블 추가 */
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `fear_greed_index` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `market` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `indexValue` REAL NOT NULL,
                        `fearGreedValue` REAL NOT NULL,
                        `oscillator` REAL NOT NULL,
                        `rsi` REAL NOT NULL,
                        `momentum` REAL NOT NULL,
                        `putCallRatio` REAL NOT NULL,
                        `volatility` REAL NOT NULL,
                        `spread` REAL NOT NULL,
                        `lastUpdated` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fear_greed_index_date` ON `fear_greed_index` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fear_greed_index_market_date` ON `fear_greed_index` (`market`, `date`)")
                Timber.d("Migration v12→v13 성공: fear_greed_index 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v12→v13 실패")
                throw e
            }
        }
    }

    /** Migration v13→v14: added signal_history and calibration_state tables */
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `signal_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ticker` TEXT NOT NULL,
                        `algo_name` TEXT NOT NULL,
                        `raw_score` REAL NOT NULL,
                        `date` TEXT NOT NULL,
                        `outcome_return` REAL,
                        `created_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_signal_history_algo_name_date` ON `signal_history` (`algo_name`, `date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_signal_history_ticker_date` ON `signal_history` (`ticker`, `date`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `calibration_state` (
                        `algo_name` TEXT NOT NULL PRIMARY KEY,
                        `method` TEXT NOT NULL,
                        `state_json` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                Timber.d("Migration v13→v14 성공: signal_history, calibration_state 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v13→v14 실패")
                throw e
            }
        }
    }

    /** Migration v14→v15: added kospi_index and regime_state tables */
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `kospi_index` (
                        `date` TEXT NOT NULL PRIMARY KEY,
                        `close_value` REAL NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_kospi_index_date` ON `kospi_index` (`date`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `regime_state` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `state_json` TEXT NOT NULL,
                        `regime_name` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `trained_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                Timber.d("Migration v14→v15 성공: kospi_index, regime_state 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v14→v15 실패")
                throw e
            }
        }
    }

    /** Migration v11→v12: added stock_name column to consensus_reports */
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    "ALTER TABLE `consensus_reports` ADD COLUMN `stock_name` TEXT NOT NULL DEFAULT ''"
                )
                Timber.d("Migration v11→v12 성공: consensus_reports에 stock_name 컬럼 추가")
            } catch (e: Exception) {
                Timber.e(e, "Migration v11→v12 실패")
                throw e
            }
        }
    }

    /** Migration v15→v16: added feature_cache table */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `feature_cache` (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `ticker` TEXT NOT NULL,
                        `feature_name` TEXT NOT NULL,
                        `value` TEXT NOT NULL,
                        `computed_at` INTEGER NOT NULL,
                        `ttl_ms` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_feature_cache_ticker` ON `feature_cache` (`ticker`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_feature_cache_computed_at` ON `feature_cache` (`computed_at`)")
                Timber.d("Migration v15→v16 성공: feature_cache 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v15→v16 실패")
                throw e
            }
        }
    }

    /** Migration v16→v17: added dart_corp_code table */
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `dart_corp_code` (
                        `ticker` TEXT NOT NULL PRIMARY KEY,
                        `corp_code` TEXT NOT NULL,
                        `corp_name` TEXT NOT NULL,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dart_corp_code_corp_code` ON `dart_corp_code` (`corp_code`)")
                Timber.d("Migration v16→v17 성공: dart_corp_code 테이블 생성")
            } catch (e: Exception) {
                Timber.e(e, "Migration v16→v17 실패")
                throw e
            }
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return try {
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "tiny_oscillator.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Timber.d("Database opened successfully (version %d)", db.version)
                    }
                })
                .build()
        } catch (e: Exception) {
            Timber.e(e, "Database 빌더 생성 실패 → 재생성 시도")
            // All data is cache (re-fetchable) — safe to recreate
            context.deleteDatabase("tiny_oscillator.db")
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "tiny_oscillator.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .build()
        }
    }

    @Provides
    fun provideStockMasterDao(db: AppDatabase): StockMasterDao = db.stockMasterDao()

    @Provides
    fun provideAnalysisCacheDao(db: AppDatabase): AnalysisCacheDao = db.analysisCacheDao()

    @Provides
    fun provideAnalysisHistoryDao(db: AppDatabase): AnalysisHistoryDao = db.analysisHistoryDao()

    @Provides
    fun provideFinancialCacheDao(db: AppDatabase): FinancialCacheDao = db.financialCacheDao()

    @Provides
    fun provideEtfDao(db: AppDatabase): EtfDao = db.etfDao()

    @Provides
    fun provideMarketOscillatorDao(db: AppDatabase): MarketOscillatorDao = db.marketOscillatorDao()

    @Provides
    fun provideMarketDepositDao(db: AppDatabase): MarketDepositDao = db.marketDepositDao()

    @Provides
    fun providePortfolioDao(db: AppDatabase): PortfolioDao = db.portfolioDao()

    @Provides
    fun provideFundamentalCacheDao(db: AppDatabase): FundamentalCacheDao = db.fundamentalCacheDao()

    @Provides
    fun provideWorkerLogDao(db: AppDatabase): WorkerLogDao = db.workerLogDao()

    @Provides
    fun provideConsensusReportDao(db: AppDatabase): ConsensusReportDao = db.consensusReportDao()

    @Provides
    fun provideFearGreedDao(db: AppDatabase): com.tinyoscillator.core.database.dao.FearGreedDao = db.fearGreedDao()

    @Provides
    fun provideCalibrationDao(db: AppDatabase): CalibrationDao = db.calibrationDao()

    @Provides
    fun provideRegimeDao(db: AppDatabase): RegimeDao = db.regimeDao()

    @Provides
    fun provideFeatureCacheDao(db: AppDatabase): FeatureCacheDao = db.featureCacheDao()

    @Provides
    fun provideDartDao(db: AppDatabase): DartDao = db.dartDao()
}
