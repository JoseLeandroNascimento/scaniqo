package com.joseleandro.scaniqo.data.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.joseleandro.scaniqo.R

/** A notificação só é apresentada quando há arquivos alterados para enviar. */
internal fun createBackupForegroundInfo(context: Context): ForegroundInfo {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                BACKUP_CHANNEL,
                context.getString(R.string.backup_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
    val notification = NotificationCompat.Builder(context, BACKUP_CHANNEL)
        .setSmallIcon(R.drawable.ic_backup_notification)
        .setContentTitle(context.getString(R.string.backup_notification_title))
        .setContentText(context.getString(R.string.backup_notification_message))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setProgress(0, 0, true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .apply {
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                setContentIntent(PendingIntent.getActivity(
                    context, BACKUP_NOTIFICATION_ID, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ))
            }
        }
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(BACKUP_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else ForegroundInfo(BACKUP_NOTIFICATION_ID, notification)
}

private const val BACKUP_CHANNEL = "document_backup"
private const val BACKUP_NOTIFICATION_ID = 2401
