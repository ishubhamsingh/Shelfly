package dev.ishubhamsingh.shelfly.agent

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
    ) : ModelState
    /** Download intentionally stopped; partial file is kept on disk. Resume is possible. */
    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : ModelState
    data class Ready(val modelPath: String, val sizeBytes: Long) : ModelState
    data class Error(val message: String) : ModelState
}
