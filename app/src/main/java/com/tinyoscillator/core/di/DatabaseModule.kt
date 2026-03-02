package com.tinyoscillator.core.di

import android.content.Context
import androidx.room.Room
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tiny_oscillator.db"
        ).build()
    }

    @Provides
    fun provideStockMasterDao(db: AppDatabase): StockMasterDao = db.stockMasterDao()

    @Provides
    fun provideAnalysisCacheDao(db: AppDatabase): AnalysisCacheDao = db.analysisCacheDao()

    @Provides
    fun provideAnalysisHistoryDao(db: AppDatabase): AnalysisHistoryDao = db.analysisHistoryDao()
}
