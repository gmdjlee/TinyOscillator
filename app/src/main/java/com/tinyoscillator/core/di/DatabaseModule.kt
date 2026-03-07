package com.tinyoscillator.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.dao.StockMasterDao
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return try {
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "tiny_oscillator.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
}
