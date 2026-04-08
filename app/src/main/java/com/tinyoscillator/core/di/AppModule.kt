package com.tinyoscillator.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.api.BokEcosApiClient
import com.tinyoscillator.core.api.DartApiClient
import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.ConsensusReportDao
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.dao.PortfolioDao
import com.tinyoscillator.core.scraper.EquityReportScraper
import com.tinyoscillator.core.scraper.FnGuideReportScraper
import com.tinyoscillator.core.scraper.NaverFinanceScraper
import com.tinyoscillator.data.preferences.IndicatorPreferencesRepository
import com.tinyoscillator.data.repository.ConsensusRepository
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.InvestOpinionRepository
import com.tinyoscillator.data.repository.FundamentalHistoryRepository
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.data.repository.PortfolioRepository
import com.tinyoscillator.data.repository.StockRepository
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.ProbabilityInterpreter
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.MarketOscillatorCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

private val Context.indicatorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "indicator_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideIndicatorDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.indicatorDataStore

    @Provides
    @Singleton
    fun provideIndicatorPreferencesRepository(
        dataStore: DataStore<Preferences>,
    ): IndicatorPreferencesRepository = IndicatorPreferencesRepository(dataStore)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = KiwoomApiClient.createDefaultClient()

    @Provides
    @Singleton
    fun provideKiwoomApiClient(httpClient: OkHttpClient): KiwoomApiClient =
        KiwoomApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideKisApiClient(httpClient: OkHttpClient): KisApiClient =
        KisApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideJson(): Json = KiwoomApiClient.createDefaultJson()

    @Provides
    @Singleton
    fun provideCalcOscillatorUseCase(): CalcOscillatorUseCase = CalcOscillatorUseCase()

    @Provides
    @Singleton
    fun provideCalcDemarkTDUseCase(): CalcDemarkTDUseCase = CalcDemarkTDUseCase()

    @Provides
    @Singleton
    fun provideAiApiClient(httpClient: OkHttpClient): AiApiClient =
        AiApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideDartApiClient(httpClient: OkHttpClient): DartApiClient =
        DartApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideBokEcosApiClient(httpClient: OkHttpClient): BokEcosApiClient =
        BokEcosApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideAiAnalysisPreparer(): AiAnalysisPreparer = AiAnalysisPreparer()

    @Provides
    @Singleton
    fun provideProbabilityInterpreter(): ProbabilityInterpreter = ProbabilityInterpreter()

    @Provides
    @Singleton
    fun provideFinancialRepository(
        financialCacheDao: FinancialCacheDao,
        kisApiClient: KisApiClient,
        json: Json
    ): FinancialRepository = FinancialRepository(financialCacheDao, kisApiClient, json)

    @Provides
    @Singleton
    fun provideInvestOpinionRepository(
        kisApiClient: KisApiClient,
        json: Json
    ): InvestOpinionRepository = InvestOpinionRepository(kisApiClient, json)

    @Provides
    @Singleton
    fun provideKrxApiClient(): KrxApiClient = KrxApiClient()

    @Provides
    @Singleton
    fun provideEtfRepository(
        etfDao: EtfDao,
        krxApiClient: KrxApiClient
    ): EtfRepository = EtfRepository(etfDao, krxApiClient)

    @Provides
    @Singleton
    fun provideMarketOscillatorCalculator(krxApiClient: KrxApiClient): MarketOscillatorCalculator =
        MarketOscillatorCalculator(krxApiClient)

    @Provides
    @Singleton
    fun provideNaverFinanceScraper(httpClient: OkHttpClient): NaverFinanceScraper = NaverFinanceScraper(httpClient)

    @Provides
    @Singleton
    fun providePortfolioRepository(
        portfolioDao: PortfolioDao,
        analysisCacheDao: AnalysisCacheDao,
        stockRepository: StockRepository
    ): PortfolioRepository = PortfolioRepository(portfolioDao, analysisCacheDao, stockRepository)

    @Provides
    @Singleton
    fun provideMarketIndicatorRepository(
        oscillatorDao: MarketOscillatorDao,
        depositDao: MarketDepositDao,
        calculator: MarketOscillatorCalculator,
        scraper: NaverFinanceScraper,
        krxApiClient: KrxApiClient
    ): MarketIndicatorRepository = MarketIndicatorRepository(oscillatorDao, depositDao, calculator, scraper, krxApiClient)

    @Provides
    @Singleton
    fun provideEquityReportScraper(httpClient: OkHttpClient): EquityReportScraper =
        EquityReportScraper(httpClient)

    @Provides
    @Singleton
    fun provideFnGuideReportScraper(httpClient: OkHttpClient): FnGuideReportScraper =
        FnGuideReportScraper(httpClient)

    @Provides
    @Singleton
    fun provideConsensusRepository(
        consensusReportDao: ConsensusReportDao,
        equityScraper: EquityReportScraper,
        fnGuideScraper: FnGuideReportScraper,
        analysisCacheDao: AnalysisCacheDao
    ): ConsensusRepository = ConsensusRepository(consensusReportDao, equityScraper, fnGuideScraper, analysisCacheDao)

    @Provides
    @Singleton
    fun provideFundamentalHistoryRepository(
        fundamentalCacheDao: FundamentalCacheDao,
        krxApiClient: KrxApiClient
    ): FundamentalHistoryRepository = FundamentalHistoryRepository(fundamentalCacheDao, krxApiClient)

    @Provides
    @Singleton
    fun provideFearGreedRepository(
        fearGreedDao: com.tinyoscillator.core.database.dao.FearGreedDao,
        krxApiClient: KrxApiClient
    ): com.tinyoscillator.data.repository.FearGreedRepository =
        com.tinyoscillator.data.repository.FearGreedRepository(fearGreedDao, krxApiClient)
}
