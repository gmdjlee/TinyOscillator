package com.tinyoscillator.core.di

import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.AnalysisHistoryDao
import com.tinyoscillator.core.database.dao.AnalysisSnapshotDao
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.ConsensusReportDao
import com.tinyoscillator.core.database.dao.DartDao
import com.tinyoscillator.core.database.dao.EnsembleHistoryDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FearGreedDao
import com.tinyoscillator.core.database.dao.FeatureCacheDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.IncrementalModelDao
import com.tinyoscillator.core.database.dao.MacroDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.core.database.dao.RegimeDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.dao.UserThemeDao
import com.tinyoscillator.core.database.dao.WorkerLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * AppDatabase가 노출하는 DAO들을 Hilt에 제공하는 모듈.
 *
 * [DatabaseModule]에서 분리됨 (Phase 4.5) — DAO 프로바이더는 기계적 위임이므로
 * 마이그레이션·백업 로직과 섞여 있을 필요가 없다.
 */
@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideStockMasterDao(db: AppDatabase): StockMasterDao = db.stockMasterDao()

    @Provides
    fun provideAnalysisCacheDao(db: AppDatabase): AnalysisCacheDao = db.analysisCacheDao()

    @Provides
    fun provideAnalysisHistoryDao(db: AppDatabase): AnalysisHistoryDao = db.analysisHistoryDao()

    @Provides
    fun provideAnalysisSnapshotDao(db: AppDatabase): AnalysisSnapshotDao = db.analysisSnapshotDao()

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
    fun provideFearGreedDao(db: AppDatabase): FearGreedDao = db.fearGreedDao()

    @Provides
    fun provideCalibrationDao(db: AppDatabase): CalibrationDao = db.calibrationDao()

    @Provides
    fun provideRegimeDao(db: AppDatabase): RegimeDao = db.regimeDao()

    @Provides
    fun provideFeatureCacheDao(db: AppDatabase): FeatureCacheDao = db.featureCacheDao()

    @Provides
    fun provideDartDao(db: AppDatabase): DartDao = db.dartDao()

    @Provides
    fun provideMacroDao(db: AppDatabase): MacroDao = db.macroDao()

    @Provides
    fun provideEnsembleHistoryDao(db: AppDatabase): EnsembleHistoryDao = db.ensembleHistoryDao()

    @Provides
    fun provideIncrementalModelDao(db: AppDatabase): IncrementalModelDao = db.incrementalModelDao()

    @Provides
    fun provideUserThemeDao(db: AppDatabase): UserThemeDao = db.userThemeDao()
}
