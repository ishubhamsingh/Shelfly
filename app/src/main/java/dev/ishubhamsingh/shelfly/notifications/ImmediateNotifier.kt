package dev.ishubhamsingh.shelfly.notifications

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.Settings
import dev.ishubhamsingh.shelfly.domain.util.statusFor
import dev.ishubhamsingh.shelfly.domain.model.ItemStatus
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
        val notification = context.buildExpiryNotification(listOf(item))
        context.getSystemService(NotificationManager::class.java)
            .notify(item.id.hashCode(), notification)
    }

    fun sendTestNotification() {
        context.ensureNotificationChannel()
        val notification = context.buildTestNotification()
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_TEST_ID, notification)
    }

    companion object {
        private const val NOTIF_TEST_ID = 1002
    }
}
