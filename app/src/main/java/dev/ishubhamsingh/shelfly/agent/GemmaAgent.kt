package dev.ishubhamsingh.shelfly.agent

import android.content.Context
import android.util.Log
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
    private val aiCoreManager: AiCoreManager,
    private val tiler: LabelTiler,
    private val catalog: AgentToolCatalog,
) {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var loadedModelPath: String? = null

    // Dedicated single thread for all LiteRT-LM work — never competes with Compose's thread pool
    private val inferenceDispatcher = Executors
        .newSingleThreadExecutor { r -> Thread(r, "gemma-inference").apply { isDaemon = true } }
        .asCoroutineDispatcher()


    /** Pre-loads whichever backend will serve the next turn. */
    suspend fun warmUp() = mutex.withLock {
        if (aiCoreManager.isReadyNow()) {
            aiCoreManager.warmUp()
            return@withLock
        }
        val modelState = modelManager.state.value
        if (modelState !is ModelState.Ready) return@withLock
        withContext(inferenceDispatcher) {
            runCatching { getOrCreateEngine(modelState.modelPath) }
        }
    }

    suspend fun run(userInput: String, imagePaths: List<String> = emptyList()): AgentTurn = mutex.withLock {
        val today = LocalDate.now().toString()
        val systemPrompt =
            if (imagePaths.isNotEmpty()) buildLabelScanPrompt()
            else buildSystemPrompt(today)
        val promptText = if (imagePaths.isNotEmpty() && userInput.isBlank())
            "Add this item to my shelf."
        else userInput

        // Nano first: zero app-side bytes, faster, and measured at least as accurate as
        // the downloaded model. Falls through to LiteRT on any failure — readiness can
        // lapse between the check and the call, so this is a real path, not paranoia.
        // Tile once, share across backends: the encoder resizes to a fixed square, so
        // whole photos waste almost all of that budget on background. Computed here
        // rather than at capture time so the tiling strategy stays with the model.
        // Crops per photo shrink as photo count grows: each tile costs a fixed ~256-400
        // tokens, so 3 photos x 3 tiles would push past LiteRT's 4096 budget and eat
        // into Nano's 8192. Prefer a few well-chosen tiles over many mediocre ones.
        val cropsPerPhoto = when {
            imagePaths.size <= 1 -> 3
            imagePaths.size == 2 -> 2
            else                 -> 1
        }
        val tiles: List<ByteArray> =
            if (imagePaths.isEmpty()) emptyList()
            else if (!TILING_ENABLED) imagePaths.map { java.io.File(it).readBytes() }
            else imagePaths.flatMap { path ->
                runCatching { tiler.tile(path, maxCrops = cropsPerPhoto) }
                    .getOrElse {
                        Log.w(TAG, "tiling failed for $path; sending original", it)
                        listOf(java.io.File(path).readBytes())
                    }
            }.take(MAX_TILES_PER_TURN)

        val aiCoreReady = runCatching { aiCoreManager.isReadyNow() }.getOrDefault(false)
        val raw: String? = if (aiCoreReady) {
            runCatching {
                if (tiles.isEmpty() || !TWO_STAGE_ENABLED) {
                    aiCoreManager.generate(systemPrompt, promptText, tiles)
                } else {
                    // Two passes: read the tiles, then structure the transcript with no
                    // images in context. One-pass reads correctly and then corrupts the
                    // value on the way into the field.
                    val transcript = aiCoreManager.generate(
                        buildReadPrompt(), promptText, tiles,
                    )
                    Log.i(TAG, "read stage (AICore): ${transcript.take(400)}")
                    aiCoreManager.generate(
                        buildStructurePrompt(transcript), promptText, emptyList(),
                    )
                }
            }.onFailure { Log.w(TAG, "AICore turn failed, falling back to LiteRT", it) }
                .getOrNull()
        } else null

        if (raw != null) {
            Log.i(TAG, "turn served by AICore (Nano)")
            return interpret(raw)
        }
        Log.i(TAG, "turn served by LiteRT (aiCoreReady=$aiCoreReady)")

        // ── LiteRT fallback ──
        val modelState = modelManager.state.value
        if (modelState !is ModelState.Ready) {
            return AgentTurn.Failure("model_not_ready")
        }

        return withContext(inferenceDispatcher) {
            try {
                val llm = getOrCreateEngine(modelState.modelPath)

                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    // Label scanning is transcription: we want the SAME answer every
                    // time for the same photos. At temp=1.0/topK=64 identical inputs
                    // produced "330 ml" on one run and "150 g" on the next, which reads
                    // as a regression but is just sampling noise.
                    //
                    // Free-form text keeps the looser sampler — Phase 0 measured no
                    // accuracy difference there, and topK=1 was previously too
                    // restrictive for reasoning about image content.
                    samplerConfig = if (imagePaths.isNotEmpty())
                        SamplerConfig(topK = 8, topP = 0.9, temperature = 0.1)
                    else
                        SamplerConfig(topK = 64, topP = 0.95, temperature = 1.0),
                )
                /** One isolated turn. Fresh conversation each time so stage 2 does not
                 *  inherit the images or the reading instructions from stage 1. */
                fun ask(system: String, parts: List<Content>): String {
                    val convo = llm.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(system),
                            samplerConfig     = conversationConfig.samplerConfig,
                        )
                    )
                    return try {
                        convo.sendMessage(Contents.of(*parts.toTypedArray())).toString()
                    } finally {
                        convo.close()
                    }
                }

                val litertRaw = if (tiles.isEmpty() || !TWO_STAGE_ENABLED) {
                    ask(systemPrompt, buildList {
                        tiles.forEach { add(Content.ImageBytes(it)) }
                        add(Content.Text(promptText))
                    })
                } else {
                    // Images first, then text — PNG bytes, which the native decoder
                    // handles reliably.
                    val readParts = buildList<Content> {
                        tiles.forEach { add(Content.ImageBytes(it)) }
                        add(Content.Text(promptText))
                    }
                    val transcript = ask(buildReadPrompt(), readParts)
                    Log.i(TAG, "read stage (LiteRT): ${transcript.take(400)}")
                    ask(buildStructurePrompt(transcript), listOf(Content.Text(promptText)))
                }

                interpret(litertRaw)
            } catch (e: Exception) {
                // Swallowing this made every LiteRT failure look identical in the UI
                // ("I'm a bit stuck") with nothing to diagnose from.
                Log.e(TAG, "LiteRT turn failed (images=${imagePaths.size})", e)
                AgentTurn.Failure(e.message ?: "inference_error")
            }
        }
    }

    /**
     * Turns raw model text into an [AgentTurn]. Shared by both backends so that
     * confirmation interception and tool execution behave identically no matter which
     * model produced the JSON.
     */
    private suspend fun interpret(raw: String): AgentTurn {
        val parsed = parseResponse(raw)
        return when {
            parsed.parseError != null -> {
                // Without the raw text a parse failure is undiagnosable — it looks
                // identical whether the model returned prose, was truncated by the
                // input cap, or returned nothing at all.
                Log.w(TAG, "unparseable (${parsed.parseError}); len=${raw.length} raw=${raw.take(600)}")
                AgentTurn.Failure("model_output_unparseable: ${parsed.parseError}")
            }

            parsed.functionName == null ->
                AgentTurn.Reply(parsed.reason ?: raw.trim())

            else -> {
                val args = parsed.args ?: JSONObject()
                Log.i(TAG, "tool=${parsed.functionName} args=$args observed=${parsed.observed}")
                // Intercept add and destructive operations — let the UI confirm first.
                if (parsed.functionName in PENDING_FUNCTIONS) {
                    AgentTurn.Pending(parsed.functionName, args)
                } else {
                    val result = catalog.execute(parsed.functionName, args)
                    AgentTurn.ToolCall(
                        functionName = parsed.functionName,
                        args         = args,
                        result       = result,
                        reply        = syntheticReply(parsed.functionName, args, result),
                    )
                }
            }
        }
    }

    /**
     * Returns the actual DB item name for a user-supplied name query.
     * Used to show the real stored name in destructive-action confirmation cards.
     */
    suspend fun lookupItemName(query: String): String? = catalog.lookupItemName(query)

    /**
     * Execute a previously intercepted [AgentTurn.Pending] after the user confirmed.
     * Does NOT hold the inference mutex — catalog calls are safe without it.
     */
    suspend fun confirm(functionName: String, args: JSONObject): AgentTurn {
        return try {
            val result = catalog.execute(functionName, args)
            val reply  = syntheticReply(functionName, args, result)
            AgentTurn.ToolCall(functionName, args, result, reply)
        } catch (e: Exception) {
            AgentTurn.Failure(e.message ?: "confirm_error")
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
        // visionBackend MUST be set explicitly for image inference to work.
        // Without it the engine allocates no vision resources and dereferences a
        // null pointer when an image is present (SIGSEGV fault addr 0x0).
        // Edge Gallery (same LiteRT-LM 0.11.0) uses GPU for both backends.
        // maxNumTokens = 4096: with 2 images the budget is ~1572 tokens
        // (system ~700 + 2×image ~430 + text ~12), leaving only ~476 headroom at 2048.
        // 4096 is safe now that visionBackend is set — the earlier SIGSEGV at 4096
        // was caused by the engine having no vision buffers allocated, not token count.
        val config = EngineConfig(
            modelPath     = modelPath,
            backend       = Backend.GPU(),
            visionBackend = Backend.GPU(),
            maxNumTokens  = 4096,
            cacheDir      = context.cacheDir.absolutePath,
        )
        return Engine(config).also { e ->
            e.initialize()
            engine = e
            loadedModelPath = modelPath
        }
    }
}

