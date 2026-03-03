package com.tinyoscillator.core.di

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.data.repository.FinancialRepository
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
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
    fun provideFinancialRepository(
        financialCacheDao: FinancialCacheDao,
        kisApiClient: KisApiClient,
        json: Json
    ): FinancialRepository = FinancialRepository(financialCacheDao, kisApiClient, json)
}
