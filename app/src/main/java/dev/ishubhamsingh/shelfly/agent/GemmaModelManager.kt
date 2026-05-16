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
        get() = context.getExternalFilesDir("models")?.absolutePath + "/gemma.litertlm"

    companion object {
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_VERSION = "gemma4-e2b-it-litertlm-v1"

        // The complete model is ~2.6 GB. Any file smaller than 2.5 GB is a
        // partial or corrupt download — treat it as not-downloaded and delete it.
        private const val MIN_MODEL_SIZE_BYTES = 2_500_000_000L
    }

    init {
        refreshState()
        observeDownloadWorker()
    }

    fun refreshState() {
        scope.launch {
            val file = resolveModelFile()

            // A non-empty file that is smaller than the minimum expected size is a
            // partial or corrupt download. Delete it now so it can never be mistaken
            // for a valid model — loading it would crash the native LiteRT-LM layer.
            if (file != null && file.exists() && file.length() in 1 until MIN_MODEL_SIZE_BYTES) {
                file.delete()
                dataStore.edit { prefs -> prefs.remove(DataStoreKeys.MODEL_FILE_PATH) }
                _state.value = ModelState.NotDownloaded
                return@launch
            }

            _state.value = if (file != null && file.exists() && file.length() >= MIN_MODEL_SIZE_BYTES) {
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
        // If the stored path points to the old .task format, clear it and force re-download
        if (storedPath != null && !storedPath.endsWith(".litertlm")) {
            dataStore.edit { prefs ->
                prefs.remove(DataStoreKeys.MODEL_FILE_PATH)
                prefs.remove(DataStoreKeys.MODEL_VERSION)
            }
            return File(defaultModelPath)
        }
        return File(storedPath ?: defaultModelPath)
    }
}
