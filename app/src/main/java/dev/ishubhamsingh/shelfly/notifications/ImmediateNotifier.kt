package dev.ishubhamsingh.shelfly.notifications

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.ItemStatus
import dev.ishubhamsingh.shelfly.domain.model.Settings
import dev.ishubhamsingh.shelfly.domain.util.statusFor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImmediateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyIfWithinWindow(item: Item, settings: Settings) {
        val status = item.statusFor(settings.defaultLeadTimeDays)
        if (status != ItemStatus.EXPIRING_SOON && status != ItemStatus.EXPIRED) return

        context.ensureNotificationChannel()
        val notification = context.buildExpiryNotification(
            items      = listOf(item),
            showCount  = settings.notifShowCount,
            showDays   = settings.notifShowDays,
        )
        context.getSystemService(NotificationManager::class.java)
            .notify(item.id.hashCode(), notification)
    }

    fun sendTestNotification(showCount: Boolean = true, showDays: Boolean = true) {
        context.ensureNotificationChannel()
        val notification = context.buildTestNotification(showCount, showDays)
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_TEST_ID, notification)
    }

    companion object {
        private const val NOTIF_TEST_ID = 1002
    }
}
