package dev.ishubhamsingh.shelfly.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.ishubhamsingh.shelfly.data.settings.DataStoreKeys
import dev.ishubhamsingh.shelfly.domain.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            defaultLeadTimeDays = prefs[DataStoreKeys.LEAD_TIME_DAYS]   ?: Settings().defaultLeadTimeDays,
            dynamicColor        = prefs[DataStoreKeys.DYNAMIC_COLOR]    ?: Settings().dynamicColor,
            quietHours          = prefs[DataStoreKeys.QUIET_HOURS]      ?: Settings().quietHours,
            notifShowCount      = prefs[DataStoreKeys.NOTIF_SHOW_COUNT] ?: Settings().notifShowCount,
            notifShowDays       = prefs[DataStoreKeys.NOTIF_SHOW_DAYS]  ?: Settings().notifShowDays,
        )
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DataStoreKeys.ONBOARDING_DONE] ?: false
    }

    suspend fun setLeadTimeDays(days: Int) {
        dataStore.edit { prefs -> prefs[DataStoreKeys.LEAD_TIME_DAYS] = days.coerceIn(1, 14) }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DataStoreKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setQuietHours(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DataStoreKeys.QUIET_HOURS] = enabled }
    }

    suspend fun setOnboardingDone() {
        dataStore.edit { prefs -> prefs[DataStoreKeys.ONBOARDING_DONE] = true }
    }

    suspend fun setNotifShowCount(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DataStoreKeys.NOTIF_SHOW_COUNT] = enabled }
    }

    suspend fun setNotifShowDays(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DataStoreKeys.NOTIF_SHOW_DAYS] = enabled }
    }
}
