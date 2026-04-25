package com.tinyoscillator.presentation.settings

import android.content.Context
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.domain.model.ThemeExchange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun saveApiSettings(
    context: Context,
    kiwoomAppKey: String, kiwoomSecretKey: String, kiwoomMode: InvestmentMode,
    kisAppKey: String, kisAppSecret: String, kisMode: InvestmentMode,
    krxId: String, krxPassword: String
): String {
    return try {
        withContext(Dispatchers.IO) {
            getEncryptedPrefs(context).edit()
                .putString(PrefsKeys.KIWOOM_APP_KEY, kiwoomAppKey)
                .putString(PrefsKeys.KIWOOM_SECRET_KEY, kiwoomSecretKey)
                .putString(PrefsKeys.KIWOOM_MODE, kiwoomMode.name)
                .putString(PrefsKeys.KIS_APP_KEY, kisAppKey)
                .putString(PrefsKeys.KIS_APP_SECRET, kisAppSecret)
                .putString(PrefsKeys.KIS_MODE, kisMode.name)
                .putString(PrefsKeys.KRX_ID, krxId)
                .putString(PrefsKeys.KRX_PASSWORD, krxPassword)
                .apply()
        }
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}

internal suspend fun saveEtfKeywordSettings(
    context: Context,
    includeKeywords: List<String>,
    excludeKeywords: List<String>
): String {
    return try {
        saveEtfKeywordFilter(context, EtfKeywordFilter(includeKeywords, excludeKeywords))
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}

internal suspend fun saveScheduleSettings(
    context: Context,
    etfScheduleEnabled: Boolean, scheduleHour: Int, scheduleMinute: Int,
    oscScheduleEnabled: Boolean, oscScheduleHour: Int, oscScheduleMinute: Int,
    depositScheduleEnabled: Boolean, depositScheduleHour: Int, depositScheduleMinute: Int,
    marketCloseRefreshEnabled: Boolean = false, marketCloseRefreshHour: Int = 19, marketCloseRefreshMinute: Int = 0,
    consensusScheduleEnabled: Boolean = false, consensusScheduleHour: Int = 3, consensusScheduleMinute: Int = 0,
    fgScheduleEnabled: Boolean = false, fgScheduleHour: Int = 4, fgScheduleMinute: Int = 0,
    themeScheduleEnabled: Boolean = false, themeScheduleHour: Int = 2, themeScheduleMinute: Int = 30,
    themeExchange: ThemeExchange = ThemeExchange.KRX
): String {
    return try {
        saveEtfScheduleTime(context, EtfScheduleTime(scheduleHour, scheduleMinute, etfScheduleEnabled))
        saveOscillatorScheduleTime(context, OscillatorScheduleTime(oscScheduleHour, oscScheduleMinute, oscScheduleEnabled))
        saveDepositScheduleTime(context, DepositScheduleTime(depositScheduleHour, depositScheduleMinute, depositScheduleEnabled))
        saveMarketCloseRefreshScheduleTime(context, MarketCloseRefreshScheduleTime(marketCloseRefreshHour, marketCloseRefreshMinute, marketCloseRefreshEnabled))
        saveConsensusScheduleTime(context, ConsensusScheduleTime(consensusScheduleHour, consensusScheduleMinute, consensusScheduleEnabled))
        saveFearGreedScheduleTime(context, FearGreedScheduleTime(fgScheduleHour, fgScheduleMinute, fgScheduleEnabled))
        saveThemeScheduleTime(context, ThemeScheduleTime(themeScheduleHour, themeScheduleMinute, themeScheduleEnabled))
        saveThemeExchangeFilter(context, themeExchange)
        if (etfScheduleEnabled) {
            WorkManagerHelper.scheduleEtfUpdate(context, scheduleHour, scheduleMinute, forceUpdate = true)
        } else {
            WorkManagerHelper.cancelEtfUpdate(context)
        }
        if (oscScheduleEnabled) {
            WorkManagerHelper.scheduleOscillatorUpdate(context, oscScheduleHour, oscScheduleMinute, forceUpdate = true)
        } else {
            WorkManagerHelper.cancelOscillatorUpdate(context)
        }
        if (depositScheduleEnabled) {
            WorkManagerHelper.scheduleDepositUpdate(context, depositScheduleHour, depositScheduleMinute, forceUpdate = true)
        } else {
            WorkManagerHelper.cancelDepositUpdate(context)
        }
        if (marketCloseRefreshEnabled) {
            WorkManagerHelper.scheduleMarketCloseRefresh(context, marketCloseRefreshHour, marketCloseRefreshMinute, forceUpdate = true)
        } else {
            WorkManagerHelper.cancelMarketCloseRefresh(context)
        }
        if (consensusScheduleEnabled) {
            WorkManagerHelper.scheduleConsensusUpdate(context, consensusScheduleHour, consensusScheduleMinute, forceUpdate = true)
        } else {
            WorkManagerHelper.cancelConsensusUpdate(context)
        }
        if (fgScheduleEnabled) {
            WorkManagerHelper.scheduleFearGreedUpdate(context, fgScheduleHour, fgScheduleMinute, forceUpdate = true)
        } else {
            WorkManagerHelper.cancelFearGreedUpdate(context)
        }
        if (themeScheduleEnabled) {
            WorkManagerHelper.scheduleThemeUpdate(context, themeScheduleHour, themeScheduleMinute, forceUpdate = true)
        } else {
            WorkManagerHelper.cancelThemeUpdate(context)
        }
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}

internal suspend fun saveCollectionSettings(
    context: Context,
    etfDays: Int,
    oscDays: Int,
    depositDays: Int,
    consensusDays: Int,
    fearGreedDays: Int = 365
): String {
    return try {
        saveEtfCollectionPeriod(context, EtfCollectionPeriod(etfDays))
        saveMarketOscillatorCollectionPeriod(context, MarketOscillatorCollectionPeriod(oscDays))
        saveMarketDepositCollectionPeriod(context, MarketDepositCollectionPeriod(depositDays))
        saveConsensusCollectionPeriod(context, ConsensusCollectionPeriod(consensusDays))
        saveFearGreedCollectionPeriod(context, FearGreedCollectionPeriod(fearGreedDays))
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}
