package dev.ishubhamsingh.shelfly.spike.eval

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

/**
 * LiteRT-LM backend — used for BOTH Gemma E2B (current shipping model) and E4B.
 * The two differ only by [modelPath], so a single implementation covers both tiers.
 *
 * Engine config intentionally mirrors production GemmaAgent.getOrCreateEngine():
 * same backends, same token budget, same sampler. If we tuned the spike differently
 * the numbers would not transfer to the shipped agent.
 */
class LiteRtBackend(
    private val context: Context,
    private val modelPath: String,
    override val name: String,
    /**
     * When false, use production's sampler (topK=64, topP=0.95, temp=1.0).
     * When true, match what the AICore backend was given (topK=16, temp=0.2).
     *
     * This exists to settle a confound: the AICore and LiteRT runs were not sampled
     * identically, so any accuracy gap between them was partly a sampler artifact
     * rather than a model difference. It also tests something production cares about
     * directly — whether temp=1.0 is costing accuracy on structured extraction.
     */
    private val lowTemp: Boolean = false,
) : EvalBackend {

    private var engine: Engine? = null

    override suspend fun unavailableReason(): String? {
        val f = File(modelPath)
        return when {
            !f.exists() -> "model file not found at $modelPath"
            f.length() < 100_000_000L -> "file too small (${f.length() / 1_000_000} MB) — likely a partial download"
            else -> null
        }
    }

    private fun getOrCreateEngine(): Engine {
        engine?.let { return it }
        val config = EngineConfig(
            modelPath     = modelPath,
            backend       = Backend.GPU(),
            visionBackend = Backend.GPU(),
            maxNumTokens  = 4096,
            cacheDir      = context.cacheDir.absolutePath,
        )
        return Engine(config).also {
            it.initialize()
            engine = it
        }
    }

    override suspend fun generate(
        systemPrompt: String,
        userInput: String,
        images: List<android.graphics.Bitmap>,
    ): String {
        val llm = getOrCreateEngine()
        val conversation = llm.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = if (lowTemp)
                    SamplerConfig(topK = 16, topP = 0.95, temperature = 0.2)
                else
                    SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
            )
        )
        // Images first, then text — same ordering production uses. PNG bytes, because
        // that is what the native decoder reliably handles (see GemmaAgent).
        val parts = buildList<Content> {
            images.forEach { add(Content.ImageBytes(bitmapToPngBytes(it))) }
            add(Content.Text(userInput))
        }
        return try {
            conversation.sendMessage(Contents.of(*parts.toTypedArray())).toString()
        } finally {
            conversation.close()
        }
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
