package dev.ishubhamsingh.shelfly.agent

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
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
    private var engine: Engine? = null
    private var loadedModelPath: String? = null

    // Dedicated single thread for all LiteRT-LM work — never competes with Compose's thread pool
    private val inferenceDispatcher = Executors
        .newSingleThreadExecutor { r -> Thread(r, "gemma-inference").apply { isDaemon = true } }
        .asCoroutineDispatcher()


    /** Pre-loads the engine so the first real inference has no cold-start lag. */
    suspend fun warmUp() = mutex.withLock {
        val modelState = modelManager.state.value
        if (modelState !is ModelState.Ready) return@withLock
        withContext(inferenceDispatcher) {
            runCatching { getOrCreateEngine(modelState.modelPath) }
        }
    }

    suspend fun run(userInput: String, imagePath: String? = null): AgentTurn = mutex.withLock {
        val modelState = modelManager.state.value
        if (modelState !is ModelState.Ready) {
            return AgentTurn.Failure("model_not_ready")
        }

        return withContext(inferenceDispatcher) {
            try {
                val llm = getOrCreateEngine(modelState.modelPath)
                val today = LocalDate.now().toString()
                val systemPrompt = buildSystemPrompt(today)

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    samplerConfig     = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0),
                )
                val conversation = llm.createConversation(conversationConfig)

                // If an image path is provided, pass it directly to the model.
                // gemma-4-E2B-it is multimodal; Content.ImageFile lets the engine
                // decode the JPEG itself (Content.ImageBytes crashes because it
                // expects raw pixels, not compressed data).
                val promptText = if (imagePath != null && userInput.isBlank())
                    "Add this item to my shelf."
                else userInput

                val userContent = if (imagePath != null)
                    Contents.of(Content.ImageFile(imagePath), Content.Text(promptText))
                else
                    Contents.of(promptText)

                val raw = try {
                    conversation.sendMessage(userContent).toString()
                } finally {
                    conversation.close()
                }

                val parsed = parseResponse(raw)

                when {
                    parsed.parseError != null ->
                        AgentTurn.Failure("model_output_unparseable: ${parsed.parseError}")

                    parsed.functionName == null ->
                        AgentTurn.Reply(parsed.reason ?: raw.trim())

                    else -> {
                        val args = parsed.args ?: JSONObject()
                        val result = catalog.execute(parsed.functionName, args)
                        val reply = syntheticReply(parsed.functionName, args, result)

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
        engine?.close()
        engine = null
        loadedModelPath = null
    }

    private fun getOrCreateEngine(modelPath: String): Engine {
        if (engine != null && loadedModelPath == modelPath) return engine!!
        engine?.close()
        val config = EngineConfig(
            modelPath = modelPath,
            backend   = Backend.GPU(),
            cacheDir  = context.cacheDir.absolutePath,
        )
        return Engine(config).also { e ->
            e.initialize()
            engine = e
            loadedModelPath = modelPath
        }
    }
}

// ── Prompt construction ───────────────────────────────────────────────────────

private fun buildSystemPrompt(todayIso: String): String = """
You are Shelfly, a shelf-tracking assistant. Today: $todayIso.

## Output schema
Respond with exactly one JSON object — no prose, no markdown.
{ "function": <tool_name | null>, "args": { <param: value, ...> }, "reason": <string | null> }
  - function : name of the tool to call, or null if no tool applies
  - args     : object (never an array) containing the tool's parameters
  - reason   : plain-language explanation, only when function is null

## Tools
addItem(name*, expiryIn*, category*, amount, notes)
  Add an item. expiryIn is the expiry duration the user stated.

addItemFromMfg(name*, mfgDate*, bestBeforeMonths*, category*, amount, notes)
  Add an item given its manufacture date and shelf-life in months.

listExpiringSoon(withinDays*)
  Return items expiring within the next N days.

listExpired()
  Return all items already past their expiry date.

listByCategory(category*)
  Return all items belonging to one category.

getItem(id*)
  Return a single item by its UUID.

markConsumed(id*)
  Mark an item as consumed and remove it from the active shelf.

updateExpiry(id*, expiryIn*)
  Change the expiry date of an existing item. expiryIn is the new duration from today.

deleteItem(id*)
  Permanently delete an item.

(* = required parameter)

## Field definitions

name : string
  Item name only — no quantity, no unit. Use the name the user stated, do not substitute.
  Correct: "Eggs" / "Sunscreen" / "Vitamin E"
  Wrong  : "1 dozen eggs" / "500ml milk" / a different product name the user did not say

amount : string  (optional)
  Quantity and unit as a single string, exactly as the user stated.
  Format: "<number> <unit>" where unit is one of: count / g / kg / mg / ml / L / tablets / pieces.
  Copy the exact number and unit the user said — do not round or convert.
  Count conversions: "1 dozen" → "12 count", "1 half-dozen" → "6 count".
  Omit this field entirely if the user did not mention a quantity.

category : enum
  "FOOD"     — food and drinks
  "MEDICINE" — medicines, supplements, vitamins, pills
  "COSMETIC" — skincare, beauty, personal care, sunscreen
  "OTHER"    — anything else

expiryIn : string
  The expiry duration as stated by the user: "<number> <unit>".
  unit must be one of: days / weeks / months / years.
  Copy the EXACT number the user said — do not round or snap to a nearby value.
  "10 days" → "10 days", NOT "1 week". "3 weeks" → "3 weeks", NOT "1 month".
  Convert word-numbers to digits: "one" → 1, "half" → 0.5, "one and a half" → 1.5.

mfgDate : string  ISO-8601 manufacture date (YYYY-MM-DD)

bestBeforeMonths : integer  shelf-life in whole months after manufacture

withinDays : integer  number of days ahead to check

id : string  UUID of the target item

## Label scanning
When the message starts with "Product label text:" the text that follows was
extracted by OCR from a product photo. Read it to identify the item name,
expiry date or shelf life, and category, then call the appropriate tool.
Prefer addItem when you can determine an expiry date or duration.
Prefer addItemFromMfg when you see a manufacture date and a best-before window.
""".trimIndent()

// ── Synthetic reply ───────────────────────────────────────────────────────────

private fun syntheticReply(name: String, args: JSONObject, result: JSONObject): String {
    val ok = result.optBoolean("ok", false)
    if (!ok) return "Something went wrong — ${result.optString("error", "unknown error")}."
    return when (name) {
        "addItem", "addItemFromMfg" -> {
            val itemName = result.optJSONObject("item")?.optString("name") ?: args.optString("name")
            "Added $itemName to your shelf."
        }
        "listExpiringSoon" -> {
            val count = result.optInt("count", 0)
            val days  = args.optInt("withinDays", 7)
            when (count) {
                0    -> "Nothing is expiring in the next $days days."
                1    -> "1 item is expiring in the next $days days."
                else -> "$count items are expiring in the next $days days."
            }
        }
        "listExpired" -> when (val count = result.optInt("count", 0)) {
            0    -> "Nothing on your shelf has expired."
            1    -> "1 item has already expired."
            else -> "$count items have already expired."
        }
        "listByCategory" -> {
            val count    = result.optInt("count", 0)
            val category = args.optString("category").lowercase()
            when (count) {
                0    -> "No $category items on your shelf."
                1    -> "1 $category item on your shelf."
                else -> "$count $category items on your shelf."
            }
        }
        "getItem" -> {
            val item = result.optJSONObject("item")
            if (item == null) "Item not found."
            else {
                val itemName = item.optString("name")
                val days     = item.optInt("daysUntilExpiry")
                when {
                    days > 0  -> "$itemName expires in $days days."
                    days == 0 -> "$itemName expires today."
                    else      -> "$itemName expired ${-days} days ago."
                }
            }
        }
        "markConsumed"  -> "Marked as consumed."
        "updateExpiry"  -> {
            val itemName = result.optJSONObject("item")?.optString("name") ?: "Item"
            "Updated expiry for $itemName."
        }
        "deleteItem"    -> "Item deleted from your shelf."
        else            -> "Done."
    }
}

// ── Response parser ───────────────────────────────────────────────────────────

private data class ParsedCall(
    val functionName: String?,
    val args: JSONObject?,
    val reason: String?,
    val parseError: String?,
)

private fun parseResponse(raw: String): ParsedCall {
    val jsonStart = raw.indexOf('{')
    if (jsonStart == -1) return ParsedCall(null, null, null, "No JSON object found")
    val repaired = repairJson(raw.substring(jsonStart))
    return try {
        val obj = JSONObject(repaired)
        val name = if (obj.isNull("function")) null
                   else obj.optString("function").takeIf { it.isNotBlank() }
        // model sometimes emits "args":[{...}] instead of "args":{...}
        val args = obj.optJSONObject("args")
            ?: obj.optJSONArray("args")?.optJSONObject(0)
        val reason = obj.optString("reason").takeIf { it.isNotBlank() }
        ParsedCall(name, args, reason, null)
    } catch (e: Exception) {
        ParsedCall(null, null, null, e.message ?: "JSON parse error")
    }
}

private fun repairJson(s: String): String {
    val sb    = StringBuilder(s)
    val stack = ArrayDeque<Char>()
    var inString = false
    var escape   = false
    for (ch in s) {
        when {
            escape          -> escape = false
            ch == '\\'      -> escape = true
            ch == '"'       -> inString = !inString
            inString        -> Unit
            ch == '{'       -> stack.addLast('}')
            ch == '['       -> stack.addLast(']')
            ch == '}' || ch == ']' -> if (stack.isNotEmpty()) stack.removeLast()
        }
    }
    while (stack.isNotEmpty()) sb.append(stack.removeLast())
    return sb.toString()
}
