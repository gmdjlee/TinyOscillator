package com.tinyoscillator.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.ConsensusReportDao
import com.tinyoscillator.core.database.dao.DartDao
import com.tinyoscillator.core.database.dao.EnsembleHistoryDao
import com.tinyoscillator.core.database.dao.IncrementalModelDao
import com.tinyoscillator.core.database.dao.MacroDao
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.core.database.dao.SectorIndexCandleDao
import com.tinyoscillator.core.database.dao.SectorMasterDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.dao.FearGreedDao
import com.tinyoscillator.core.database.dao.ThemeGroupDao
import com.tinyoscillator.core.database.dao.ThemeStockDao
import com.tinyoscillator.core.database.dao.WorkerLogDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.core.database.entity.AnalysisHistoryEntity
import com.tinyoscillator.core.database.entity.AnalysisSnapshotEntity
import com.tinyoscillator.core.database.entity.CalibrationStateEntity
import com.tinyoscillator.core.database.entity.ConsensusReportEntity
import com.tinyoscillator.core.database.entity.DartCorpCodeEntity
import com.tinyoscillator.core.database.entity.EnsembleHistoryEntity
import com.tinyoscillator.core.database.entity.IncrementalModelStateEntity
import com.tinyoscillator.core.database.entity.ModelDriftAlertEntity
import com.tinyoscillator.core.database.entity.MacroIndicatorEntity
import com.tinyoscillator.core.database.entity.KospiIndexEntity
import com.tinyoscillator.core.database.entity.RegimeStateEntity
import com.tinyoscillator.core.database.entity.FeatureCacheEntity
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.FearGreedEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.core.database.entity.FinancialCacheEntity
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity
import com.tinyoscillator.core.database.entity.MarketDepositEntity
import com.tinyoscillator.core.database.entity.MarketOscillatorEntity
import com.tinyoscillator.core.database.entity.PortfolioEntity
import com.tinyoscillator.core.database.entity.PortfolioHoldingEntity
import com.tinyoscillator.core.database.entity.PortfolioTransactionEntity
import com.tinyoscillator.core.database.entity.SectorIndexCandleEntity
import com.tinyoscillator.core.database.entity.SectorMasterEntity
import com.tinyoscillator.core.database.entity.SignalHistoryEntity
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.core.database.entity.ThemeGroupEntity
import com.tinyoscillator.core.database.entity.ThemeStockEntity
import com.tinyoscillator.core.database.entity.WorkerLogEntity

@Database(
    entities = [
        StockMasterEntity::class,
        AnalysisCacheEntity::class,
        AnalysisHistoryEntity::class,
        FinancialCacheEntity::class,
        EtfEntity::class,
        EtfHoldingEntity::class,
        MarketOscillatorEntity::class,
        MarketDepositEntity::class,
        PortfolioEntity::class,
        PortfolioHoldingEntity::class,
        PortfolioTransactionEntity::class,
        FundamentalCacheEntity::class,
        WorkerLogEntity::class,
        ConsensusReportEntity::class,
        FearGreedEntity::class,
        SignalHistoryEntity::class,
        CalibrationStateEntity::class,
        KospiIndexEntity::class,
        RegimeStateEntity::class,
        FeatureCacheEntity::class,
        DartCorpCodeEntity::class,
        MacroIndicatorEntity::class,
        EnsembleHistoryEntity::class,
        IncrementalModelStateEntity::class,
        ModelDriftAlertEntity::class,
        AnalysisSnapshotEntity::class,
        SectorMasterEntity::class,
        SectorIndexCandleEntity::class,
        ThemeGroupEntity::class,
        ThemeStockEntity::class,
    ],
    version = 30,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockMasterDao(): StockMasterDao
    abstract fun analysisCacheDao(): AnalysisCacheDao
    abstract fun analysisHistoryDao(): AnalysisHistoryDao
    abstract fun financialCacheDao(): FinancialCacheDao
    abstract fun etfDao(): EtfDao
    abstract fun marketOscillatorDao(): MarketOscillatorDao
    abstract fun marketDepositDao(): MarketDepositDao
    abstract fun portfolioDao(): PortfolioDao
    abstract fun fundamentalCacheDao(): FundamentalCacheDao
    abstract fun workerLogDao(): WorkerLogDao
    abstract fun consensusReportDao(): ConsensusReportDao
    abstract fun fearGreedDao(): FearGreedDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun regimeDao(): RegimeDao
    abstract fun featureCacheDao(): FeatureCacheDao
    abstract fun dartDao(): DartDao
    abstract fun macroDao(): MacroDao
    abstract fun ensembleHistoryDao(): EnsembleHistoryDao
    abstract fun incrementalModelDao(): IncrementalModelDao
    abstract fun analysisSnapshotDao(): AnalysisSnapshotDao
    abstract fun sectorMasterDao(): SectorMasterDao
    abstract fun sectorIndexCandleDao(): SectorIndexCandleDao
    abstract fun themeGroupDao(): ThemeGroupDao
    abstract fun themeStockDao(): ThemeStockDao
}