private const val TAG = "GemmaAgent"

/** Hard ceiling on images per turn, whatever the tiler produces. ~256-400 tokens each. */
private const val MAX_TILES_PER_TURN = 6

/**
 * Tiling and two-stage extraction are OFF pending a repeatable eval.
 *
 * Both are well-motivated — the encoder's fixed input size means cropping is the only
 * way to spend more pixels on small print, and separating reading from structuring is
 * the documented fix for schema-constrained decoding damaging transcription. But
 * measured on the reference photos, each made the result worse, not better:
 *
 *   no tiling, single call   -> name+category+date correct, quantity wrong
 *   + tiling                 -> date wrong ("206-09")
 *   + tiling + two-stage     -> name, date and quantity all wrong
 *
 * Tiling surfaced MORE readable text, including "150 150 1703 082" adjacent to the
 * date, so it added distractors faster than it added detail. Turn these back on only
 * against a golden set where the effect can be measured over several runs — single
 * manual runs cannot separate a real change from sampling variance.
 */
private const val TILING_ENABLED = false
private const val TWO_STAGE_ENABLED = false

// Functions that require user confirmation before execution.
private val PENDING_FUNCTIONS = setOf(
    "addItem", "addItemFromMfg",
    "markConsumedByName", "deleteItemByName",
)

