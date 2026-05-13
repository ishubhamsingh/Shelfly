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
            defaultLeadTimeDays = prefs[DataStoreKeys.LEAD_TIME_DAYS] ?: Settings().defaultLeadTimeDays,
            dynamicColor        = prefs[DataStoreKeys.DYNAMIC_COLOR]  ?: Settings().dynamicColor,
            quietHours          = prefs[DataStoreKeys.QUIET_HOURS]    ?: Settings().quietHours,
            notifGrouped        = prefs[DataStoreKeys.NOTIF_GROUPED]  ?: Settings().notifGrouped,
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

    suspend fun setNotifGrouped(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[DataStoreKeys.NOTIF_GROUPED] = enabled }
    }
}
