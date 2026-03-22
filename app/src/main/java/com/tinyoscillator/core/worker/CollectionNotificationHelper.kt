package com.tinyoscillator.core.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tinyoscillator.MainActivity
import com.tinyoscillator.R

object CollectionNotificationHelper {

    const val CHANNEL_ID = "data_collection"
    const val ETF_NOTIFICATION_ID = 1001
    const val OSCILLATOR_NOTIFICATION_ID = 1002
    const val DEPOSIT_NOTIFICATION_ID = 1003
    const val INTEGRITY_CHECK_NOTIFICATION_ID = 1004

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "데이터 수집",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "데이터 수집 진행 상황을 표시합니다"
            setShowBadge(false)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildProgressNotification(
        context: Context,
        title: String,
        message: String,
        progress: Int = 0,
        maxProgress: Int = 100,
        indeterminate: Boolean = false
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setProgress(maxProgress, progress, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    fun buildCompletionNotification(
        context: Context,
        title: String,
        message: String,
        isError: Boolean = false
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(false)
            .setContentIntent(pendingIntent)
    }

    fun showNotification(context: Context, notificationId: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
