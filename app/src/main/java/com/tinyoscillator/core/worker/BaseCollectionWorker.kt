package com.tinyoscillator.core.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tinyoscillator.core.database.dao.WorkerLogDao
import com.tinyoscillator.core.database.entity.WorkerLogEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

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

    /**
     * OneTime Expedited Worker의 Android <12 폴백용.
     * Periodic Worker의 doWork()에서는 호출하지 않는다.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        CollectionNotificationHelper.createChannel(applicationContext)
        val notification = CollectionNotificationHelper.buildProgressNotification(
            applicationContext, notificationTitle, notificationTitle, indeterminate = true
        )
        return ForegroundInfo(
            notificationId,
            notification.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    protected fun showInitialNotification(message: String) {
        CollectionNotificationHelper.createChannel(applicationContext)
        updateNotification(message, 0)
    }

    /**
     * 워커 실행 결과를 Room DB에 저장합니다.
     * 로그 저장 실패 시 워커 동작에 영향을 주지 않습니다.
     */
    protected suspend fun saveLog(
        workerLabel: String,
        status: String,
        message: String,
        errorDetail: String? = null
    ) {
        try {
            val dao = EntryPointAccessors.fromApplication(
                applicationContext, WorkerLogEntryPoint::class.java
            ).workerLogDao()
            dao.insertAndCleanup(
                WorkerLogEntity(
                    workerName = workerLabel,
                    status = status,
                    message = message,
                    errorDetail = errorDetail
                )
            )
        } catch (_: Exception) {
            // 로그 저장 실패 시 무시 — 워커 동작에 영향 없음
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerLogEntryPoint {
        fun workerLogDao(): WorkerLogDao
    }
}
