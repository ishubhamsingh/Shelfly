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
import dev.ishubhamsingh.shelfly.domain.util.daysUntilExpiry

const val CHANNEL_ID = "expiry_alerts"
const val NOTIF_ID   = 1001

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

// ── Shared formatting ──────────────────────────────────────────────────────────

/**
 * Builds the (title, body) strings for an expiry notification.
 * Reused by the real worker, the immediate notifier, and the Settings preview card.
 */
fun Context.formatExpiryContent(
    items: List<Item>,
    showCount: Boolean,
    showDays: Boolean,
): Pair<String, String> {
    val title = when {
        showCount && items.size == 1 -> getString(R.string.notif_title_singular)
        showCount                    -> getString(R.string.notif_title_plural, items.size)
        else                         -> getString(R.string.notif_title_no_count)
    }
    val body = items.take(3).joinToString(", ") { item ->
        if (showDays) {
            val days = item.daysUntilExpiry
            val label = when {
                days <= 0L -> getString(R.string.notif_body_today)
                days == 1L -> getString(R.string.notif_body_tomorrow)
                else       -> getString(R.string.notif_body_in_days, days)
            }
            "${item.name} ($label)"
        } else {
            item.name
        }
    }
    return title to body
}

// ── Notification builders ──────────────────────────────────────────────────────

fun Context.buildExpiryNotification(
    items: List<Item>,
    showCount: Boolean = true,
    showDays: Boolean  = true,
): android.app.Notification {
    val (title, body) = formatExpiryContent(items, showCount, showDays)
    return buildNotification(title, body)
}

fun Context.buildTestNotification(
    showCount: Boolean = true,
    showDays: Boolean  = true,
): android.app.Notification {
    // Two hardcoded sample items that mirror the live preview card
    val title: String
    val body: String
    if (showCount) {
        title = getString(R.string.notif_title_plural, 2)
    } else {
        title = getString(R.string.notif_title_no_count)
    }
    body = if (showDays) {
        "${getString(R.string.notif_preview_item1)} (${getString(R.string.notif_body_tomorrow)}), " +
        "${getString(R.string.notif_preview_item2)} (${getString(R.string.notif_body_in_days, 5L)})"
    } else {
        "${getString(R.string.notif_preview_item1)}, ${getString(R.string.notif_preview_item2)}"
    }
    return buildNotification(title, body)
}

private fun Context.buildNotification(title: String, body: String): android.app.Notification {
    val launchIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        this, 0, launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
}
