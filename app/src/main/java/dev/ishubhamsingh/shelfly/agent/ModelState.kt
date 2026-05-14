package dev.ishubhamsingh.shelfly.agent

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : ModelState
    data class Ready(val modelPath: String, val sizeBytes: Long) : ModelState
    data class Error(val message: String) : ModelState
}
