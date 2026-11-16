package dev.ishubhamsingh.shelfly.spike.eval

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.collect

/**
 * Gemini Nano via ML Kit's GenAI Prompt API (backed by the AICore system service).
 *
 * Why this backend exists: if Nano is available on a device, we get function-calling
 * quality with ZERO app-side model download — the 2.6 GB Gemma download is the single
 * biggest install-cost in v0.2. Nano is system-managed and shared across apps.
 *
 * Two constraints that shape the design:
 *  - Input is capped at ~4000 tokens. Production's system prompt (~700) plus user
 *    text fits comfortably, but that ceiling is why we do not simply paste the full
 *    tool catalogue plus few-shot examples here.
 *  - The model may need a one-time download even on supported hardware, hence the
 *    DOWNLOADABLE state and [ensureDownloaded].
 *
 * System instructions are prepended to the user turn rather than passed as a separate
 * system-instruction field, so this path stays comparable to the LiteRT one.
 */
class AiCoreBackend : EvalBackend {

    override val name: String = "AICore (Gemini Nano)"

    private val model: GenerativeModel by lazy { Generation.getClient() }

    /** Populated by [unavailableReason] so the UI can show the raw status. */
    var lastStatus: String = "unknown"
        private set

    override suspend fun unavailableReason(): String? = try {
        when (val status = model.checkStatus()) {
            FeatureStatus.AVAILABLE -> {
                lastStatus = "AVAILABLE"
                null
            }
            FeatureStatus.DOWNLOADABLE -> {
                lastStatus = "DOWNLOADABLE"
                "Gemini Nano is supported but not downloaded yet — tap \"Download Nano\" first."
            }
            FeatureStatus.DOWNLOADING -> {
                lastStatus = "DOWNLOADING"
                "Gemini Nano is still downloading — retry shortly."
            }
            else -> {
                lastStatus = "UNAVAILABLE ($status)"
                "Gemini Nano is not available on this device/build."
            }
        }
    } catch (e: Exception) {
        lastStatus = "ERROR: ${e.message}"
        "checkStatus() failed: ${e.message}"
    }

    /** Triggers the one-time Nano download. Emits progress strings for the UI. */
    suspend fun ensureDownloaded(onProgress: (String) -> Unit) {
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted   -> onProgress("Download started…")
                is DownloadStatus.DownloadProgress  -> onProgress("Downloading… ${status.totalBytesDownloaded} bytes")
                DownloadStatus.DownloadCompleted    -> onProgress("Download complete.")
                is DownloadStatus.DownloadFailed    -> onProgress("Download failed: ${status.e.message}")
            }
        }
    }

    override suspend fun generate(
        systemPrompt: String,
        userInput: String,
        images: List<android.graphics.Bitmap>,
    ): String {
        val prompt = "$systemPrompt\n\nUser request: $userInput"
        // beta4's Content holds a List<Part>, so multiple images ARE expressible here.
        // (beta2 had only (TextPart) and (ImagePart, TextPart) factories plus a singular
        // `image` field — that was a binding limitation, not a Nano limitation.)
        val content = content {
            images.forEach { image(it) }
            text(prompt)
        }
        val request = generateContentRequest(content) { temperature = 0.2f; topK = 16 }
        val response = model.generateContent(request)
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    override fun close() {
        // GenerativeModel is process-scoped and managed by ML Kit; nothing to release.
    }
}
