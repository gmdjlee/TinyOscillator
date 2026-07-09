package com.tinyoscillator.feature.bearsignal.di

import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao
import com.tinyoscillator.feature.bearsignal.data.repository.BearSignalRepositoryImpl
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** BearSignal 기능 Hilt 바인딩 모듈 (TASK.md §2). */
@Module
@InstallIn(SingletonComponent::class)
object BearSignalModule {

    @Provides
    @Singleton
    fun provideBearSignalRepository(
        bearSignalDao: BearSignalDao,
        krxApiClient: KrxApiClient,
        apiConfigProvider: ApiConfigProvider
    ): BearSignalRepository = BearSignalRepositoryImpl(bearSignalDao, krxApiClient, apiConfigProvider)

    @Provides
    @Singleton
    fun provideRefreshAutoInputsUseCase(
        repository: BearSignalRepository
    ): RefreshAutoInputsUseCase = RefreshAutoInputsUseCase(repository)
}
