package dev.ishubhamsingh.shelfly.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.ishubhamsingh.shelfly.data.repo.ItemRepository
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar

@HiltWorker
class ExpiryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val itemRepo: ItemRepository,
    private val settingsRepo: SettingsRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepo.settings.first()
        if (settings.quietHours) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 22 || hour < 8) return Result.success()
        }
        val expiring = itemRepo.getExpiringSoon(settings.defaultLeadTimeDays)
        if (expiring.isEmpty()) return Result.success()

        applicationContext.ensureNotificationChannel()
        val notification = applicationContext.buildExpiryNotification(
            items     = expiring,
            showCount = settings.notifShowCount,
            showDays  = settings.notifShowDays,
        )
        applicationContext
            .getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, notification)

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "shelfly_expiry_check"
    }
}