// ── Prompt construction ───────────────────────────────────────────────────────

/**
 * Focused prompt for camera/label turns. Deliberately exposes ONE tool.
 *
 * Why not reuse the full catalogue: measured on real can photos, the 11-tool prompt made
 * Gemma E2B agonise between addItem and addItemFromMfg — its own `reason` read
 * "assuming no explicit expiry date is clearly visible or preferred over
 * mfgDate/bestBeforeMonths" — and it then invented `mfgDate 2080-06-26`. A single-tool
 * prompt of roughly this shape read the same photos correctly (330 ml, 2026-10-19).
 *
 * On a photo turn the only sensible action is "add what you can see", so the other ten
 * tools are pure distraction.
 */
/**
 * STAGE 1 of the photo path: read, do not decide.
 *
 * The model reliably transcribes what is on the tiles — measured `EXP: 19/10/26` read
 * correctly — but mangles the value on the way into a JSON field in the same breath
 * ("206-09"). Splitting the two means the vision call never has to hold a schema in
 * mind, and the schema call never has to look at a photograph.
 */
private fun buildReadPrompt(): String = """
You are reading photographs of ONE product, taken from different angles.

Transcribe what you can actually read. Do not interpret, summarise or fill gaps.

For each image, list every piece of text you can make out, especially small stamped or
embossed codes near the base, rim, neck or seam. Copy them character for character,
including prefixes like EXP, BB, MFD, USP and any slashes.

If something is illegible, write "illegible" rather than guessing at it.

Reply as a plain list, one line per piece of text. No JSON, no commentary.
""".trimIndent()

/**
 * STAGE 2 of the photo path: structure a transcript. Text-only — no images — so the
 * model spends all its attention on mapping strings to fields.
 */
