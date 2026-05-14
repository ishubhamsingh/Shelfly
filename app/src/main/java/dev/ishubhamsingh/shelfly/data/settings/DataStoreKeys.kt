package dev.ishubhamsingh.shelfly.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object DataStoreKeys {
    val LEAD_TIME_DAYS    = intPreferencesKey("lead_time_days")
    val DYNAMIC_COLOR     = booleanPreferencesKey("dynamic_color")
    val QUIET_HOURS       = booleanPreferencesKey("quiet_hours")
    val ONBOARDING_DONE   = booleanPreferencesKey("onboarding_done")
    val NOTIF_GROUPED     = booleanPreferencesKey("notif_grouped")

    // Gemma model lifecycle
    val MODEL_FILE_PATH   = stringPreferencesKey("gemma_model_path")
    val MODEL_VERSION     = stringPreferencesKey("gemma_model_version")
    val MODEL_DOWNLOAD_URL= stringPreferencesKey("gemma_download_url")
}
