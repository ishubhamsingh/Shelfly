package dev.ishubhamsingh.shelfly.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.ishubhamsingh.shelfly.MainActivity
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.domain.model.Category
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

private fun Context.formatExpiryContent(items: List<Item>): Pair<String, String> {
    val title = if (items.size == 1)
        getString(R.string.notif_title_singular)
    else
        getString(R.string.notif_title_plural, items.size)

    val body = items.take(3).joinToString(", ") { item ->
        val days  = item.daysUntilExpiry
        val label = when {
            days <= 0L -> getString(R.string.notif_body_today)
            days == 1L -> getString(R.string.notif_body_tomorrow)
            else       -> getString(R.string.notif_body_in_days, days)
        }
        "${item.name} ($label)"
    }
    return title to body
}

private fun Context.formatSingleExpiryContent(item: Item): Pair<String, String> {
    val days  = item.daysUntilExpiry
    val label = when {
        days <= 0L -> getString(R.string.notif_body_today)
        days == 1L -> getString(R.string.notif_body_tomorrow)
        else       -> getString(R.string.notif_body_in_days, days)
    }
    return getString(R.string.notif_title_singular) to "${item.name} ($label)"
}

// ── Notification builders ──────────────────────────────────────────────────────

/** Single grouped notification for all expiring items. Deep-links to Detail for one item, Home for many. */
fun Context.buildExpiryNotification(items: List<Item>): android.app.Notification {
    val (title, body) = formatExpiryContent(items)
    val itemId = if (items.size == 1) items.first().id else null
    return buildNotification(title, body, items.take(3).map { it.name to it.category }, itemId)
}

/**
 * One notification per item for separate (ungrouped) mode.
 * Returns a list of (notificationId, notification) pairs.
 */
fun Context.buildSeparateNotifications(items: List<Item>): List<Pair<Int, android.app.Notification>> =
    items.mapIndexed { index, item ->
        val (title, body) = formatSingleExpiryContent(item)
        val notification  = buildNotification(title, body, listOf(item.name to item.category))
        (NOTIF_ID + 1 + index) to notification
    }

fun Context.buildTestNotification(): android.app.Notification {
    val item1 = getString(R.string.notif_preview_item1)
    val item2 = getString(R.string.notif_preview_item2)
    val title = getString(R.string.notif_title_plural, 2)
    val body  = "$item1 (${getString(R.string.notif_body_tomorrow)}), " +
                "$item2 (${getString(R.string.notif_body_in_days, 5L)})"
    val sampleChips = listOf(item1 to Category.FOOD, item2 to Category.FOOD)
    return buildNotification(title, body, sampleChips)
}

// ── Internal builder ──────────────────────────────────────────────────────────

/** Core builder — takes pre-resolved (name, category) pairs for chip rendering. */
private fun Context.buildNotification(
    title: String,
    body: String,
    chips: List<Pair<String, Category>>,
    itemId: String? = null,
): android.app.Notification {
    val pendingIntent = launchPendingIntent(itemId)

    val bigView = buildBigContentView(title, body, chips)

    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomBigContentView(bigView)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
}

private fun Context.launchPendingIntent(itemId: String? = null): PendingIntent {
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (itemId != null) putExtra("ITEM_ID", itemId)
    }
    return PendingIntent.getActivity(
        this, itemId?.hashCode() ?: 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun Context.buildBigContentView(
    title: String,
    body: String,
    chips: List<Pair<String, Category>>,
): RemoteViews {
    val view = RemoteViews(packageName, R.layout.notif_expanded)
    view.setTextViewText(R.id.notif_title, title)
    view.setTextViewText(R.id.notif_body, body)

    chips.forEach { (name, category) ->
        val chip = RemoteViews(packageName, R.layout.notif_chip_item)
        chip.setTextViewText(R.id.chip_label, name)
        chip.setImageViewResource(R.id.chip_icon, category.chipIconRes())

        val bgColor = ContextCompat.getColor(this, category.chipBgColorRes())
        val fgColor = ContextCompat.getColor(this, category.chipFgColorRes())

        // API 31+ (minSdk 36): tint the rounded-rect background and icon/text
        chip.setColorStateList(
            R.id.chip_root,
            "setBackgroundTintList",
            ColorStateList.valueOf(bgColor),
        )
        chip.setTextColor(R.id.chip_label, fgColor)
        chip.setColorStateList(
            R.id.chip_icon,
            "setImageTintList",
            ColorStateList.valueOf(fgColor),
        )

        view.addView(R.id.notif_chips_row, chip)
    }

    return view
}

// ── Category → resource mappings ──────────────────────────────────────────────

private fun Category.chipIconRes(): Int = when (this) {
    Category.FOOD     -> R.drawable.ic_cat_food
    Category.MEDICINE -> R.drawable.ic_cat_medicine
    Category.COSMETIC -> R.drawable.ic_cat_cosmetic
    Category.OTHER    -> R.drawable.ic_cat_other
}

private fun Category.chipBgColorRes(): Int = when (this) {
    Category.FOOD     -> R.color.chip_food_bg
    Category.MEDICINE -> R.color.chip_medicine_bg
    Category.COSMETIC -> R.color.chip_cosmetic_bg
    Category.OTHER    -> R.color.chip_other_bg
}

private fun Category.chipFgColorRes(): Int = when (this) {
    Category.FOOD     -> R.color.chip_food_fg
    Category.MEDICINE -> R.color.chip_medicine_fg
    Category.COSMETIC -> R.color.chip_cosmetic_fg
    Category.OTHER    -> R.color.chip_other_fg
}
