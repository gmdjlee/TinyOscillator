package com.tinyoscillator.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import com.tinyoscillator.core.database.entity.StockMasterEntity

@Database(
    entities = [
        StockMasterEntity::class,
        AnalysisCacheEntity::class,
        AnalysisHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockMasterDao(): StockMasterDao
    abstract fun analysisCacheDao(): AnalysisCacheDao
    abstract fun analysisHistoryDao(): AnalysisHistoryDao
}
