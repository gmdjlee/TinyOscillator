package com.tinyoscillator.core.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Base class for data collection workers that share notification and progress reporting logic.
 */
abstract class BaseCollectionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /** Display title for notifications (e.g. "ETF 데이터 수집") */
    abstract val notificationTitle: String

    /** Notification ID from CollectionNotificationHelper */
    abstract val notificationId: Int

    protected suspend fun updateProgress(message: String, status: String, progress: Float = 0f) {
        setProgress(workDataOf(
            KEY_PROGRESS to progress,
            KEY_MESSAGE to message,
            KEY_STATUS to status
        ))
    }

    protected fun updateNotification(message: String, progress: Int) {
        val notification = CollectionNotificationHelper.buildProgressNotification(
            applicationContext, notificationTitle, message, progress
        )
        CollectionNotificationHelper.showNotification(
            applicationContext, notificationId, notification
        )
    }

    protected fun showCompletion(message: String, isError: Boolean = false) {
        val notification = CollectionNotificationHelper.buildCompletionNotification(
            applicationContext, notificationTitle, message, isError
        )
        CollectionNotificationHelper.showNotification(
            applicationContext, notificationId, notification
        )
    }

    protected fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = CollectionNotificationHelper.buildProgressNotification(
            applicationContext, notificationTitle, message, indeterminate = true
        )
        return ForegroundInfo(
            notificationId,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
