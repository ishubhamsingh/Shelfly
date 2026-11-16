package dev.ishubhamsingh.shelfly.spike

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Thin wrapper around MediaPipe LlmInference for the Phase-0 function-calling spike.
 * Not thread-safe; call all methods from the same coroutine context.
 */
internal class GemmaSpike(context: Context, modelPath: String) : AutoCloseable {

    private val inference: LlmInference

    init {
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setMaxTopK(1)
            .build()
        inference = LlmInference.createFromOptions(context, options)
    }

    /**
     * Runs a single turn. Returns the raw model response string.
     * Blocking — call inside withContext(Dispatchers.Default).
     */
    fun runBlocking(prompt: String): String =
        inference.generateResponse(prompt)

    override fun close() {
        inference.close()
    }
}

// ── Tool definitions ──────────────────────────────────────────────────────────

internal val TOOL_DEFINITIONS = """
[
  {
    "name": "addItem",
    "description": "Add a new item to the shelf with a specific expiry date.",
    "parameters": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "description": "Human-readable name of the item, e.g. 'Whole milk'."
        },
        "expiryDate": {
          "type": "string",
          "description": "ISO-8601 date string, e.g. '2026-05-17'. Resolve relative dates like 'tomorrow' or 'next Friday' to absolute dates."
        },
        "category": {
          "type": "string",
          "enum": ["FOOD", "MEDICINE", "COSMETIC", "OTHER"],
          "description": "Item category."
        },
        "qty": {
          "type": "number",
          "description": "Optional numeric quantity."
        },
        "unit": {
          "type": "string",
          "description": "Optional unit, e.g. 'g', 'ml', 'tablets'."
        },
        "notes": {
          "type": "string",
          "description": "Optional freeform notes."
        }
      },
      "required": ["name", "expiryDate", "category"]
    }
  },
  {
    "name": "listExpiringSoon",
    "description": "List items expiring within the next N days.",
    "parameters": {
      "type": "object",
      "properties": {
        "withinDays": {
          "type": "integer",
          "description": "How many days ahead to look. Use 7 for 'this week', 30 for 'this month', etc."
        }
      },
      "required": ["withinDays"]
    }
  }
]
""".trimIndent()

// ── Prompt builder ────────────────────────────────────────────────────────────

internal fun buildSystemPrompt(todayIso: String): String = """
You are Shelfly, a smart shelf-tracking assistant. Today's date is $todayIso.

You have access to these functions:
$TOOL_DEFINITIONS

When the user asks you to do something, respond with ONLY a JSON function call — no prose, no markdown, no explanation.
Use this exact format:
{"function": "<name>", "args": {<args object>}}

If the request does not match any function, respond with:
{"function": null, "reason": "<short explanation>"}
""".trimIndent()

internal fun buildPrompt(systemPrompt: String, userInput: String): String =
    "<start_of_turn>user\n$systemPrompt\n\nUser request: $userInput<end_of_turn>\n<start_of_turn>model\n"

// ── Response parser ───────────────────────────────────────────────────────────

internal data class ParsedCall(
    val functionName: String?,
    val args: JSONObject?,
    val reason: String?,
    val rawJson: String,
    val parseError: String?,
)

internal fun parseResponse(raw: String): ParsedCall {
    val trimmed = raw.trim()
    // Find the outermost JSON object
    val jsonStart = trimmed.indexOf('{')
    val jsonEnd = trimmed.lastIndexOf('}')
    if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
        return ParsedCall(null, null, null, trimmed, "No JSON object found in response")
    }
    val jsonStr = trimmed.substring(jsonStart, jsonEnd + 1)
    return try {
        val obj = JSONObject(jsonStr)
        val name = if (obj.isNull("function")) null else obj.optString("function").takeIf { it.isNotBlank() }
        val args = obj.optJSONObject("args")
        val reason = obj.optString("reason").takeIf { it.isNotBlank() }
        ParsedCall(name, args, reason, jsonStr, null)
    } catch (e: Exception) {
        ParsedCall(null, null, null, jsonStr, e.message ?: "JSON parse error")
    }
}

// ── Spike test prompts ────────────────────────────────────────────────────────

internal data class SpikePrompt(val label: String, val input: String)

internal val SPIKE_PROMPTS = listOf(
    SpikePrompt("Simple add",          "add milk expiring tomorrow"),
    SpikePrompt("Query week",          "what's expiring this week"),
    SpikePrompt("Add with qty/unit",   "add 6 vitamin D tablets, expires next March"),
    SpikePrompt("Category ambiguous",  "add my sunscreen, expires in 2 years"),
    SpikePrompt("Relative Friday",     "add bread expiring this Friday"),
    SpikePrompt("Add medicine",        "I bought ibuprofen, best before December 2027"),
    SpikePrompt("Query month",         "show me what expires in the next 30 days"),
    SpikePrompt("Empty-ish",           "do something"),
    SpikePrompt("Off-topic",           "what's the weather like today?"),
    SpikePrompt("Mixed language",      "add पनीर, expires next week"),
)
