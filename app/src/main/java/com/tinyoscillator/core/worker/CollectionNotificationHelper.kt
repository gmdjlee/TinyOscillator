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
    const val MARKET_CLOSE_REFRESH_NOTIFICATION_ID = 1005
    const val CONSENSUS_NOTIFICATION_ID = 1006
    const val FEAR_GREED_NOTIFICATION_ID = 1007
    const val REGIME_NOTIFICATION_ID = 1008
    const val META_LEARNER_NOTIFICATION_ID = 1010
    const val INCREMENTAL_MODEL_NOTIFICATION_ID = 1011
    const val SIGNAL_OUTCOME_NOTIFICATION_ID = 1012
    const val THEME_NOTIFICATION_ID = 1013
    const val PROBABILITY_BATCH_NOTIFICATION_ID = 1014

    const val SIGNAL_ALERT_CHANNEL_ID = "signal_alerts"
    const val SIGNAL_ALERT_NOTIFICATION_ID = 1015

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

    /** 신호 알림 채널 — 데이터 수집 채널과 달리 소리/배지가 있는 DEFAULT 중요도 */
    fun createSignalAlertChannel(context: Context) {
        val channel = NotificationChannel(
            SIGNAL_ALERT_CHANNEL_ID,
            "매매 신호 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "확률분석 점수가 임계값을 돌파하면 알립니다"
            setShowBadge(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /** 신호 임계 돌파 알림 — 종목별 한 줄씩 InboxStyle로 표시 */
    fun showSignalAlert(context: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        createSignalAlertChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }

        val builder = NotificationCompat.Builder(context, SIGNAL_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("확률분석 신호 (${lines.size}건)")
            .setContentText(lines.first())
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        showNotification(context, SIGNAL_ALERT_NOTIFICATION_ID, builder)
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
