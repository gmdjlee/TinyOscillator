package com.tinyoscillator.core.di

import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.domain.usecase.CalcOscillatorUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideKiwoomApiClient(): KiwoomApiClient = KiwoomApiClient()

    @Provides
    @Singleton
    fun provideJson(): Json = KiwoomApiClient.createDefaultJson()

    @Provides
    fun provideCalcOscillatorUseCase(): CalcOscillatorUseCase = CalcOscillatorUseCase()
}
