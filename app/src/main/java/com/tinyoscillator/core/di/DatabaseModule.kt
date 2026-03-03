package com.tinyoscillator.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return try {
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "tiny_oscillator.db"
            )
                .addMigrations(MIGRATION_1_2)
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
                .addMigrations(MIGRATION_1_2)
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
}
