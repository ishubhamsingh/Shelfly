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
import kotlinx.coroutines.flow.update
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
            val file  = resolveModelFile()
            val prefs = dataStore.data.first()

            when {
                // Complete model present
                file != null && file.exists() && file.length() >= MIN_MODEL_SIZE_BYTES -> {
                    // Clear any stale paused-download keys
                    dataStore.edit { p ->
                        p.remove(DataStoreKeys.PAUSED_DOWNLOADED_BYTES)
                        p.remove(DataStoreKeys.PAUSED_TOTAL_BYTES)
                    }
                    _state.value = ModelState.Ready(file.absolutePath, file.length())
                }

                // Partial file on disk — check whether it was an intentional pause
                file != null && file.exists() && file.length() > 0 -> {
                    val pausedDownloaded = prefs[DataStoreKeys.PAUSED_DOWNLOADED_BYTES]
                    val pausedTotal      = prefs[DataStoreKeys.PAUSED_TOTAL_BYTES]
                    if (pausedDownloaded != null && pausedDownloaded > 0L) {
                        _state.value = ModelState.Paused(
                            downloadedBytes = pausedDownloaded,
                            totalBytes      = pausedTotal ?: 0L,
                        )
                    } else {
                        // Orphaned partial file (crash / corrupt) — delete it
                        file.delete()
                        dataStore.edit { p -> p.remove(DataStoreKeys.MODEL_FILE_PATH) }
                        _state.value = ModelState.NotDownloaded
                    }
                }

                else -> _state.value = ModelState.NotDownloaded
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
        // Delete partial file and clear all download / paused state
        scope.launch {
            resolveModelFile()?.takeIf { it.exists() }?.delete()
            dataStore.edit { prefs ->
                prefs.remove(DataStoreKeys.PAUSED_DOWNLOADED_BYTES)
                prefs.remove(DataStoreKeys.PAUSED_TOTAL_BYTES)
            }
            _state.value = ModelState.NotDownloaded
        }
    }

    /**
     * Stop the active download but keep the partial file on disk so that
     * [resumeDownload] can continue from where it left off via HTTP Range.
     */
    fun pauseDownload() {
        val current = _state.value
        val downloaded = (current as? ModelState.Downloading)?.downloadedBytes ?: 0L
        val total      = (current as? ModelState.Downloading)?.totalBytes      ?: 0L

        // Set Paused BEFORE cancelling so the WorkManager CANCELLED event doesn't overwrite it
        _state.value = ModelState.Paused(downloadedBytes = downloaded, totalBytes = total)

        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)

        // Persist paused position — survives process death
        scope.launch {
            dataStore.edit { prefs ->
                prefs[DataStoreKeys.PAUSED_DOWNLOADED_BYTES] = downloaded
                prefs[DataStoreKeys.PAUSED_TOTAL_BYTES]      = total
            }
        }
    }

    /**
     * Re-enqueue the download worker. Because the partial file is still on disk
     * the worker will send a Range header and continue from [ModelState.Paused.downloadedBytes].
     *
     * IMPORTANT: we must transition out of [ModelState.Paused] BEFORE the worker starts
     * emitting RUNNING events, otherwise the stale-RUNNING guard in [observeDownloadWorker]
     * will drop every progress update and the UI stays frozen on the Paused screen.
     */
    fun resumeDownload() {
        // Immediately move to Downloading so the RUNNING guard lets new events through
        val paused = _state.value as? ModelState.Paused
        _state.value = ModelState.Downloading(
            progress       = if (paused != null && paused.totalBytes > 0)
                                 paused.downloadedBytes.toFloat() / paused.totalBytes else 0f,
            downloadedBytes = paused?.downloadedBytes ?: 0L,
            totalBytes      = paused?.totalBytes      ?: 0L,
        )

        // Clear paused keys and re-enqueue with REPLACE so any lingering cancelled
        // work record doesn't block the new request via KEEP policy
        scope.launch {
            dataStore.edit { prefs ->
                prefs.remove(DataStoreKeys.PAUSED_DOWNLOADED_BYTES)
                prefs.remove(DataStoreKeys.PAUSED_TOTAL_BYTES)
            }
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
            ExistingWorkPolicy.REPLACE,
            request,
        )
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
                        val progress   = info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f)
                        val downloaded = info.progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED_BYTES, 0L)
                        val total      = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L)
                        // Use atomic update (CAS loop) so pauseDownload() can never be
                        // overwritten by a stale RUNNING event even if they race on
                        // different threads. Only advance to Downloading if we're currently
                        // in a state that expects progress — Paused/Ready/Error are left alone.
                        _state.update { current ->
                            if (current is ModelState.Downloading || current is ModelState.NotDownloaded)
                                ModelState.Downloading(progress, downloaded, total)
                            else
                                current
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> refreshState()
                    WorkInfo.State.FAILED -> {
                        val msg = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                            ?: "Download failed"
                        _state.value = ModelState.Error(msg)
                    }
                    WorkInfo.State.CANCELLED -> {
                        // Atomic update: only reset to NotDownloaded if the worker was
                        // system-killed while we were Downloading. Preserves Paused (set
                        // by pauseDownload()) and NotDownloaded (set by cancelDownload())
                        // even when CANCELLED races with either of those writes.
                        _state.update { current ->
                            if (current is ModelState.Downloading) ModelState.NotDownloaded
                            else current
                        }
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
