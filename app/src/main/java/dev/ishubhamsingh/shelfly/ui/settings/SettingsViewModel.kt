package dev.ishubhamsingh.shelfly.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ishubhamsingh.shelfly.data.repo.SettingsRepository
import dev.ishubhamsingh.shelfly.domain.model.Settings
import dev.ishubhamsingh.shelfly.notifications.ExpiryWorker
import dev.ishubhamsingh.shelfly.notifications.ImmediateNotifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val notifier: ImmediateNotifier,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    private val _workerScheduled = MutableStateFlow<Boolean?>(null)
    val workerScheduled: StateFlow<Boolean?> = _workerScheduled.asStateFlow()

    init { checkWorkerStatus() }

    // ── Settings setters ───────────────────────────────────────────────────────

    fun setLeadTimeDays(days: Int) {
        viewModelScope.launch { settingsRepo.setLeadTimeDays(days) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setDynamicColor(enabled) }
    }

    fun setQuietHours(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setQuietHours(enabled) }
    }

    fun setNotifGrouped(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setNotifGrouped(enabled) }
    }

    // ── Notifications ──────────────────────────────────────────────────────────

    fun sendTestNotification() {
        notifier.sendTestNotification()
    }

    fun checkWorkerStatus() {
        viewModelScope.launch {
            val infos = WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkFlow(ExpiryWorker.WORK_NAME)
                .first()
            _workerScheduled.value = infos.any {
                it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
            }
        }
    }

    fun rescheduleWorker() {
        val request = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            ExpiryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
        checkWorkerStatus()
    }
}
