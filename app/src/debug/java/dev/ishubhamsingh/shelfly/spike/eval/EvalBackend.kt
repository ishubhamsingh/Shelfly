package dev.ishubhamsingh.shelfly.spike.eval

import android.graphics.Bitmap

/**
 * One inference backend under evaluation.
 *
 * Implementations must be usable from a background dispatcher and must not assume
 * they are the only backend alive — the eval screen loads and closes them one at a
 * time so a 2.6 GB Gemma engine is never resident alongside another.
 */
interface EvalBackend : AutoCloseable {
    /** Display name, e.g. "AICore (Gemini Nano)". */
    val name: String

    /**
     * Human-readable readiness string, shown before any run so an UNAVAILABLE
     * backend is obvious rather than surfacing as 12 failed cases.
     * Returns null when the backend is ready to run.
     */
    suspend fun unavailableReason(): String?

    /**
     * Runs one turn. Returns raw model text. Throws on inference failure.
     *
     * [images] are passed before the text prompt, mirroring production's ordering.
     * Backends that cannot do vision should throw rather than silently ignoring them —
     * a text-only answer scored against an image case would look like a model failure
     * when it is really an unsupported capability.
     */
    suspend fun generate(
        systemPrompt: String,
        userInput: String,
        images: List<Bitmap> = emptyList(),
    ): String
}

/** Per-case timing + output, backend-agnostic. */
data class EvalRun(
    val case: EvalCase,
    val raw: String,
    val parsed: ParsedEval,
    val score: CaseScore,
    val latencyMs: Long,
)
