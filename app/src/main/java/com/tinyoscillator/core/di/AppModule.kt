package com.tinyoscillator.core.di

import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.EtfDao
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.scraper.NaverFinanceScraper
import com.tinyoscillator.data.repository.EtfRepository
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.data.repository.MarketIndicatorRepository
import com.tinyoscillator.domain.usecase.AiAnalysisPreparer
import com.tinyoscillator.domain.usecase.CalcDemarkTDUseCase
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import com.tinyoscillator.domain.usecase.MarketOscillatorCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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
    fun provideAiAnalysisPreparer(): AiAnalysisPreparer = AiAnalysisPreparer()

    @Provides
    @Singleton
    fun provideFinancialRepository(
        financialCacheDao: FinancialCacheDao,
        kisApiClient: KisApiClient,
        json: Json
    ): FinancialRepository = FinancialRepository(financialCacheDao, kisApiClient, json)

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
    fun provideNaverFinanceScraper(): NaverFinanceScraper = NaverFinanceScraper()

    @Provides
    @Singleton
    fun provideMarketIndicatorRepository(
        oscillatorDao: MarketOscillatorDao,
        depositDao: MarketDepositDao,
        calculator: MarketOscillatorCalculator,
        scraper: NaverFinanceScraper,
        krxApiClient: KrxApiClient
    ): MarketIndicatorRepository = MarketIndicatorRepository(oscillatorDao, depositDao, calculator, scraper, krxApiClient)
}
