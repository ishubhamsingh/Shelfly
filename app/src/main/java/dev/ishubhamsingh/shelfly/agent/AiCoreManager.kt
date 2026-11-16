package dev.ishubhamsingh.shelfly.agent

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Readiness of Gemini Nano, served by the AICore system service via ML Kit's Prompt API.
 *
 * Deliberately NOT a boolean like [dev.ishubhamsingh.shelfly.ui.components.isAppFunctionsCapable]:
 * AICore has a genuine intermediate state where the device supports Nano but the model
 * has not been downloaded by the system yet, and the check is asynchronous.
 */
sealed interface AiCoreState {
    /** Not yet queried. Callers should treat this as "don't know", not "no". */
    data object Unknown : AiCoreState

    /** Device or build cannot run Nano — fall back to the downloaded Gemma model. */
    data object Unsupported : AiCoreState

    /** Supported, but the system needs a one-time model download first. */
    data object Downloadable : AiCoreState

    /** System download in progress. */
    data object Downloading : AiCoreState

    /** Ready to serve inference. */
    data object Ready : AiCoreState

    data class Error(val reason: String) : AiCoreState
}

/**
 * Owns the AICore availability check.
 *
 * Nano costs the app ZERO bytes — the model is system-managed and shared across apps,
 * unlike the ~2.6 GB [GemmaModelManager] download. Google also updates it independently
 * of app releases, which is the main reason to prefer it where present.
 *
 * MULTI-IMAGE: supported from genai-prompt 1.0.0-beta4 onward via `Content`'s part list.
 * (beta2 had a singular `image` field and could only take one — that was a binding
 * limitation, never a Nano limitation.) Do NOT downgrade below beta4: Shelfly's label
 * scan passes two images (front + back) and would silently lose one.
 *
 * READINESS IS NOT A LATCH: the system can evict Nano between sessions — measured
 * AVAILABLE and then DOWNLOADABLE again on the same device hours later. Always gate a
 * turn on [isReadyNow] rather than a cached state, and keep a fallback path.
 */
@Singleton
class AiCoreManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** See the override in [isReadyNow]. Diagnostic only. */
    private val forceLiteRtMarker: java.io.File? =
        context.getExternalFilesDir(null)?.let { java.io.File(it, "force_litert") }

    private val _state = MutableStateFlow<AiCoreState>(AiCoreState.Unknown)
    val state: StateFlow<AiCoreState> = _state.asStateFlow()

    /**
     * Created lazily and defensively: on devices without AICore this can throw rather
     * than returning an UNAVAILABLE status, and a crash here would take down any screen
     * that merely asks whether Nano exists.
     */
    private val model: GenerativeModel? by lazy {
        runCatching { Generation.getClient() }.getOrNull()
    }

    init {
        refresh()
    }

    fun refresh() {
        scope.launch { _state.value = queryStatus() }
    }

    /**
     * Authoritative, per-turn readiness check.
     *
     * Deliberately re-queries instead of reading [state]: Nano can be evicted by the
     * system at any time, so a cached `Ready` would route a turn to a backend that has
     * silently gone away. Also refreshes [state] so the UI follows along.
     */
    suspend fun isReadyNow(): Boolean {
        val t0 = System.currentTimeMillis()
        val status = queryStatus()
        android.util.Log.i("AiCoreManager", "checkStatus took ${System.currentTimeMillis() - t0}ms -> $status")
        _state.value = status
        return status is AiCoreState.Ready
    }

    /** Pre-loads Nano so the first real turn has no cold-start cost. Best-effort. */
    suspend fun warmUp() {
        runCatching { model?.warmup() }
    }

    /**
     * Runs one turn on Nano and returns the raw model text.
     *
     * @param imageBytes PNG-encoded images, in the order the model should see them
     *   (front label first). Passed as [ImagePart] byte arrays rather than Bitmaps so
     *   callers can hand over file bytes without a decode round-trip.
     *
     * Throws on any inference failure so the caller can fall back to LiteRT.
     */
    suspend fun generate(
        systemPrompt: String,
        userText: String,
        imageBytes: List<ByteArray>,
    ): String {
        val client = model ?: error("AICore client unavailable")
        val body = content {
            imageBytes.forEach { addPart(ImagePart(it)) }
            text(userText)
        }
        // SystemInstruction is a real part in beta4 — previously the system prompt had
        // to be prepended to the user turn, which muddled the two roles.
        val request = generateContentRequest(SystemInstruction(systemPrompt), body) {
            // Structured extraction, not creative text. Measured: 0.2/16 scored as well
            // as production's 1.0/64 on text and better on arithmetic.
            temperature = 0.2f
            topK = 16
        }
        // Nano's input cap is finite (~4000 tokens) and images are expensive, so a
        // multi-photo turn can silently overflow and come back truncated or empty.
        // beta4 exposes both numbers — log them so overflow is visible rather than
        // showing up as an unexplained parse failure.
        runCatching {
            val used = client.countTokens(request).totalTokens
            val limit = client.getTokenLimit()
            android.util.Log.i(
                "AiCoreManager",
                "input tokens=$used limit=$limit images=${imageBytes.size}" +
                    if (used > limit) "  ** OVER LIMIT **" else "",
            )
        }
        return client.generateContent(request).candidates.firstOrNull()?.text.orEmpty()
    }

    private suspend fun queryStatus(): AiCoreState {
        // DIAGNOSTIC OVERRIDE — remove once backend selection is settled.
        // Lets us A/B Nano against the downloaded model on the SAME photos through the
        // SAME prompt and pipeline, with the backend as the only variable:
        //   adb shell touch /sdcard/Android/data/<pkg>/files/force_litert
        //
        // Checked HERE rather than in isReadyNow() so the observable state and the
        // routing decision cannot disagree — putting it in isReadyNow() alone left
        // Settings advertising "using Gemini Nano" while turns actually ran on LiteRT.
        if (forceLiteRtMarker?.exists() == true) {
            android.util.Log.i("AiCoreManager", "force_litert marker present — skipping Nano")
            return AiCoreState.Unsupported
        }
        val client = model ?: return AiCoreState.Unsupported
        return try {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> AiCoreState.Ready
                FeatureStatus.DOWNLOADABLE -> AiCoreState.Downloadable
                FeatureStatus.DOWNLOADING -> AiCoreState.Downloading
                else -> AiCoreState.Unsupported
            }
        } catch (e: Exception) {
            AiCoreState.Error(e.message ?: "AICore status check failed")
        }
    }

    /**
     * Triggers the one-time system download of Nano.
     *
     * Note on progress: AICore delegates the download to Android System Intelligence in
     * another process, and in practice the [DownloadStatus] flow emits no intermediate
     * progress at all. So we do NOT drive a progress bar from it — we just wait for the
     * flow to finish and then re-query the real status. Any UI should show an
     * indeterminate indicator rather than a percentage.
     */
    fun startDownload() {
        val client = model ?: run {
            _state.value = AiCoreState.Unsupported
            return
        }
        scope.launch {
            _state.value = AiCoreState.Downloading
            try {
                client.download().collect { status ->
                    if (status is DownloadStatus.DownloadFailed) {
                        _state.value = AiCoreState.Error(
                            status.e.message ?: "Nano download failed"
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = AiCoreState.Error(e.message ?: "Nano download failed")
                return@launch
            }
            // Re-query rather than trusting DownloadCompleted — the authoritative
            // answer is whatever checkStatus() reports afterwards.
            _state.value = queryStatus()
        }
    }
}
