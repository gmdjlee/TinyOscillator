package com.tinyoscillator.core.di

import android.content.Context
import android.content.SharedPreferences
import com.tinyoscillator.data.local.llm.LlmRepositoryImpl
import com.tinyoscillator.data.mapper.AnalysisResponseParser
import com.tinyoscillator.data.mapper.ProbabilisticPromptBuilder
import com.tinyoscillator.data.repository.StatisticalRepositoryImpl
import com.tinyoscillator.domain.repository.LlmRepository
import com.tinyoscillator.domain.repository.StatisticalRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LogisticPrefs

@Module
@InstallIn(SingletonComponent::class)
abstract class StatisticalBindsModule {

    @Binds
    @Singleton
    abstract fun bindStatisticalRepository(
        impl: StatisticalRepositoryImpl
    ): StatisticalRepository

    @Binds
    @Singleton
    abstract fun bindLlmRepository(
        impl: LlmRepositoryImpl
    ): LlmRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StatisticalProvidesModule {

    @Provides
    @Singleton
    @LogisticPrefs
    fun provideLogisticPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("logistic_weights", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun providePromptBuilder(): ProbabilisticPromptBuilder = ProbabilisticPromptBuilder()

    @Provides
    @Singleton
    fun provideResponseParser(): AnalysisResponseParser = AnalysisResponseParser()
}
