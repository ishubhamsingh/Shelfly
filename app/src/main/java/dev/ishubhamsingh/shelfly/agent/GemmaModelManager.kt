package dev.ishubhamsingh.shelfly.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ishubhamsingh.shelfly.data.settings.DataStoreKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val workManager: WorkManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    val defaultModelPath: String
        get() = context.getExternalFilesDir("models")?.absolutePath + "/gemma.task"

    companion object {
        const val MODEL_DOWNLOAD_URL =
            "https://github.com/ishubhamsingh/Shelfly/releases/download/v0.2-models/gemma3-1B-it-int4.task"
        const val MODEL_VERSION = "gemma3-1b-it-int4-v1"
    }

    init {
        refreshState()
        observeDownloadWorker()
    }

    fun refreshState() {
        scope.launch {
            val file = resolveModelFile()
            _state.value = if (file != null && file.exists() && file.length() > 0) {
                ModelState.Ready(file.absolutePath, file.length())
            } else {
                ModelState.NotDownloaded
            }
        }
    }

    fun startDownload(url: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[DataStoreKeys.MODEL_DOWNLOAD_URL] = url }
        }
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            ModelDownloadWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
        // Clean up partial file
        scope.launch {
            resolveModelFile()?.takeIf { it.exists() }?.delete()
            _state.value = ModelState.NotDownloaded
        }
    }

    fun delete() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
        scope.launch {
            resolveModelFile()?.delete()
            dataStore.edit { prefs ->
                prefs.remove(DataStoreKeys.MODEL_FILE_PATH)
                prefs.remove(DataStoreKeys.MODEL_VERSION)
            }
            _state.value = ModelState.NotDownloaded
        }
    }

    private fun observeDownloadWorker() {
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)
            .onEach { infos ->
                val info = infos.firstOrNull() ?: return@onEach
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress  = info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f)
                        val downloaded = info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                        val total      = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L)
                        _state.value = ModelState.Downloading(progress, downloaded, total)
                    }
                    WorkInfo.State.SUCCEEDED -> refreshState()
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        val msg = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                            ?: "Download failed"
                        _state.value = ModelState.Error(msg)
                    }
                    else -> Unit
                }
            }
            .launchIn(scope)
    }

    private suspend fun resolveModelFile(): File? {
        val storedPath = dataStore.data.first()[DataStoreKeys.MODEL_FILE_PATH]
        return File(storedPath ?: defaultModelPath)
    }
}
