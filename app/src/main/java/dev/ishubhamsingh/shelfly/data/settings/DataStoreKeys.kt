package dev.ishubhamsingh.shelfly.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

object DataStoreKeys {
    val LEAD_TIME_DAYS  = intPreferencesKey("lead_time_days")
    val DYNAMIC_COLOR   = booleanPreferencesKey("dynamic_color")
    val QUIET_HOURS     = booleanPreferencesKey("quiet_hours")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
}
