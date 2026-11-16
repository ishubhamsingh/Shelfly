package dev.ishubhamsingh.shelfly.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.data.settings.DataStoreKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val dataStore: DataStore<Preferences>,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    // No read/write timeout — downloading a 2.6 GB file on a slow connection must not time out mid-stream.
    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(0f, 0L, 0L, -1L)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        setForeground(buildForegroundInfo(0f, 0L, 0L, -1L))

        val prefs = dataStore.data.first()
        val url = prefs[DataStoreKeys.MODEL_DOWNLOAD_URL]
            ?: return@withContext Result.failure(errorData("No download URL configured"))

        val destFile = File(
            appContext.getExternalFilesDir("models"),
            "gemma.litertlm"
        )
        destFile.parentFile?.mkdirs()

        return@withContext try {
            downloadWithResume(url, destFile)
            dataStore.edit { it[DataStoreKeys.MODEL_FILE_PATH] = destFile.absolutePath }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Worker was cancelled by pause or cancel action. Keep the partial file on
            // disk so a subsequent resume can continue via HTTP Range. Rethrow so
            // WorkManager marks the work as CANCELLED rather than FAILED.
            throw e
        } catch (e: Exception) {
            // Genuine download error — partial file may be corrupt, remove it.
            destFile.delete()
            Result.failure(errorData(e.message ?: "Unknown error"))
        }
    }

    private suspend fun downloadWithResume(url: String, dest: File) {
        val existingBytes = if (dest.exists()) dest.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) requestBuilder.addHeader("Range", "bytes=$existingBytes-")

        val response = downloadClient.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful && response.code != 206) {
            throw Exception("HTTP ${response.code}")
        }

        val totalBytes = response.header("Content-Range")
            ?.substringAfterLast('/')?.toLongOrNull()
            ?: (existingBytes + (response.body?.contentLength() ?: 0L))

        val body = response.body ?: throw Exception("Empty response body")
        val append = response.code == 206

        // Fix: open the correct stream BEFORE touching the file — outputStream() truncates immediately.
        body.byteStream().use { input ->
            (if (append) dest.appendingOutputStream() else dest.outputStream()).use { out ->
                val buffer = ByteArray(512 * 1024) // 512 KB — much faster for large files
                var downloaded    = existingBytes
                var read          : Int
                var lastNotifMs   = 0L   // throttle setForeground() — it's a heavy IPC call
                var lastSpeedBytes = existingBytes
                var lastSpeedMs    = 0L
                var remainingSecs  = -1L

                while (input.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    downloaded += read

                    if (totalBytes > 0) {
                        val progress = downloaded.toFloat() / totalBytes
                        // Always update WorkManager state (cheap, in-process)
                        setProgress(workDataOf(
                            KEY_PROGRESS        to progress,
                            KEY_DOWNLOADED_BYTES to downloaded,
                            KEY_TOTAL_BYTES      to totalBytes,
                        ))
                        // Throttle notification IPC to once every 2 s — avoids flooding
                        // ActivityManager/NotificationManager and triggering watchdog reboots.
                        val now = System.currentTimeMillis()
                        if (now - lastNotifMs >= 2_000L) {
                            // Estimate time remaining from bytes-per-ms over the last interval
                            val elapsedMs = now - lastSpeedMs
                            if (lastSpeedMs > 0 && elapsedMs > 0) {
                                val bytesPerMs = (downloaded - lastSpeedBytes).toFloat() / elapsedMs
                                if (bytesPerMs > 0) {
                                    remainingSecs = ((totalBytes - downloaded) / bytesPerMs / 1000).toLong()
                                }
                            }
                            lastSpeedBytes = downloaded
                            lastSpeedMs    = now

                            setForeground(buildForegroundInfo(progress, downloaded, totalBytes, remainingSecs))
                            lastNotifMs = now
                        }
                    }
                }
            }
        }

        // Verify the file is complete — server may close connection early without an error.
        if (totalBytes > 0 && dest.length() < totalBytes) {
            throw Exception("Incomplete download: got ${dest.length()} of $totalBytes bytes")
        }
    }

    private fun buildForegroundInfo(
        progress       : Float,
        downloadedBytes: Long,
        totalBytes     : Long,
        remainingSecs  : Long = -1L,
    ): ForegroundInfo {
        ensureDownloadChannel()

        fun fmtGb(bytes: Long) = "%.2f GB".format(bytes / 1_000_000_000.0)

        val contentText = when {
            progress <= 0f  -> appContext.getString(R.string.model_download_notif_starting)
            totalBytes > 0  -> {
                val dlGb  = fmtGb(downloadedBytes)
                val totGb = fmtGb(totalBytes)
                if (remainingSecs > 0) {
                    val timeStr = if (remainingSecs < 60)
                        appContext.getString(R.string.model_download_notif_sec_remaining, remainingSecs.toInt())
                    else
                        appContext.getString(R.string.model_download_notif_min_remaining, (remainingSecs / 60).toInt())
                    appContext.getString(R.string.model_download_notif_gb_remaining, dlGb, totGb, timeStr)
                } else {
                    appContext.getString(
                        R.string.model_download_notif_gb_progress,
                        dlGb, totGb, (progress * 100).toInt(),
                    )
                }
            }
            else -> appContext.getString(R.string.model_download_notif_progress_pct, (progress * 100).toInt())
        }

        val notification = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(appContext.getString(R.string.model_download_notif_title))
            .setContentText(contentText)
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(DOWNLOAD_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(DOWNLOAD_NOTIF_ID, notification)
        }
    }

    private fun ensureDownloadChannel() {
        val mgr = appContext.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(DOWNLOAD_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            appContext.getString(R.string.model_download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        mgr.createNotificationChannel(channel)
    }

    private fun errorData(message: String): Data = workDataOf(KEY_ERROR to message)

    companion object {
        const val WORK_NAME             = "gemma_model_download"
        const val KEY_PROGRESS          = "progress"
        const val KEY_DOWNLOADED_BYTES  = "downloaded_bytes"
        const val KEY_TOTAL_BYTES       = "total_bytes"
        const val KEY_ERROR             = "error"
        const val DOWNLOAD_CHANNEL_ID   = "model_download"
        const val DOWNLOAD_NOTIF_ID     = 2001
    }
}

private fun File.appendingOutputStream() = java.io.FileOutputStream(this, true)
