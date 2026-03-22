package com.tinyoscillator

import android.app.Application
import androidx.work.Configuration
import com.tinyoscillator.core.worker.CollectionNotificationHelper
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.presentation.settings.loadDepositScheduleTime
import com.tinyoscillator.presentation.settings.loadEtfScheduleTime
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
        }
    }
}
