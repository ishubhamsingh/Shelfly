package dev.ishubhamsingh.shelfly.agent

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: GemmaModelManager,
    private val catalog: AgentToolCatalog,
) {
    private val mutex = Mutex()
    private var inference: LlmInference? = null
    private var loadedModelPath: String? = null

    suspend fun run(userInput: String): AgentTurn = mutex.withLock {
        val modelState = modelManager.state.value
        if (modelState !is ModelState.Ready) {
            return AgentTurn.Failure("model_not_ready")
        }

        return withContext(Dispatchers.Default) {
            try {
                val llm = getOrCreateInference(modelState.modelPath)
                val today = LocalDate.now().toString()
                val systemPrompt = buildSystemPrompt(today, catalog.toolDefinitionsJson())

                // Turn 1 — function selection
                val turn1Prompt = formatPrompt(systemPrompt, userInput)
                val turn1Raw = llm.generateResponse(turn1Prompt)
                val parsed = parseResponse(turn1Raw)

                when {
                    parsed.parseError != null ->
                        AgentTurn.Failure("model_output_unparseable: ${parsed.parseError}")

                    parsed.functionName == null ->
                        AgentTurn.Reply(parsed.reason ?: turn1Raw.trim())

                    else -> {
                        val args = parsed.args ?: JSONObject()
                        val result = catalog.execute(parsed.functionName, args)

                        // Turn 2 — natural language reply
                        val turn2Prompt = formatReplyPrompt(
                            systemPrompt   = systemPrompt,
                            userInput      = userInput,
                            turn1Response  = turn1Raw.trim(),
                            functionResult = result.toString(),
                        )
                        val reply = llm.generateResponse(turn2Prompt).trim()

                        AgentTurn.ToolCall(
                            functionName = parsed.functionName,
                            args         = args,
                            result       = result,
                            reply        = reply,
                        )
                    }
                }
            } catch (e: Exception) {
                AgentTurn.Failure(e.message ?: "inference_error")
            }
        }
    }

    fun closeSession() {
        inference?.close()
        inference = null
        loadedModelPath = null
    }

    private fun getOrCreateInference(modelPath: String): LlmInference {
        if (inference != null && loadedModelPath == modelPath) return inference!!
        inference?.close()
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .setMaxTopK(1)
            .build()
        return LlmInference.createFromOptions(context, options).also {
            inference = it
            loadedModelPath = modelPath
        }
    }
}

// ── Prompt construction ───────────────────────────────────────────────────────

private fun buildSystemPrompt(todayIso: String, toolsJson: String): String = """
You are Shelfly, a smart shelf-tracking assistant. Today's date is $todayIso.

You have access to these functions:
$toolsJson

RULES:
1. Respond with ONLY a single JSON object — no prose, no markdown fences.
2. Always include ALL required fields listed in each function's "required" array.
3. Resolve relative dates ("tomorrow", "next Friday", "in 2 years") to absolute ISO-8601 dates using today's date.
4. Infer the best category from context: food → FOOD, medicine/tablets → MEDICINE, skincare/cosmetics → COSMETIC, else OTHER.
5. If the request matches a function, call it. If nothing matches, reply: {"function":null,"reason":"<short explanation>"}

Response format (pick one):
{"function":"<name>","args":{<args>}}
{"function":null,"reason":"<explanation>"}
""".trimIndent()

private fun formatPrompt(systemPrompt: String, userInput: String): String =
    "<start_of_turn>user\n$systemPrompt\n\nUser request: $userInput<end_of_turn>\n<start_of_turn>model\n"

private fun formatReplyPrompt(
    systemPrompt: String,
    userInput: String,
    turn1Response: String,
    functionResult: String,
): String = buildString {
    append("<start_of_turn>user\n")
    append(systemPrompt)
    append("\n\nUser request: ")
    append(userInput)
    append("<end_of_turn>\n")
    append("<start_of_turn>model\n")
    append(turn1Response)
    append("<end_of_turn>\n")
    append("<start_of_turn>user\n")
    append("Function result: $functionResult\n")
    append("Now respond to the user in one friendly sentence confirming what was done or answering their question. No JSON.")
    append("<end_of_turn>\n")
    append("<start_of_turn>model\n")
}

// ── Response parser (shared with spike) ──────────────────────────────────────

private data class ParsedCall(
    val functionName: String?,
    val args: JSONObject?,
    val reason: String?,
    val parseError: String?,
)

private fun parseResponse(raw: String): ParsedCall {
    val trimmed = raw.trim()
    val jsonStart = trimmed.indexOf('{')
    val jsonEnd = trimmed.lastIndexOf('}')
    if (jsonStart == -1 || jsonEnd <= jsonStart) {
        return ParsedCall(null, null, null, "No JSON object found")
    }
    val jsonStr = trimmed.substring(jsonStart, jsonEnd + 1)
    return try {
        val obj = JSONObject(jsonStr)
        val name = if (obj.isNull("function")) null
                   else obj.optString("function").takeIf { it.isNotBlank() }
        val args = obj.optJSONObject("args")
        val reason = obj.optString("reason").takeIf { it.isNotBlank() }
        ParsedCall(name, args, reason, null)
    } catch (e: Exception) {
        ParsedCall(null, null, null, e.message ?: "JSON parse error")
    }
}