private fun buildStructurePrompt(transcript: String): String = """
Below is text transcribed from photographs of one product.

--- TRANSCRIPT ---
$transcript
--- END ---

Return exactly one JSON object, no markdown, no trailing commas:
{ "function": "addItem", "args": { ... } }

addItem(name*, expiryDate*, category*, amount, notes)   (* = required)

name : the product name only, from the transcript. No quantity, no slogans.

expiryDate : ISO-8601 YYYY-MM-DD.
  Use ONLY a date that appears in the transcript after EXP, EXPIRY, BB or BEST BEFORE.
  Two-digit years are 20xx. Day comes first in DD/MM/YY.
  "EXP: 19/10/26" -> "2026-10-19"
  Ignore MFD / MF / MFG dates — those are manufacture, not expiry.
  If the transcript has no expiry date, OMIT this field. Never invent or reshape one.

category : "FOOD" | "MEDICINE" | "COSMETIC" | "OTHER". Drinks are FOOD.

amount : net quantity as "<number> <unit>", unit one of
  count / g / kg / mg / ml / L / tablets / pieces.
  Take it from the transcript only. Ignore unit prices ("USP Rs. 0.15/ml"),
  batch codes and nutrition figures. Omit if the transcript has no net quantity.

Copy values verbatim from the transcript. If a field is not in the transcript, omit it.
""".trimIndent()

private fun buildLabelScanPrompt(): String = """
You are Shelfly, a shelf-tracking assistant.

You are given photographs of ONE product, taken from different angles.

## Output
Respond with exactly one JSON object — no markdown, no trailing commas.
Fill the fields IN THIS ORDER:

{
  "observed": "<for each image in turn, list the text you can actually read on it,
                especially any stamped or embossed codes near the base, rim or neck>",
  "function": "addItem",
  "args": { ... }
}

Write "observed" FIRST and read carefully before filling args — small stamped dates
are easy to miss on a first pass. Then use only what you wrote there.

## The only tool
addItem(name*, expiryDate*, category*, amount, notes)   (* = required)

name : the product name only. No quantity, no unit, no marketing words.
       e.g. "Diet Coke", "Whole Milk", "Ibuprofen"

expiryDate : ISO-8601 (YYYY-MM-DD). The expiry printed on the pack, transcribed
       exactly as printed. Do NOT convert it to a duration. Do NOT round it.
       "EXP 19/10/26" -> "2026-10-19".  "BEST BEFORE 20 SEP 2026" -> "2026-09-20".
       Dates are usually stamped on the base, rim, neck or back — check every image.
       If a manufacture date and a shelf life are printed but no expiry date,
       add them together to get expiryDate.

category : "FOOD" | "MEDICINE" | "COSMETIC" | "OTHER"
       Drinks, including soft drinks and water, are FOOD.

amount : the NET QUANTITY printed on the pack, as "<number> <unit>",
       unit one of: count / g / kg / mg / ml / L / tablets / pieces.
       e.g. "330 ml", "500 g", "30 tablets".
       Never describe the container — "1 can", "1 bottle", "1 pack" are WRONG.
       Ignore other printed numbers: unit price ("USP Rs. 0.15/ml"), per-100
       nutrition figures, batch codes, barcodes, MRP.
       Omit this field if no net quantity is printed.

Read what is actually printed. Do not guess a value you cannot see.
Never output today's date, or any date you did not read off the pack.
If no expiry date is legible in any image, omit expiryDate entirely.
""".trimIndent()

