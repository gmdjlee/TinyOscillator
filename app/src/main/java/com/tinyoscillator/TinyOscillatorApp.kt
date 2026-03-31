package com.tinyoscillator

import android.app.Application
import androidx.work.Configuration
import com.tinyoscillator.core.worker.CollectionNotificationHelper
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.presentation.settings.loadConsensusScheduleTime
import com.tinyoscillator.presentation.settings.loadDepositScheduleTime
import com.tinyoscillator.presentation.settings.loadEtfScheduleTime
import com.tinyoscillator.presentation.settings.loadFearGreedScheduleTime
import com.tinyoscillator.presentation.settings.loadMarketCloseRefreshScheduleTime
import com.tinyoscillator.presentation.settings.loadOscillatorScheduleTime
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class TinyOscillatorApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerConfiguration: Configuration

    override val workManagerConfiguration: Configuration
        get() = workerConfiguration

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        CollectionNotificationHelper.createChannel(this)

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val etfSchedule = loadEtfScheduleTime(this@TinyOscillatorApp)
            if (etfSchedule.enabled) {
                WorkManagerHelper.scheduleEtfUpdate(this@TinyOscillatorApp, etfSchedule.hour, etfSchedule.minute)
            }

            val oscSchedule = loadOscillatorScheduleTime(this@TinyOscillatorApp)
            if (oscSchedule.enabled) {
                WorkManagerHelper.scheduleOscillatorUpdate(this@TinyOscillatorApp, oscSchedule.hour, oscSchedule.minute)
            }

            val depositSchedule = loadDepositScheduleTime(this@TinyOscillatorApp)
            if (depositSchedule.enabled) {
                WorkManagerHelper.scheduleDepositUpdate(this@TinyOscillatorApp, depositSchedule.hour, depositSchedule.minute)
            }

            val mcRefreshSchedule = loadMarketCloseRefreshScheduleTime(this@TinyOscillatorApp)
            if (mcRefreshSchedule.enabled) {
                WorkManagerHelper.scheduleMarketCloseRefresh(this@TinyOscillatorApp, mcRefreshSchedule.hour, mcRefreshSchedule.minute)
            }

            val consensusSchedule = loadConsensusScheduleTime(this@TinyOscillatorApp)
            if (consensusSchedule.enabled) {
                WorkManagerHelper.scheduleConsensusUpdate(this@TinyOscillatorApp, consensusSchedule.hour, consensusSchedule.minute)
            }

            val fgSchedule = loadFearGreedScheduleTime(this@TinyOscillatorApp)
            if (fgSchedule.enabled) {
                WorkManagerHelper.scheduleFearGreedUpdate(this@TinyOscillatorApp, fgSchedule.hour, fgSchedule.minute)
            }
        }
    }
}
