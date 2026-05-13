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
        )
    }

    suspend fun setLeadTimeDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[DataStoreKeys.LEAD_TIME_DAYS] = days.coerceIn(1, 14)
        }
    }
}
