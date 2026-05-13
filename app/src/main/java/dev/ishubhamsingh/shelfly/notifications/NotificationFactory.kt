package dev.ishubhamsingh.shelfly.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.ishubhamsingh.shelfly.MainActivity
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.domain.model.Item

const val CHANNEL_ID   = "expiry_alerts"
const val NOTIF_ID     = 1001

fun Context.ensureNotificationChannel() {
    val mgr = getSystemService(NotificationManager::class.java)
    if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
    val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notif_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = getString(R.string.notif_channel_desc)
    }
    mgr.createNotificationChannel(channel)
}

fun Context.buildExpiryNotification(items: List<Item>): android.app.Notification {
    val launchIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        this, 0, launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val title = if (items.size == 1)
        getString(R.string.notif_title_singular)
    else
        getString(R.string.notif_title_plural, items.size)

    val body = items.take(3).joinToString(", ") { it.name }

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
}
