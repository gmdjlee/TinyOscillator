package com.tinyoscillator

import android.app.Application
import androidx.work.Configuration
import com.tinyoscillator.core.worker.CollectionNotificationHelper
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.presentation.settings.loadDepositScheduleTime
import com.tinyoscillator.presentation.settings.loadEtfScheduleTime
import com.tinyoscillator.presentation.settings.loadOscillatorScheduleTime
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.runBlocking
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

        val etfSchedule = runBlocking { loadEtfScheduleTime(this@TinyOscillatorApp) }
        if (etfSchedule.enabled) {
            WorkManagerHelper.scheduleEtfUpdate(this, etfSchedule.hour, etfSchedule.minute)
        }

        val oscSchedule = runBlocking { loadOscillatorScheduleTime(this@TinyOscillatorApp) }
        if (oscSchedule.enabled) {
            WorkManagerHelper.scheduleOscillatorUpdate(this, oscSchedule.hour, oscSchedule.minute)
        }

        val depositSchedule = runBlocking { loadDepositScheduleTime(this@TinyOscillatorApp) }
        if (depositSchedule.enabled) {
            WorkManagerHelper.scheduleDepositUpdate(this, depositSchedule.hour, depositSchedule.minute)
        }
    }
}
