package com.tinyoscillator.feature.bearsignal.di

import android.content.Context
import com.tinyoscillator.core.api.BokEcosApiClient
import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextDao
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao
import com.tinyoscillator.feature.bearsignal.data.local.BearSnapshotDao
import com.tinyoscillator.feature.bearsignal.data.local.ThresholdsProvider
import com.tinyoscillator.feature.bearsignal.data.remote.CustomsTradeApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.FredApiClient
import com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource
import com.tinyoscillator.feature.bearsignal.data.remote.StooqCsvClient
import com.tinyoscillator.feature.bearsignal.data.remote.YahooChartApiClient
import com.tinyoscillator.feature.bearsignal.data.repository.AiContextRepositoryImpl
import com.tinyoscillator.feature.bearsignal.data.repository.BearSignalRepositoryImpl
import com.tinyoscillator.feature.bearsignal.data.repository.SnapshotRepositoryImpl
import com.tinyoscillator.feature.bearsignal.data.repository.SuggestionRepositoryImpl
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholds
import com.tinyoscillator.feature.bearsignal.domain.repository.AiContextRepository
import com.tinyoscillator.feature.bearsignal.domain.repository.BearSignalRepository
import com.tinyoscillator.feature.bearsignal.domain.repository.SnapshotRepository
import com.tinyoscillator.feature.bearsignal.domain.repository.SuggestionRepository
import com.tinyoscillator.feature.bearsignal.domain.usecase.ApplySuggestionUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ApproveAiContextClaimsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.BuildBearSnapshotUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.DetectTransitionsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.EvaluateSnapshotFreshnessUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.FetchAiContextUpdatesUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.FetchSuggestionsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.GetApprovedAiContextUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.MergeBearSignalInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ObserveBearSignalStateUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshExternalAutoInputsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.RefreshMarketReturnsUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.ResetToReportBaselineUseCase
import com.tinyoscillator.feature.bearsignal.domain.usecase.UpdateManualInputUseCase
import com.tinyoscillator.presentation.settings.loadBearSignalIndexSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/** BearSignal 기능 Hilt 바인딩 모듈 (TASK_bear_signal_console.md §2). */
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
    fun provideYahooChartApiClient(httpClient: OkHttpClient): YahooChartApiClient =
        YahooChartApiClient(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideBearSignalRepository(
        @ApplicationContext context: Context,
        bearSignalDao: BearSignalDao,
        krxApiClient: KrxApiClient,
        apiConfigProvider: ApiConfigProvider,
        customsTradeApiClient: CustomsTradeApiClient,
        fredApiClient: FredApiClient,
        bokEcosApiClient: BokEcosApiClient,
        stooqCsvClient: StooqCsvClient,
        yahooChartApiClient: YahooChartApiClient,
        marketDepositDao: MarketDepositDao
    ): BearSignalRepository = BearSignalRepositoryImpl(
        bearSignalDao,
        krxApiClient,
        apiConfigProvider,
        customsTradeApiClient,
        fredApiClient,
        bokEcosApiClient,
        stooqCsvClient,
        yahooChartApiClient,
        marketDepositDao,
        // 설정 변경이 다음 갱신에 즉시 반영되도록 캐시 없이 매 갱신마다 읽는다(월 1회 + 수동 갱신뿐)
        indexSourceProvider = { loadBearSignalIndexSource(context) }
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

    @Provides
    @Singleton
    fun provideUpdateManualInputUseCase(
        repository: BearSignalRepository
    ): UpdateManualInputUseCase = UpdateManualInputUseCase(repository)

    @Provides
    @Singleton
    fun provideResetToReportBaselineUseCase(
        repository: BearSignalRepository
    ): ResetToReportBaselineUseCase = ResetToReportBaselineUseCase(repository)

    @Provides
    @Singleton
    fun provideMergeBearSignalInputsUseCase(): MergeBearSignalInputsUseCase = MergeBearSignalInputsUseCase()

    /** §3.0 v1.2 임계치 외부화 — `assets/bear_thresholds.json` 로드 (Context 필요, 앱 전용 계층). */
    @Provides
    @Singleton
    fun provideThresholdsProvider(@ApplicationContext context: Context): ThresholdsProvider =
        ThresholdsProvider(context)

    /** §3.0 임계치 SSOT 인스턴스 — 앱 기동 시 1회 로드해 싱글턴으로 공유(`BearThresholds`). */
    @Provides
    @Singleton
    fun provideBearThresholds(thresholdsProvider: ThresholdsProvider): BearThresholds =
        thresholdsProvider.load()

    @Provides
    @Singleton
    fun provideComputeBearSignalUseCase(thresholds: BearThresholds): ComputeBearSignalUseCase =
        ComputeBearSignalUseCase(thresholds)

    @Provides
    @Singleton
    fun provideObserveBearSignalStateUseCase(
        repository: BearSignalRepository,
        mergeBearSignalInputsUseCase: MergeBearSignalInputsUseCase,
        computeBearSignalUseCase: ComputeBearSignalUseCase
    ): ObserveBearSignalStateUseCase = ObserveBearSignalStateUseCase(
        repository,
        mergeBearSignalInputsUseCase,
        computeBearSignalUseCase
    )

    // ── §6.1 Phase 3.5-1: 스냅샷 이력·전이 감지 ──────────────────────────

    @Provides
    @Singleton
    fun provideSnapshotRepository(bearSnapshotDao: BearSnapshotDao): SnapshotRepository =
        SnapshotRepositoryImpl(bearSnapshotDao)

    @Provides
    @Singleton
    fun provideDetectTransitionsUseCase(): DetectTransitionsUseCase = DetectTransitionsUseCase()

    @Provides
    @Singleton
    fun provideBuildBearSnapshotUseCase(): BuildBearSnapshotUseCase = BuildBearSnapshotUseCase()

    @Provides
    @Singleton
    fun provideEvaluateSnapshotFreshnessUseCase(): EvaluateSnapshotFreshnessUseCase =
        EvaluateSnapshotFreshnessUseCase()

    // ── §4.5 Phase 4/6: 웹/LLM 수집 · 승인 흐름(v1.3부터 Claude+Gemini 이원화) ──────

    /** [LlmMarketDataSource]의 `geminiBaseUrl`/`geminiRateLimitMs`는 프로덕션 기본값(생성자 기본 인자)을 그대로 쓴다. */
    @Provides
    @Singleton
    fun provideLlmMarketDataSource(httpClient: OkHttpClient): LlmMarketDataSource =
        LlmMarketDataSource(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideSuggestionRepository(
        llmMarketDataSource: LlmMarketDataSource,
        apiConfigProvider: ApiConfigProvider
    ): SuggestionRepository = SuggestionRepositoryImpl(llmMarketDataSource, apiConfigProvider)

    @Provides
    @Singleton
    fun provideFetchSuggestionsUseCase(repository: SuggestionRepository): FetchSuggestionsUseCase =
        FetchSuggestionsUseCase(repository)

    @Provides
    @Singleton
    fun provideApplySuggestionUseCase(repository: BearSignalRepository): ApplySuggestionUseCase =
        ApplySuggestionUseCase(repository)

    // ── §4.7 Phase 7-2: 정적 참조 콘텐츠 동적 갱신(정세 업데이트) ──────────────

    @Provides
    @Singleton
    fun provideAiContextRepository(
        llmMarketDataSource: LlmMarketDataSource,
        apiConfigProvider: ApiConfigProvider,
        bearSignalAiContextDao: BearSignalAiContextDao
    ): AiContextRepository = AiContextRepositoryImpl(llmMarketDataSource, apiConfigProvider, bearSignalAiContextDao)

    @Provides
    @Singleton
    fun provideFetchAiContextUpdatesUseCase(repository: AiContextRepository): FetchAiContextUpdatesUseCase =
        FetchAiContextUpdatesUseCase(repository)

    @Provides
    @Singleton
    fun provideApproveAiContextClaimsUseCase(repository: AiContextRepository): ApproveAiContextClaimsUseCase =
        ApproveAiContextClaimsUseCase(repository)

    @Provides
    @Singleton
    fun provideGetApprovedAiContextUseCase(repository: AiContextRepository): GetApprovedAiContextUseCase =
        GetApprovedAiContextUseCase(repository)
}