private fun buildSystemPrompt(todayIso: String): String = """
You are Shelfly, a shelf-tracking assistant. Today: $todayIso.

## Output schema
Respond with exactly one JSON object — no prose, no markdown.
{ "function": <tool_name | null>, "args": { <param: value, ...> }, "reason": <string | null> }
  - function : name of the tool to call, or null if no tool applies
  - args     : object (never an array) containing the tool's parameters
  - reason   : plain-language explanation, only when function is null

## Tools
addItem(name*, expiryDate* OR expiryIn*, category*, amount, notes)
  Add an item.
  Use expiryDate when a date is printed on a label or stated as a date.
  Use expiryIn when the user stated a duration ("in 10 days").
  Give exactly one of the two — never both.

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

markConsumedByName(name*)
  Find an item by name and mark it as consumed. Prefer this over markConsumed when the user refers to an item by name.

updateExpiry(id*, expiryIn*)
  Change the expiry date of an existing item. expiryIn is the new duration from today.

deleteItem(id*)
  Permanently delete an item.

deleteItemByName(name*)
  Find an item by name and permanently delete it. Prefer this over deleteItem when the user refers to an item by name.

(* = required parameter)

## Field definitions

name : string
  Item name only — no quantity, no unit. Use the name the user stated, do not substitute.
  Correct: "Eggs" / "Sunscreen" / "Vitamin E"
  Wrong  : "1 dozen eggs" / "500ml milk" / a different product name the user did not say

amount : string  (optional)
  The NET QUANTITY, as "<number> <unit>" where unit is one of:
  count / g / kg / mg / ml / L / tablets / pieces.
  From text: copy the exact number and unit the user said — do not round or convert.
  From a label: read the printed net volume or weight, e.g. "330 ml", "500 g".
  Never describe the container — "1 can", "1 bottle", "1 pack" are WRONG.
  Packs also print numbers that are NOT the quantity. Ignore them:
    - unit price, e.g. "USP Rs. 0.15/ml", "£0.28/100g"  -> NOT "0.15 ml"
    - nutrition per-100 figures, batch codes, barcodes, MRP
  Take only the pack's net content, usually next to the product name.
  Count conversions: "1 dozen" → "12 count", "1 half-dozen" → "6 count".
  Omit this field entirely if no quantity is stated or printed.

category : enum
  "FOOD"     — food and drinks
  "MEDICINE" — medicines, supplements, vitamins, pills
  "COSMETIC" — skincare, beauty, personal care, sunscreen
  "OTHER"    — anything else

expiryDate : string
  ISO-8601 (YYYY-MM-DD). The date PRINTED on the label, transcribed exactly.
  Do NOT convert it to a duration and do NOT round it.
  "19/10/26" → "2026-10-19".  "BEST BEFORE 20 SEP 2026" → "2026-09-20".
  Prefer this whenever a date is visible or stated.

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
You may receive several photographs of the SAME product from different angles.
Treat them as one item: the front usually carries the name and brand, the back,
base or rim usually carries the printed expiry date and the net quantity.
Combine everything you can read into a single tool call.

Read what is printed. Do not estimate, round, or infer a value you cannot see —
if the expiry date is legible, transcribe it into expiryDate rather than working
out how far away it is.

Prefer addItem with expiryDate when an expiry date is printed.
Prefer addItemFromMfg when you see a manufacture date and a best-before window
but no printed expiry date.
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
        "markConsumedByName" -> {
            val itemName = result.optJSONObject("item")?.optString("name") ?: args.optString("name")
            "Marked $itemName as consumed."
        }
        "deleteItemByName" -> {
            val itemName = result.optString("deletedName").takeIf { it.isNotBlank() } ?: args.optString("name")
            "Deleted $itemName from your shelf."
        }
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
    /**
     * What the model says it could read off the images, emitted before args so it reads
     * carefully before committing to values. Diagnostic only — never shown to the user,
     * but it makes "why did it get this wrong" answerable from a log line.
     */
    val observed: String? = null,
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
        ParsedCall(name, args, reason, null, obj.optString("observed").takeIf { it.isNotBlank() })
    } catch (e: Exception) {
        ParsedCall(null, null, null, e.message ?: "JSON parse error")
    }
}

/**
 * Strips trailing commas before a closing brace/bracket — `{"a": 1,}` — which are legal
 * in neither JSON nor org.json. Observed from Gemma E2B on multi-image turns; without
 * this the whole turn is discarded over a comma.
 */
private fun stripTrailingCommas(s: String): String {
    val sb = StringBuilder(s.length)
    var inString = false
    var escape = false
    for (i in s.indices) {
        val ch = s[i]
        when {
            escape -> escape = false
            ch == '\\' -> escape = true
            ch == '"' -> inString = !inString
        }
        if (!inString && ch == ',') {
            // Look ahead past whitespace: a comma followed by } or ] is trailing.
            var j = i + 1
            while (j < s.length && s[j].isWhitespace()) j++
            if (j < s.length && (s[j] == '}' || s[j] == ']')) continue
        }
        sb.append(ch)
    }
    return sb.toString()
}

private fun repairJson(raw: String): String {
    val s     = stripTrailingCommas(raw)
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
