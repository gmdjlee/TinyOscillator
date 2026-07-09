package com.tinyoscillator.feature.bearsignal.di

import com.tinyoscillator.core.api.BokEcosApiClient
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao
import com.tinyoscillator.feature.bearsignal.data.remote.CustomsTradeApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.FredApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.StooqCsvClient
import com.tinyoscillator.feature.bearsignal.data.repository.BearSignalRepositoryImpl
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshExternalAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshMarketReturnsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** BearSignal 기능 Hilt 바인딩 모듈 (TASK.md §2). */
@Module
@InstallIn(SingletonComponent::class)
object BearSignalModule {

    @Provides
    @Singleton
    fun provideCustomsTradeApiClient(httpClient: OkHttpClient): CustomsTradeApiClient =
        CustomsTradeApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideFredApiClient(httpClient: OkHttpClient): FredApiClient =
        FredApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideStooqCsvClient(httpClient: OkHttpClient): StooqCsvClient =
        StooqCsvClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideBearSignalRepository(
        bearSignalDao: BearSignalDao,
        krxApiClient: KrxApiClient,
        apiConfigProvider: ApiConfigProvider,
        customsTradeApiClient: CustomsTradeApiClient,
        fredApiClient: FredApiClient,
        bokEcosApiClient: BokEcosApiClient,
        stooqCsvClient: StooqCsvClient
    ): BearSignalRepository = BearSignalRepositoryImpl(
        bearSignalDao,
        krxApiClient,
        apiConfigProvider,
        customsTradeApiClient,
        fredApiClient,
        bokEcosApiClient,
        stooqCsvClient
    )

    @Provides
    @Singleton
    fun provideRefreshAutoInputsUseCase(
        repository: BearSignalRepository
    ): RefreshAutoInputsUseCase = RefreshAutoInputsUseCase(repository)

    @Provides
    @Singleton
    fun provideRefreshExternalAutoInputsUseCase(
        repository: BearSignalRepository
    ): RefreshExternalAutoInputsUseCase = RefreshExternalAutoInputsUseCase(repository)

    @Provides
    @Singleton
    fun provideRefreshMarketReturnsUseCase(
        repository: BearSignalRepository
    ): RefreshMarketReturnsUseCase = RefreshMarketReturnsUseCase(repository)
}
