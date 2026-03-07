package com.tinyoscillator

import android.app.Application
import androidx.work.Configuration
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.presentation.settings.loadEtfScheduleTime
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
        // No tree planted in release = zero logging output

        // Schedule daily ETF update at user-configured time (default 00:30)
        val schedule = runBlocking { loadEtfScheduleTime(this@TinyOscillatorApp) }
        if (schedule.enabled) {
            WorkManagerHelper.scheduleEtfUpdate(this, schedule.hour, schedule.minute)
        }
    }
}
